package edu.stanford.nlp.sempre.paraphrase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Joiner;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.sempre.FbFormulasInfo;
import edu.stanford.nlp.sempre.Formula;
import edu.stanford.nlp.sempre.FormulaGenerationInfo;
import edu.stanford.nlp.sempre.FreebaseInfo;
import edu.stanford.nlp.sempre.JoinFormula;
import edu.stanford.nlp.sempre.Json;
import edu.stanford.nlp.sempre.NameValue;
import edu.stanford.nlp.sempre.Value;
import edu.stanford.nlp.sempre.ValueFormula;
import edu.stanford.nlp.sempre.FbFormulasInfo.BinaryFormulaInfo;
import edu.stanford.nlp.sempre.fbalignment.lexicons.LexicalEntry.LexiconValue;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import fig.basic.LogInfo;
import fig.basic.MapUtils;
import fig.basic.Option;
import fig.basic.Pair;

/**
 * Generating questions from Freebase relations
 * @author jonathanberant
 *
 */
public class QuestionGenerator {

  public static class Options {
    @Option(gloss="whether to generate with bow mode") public boolean bowGeneration;
    @Option(gloss="whether to generate with lexicon") public boolean genFromLex=true;
    @Option(gloss="verbose") public int verbose = 0;
    @Option(gloss="Threshold for uploading ot alignment lexicon") public double alignmentLexiconThreshold = 9.9;
  }
  public static Options opts = new Options();

  private final FbFormulasInfo fbFormulasInfo = FbFormulasInfo.getSingleton();
  private final StanfordCoreNLP pipeline;
  private final Map<Formula,Pair<String,Double>> formulaToLexemsMap = new HashMap<Formula, Pair<String,Double>>();
  private final String lexiconFile = "lib/fb_data/6/binaryInfoStringAndAlignment.txt";

  private Map<String,Annotation> lingAnnotationCache = new HashMap<String, Annotation>();
  private static final boolean DROP_TYPE1 = true;
  private static final Pattern dropRedundantType1Pattern = Pattern.compile("what (.+) is the (.+) of");
  private static final Pattern dropPattern = Pattern.compile("what.* is (.*)");

  public QuestionGenerator() throws IOException {
    Properties props = new Properties();
    props.put("annotators", "tokenize,ssplit,pos,parse");
    pipeline = new StanfordCoreNLP(props);
    LogInfo.begin_track("uploading lexicon");
    uploadAlignmentLexicon();
    LogInfo.logs("Number of lexicon formulas: %s",formulaToLexemsMap.size());
    LogInfo.end_track();
  }

  private void uploadAlignmentLexicon() {
    for(String line: IOUtils.readLines(lexiconFile)) {
      LexiconValue lv = Json.readValueHard(line, LexiconValue.class);
      double newCount = MapUtils.getDouble(lv.features, "Intersection_size_typed", 0.0);
      if(newCount>opts.alignmentLexiconThreshold) {
        if(formulaToLexemsMap.containsKey(lv.formula)) {
          double currCount = formulaToLexemsMap.get(lv.formula).getSecond();
          if(newCount>currCount) {
            formulaToLexemsMap.put(lv.formula, Pair.newPair(lv.lexeme, newCount));
          }
        }
        else {
          formulaToLexemsMap.put(lv.formula, Pair.newPair(lv.lexeme, newCount));
        }
      }
    }
  }

  /**
   * Have explicit entity description
   * @param binary
   * @param entity
   * @return
   */
  public Set<String> getQuestionsForFgInfo(FormulaGenerationInfo fgInfo) {
    if(opts.bowGeneration) {
      return bowGenerate(fgInfo);
    }
    //generate from formula info
    Set<String> res = generateQuestions(fgInfo);
    //generate from equivalent formula if it exists
		//added the check for equivalent formula based on github question (unclear why hasn't happened earlier)
    if(fbFormulasInfo.hasOpposite(fgInfo.bInfo.formula) && 
			fbFormulasInfo.getBinaryInfo(fbFormulasInfo.equivalentFormula(fgInfo.bInfo.formula)) != null) {
      FormulaGenerationInfo eqInfo = 
          new FormulaGenerationInfo(fbFormulasInfo.getBinaryInfo(fbFormulasInfo.equivalentFormula(fgInfo.bInfo.formula)),
              fgInfo.injectedInfo, fgInfo.entityInfo1, fgInfo.entityInfo2, fgInfo.uInfo, fgInfo.isCount, fgInfo.isInject, fgInfo.isUnary);
      res.addAll(generateQuestions(eqInfo));
    }
    return res;
  }

  private Set<String> bowGenerate(FormulaGenerationInfo fgInfo) {
    List<String> question = new ArrayList<String>();
    question.add(fgInfo.getQuestionWord()); //question word
    String type1Desc = getType1Desc(fgInfo);
    if(type1Desc==null)
      return Collections.emptySet();
    question.add(type1Desc);
    question.add(fgInfo.entityInfo1.desc);
    for(String binaryDesc: fgInfo.bInfo.descriptions)
      question.add(binaryDesc);
    if(fgInfo.isInject) {
      question.add(fgInfo.entityInfo2.desc);
    }
    return Collections.singleton(Joiner.on(' ').join(question));
  }

  /**
   * Get questions for a binary formula - main interface with this class - use both FB descriptions and lexicon
   * @param bInfo
   * @return
   */
  private Set<String> generateQuestions(FormulaGenerationInfo fgInfo) {

    Set<String> res = new LinkedHashSet<String>();
    //hack to identify cvt things (I don't trust the CVT markings on freebase schema)
    if(fgInfo.bInfo.toReverseString().contains("lambda")) 
      handleCvtBinary(fgInfo, res);
    else 
      handleNonCvtBinary(fgInfo, res);

    if(opts.genFromLex) {
      if(formulaToLexemsMap.containsKey(fgInfo.bInfo.formula)) {
        handleLexiconBinary(fgInfo, res);
      }
    }
    res = postProcess(res,fgInfo);
    if(fgInfo.isInject)
      res = handleInjection(res,fgInfo);
    return res;
  }

  private Set<String> handleInjection(Set<String> uninjected, FormulaGenerationInfo fgInfo) {
    Set<String> injected = new HashSet<String>();
    for(String question: uninjected) {
      String injectedQuestion = question.replace("?",Joiner.on(' ').join("in",fgInfo.entityInfo2.desc,"?"));
      injected.add(injectedQuestion);
    }
    return injected;
  }

  private Set<String> postProcess(Set<String> questions, FormulaGenerationInfo fgInfo) {
    Set<String> res = new LinkedHashSet<String>();
    for(String q: questions) {
      String postProcessed = q.replace("what person", "who");
      postProcessed = postProcessed.replace("what date", "when");
      postProcessed=dropRedundantType1(postProcessed);
      res.add(postProcessed.trim());
      //possibly add another without type1
      if(DROP_TYPE1 && !fgInfo.isUnary) {
        dropType1(postProcessed,res);
      }
    }
    return res;
  }

  private static void dropType1(String postProcessed, Set<String> res) {
    Matcher m = dropPattern.matcher(postProcessed);
    if(m.find()) {
      res.add(m.group(1));
    }    
  }

  private static String dropRedundantType1(String postProcessed) {
    Matcher m = dropRedundantType1Pattern.matcher(postProcessed);
    if(m.find()) {
      String type1 = m.group(1);
      String description = m.group(2);
      if(type1.startsWith(description) || type1.endsWith(description))
        postProcessed = Joiner.on(' ').join("what is the",type1,"of",postProcessed.substring(m.end()+1));
      else if(description.startsWith(type1) || description.endsWith(type1))
        postProcessed = Joiner.on(' ').join("what is the",description,"of",postProcessed.substring(m.end()+1));
    }
    return postProcessed;
  }

  private void handleLexiconBinary(FormulaGenerationInfo fgInfo, Set<String> res) {
    String binaryDesc = formulaToLexemsMap.get(fgInfo.bInfo.formula).getFirst();
    String type1Desc = getType1Desc(fgInfo);
    if(type1Desc==null)
      return;
    res.add(Joiner.on(' ').join(new String[]{fgInfo.getQuestionWord(),type1Desc,binaryDesc,fgInfo.entityInfo1.desc,"?"}));
  }

  private Annotation getAnnotation(String desc) {
    synchronized(lingAnnotationCache) {
      if(lingAnnotationCache.containsKey(desc))
        return lingAnnotationCache.get(desc);
    }
    Annotation ann = new Annotation(desc);
    synchronized(pipeline) {
      pipeline.annotate(ann);
    }
    synchronized (lingAnnotationCache) {
      lingAnnotationCache.put(desc, ann);
    }
    return ann;
  }
  
  private void handleNonCvtBinary(FormulaGenerationInfo fgInfo, Set<String> res) {
    String description = normalizeFbDescription(fgInfo.bInfo.descriptions.get(0));
    Annotation a = getAnnotation(description);
    String question = generateNonCvtQuestion(fgInfo,
        description,
        getPosTagsFromAnnotation(a),
        a.get(SentencesAnnotation.class).get(0).get(TreeAnnotation.class).firstChild(),
        fbFormulasInfo.isReversed(fgInfo.bInfo.formula));
    if(question!=null)
      res.add(question);
  }
  
  private void handleCvtBinary(FormulaGenerationInfo fgInfo, Set<String> res) {
    String description1 = normalizeFbDescription(fgInfo.bInfo.descriptions.get(0));
    String description2 = normalizeFbDescription(fgInfo.bInfo.descriptions.get(1));
    Annotation a1 = getAnnotation(description1);
    Annotation a2 = getAnnotation(description2); 

    String question = generateCvtQuestion(fgInfo,description1,description2,
        a1.get(SentencesAnnotation.class).get(0).get(TreeAnnotation.class).firstChild(),
        a2.get(SentencesAnnotation.class).get(0).get(TreeAnnotation.class).firstChild(),
        getPosTagsFromAnnotation(a1),
        getPosTagsFromAnnotation(a2));
    if(question!=null)
      res.add(question);
  }
  
  /**
   * This method has all the ruls for generating a question
   * @param bInfo
   * @param binaryDesc
   * @param posTags
   * @param t
   * @param isReversed
   * @param isCvt
   * @return
   */
  private String generateNonCvtQuestion(FormulaGenerationInfo fgInfo, String binaryDesc,
      List<String> posTags, Tree t, boolean isReversed) {

    String res;
    String type1Desc=getType1Desc(fgInfo);
    if(type1Desc==null) //TODO hack
      return null;

    String entityDesc = fgInfo.entityInfo1.desc;
    String qWord = fgInfo.getQuestionWord();
    binaryDesc=binaryDesc.toLowerCase();
    String category = getTreeCategory(t);

    if(binaryDesc.endsWith("here")) { //special type of description that behaves weirdly
      res = handleHere(binaryDesc,isReversed,type1Desc, entityDesc,qWord);
    }
    else if(category.equals("NP") || category.equals("X") || category.contains("SBARQ") || category.equals("ADVP")) {
      res=handleNP(binaryDesc, isReversed, type1Desc, entityDesc, qWord);
    }
    else if (category.equals("VP") || category.equals("ADJP") || category.equals("SBAR") || category.equals("SINV")) {
      res = handleVP(binaryDesc, posTags, t, isReversed, type1Desc, entityDesc,qWord);
    }
    else if(category.equals("PP")) {
      res = handlePP(binaryDesc, isReversed, type1Desc, entityDesc, qWord);
    }
    else if(category.equals("S")) {
      String NP,VP;
      if(t.children().length==2 && t.getChild(0).label().toString().equals("NP") &&
          (t.getChild(1).label().toString().equals("VP") || t.getChild(1).label().toString().equals("ADJP"))) {
        NP = yield(t.getChild(0));
        VP = yield(t.getChild(1));
      }
      else if(t.children().length==1 && t.getChild(0).label().toString().equals("NP") &&
          t.getChild(0).getChild(0).label().toString().equals("NP") &&
          t.getChild(0).numChildren()==2) {
        NP = yield(t.getChild(0).getChild(0));
        VP = yield(t.getChild(0).getChild(1));
      }
      else if(t.getChild(0).label().toString().equals("VP")) {
        NP = "";
        VP = yield(t);
      }
      else throw new RuntimeException("Unhandled S node: " + t);
      res = handleS(binaryDesc, isReversed, type1Desc, entityDesc, NP, VP, qWord);
    }
    
    else if(category.equals("FRAG")) {
      if(t.getChild(t.numChildren()-1).label().toString().equals("PP")) {
        res = handleFinalPP(fgInfo.bInfo, binaryDesc, isReversed, type1Desc, entityDesc,qWord);
      }
      else {
        if(t.numChildren()==1 && t.getChild(0).label().toString().equals("NP")) {
          res = handleNP(binaryDesc, isReversed, type1Desc, entityDesc, qWord);
        }
        else if(t.numChildren()==1 && t.getChild(0).label().toString().equals("VP")) {
          res = handleVP(binaryDesc, posTags, t.getChild(0), isReversed, type1Desc, entityDesc,qWord);
        }
        else if(t.numChildren()==2 && t.getChild(0).label().toString().equals("NP") && t.getChild(1).label().toString().equals("VP")) {
          res = handleS(binaryDesc, isReversed, type1Desc, entityDesc, yield(t.getChild(0)), yield(t.getChild(1)),qWord);
        }
        else if(posTags.contains("NP"))
          res = handleNP(binaryDesc, isReversed, type1Desc, entityDesc, qWord);
        else
          res = handleVP(binaryDesc, posTags, t, isReversed, type1Desc, entityDesc,qWord);
      }
    }
    else throw new RuntimeException("Not handling " + fgInfo.bInfo+", category="+category);
    return res;
  }

  private String handlePP(String binaryDesc, boolean isReversed, String type1Desc, String entityDesc, String qWord) {
    if(isReversed) 
      return Joiner.on(' ').join(new String[]{qWord,type1Desc,entityDesc,binaryDesc,"?"});
    else 
      return Joiner.on(' ').join(new String[]{qWord,type1Desc,binaryDesc,entityDesc,"?"});   
  }

  //TODO - maybe simply delete this case
  private String handleHere(String description, boolean isReversed, String type1Desc, String entityDesc, String qWord) {
    String[] tokens = description.split("\\s+");
    if(isReversed) 
      return Joiner.on(' ').join(new String[]{qWord,type1Desc,"is",tokens[tokens.length-2],"in",entityDesc,"?"});
    else 
      return Joiner.on(' ').join(new String[]{qWord,type1Desc,"is",entityDesc,tokens[tokens.length-2],"in","?"});
  }

  private String handleFinalPP(BinaryFormulaInfo bInfo, String description,
      boolean isReversed, String type1Desc, String entityDesc, String qWord) {
    return Joiner.on(' ').join(new String[]{qWord,type1Desc,"is",description,entityDesc,"?"});   
  }

  private String handleVP(String description, List<String> posTags, Tree t,
      boolean isReversed, String type1Desc, String entityDesc, String qWord) {
    description=dropTrailingNP(description,t);
    if(isReversed) {
      if(posTags.contains("VBN"))
        return Joiner.on(' ').join(new String[]{qWord,type1Desc,"is",entityDesc,description,"?"});
      else if(posTags.contains("VBD"))
        return Joiner.on(' ').join(new String[]{qWord,type1Desc,"did",entityDesc,description,"?"});
      else if(posTags.contains("VBZ"))
        return Joiner.on(' ').join(new String[]{qWord,type1Desc,"does",entityDesc,description,"?"});
      else
        return Joiner.on(' ').join(new String[]{qWord,type1Desc,"do",entityDesc,description,"?"});
    }
    else {
      if(posTags.contains("VBN"))
        return Joiner.on(' ').join(new String[]{qWord,type1Desc,"is",description,entityDesc,"?"});
      else 
        return Joiner.on(' ').join(new String[]{qWord,type1Desc,description,entityDesc,"?"});   
    }
  }

  private String handleS(String description, boolean isReversed,
      String type1Desc, String entity, String NP, String VP, String qWord) {
    if(isReversed) {
      if(description.endsWith("by")) { //TODO try getting rid of this case
        VP = VP.substring(0,VP.indexOf("by"));
        return Joiner.on(' ').join(new String[]{qWord,type1Desc,VP,entity,"?"});
      }
      else
        return Joiner.on(' ').join(new String[]{qWord,NP,"is",VP,"by",entity,"?"});
    }
    else {
      if(description.endsWith("by")) //TODO try getting rid of this case
        return Joiner.on(' ').join(new String[]{qWord,NP,"is",VP,entity,"?"});
      else
        return Joiner.on(' ').join(new String[]{qWord,type1Desc,VP,"the",NP,entity,"?"});
    }
  }

  private String handleNP(String description, boolean isReversed,
      String type1Desc, String entityDesc, String qWord) {
    if(isReversed) {
      if(description.endsWith("of"))
        return Joiner.on(' ').join(new String[]{qWord,type1Desc,"is","the",description,entityDesc,"?"});
      else
        return Joiner.on(' ').join(new String[]{qWord,type1Desc,"is","the",description,"of",entityDesc,"?"});
    }
    else {
      return Joiner.on(' ').join(new String[]{qWord,type1Desc,"has",entityDesc,"as",description,"?"});
    }
  }
  
  private String generateCvtQuestion(FormulaGenerationInfo fgInfo,
      String description1, String description2, Tree t1, Tree t2,
      List<String> posTags1, List<String> posTags2) {

    boolean isReversed = fbFormulasInfo.isReversed(fgInfo.bInfo.formula);
    String type1Desc = getType1Desc(fgInfo);
    if(type1Desc==null)
      return null;
    String qWord = fgInfo.getQuestionWord();
    String entityDesc = fgInfo.entityInfo1.desc;

    description1=description1.toLowerCase();
    description2=description2.toLowerCase();
    String root1 = t1.label().toString();
    String root2 = t2.label().toString();
    String res;
    if(!isReversed) {
      //NP NP
      if( (isNPorXorSorFRAG(root1) || root1.equals("PP") || root1.equals("SINV") || root1.equals("SBAR")) 
          &&
          isNPorXorSorFRAG(root2))
        res = Joiner.on(' ').join(new String[]{qWord,type1Desc,"has the",description2,entityDesc,"?"});
      //VP NP
      else if( (isADJPorVPorUCP(root1) && isNPorXorSorFRAG(root2)))
        res =  Joiner.on(' ').join(new String[]{qWord,type1Desc,description1,"has the",description2,entityDesc,"?"});//VP NP non, ADJP NP non
      //NP VP or VP VP 
      else if(isNPorXorSorFRAG(root1) && isADJPorVPorUCP(root2) ||
          isADJPorVPorUCP(root1) && isADJPorVPorUCP(root2))
        res =  Joiner.on(' ').join(new String[]{qWord,type1Desc,"is",description2,entityDesc,"?"}); //NP VP non, NP ADJP non, S NP non
      //other
      else if((root1.equals("VP") && root2.equals("SINV")) ||
          (root1.equals("NP") && root2.equals("ADVP")) ||
          (root1.equals("NP") && root2.equals("PP")) ||
          (root1.equals("NP") && root2.equals("SINV")))
        res =  Joiner.on(' ').join(new String[]{qWord,type1Desc,description1,description2,entityDesc,"?"}); //NP FRAG non
      else
        throw new RuntimeException("Does not handle: "+fgInfo.bInfo+", root1=" + root1 + ", root2="+root2+", is reversed="+isReversed);
    }
    else {
      if(isNPorFRAG(root1) && isNPorXorFRAG(root2)) {
        if(FreebaseInfo.isPrimitive(fgInfo.bInfo.expectedType1))
          res =  Joiner.on(' ').join(new String[]{qWord,type1Desc,"is the",description1,"of",description2,"of",entityDesc,"?"}); //NP NP reverse, NP FRAG reverse, FRAG S reverse
        else
          res =  Joiner.on(' ').join(new String[]{qWord,type1Desc,"is the",description1,"of",entityDesc,"?"}); //NP NP reverse, NP FRAG reverse, FRAG S reverse
      }

      else if(isNPorXorFRAG(root1) && root2.equals("S")) //the S gives verb that provides information that's why we use description2 and not description1
        res =  Joiner.on(' ').join(new String[]{qWord,type1Desc,"is the",description2,"by",entityDesc,"?"});//NP S reverse

      else if(root1.equals("S") && root2.equals("S") || 
          root1.equals("S") && root2.equals("FRAG")) //maybe should split if its N V or V N - think a bit more
        res =  Joiner.on(' ').join(new String[]{qWord,type1Desc,"is the",description1,"by",entityDesc,"?"}); //S S reverse,

      else if(root1.equals("S") && root2.equals("NP")) //maybe should think about this one a bit more
        res = Joiner.on(' ').join(new String[]{qWord,type1Desc,"is",entityDesc,"a",description2,"of"});//S NP reverse

      else if((isADJPorVPorUCP(root1) && isNPorXorSorFRAG(root2)) ||
          (root1.equals("SINV") && isNPorXorFRAG(root2)))
        res =  Joiner.on(' ').join(new String[]{qWord,type1Desc,"is",entityDesc,description1}); //VP NP reverse, ADJP NP rev, VP S reverse

      //added for a case where there were pos errors so this is just so that there is no exception
      else if((root1.equals("VP") && root2.equals("VP")))
        res =  Joiner.on(' ').join(new String[]{qWord,type1Desc,"do",entityDesc,description1}); //VP NP reverse, ADJP NP rev, VP S reverse

      else if(isNPorXorSorFRAG(root1) && isADJPorVPorUCP(root2) ||
          isNPorXorSorFRAG(root1) && root2.equals("PP"))
        res =  Joiner.on(' ').join(new String[]{qWord,type1Desc,"is",description1,"that",entityDesc,description2,"?"}); //NP VP rev, NP ADJP rev

      else if(root1.equals("SINV") && root2.equals("VP")) 
        res =  Joiner.on(' ').join(new String[]{qWord,type1Desc,description1,"that",entityDesc,description2,"?"}); //NP VP rev, NP ADJP rev

      else if((isNPorXorSorFRAG(root1) || root1.equals("ADJP")) 
          && root2.equals("SINV"))
        res =  Joiner.on(' ').join(new String[]{qWord,"is the",type1Desc,"for",entityDesc,"that",description2}); //NP SINV rev

      else if((root1.equals("NP") && root2.equals("SBAR")))
        res =  Joiner.on(' ').join(new String[]{qWord,type1Desc,"is the",description2,"of",entityDesc,"?"}); 
      //handle things that start with PP
      else if((root1.equals("ADVP") || root1.equals("PP") || root1.equals("X"))
          && (root2.equals("NP") || root2.equals("FRAG") || root2.equals("ADJP") || root2.equals("X")))
        res =  Joiner.on(' ').join(new String[]{qWord,type1Desc,"is the",description1,"in",description2,"of",entityDesc,"?"});

      else if(((root1.equals("ADVP") || root1.equals("PP"))
          && root2.equals("S")))
        res =  Joiner.on(' ').join(new String[]{qWord,type1Desc,"is",description1,"of",description2,"by",entityDesc,"?"}); //NP VP rev, NP ADJP rev

      else if(root1.equals("PP")  
          && root2.equals("VP"))
        res =  Joiner.on(' ').join(new String[]{qWord,type1Desc,"is",description1,"of",entityDesc,description2,"?"}); //NP VP rev, NP ADJP rev

      else
        throw new RuntimeException("Does not handle: "+fgInfo.bInfo+", root1=" + root1 + ", root2="+root2+", is reversed="+isReversed);
    }
    if(opts.verbose>=3)
      LogInfo.logs("QuestionGenration: binary=%s, expType1=%s, exType2=%s, description1=%s, description2=%s, root1=%s, root2=%s, isReverse=%s, res=%s",
          fgInfo.bInfo.formula,fgInfo.bInfo.expectedType1,fgInfo.bInfo.expectedType2,description1,description2,root1,root2,isReversed,res);
    return res;
  }

  private String getType1Desc(FormulaGenerationInfo fgInfo) {
    if(fgInfo.isUnary) {
      return fgInfo.uInfo.getRepresentativeDescrption();
    }
    if(fgInfo.bInfo.expectedType1.equals(FreebaseInfo.DATE))
      return "date";
    if(fgInfo.bInfo.expectedType1.equals(FreebaseInfo.FLOAT))
      return "number";
    if(fgInfo.bInfo.expectedType1.equals(FreebaseInfo.INT))
      return "number";
    if(fgInfo.bInfo.expectedType1.equals(FreebaseInfo.BOOLEAN))
      return "";
    if(fgInfo.bInfo.expectedType1.equals(FreebaseInfo.TEXT))
      return "description";
    Formula type1Formula = new JoinFormula(FreebaseInfo.TYPE, new ValueFormula<Value>(new NameValue(fgInfo.bInfo.expectedType1)));
    try {
      if(fbFormulasInfo.getUnaryInfo(type1Formula)==null) {
        LogInfo.logs("No unary info for=%s",type1Formula);
        return null;
      }
      return fbFormulasInfo.getUnaryInfo(type1Formula).getRepresentativeDescrption();
    }
    catch(NullPointerException e) {
      if(type1Formula.toString().equals("(fb:type.object.type fb:type.object)"))
        return "thing";
      else {
        LogInfo.logs("Binfo exType1=%s, exType2=%s",fgInfo.bInfo.expectedType1,fgInfo.bInfo.expectedType2);
        throw new RuntimeException(e);
      }
    }
  }
  
  //STATIC METHODS
  private static String dropTrailingNP(String description, Tree t) {

    if(t.numChildren()==2 &&
        t.getChild(0).label().toString().startsWith("VB") &&
        t.getChild(1).label().toString().equals("PP") &&
        t.getChild(1).numChildren()==2 &&
        t.getChild(1).getChild(0).label().toString().equals("IN") && 
        t.getChild(1).getChild(1).label().toString().equals("NP")) {
      description = yield(t.getChild(0)) + " " + yield(t.getChild(1).getChild(0));

    }
    return description;
  }
  
  private static String getTreeCategory(Tree t) {
    String rootLabel = t.label().toString();
    if (rootLabel.equals("S") && t.numChildren()==1 && t.getChild(0).label().toString().equals("VP") 
        && t.getChild(0).getChild(0).label().toString().equals("VBG"))
      return "NP";
    if(rootLabel.equals("S") && t.numChildren()==1 && t.getChild(0).label().toString().equals("VP") 
        && t.getChild(0).getChild(0).label().toString().equals("VBN"))
      return "VP";
    return rootLabel;
  }
  
  private static List<String> getPosTagsFromAnnotation(Annotation a) {
    List<String> posTags = new ArrayList<String>();
    for(CoreLabel token: a.get(TokensAnnotation.class)) 
      posTags.add(token.get(PartOfSpeechAnnotation.class));
    return posTags;
  }

  private static String yield(Tree t) {
    StringBuilder sb = new StringBuilder();
    for(Word word: t.yieldWords())
      sb.append(word.word()+" ");
    return sb.toString().trim();
  }
  
  private static String normalizeFbDescription(String description) {
    description=description.replace("this", "the");
    int startBracket = description.indexOf('(');
    int endBracket = description.indexOf(')');
    if(startBracket!=-1 && endBracket!=-1)
      description=description.substring(0,startBracket)+description.substring(endBracket+1);
    return description;
  }

  
  private static boolean isNPorXorSorFRAG(String str) {return str.equals("NP") || str.equals("X") || str.equals("S") || str.equals("FRAG"); }
  private static boolean isNPorXorFRAG(String str) {return str.equals("NP") || str.equals("X") || str.equals("FRAG"); }
  private static boolean isNPorFRAG(String str) {return str.equals("NP") || str.equals("FRAG"); }
  private static boolean isADJPorVPorUCP(String str) {return str.equals("VP") || str.equals("ADJP") || str.equals("UCP"); }

}
