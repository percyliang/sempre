package edu.stanford.nlp.sempre.paraphrase.rules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.base.Joiner;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.sempre.FeatureVector;
import edu.stanford.nlp.sempre.LanguageInfo;
import edu.stanford.nlp.sempre.LanguageInfo.LanguageUtils;
import edu.stanford.nlp.sempre.LanguageInfo.WordInfo;
import edu.stanford.nlp.sempre.paraphrase.rules.LanguageExp.LangExpMatch;
import edu.stanford.nlp.sempre.paraphrase.rules.RuleApplication.ApplicationInfo;
import fig.basic.LogInfo;
import fig.basic.Option;
import fig.basic.Pair;

/**
 * Storing and matching efficiently a large number of syntactic rules
 * We do not use the regexp mechanism since in the trie we want to use a map
 * to find the next edges relevant and not go over all outgoing edges and match the regexp 
 * over them
 * @author jonathanberant
 */

public class SyntacticRuleSet extends RuleApplier {

  public static class Options {
    @Option(gloss= "Path to syntactic rule set") public String rulesetPath="lib/paralex/syntactic-rules.retagged.sorted.txt";
    @Option(gloss= "Verbosity") public int verbose=0;
  }
  public static Options opts = new Options();

  Trie trie = new Trie();

  public SyntacticRuleSet() {
    LogInfo.begin_track("Loading syntactic rules");
    loadRuleset();
    LogInfo.end_track();
  }

  /**
   * loading the rules we filter
   * (a) things with punctuations
   * (b) things with derivations
   * (c) things where pos tags don't match?
   * (d) count threshold?
   */
  private void loadRuleset() {
    int count=0;
    for(String line: IOUtils.readLines(opts.rulesetPath)) {
      if(validRule(line)) {
        count++;
        SubstitutableSyntacticRule rule = parseRule(line);
        if(opts.verbose>=3)
          LogInfo.logs("loadRuleSet: uploaded rule=%s",rule);
        trie.add(parseRule(line));
      }
    }
    LogInfo.logs("Number of valid rules=%s",count);
  }

  private boolean validRule(String line) {
    if(line.contains("DER"))
      return false;
    if(line.contains("._"))
      return false;
    String[] tokens = line.split("\t");
    double count = Double.parseDouble(tokens[2]);
    if(count<20.0)
      return false;
    String[] lhsTokens = tokens[0].split("\\s+");
    String[] rhsTokens = tokens[1].split("\\s+");
    Set<Integer> lhsVars = new HashSet<Integer>();
    Set<Integer> rhsVars = new HashSet<Integer>();
    List<Integer> rhsVarList = new ArrayList<Integer>();
    for(String lhsToken: lhsTokens) {
      if(Character.isDigit(lhsToken.charAt(0)))
        return false;
      if(lhsToken.indexOf('_')!=-1)
        lhsVars.add(Integer.parseInt(lhsToken.split("_")[1]));
    }
    for(String rhsToken: rhsTokens) {
      if(Character.isDigit(rhsToken.charAt(0)))
        return false;
      if(rhsToken.indexOf('_')!=-1) {
        rhsVars.add(Integer.parseInt(rhsToken.split("_")[1]));
        rhsVarList.add(Integer.parseInt(rhsToken.split("_")[1]));
      }
    }
//    if(!SetUtils.isEqualSet(lhsVars, rhsVars))
//      return false;
    if(lhsVars.size()<2 || rhsVars.size()<2) //extreme rule
      return false;
    for(int i = 0; i < rhsVarList.size()-1;++i) {
      if(rhsVarList.get(i)>rhsVarList.get(i+1))
        return true;
    }
    return false;
    //    return true;
  }

  //TODO very hacky
  private SubstitutableSyntacticRule parseRule(String line) {

    String[] tokens = line.split("\t");
    double count = Double.parseDouble(tokens[2]);
    List<LanguageExpToken> lhs = new ArrayList<LanguageExpToken>();

    String[] lhsTokens = tokens[0].split("\\s+");
    int[] varNameToPositionMap =new int[lhsTokens.length];
    String[] rhsTokens = tokens[1].split("\\s+");
    int[] mapping = new int[rhsTokens.length];
    Arrays.fill(mapping, -1);
    Arrays.fill(varNameToPositionMap, -1);
    WordInfo[] rhs = new WordInfo[rhsTokens.length];
    //generate lhs
    for(int i = 0; i < lhsTokens.length; ++i) {
      String lhsToken = lhsTokens[i];
      if(lhsToken.indexOf('_')!=-1) {
        String[] posAndVarName = lhsToken.split("_");
        lhs.add(new LanguageExpToken("pos", "["+posAndVarName[0]+"]"));
        varNameToPositionMap[Integer.parseInt(posAndVarName[1])]=i;
      }
      else {
        String[] wordInfoParts = lhsToken.split("\\|\\|");
        lhs.add(new LanguageExpToken("lemma","["+wordInfoParts[0]+"]"));
      }
    }
    //generate mapping and rhs
    for(int i = 0; i < rhsTokens.length; ++i) {
      String rhsToken = rhsTokens[i];
      if(rhsToken.indexOf('_')!=-1) {
        String[] posAndVarName = rhsToken.split("_");
        mapping[i]=varNameToPositionMap[Integer.parseInt(posAndVarName[1])];
      }
      else {
        String[] wordInfoParts = rhsToken.split("\\|\\|");
        rhs[i]=new WordInfo(wordInfoParts[0], wordInfoParts[0], wordInfoParts[1], wordInfoParts[2], "O");
      }
    }
    return new SubstitutableSyntacticRule(lhs, rhs, mapping, count);
  }

  @Override
  public List<RuleApplication> apply(LanguageInfo antecedent,
      LanguageInfo target) {

    List<RuleApplication> res = new ArrayList<RuleApplication>();
    //go over all spans
    for(int i = 0; i < antecedent.numTokens(); ++i) {
      List<Trie> currentNodes = new ArrayList<Trie>();
      currentNodes.add(trie);
      for(int span=1; span<=5 && i+span <= antecedent.numTokens(); ++span) {
        if(i==1 && span==1)
          System.out.print("");
        List<Trie> nextNodes = new ArrayList<Trie>();
        Pair<String,String> lemmaPair = new Pair<String, String>("lemma",antecedent.lemmaTokens.get(i+span-1));
        Pair<String,String> posPair = new Pair<String, String>("pos",LanguageUtils.getCanonicalPos(antecedent.posTags.get(i+span-1)));
        Pair<String,String> nerPair = new Pair<String, String>("ner",antecedent.nerTags.get(i+span-1));
        //add to next nodes all the tries we can reach with the current word
        addReachableTries(currentNodes, nextNodes, lemmaPair, posPair, nerPair);
        //now we can apply all of the rules
        generateMatchingRhsApplications(antecedent, target, res, i, span, nextNodes);
        //we set current nodes to next nodes for the next round
        currentNodes = nextNodes;
      }
    }
    return res;
  }

  private void generateMatchingRhsApplications(LanguageInfo antecedent, LanguageInfo target,
      List<RuleApplication> res, int i, int span, List<Trie> nextNodes) {

    Set<String> generatedRhsMatches = new HashSet<String>();
    for(Trie nextNode: nextNodes) {
      for(SubstitutableSyntacticRule rule: nextNode.rules) {
        List<WordInfo> rhsMatch = rule.generateRhsLemmas(antecedent, i);
        if(target.matchLemmas(rhsMatch)) {
          String rhsMatchPhrase = LanguageUtils.getLemmaPhrase(rhsMatch);
          if(!generatedRhsMatches.contains(rhsMatchPhrase)) {
            res.add(generateApplications(antecedent, i, i+span, rhsMatch, rule));
          }
        }
      }
    }
  }

  private void addReachableTries(List<Trie> currentNodes, List<Trie> nextNodes,
      Pair<String, String> lemmaPair, Pair<String, String> posPair,
      Pair<String, String> nerPair) {
    for(Trie currentNode: currentNodes) {
      addNextTrie(nextNodes, lemmaPair, currentNode);
      addNextTrie(nextNodes, posPair, currentNode);
      addNextTrie(nextNodes, nerPair, currentNode);
    }
  }

  private void addNextTrie(List<Trie> nextNodes, Pair<String, String> lemmaPair,
      Trie currentNode) {
    Trie nextTrie = currentNode.next(lemmaPair);
    if(nextTrie!=null) 
      nextNodes.add(nextTrie);
  }

  private RuleApplication generateApplications(LanguageInfo antecedent, int i, int j, List<WordInfo> rhsMatch, SubstitutableSyntacticRule rule) {
    LanguageInfo consequent = new LanguageInfo();
    consequent.addSpan(antecedent, 0, i);
    consequent.addWordInfos(rhsMatch);
    consequent.addSpan(antecedent, j, antecedent.numTokens());
    RuleApplication application = new RuleApplication(antecedent, consequent, new ApplicationInfo(SYNT_SUBST, rule.toString()));
    FeatureVector fv = new FeatureVector();
    fv.add(SYNT_SUBST, rule.toString());
    fv.add(SYNT_SUBST, "score" ,rule.count);
    application.addFeatures(fv);
    if(opts.verbose>0)
      LogInfo.logs("antecedent=%s, consequent=%s, rule=%s",antecedent.tokens,consequent.tokens,rule);
    return application;
  }

  class SubstitutableSyntacticRule {

    final List<LanguageExpToken> lhs;
    final WordInfo[] rhs;
    final int[] mapping;
    final double count;

    public SubstitutableSyntacticRule(List<LanguageExpToken> lhs, WordInfo[] rhs, 
        int[] mapping, double count) {
      this.lhs=lhs;
      this.rhs=rhs;
      this.mapping=mapping;
      this.count = count;
    }

    public List<WordInfo> generateRhsLemmas(LanguageInfo antecedent, int start) {
      List<WordInfo> res = new ArrayList<WordInfo>();
      for(int i = 0; i < rhs.length; ++i) {
        if(rhs[i]!=null)
          res.add(rhs[i]);
        else 
          res.add(antecedent.getWordInfo(start+mapping[i]));
      }
      return res;
    }

    public String toString() {
      StringBuilder sb = new StringBuilder();
      for(int i = 0; i < rhs.length; ++i) {
        if(rhs[i]==null)
          sb.append(mapping[i]+" ");
        else
          sb.append(rhs[i].toString()+" ");   
      }
      return Joiner.on(' ').join(lhs)+"-->"+sb.toString()+"("+count+")";
    }
  }


  class Trie {
    ArrayList<SubstitutableSyntacticRule> rules = new ArrayList<SubstitutableSyntacticRule>();
    HashMap<Pair<String,String>, Trie> children = new HashMap<Pair<String,String>, Trie>();

    Trie next(Pair<String,String> pair) { 
      return children.get(pair); 
    }

    void add(SubstitutableSyntacticRule rule) { add(rule, 0); }
    private void add(SubstitutableSyntacticRule rule, int i) {

      if(i == rule.lhs.size()) {
        rules.add(rule);
        return;
      }

      LanguageExpToken langToken = rule.lhs.get(i);
      Trie child = children.get(convertLangItemToPair(langToken));
      if (child == null) {
        children.put(convertLangItemToPair(langToken),
            child = new Trie());
      }
      child.add(rule, i + 1);
    }

    private Pair<String,String> convertLangItemToPair(LanguageExpToken langToken) {
      String value = langToken.value.substring(1,langToken.value.lastIndexOf(']'));
      return Pair.newPair(langToken.type.toString(),value);
    }
  }

  public static void main(String[] args) {
    opts.verbose=3;
    SyntacticRuleSet.opts.rulesetPath="/Users/jonathanberant/Research/temp/syntactic-rules.retagged.sorted.txt";
    SyntacticRuleSet srt = new SyntacticRuleSet();
    LanguageInfo antecedent = new LanguageInfo();
    antecedent.addWordInfo(new WordInfo("where", "where", "WDT", "O", "O"));
    antecedent.addWordInfo(new WordInfo("was", "be", "VBD", "O", "O"));
    antecedent.addWordInfo(new WordInfo("obama", "obama", "NNP", "PERSON", "O"));
    antecedent.addWordInfo(new WordInfo("birth", "birth", "NN", "O", "O"));
    antecedent.addWordInfo(new WordInfo("place", "place", "NN", "O", "O"));

    LanguageInfo target = new LanguageInfo();
    target.addWordInfo(new WordInfo("where", "where", "WDT", "O", "O"));
    target.addWordInfo(new WordInfo("was", "be", "VBD", "O", "O"));
    target.addWordInfo(new WordInfo("obama", "obama", "NNP", "PERSON", "O"));
    target.addWordInfo(new WordInfo("'s", "'s", "POS", "O", "O"));
    target.addWordInfo(new WordInfo("place", "place", "NN", "O", "O"));
    target.addWordInfo(new WordInfo("of", "of", "IN", "O", "O"));
    target.addWordInfo(new WordInfo("birth", "birth", "NN", "O", "O"));
    srt.apply(antecedent, target);
  }

  @Override
  public List<LangExpMatch> match(LanguageInfo lInfo) {
    throw new RuntimeException("Unsupoorted method");
  }
}
