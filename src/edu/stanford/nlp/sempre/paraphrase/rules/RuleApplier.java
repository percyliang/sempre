package edu.stanford.nlp.sempre.paraphrase.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.google.common.base.Joiner;

import edu.stanford.nlp.sempre.FeatureVector;
import edu.stanford.nlp.sempre.LanguageInfo;
import edu.stanford.nlp.sempre.LanguageInfo.LanguageUtils;
import edu.stanford.nlp.sempre.LanguageInfo.WordInfo;
import edu.stanford.nlp.sempre.paraphrase.Interval;
import edu.stanford.nlp.sempre.paraphrase.ParaphraseFeatureMatcher;
import edu.stanford.nlp.sempre.paraphrase.ParaphraseUtils;
import edu.stanford.nlp.sempre.paraphrase.Aligner.AlignmentStats;
import edu.stanford.nlp.sempre.paraphrase.paralex.PhraseTable;
import edu.stanford.nlp.sempre.paraphrase.rules.LanguageExp.LangExpMatch;
import edu.stanford.nlp.sempre.paraphrase.rules.RuleApplication.ApplicationInfo;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.StringUtils;
import fig.basic.IntPair;
import fig.basic.LispTree;
import fig.basic.Option;

public abstract class RuleApplier {
  
  public static class Options {
    @Option public int verbose=1;
  }
  public static Options opts = new Options();
  
  public static final String DELETE = "Del";
  public static final String DELETE_IN_CONTEXT = "Del_ctxt";
  public static final String DELETE_V_SEM="Del_v_sem";
  public static final String SUBST = "Subst";
  public static final String SYNT_SUBST = "SyntSubst";
  public static final String MOVE = "Move";
  public static final String PHRASE_TABLE = "Pt";
  public static final String RULE = "Rule";
  public static final String INSERT = "Ins";
  public static final String SYNTAX = "Syntax";
  public static final String SYNT_ALIGN = "SyntAlign";
  
  public abstract List<RuleApplication> apply(LanguageInfo antecedent, LanguageInfo target);
  public abstract List<LangExpMatch> match(LanguageInfo lInfo);
}

class SyntacticRule extends RuleApplier {
  
  protected LanguageExp lhs;
  protected List<Object> rhs=new ArrayList<Object>();; //list of either lhs items or wordinfos for generating the consquent

  public SyntacticRule(String lispTree) {
    LispTree tree = LispTree.proto.parseFromString(lispTree);
    if(tree.children.size()!=3)
      throw new RuntimeException("Number of children in a rule must be three");
    lhs = new LanguageExp(tree.child(1));
    LispTree rhsTree = tree.child(2);

    //now go over the tree
    for(int i = 1; i < rhsTree.children.size();++i) 
      rhs.add(addRhsObject(rhsTree.child(i).value));
  }

  private Object addRhsObject(String value) {
    try {
      return Integer.parseInt(value);
    }
    catch(NumberFormatException e) {
      String[] tokens = value.split(";;"); //HACK to get word info
      return new WordInfo(tokens[0], tokens[1], tokens[2], tokens[3], tokens[4]);
    }
  }

  public List<RuleApplication> apply(LanguageInfo antecedent, LanguageInfo target) {

    List<RuleApplication> res = new LinkedList<RuleApplication>();
    List<LangExpMatch> matches = lhs.match(antecedent);
    
    for(LangExpMatch match: matches) {
      List<RuleApplication> applications = generateApplications(antecedent,match, target);
      res.addAll(applications);      
    }
    return res; 
  }

  //TODO reuse code in generateApplications
  protected List<RuleApplication> generateApplications(LanguageInfo antecedent, LangExpMatch match, LanguageInfo target) {

    LanguageInfo res = new LanguageInfo();
    for(Object obj: rhs) {
      if(obj instanceof WordInfo) {
        res.addWordInfo(((WordInfo) obj));
      }
      else if(obj instanceof Integer) {
        Interval matchingInterval = match.get(((Integer) obj));
        res.addSpan(antecedent, matchingInterval.start, matchingInterval.end);
      }
      else
        throw new RuntimeException("Rhs should be a list of wordinfo or integer keys to intervals only");
    }
    RuleApplication application = new RuleApplication(antecedent, res, new ApplicationInfo(RULE,""));
    application.addFeatures(featurizeParaphraseRule());
    return Collections.singletonList(application);
  }

  private FeatureVector featurizeParaphraseRule() {
    FeatureVector fv = new FeatureVector();
    fv.add(RULE, lhs+"_"+rhs);
    return fv;
  }

  public String toString() {
    String lhsStr = lhs.toString();
    List<LangItem> items = lhs.items();
    List<String> rhsStr = new ArrayList<String>();
    for(Object obj: rhs) {
      if(obj instanceof Integer)
        rhsStr.add(items.get((Integer) obj).toString());
      else {
        rhsStr.add(((WordInfo) obj).lemma); 
      }
    }
    return lhsStr+"->"+Joiner.on(' ').join(rhsStr);
  }

  @Override
  public List<LangExpMatch> match(LanguageInfo lInfo) {
    return lhs.match(lInfo);
  }
}

//In deletion rules we know that the text span specified at index 1 of the match is the one that has been deleted
class DeletionRule extends SyntacticRule {

  public static final Map<String,Counter<String>> verbSemclassMap = VerbSemClassExtractor.getWordToWordCounterMap();

  public DeletionRule(String expression) {
    super(expression);
    LispTree tree = LispTree.proto.parseFromString(expression);
    LispTree rhsTree = tree.child(2);
    if(rhsTree.children.size()!=3) 
      throw new RuntimeException("In a deletion rules there must be exactly three items on the rhs");
  }

  protected List<RuleApplication> generateApplications(LanguageInfo antecedent, LangExpMatch match, LanguageInfo target) {

    LanguageInfo res = new LanguageInfo();
    for(Object obj: rhs) {
      if(obj instanceof WordInfo) {
        res.addWordInfo(((WordInfo) obj));
      }
      else if(obj instanceof Integer) {
        Interval matchingInterval = match.get(((Integer) obj));
        res.addSpan(antecedent, matchingInterval.start, matchingInterval.end);
      }
      else
        throw new RuntimeException("Rhs should be a list of wordinfo or integer keys to intervals only");
    }
    RuleApplication application = new RuleApplication(antecedent, res,new ApplicationInfo(DELETE, 
        antecedent.lemmaPhrase(match.get(1).start, match.get(1).end)));
    application.addFeatures(featurizeDeletedSpan(new Interval(match.get(1).start,match.get(1).end),antecedent));  
    return Collections.singletonList(application);
  }

  private FeatureVector featurizeDeletedSpan(Interval span, LanguageInfo utterance) {

    List<String> spanProperties = utterance.getSpanProperties(span.start,span.end);
    FeatureVector res = new FeatureVector();

    for(String spanProperty: spanProperties) {
      if(ParaphraseFeatureMatcher.containsDomain(DELETE))
        res.add(DELETE,"match: " + spanProperty); 
      if(ParaphraseFeatureMatcher.containsDomain(DELETE_IN_CONTEXT)) {
        if(span.start>0) {
          List<String> lcProperties = utterance.getSpanProperties(span.start-1,span.start);
          for(String lcProperty: lcProperties) {
            res.add(DELETE_IN_CONTEXT,"match_" + spanProperty+",lc_"+lcProperty); //properties of deleted match
          }
        }
        if(span.end<utterance.numTokens()) {
          List<String> rcProperties = utterance.getSpanProperties(span.end,span.end+1);
          for(String rcProperty: rcProperties) {
            res.add(DELETE_IN_CONTEXT,"match_" + spanProperty+",rc_"+rcProperty); //properties of deleted match
          }
        }
      }
    }
    //verb semclass features
    if(ParaphraseFeatureMatcher.containsDomain(DELETE_V_SEM)) {
      String lemmaTokens = utterance.lemmaPhrase(span.start, span.end);
      Counter<String> cooccurringWords = verbSemclassMap.get(lemmaTokens);
      if(cooccurringWords!=null) {
        for(String lemma: utterance.lemmaTokens) {
          if(cooccurringWords.containsKey(lemma)) {
            res.add(DELETE_V_SEM,"match="+lemmaTokens+",context="+lemma);
            res.add(DELETE_V_SEM,"sim",Math.log(cooccurringWords.getCount(lemma)+1));
          }
        }
      }
    }
    return res;
  }
}

class SubstitutionRule extends SyntacticRule {

  public SubstitutionRule(String expression) {
    super(expression);
    LispTree tree = LispTree.proto.parseFromString(expression);
    LispTree rhsTree = tree.child(2);
    if(rhsTree.children.size()!=3) 
      throw new RuntimeException("In a substitution rules there must be exactly three items on the rhs");
  }

  protected List<RuleApplication> generateApplications(LanguageInfo antecedent, LangExpMatch match, LanguageInfo target) {

    List<RuleApplication> res = new LinkedList<RuleApplication>();
    List<Interval> targetIntervals = generateConsequentIntervals(antecedent,match,target); //finding all possible substitutions in the target
    for(Interval targetInterval: targetIntervals) {
      LanguageInfo consequent = new LanguageInfo();
      for(int i = 0; i < 3; ++i) {
        if(i==0 || i==2) {
          Interval matchingInterval = match.get(i);
          consequent.addSpan(antecedent, matchingInterval.start, matchingInterval.end);
        }
        else { //i==1
          consequent.addSpan(target, targetInterval.start, targetInterval.end);
        }
      }
      RuleApplication application = new RuleApplication(antecedent, consequent,
          new ApplicationInfo(SUBST, antecedent.lemmaPhrase(match.get(1).start, match.get(1).end)+"-->"+
              target.lemmaPhrase(targetInterval.start, targetInterval.end)));
      application.addFeatures(featurizeSubstitutedSpan(match.get(1),targetInterval,antecedent,target));
      res.add(application);
    }
    return res;
  }

  private FeatureVector featurizeSubstitutedSpan(Interval antecedentInterval,
      Interval targetInterval, LanguageInfo antecedent,
      LanguageInfo target) {

    FeatureVector res = new FeatureVector();
    String antecedentLemmas = antecedent.lemmaPhrase(antecedentInterval.start, antecedentInterval.end);
    String targetLemmas = target.lemmaPhrase(targetInterval.start, targetInterval.end);

    if(ParaphraseFeatureMatcher.containsDomain(SUBST)) {
      int editDist = StringUtils.editDistance(antecedentLemmas, targetLemmas);
      boolean prefix = antecedentLemmas.startsWith(targetLemmas) || targetLemmas.startsWith(antecedentLemmas);
      res.add(SUBST, "l="+antecedentLemmas+",r="+targetLemmas);
      res.add(SUBST, "l_pos="+antecedent.posSeq(antecedentInterval.start, antecedentInterval.end)+
          ",r_pos="+target.posSeq(targetInterval.start, targetInterval.end));
      if(editDist==0)
        res.add(SUBST, "dist=0");
      else if(editDist<4)
        res.add(SUBST, "dist="+editDist);
      else
        res.add(SUBST, "dist>3");
      if(prefix)
        res.add(SUBST, "prefix");
    }
    return res;
  }

  /**
   * Generate the consequents subject to constraints such as
   * (a) only generate consequents from the same pos
   * (b) don't generate an identity mapping
   * @param antecedent
   * @param match
   * @param target
   * @return
   */
  private List<Interval> generateConsequentIntervals(LanguageInfo antecedent,
      LangExpMatch match, LanguageInfo target) {

    List<Interval> res = new LinkedList<Interval>(); 

    String antecedentPos = antecedent.posTags.get(match.get(1).start); //we know in a substitution rule the antecedent interval is in index 1
    String antecedentLemma = antecedent.lemmaPhrase(match.get(1).start,match.get(1).end);
    //span 1 candidates
    for(int i = 0; i < target.numTokens(); ++i) {
      String targetPos = target.posTags.get(i);
      String targetLemma = target.lemmaPhrase(i, i+1);
      if(ParaphraseUtils.posCompatible(antecedentPos, targetPos) && !antecedentLemma.equals(targetLemma)) {
        res.add(new Interval(i,i+1));
      }
    }
    //span 2 candidates
    if(LanguageUtils.isNN(antecedentPos)) {
      for(int i = 0; i < target.numTokens()-1; ++i) {
        String targetLemma = target.lemmaPhrase(i, i+2);
        if(LanguageUtils.isNN(target.posTags.get(i)) && LanguageUtils.isNN(target.posTags.get(i+1))  && !antecedentLemma.equals(targetLemma))
          res.add(new Interval(i,i+2));
      }
    }
    return res;
  }
}

class MoveRule extends SyntacticRule {

  public MoveRule(String expression) {
    super(expression);
    LispTree tree = LispTree.proto.parseFromString(expression);
    LispTree rhsTree = tree.child(2);
    if(rhsTree.children.size()!=3) 
      throw new RuntimeException("In a substitution rules there must be exactly three items on the rhs");
  }

  /**
   * Move any phrase that exists in the target 
   */
  protected List<RuleApplication> generateApplications(LanguageInfo antecedent, LangExpMatch match, LanguageInfo target) {

    List<RuleApplication> res = new LinkedList<RuleApplication>();
    Interval matchedInterval = match.get(1);
    String movedPhrase = antecedent.lemmaPhrase(matchedInterval.start, matchedInterval.end); //we know that 1 is the index moved
    if(!target.lemmaPhrase(0, target.numTokens()).contains(movedPhrase)) //don't move things that are not in the target
      return res;

    for(int i = 0; i < antecedent.numTokens(); ++i) {
      LanguageInfo consequent = new LanguageInfo();
      if(i>=matchedInterval.start && i <=matchedInterval.end) continue;
      if(i<matchedInterval.start) {
        consequent.addSpan(antecedent, 0, i);
        consequent.addSpan(antecedent, matchedInterval.start,matchedInterval.end);
        consequent.addSpan(antecedent, i,matchedInterval.start);
        consequent.addSpan(antecedent, matchedInterval.end,antecedent.numTokens());
      }
      else if(i>matchedInterval.end) {
        consequent.addSpan(antecedent, 0, matchedInterval.start);
        consequent.addSpan(antecedent, matchedInterval.end,i);
        consequent.addSpan(antecedent, matchedInterval.start,matchedInterval.end);
        consequent.addSpan(antecedent, i,antecedent.numTokens());
      }
      RuleApplication application = new RuleApplication(antecedent, consequent,
          new ApplicationInfo(MOVE,antecedent.lemmaPhrase(matchedInterval.start, matchedInterval.end)));
      application.addFeatures(featurizeMovedSpan(antecedent,matchedInterval));
      res.add(application);
    }
    return res;
  }

  private FeatureVector featurizeMovedSpan(LanguageInfo antecedent,
      Interval matchedInterval) {
    FeatureVector res = new FeatureVector();

    if(ParaphraseFeatureMatcher.containsDomain(MOVE)) {
      res.add(MOVE,"lemmas="+antecedent.lemmaPhrase(matchedInterval.start, matchedInterval.end));
      res.add(MOVE,"pos="+antecedent.posSeq(matchedInterval.start, matchedInterval.end));
      res.add(MOVE,"ner="+antecedent.nerSeq(matchedInterval.start, matchedInterval.end));
    }
    return res;
  }
}

class PhraseTableRule  extends SyntacticRule {
  
  PhraseTable phraseTable;

  public PhraseTableRule(String expression) {
    super(expression);
    LispTree tree = LispTree.proto.parseFromString(expression);
    LispTree rhsTree = tree.child(2);
    if(rhsTree.children.size()!=3) 
      throw new RuntimeException("In a phrasetable rule there must be exactly three items on the rhs");
    
    if(phraseTable==null) 
      phraseTable = PhraseTable.getSingleton();
  }
  
  

  protected List<RuleApplication> generateApplications(LanguageInfo antecedent, LangExpMatch match, LanguageInfo target) {

    List<RuleApplication> res = new LinkedList<RuleApplication>();
    //get LHS phrase - guarateed that 1 is the index of the match
    int matchStart = match.get(1).start;
    int matchEnd = match.get(1).end;
        
    String lhsPhrase = antecedent.lemmaPhrase(matchStart, matchEnd); 
    Map<String,AlignmentStats> rhsCandidates = phraseTable.get(lhsPhrase);
    if(rhsCandidates==null) {
      return res;
    }
    
  //get RHS 
    Map<String,IntPair> rhsLemmaSpans = target.getLemmaSpans();
    for(String rhsLemmaSpan: rhsLemmaSpans.keySet()) {
      if(rhsCandidates.containsKey(rhsLemmaSpan)) {
        
        int targetStart = rhsLemmaSpans.get(rhsLemmaSpan).first;
        int targetEnd = rhsLemmaSpans.get(rhsLemmaSpan).second;          
        LanguageInfo consequent = generateConsequent(antecedent,matchStart,matchEnd,
            target, targetStart,targetEnd);
        RuleApplication application = new RuleApplication(antecedent, consequent,
            new ApplicationInfo(PHRASE_TABLE, lhsPhrase+"-->"+rhsLemmaSpan));
        application.addFeatures(featureizePhraseTablespan(lhsPhrase,rhsLemmaSpan,rhsCandidates.get(rhsLemmaSpan),
            antecedent,matchStart,matchEnd,target,targetStart,targetEnd));
        //do not do an identity transformation
        res.add(application);
      }
    }
    return res;
  }

  private FeatureVector featureizePhraseTablespan(String lemmaPhrase,
      String rhsLemmaSpan, AlignmentStats count, LanguageInfo antecedent, int matchStart,
      int matchEnd, LanguageInfo target, int targetStart, int targetEnd) {

    FeatureVector res = new FeatureVector();
    if(ParaphraseFeatureMatcher.containsDomain(PHRASE_TABLE)) {
      res.add(SUBST, "l="+lemmaPhrase+",r="+rhsLemmaSpan);
      res.add(SUBST, "l_pos="+antecedent.posSeq(matchStart, matchEnd)+",r_pos="+target.posSeq(targetStart, targetEnd));
      //res.add(PHRASE_TABLE, "PtScore", Math.log(count+1));
    }
    return res;
  }

  private LanguageInfo generateConsequent(LanguageInfo antecedent,
      int matchStart, int matchEnd, LanguageInfo target, int targetStart,
      int targetEnd) {
    LanguageInfo res = new LanguageInfo();
    res.addSpan(antecedent, 0, matchStart);
    res.addSpan(target, targetStart, targetEnd);
    res.addSpan(antecedent, matchEnd, antecedent.numTokens());
    return res;
  }
  
}
