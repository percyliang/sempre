package edu.stanford.nlp.sempre.paraphrase;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.paraphrase.Aligner.Alignment;
import edu.stanford.nlp.sempre.paraphrase.rules.ParaphraseAlignment;
import edu.stanford.nlp.sempre.paraphrase.rules.LemmaPosRule;
import edu.stanford.nlp.sempre.paraphrase.rules.LemmaPosSequence;
import edu.stanford.nlp.sempre.paraphrase.rules.LemmaPosSequence.LemmaAndPos;
import fig.basic.Fmt;
import fig.basic.LogInfo;
import fig.basic.MemUsage;

public class ParaphraseExample {

  public String id=null;
  @JsonProperty public final String source;
  @JsonProperty public final String target;
  @JsonProperty private Formula formula; //formula from which the paraphrase was generated
  @JsonProperty Value goldValue; 

  public LanguageInfo sourceInfo;
  public LanguageInfo targetInfo;

  FeatureSimilarity featureSimilarity;
  Alignment alignment;

  Evaluation eval = new Evaluation();

  private static Map<String,LanguageInfo> annotationCache = new HashMap<>();

  public ParaphraseExample(String source, String target, BooleanValue value) {
    this.source = source;
    this.target = target;
    this.goldValue = value;
    synchronized(annotationCache) {
      this.sourceInfo=annotationCache.get(source); //null if not there
      this.targetInfo=annotationCache.get(target); //null if not there
    }
  }

  @JsonCreator
  public ParaphraseExample(@JsonProperty("source") String source, @JsonProperty("target") String target, 
      @JsonProperty("formula") Formula f, @JsonProperty("value") BooleanValue value) {
    this.source = source;
    this.target = target;
    this.formula = f;
    this.goldValue = value;
  }

  //IF WE HAVE A NULL PROOF IT SEEMS THAT THIS IS BROKEN
  public double computeExampleScore() {
    throw new RuntimeException("This method might be broken because there is now a null proof that" +
        "can on the predicted proofs");
    //    if(predictedProofs==null)
    //      throw new RuntimeException("Transformation was not performed. source: " + source + " target; " + target);
    //    if(predictedProofs.size()==0)
    //      return 0.0;
    //    if(predictedProofs.get(0).score<0.0)
    //      throw new RuntimeException("Supporting only positive scores for proofs");
    //    return 1.0;
  }


  /**
   * Aligns from left to right without any crossing alignments
   */
  public ParaphraseAlignment align() {   

    ensureAnnotated();
    int[] sourceAlignment = new int[sourceInfo.tokens.size()];
    int[] targetAlignment = new int[targetInfo.tokens.size()];
    Arrays.fill(sourceAlignment, -1);
    Arrays.fill(targetAlignment, -1);

    int lastAlignedIndex = -1;
    for(int i = 0; i < sourceInfo.tokens.size();++i) {
      for(int j = lastAlignedIndex+1; j < targetInfo.tokens.size(); ++j) {
        if(sourceInfo.lemmaTokens.get(i).equals(targetInfo.lemmaTokens.get(j))) {
          lastAlignedIndex=sourceAlignment[i]=j;
          targetAlignment[j]=i;
          break;
        }
      }
    }
    return new ParaphraseAlignment(sourceAlignment,targetAlignment);
  }

  public void ensureAnnotated() {
    if(sourceInfo==null) {
      sourceInfo = new LanguageInfo();
      sourceInfo.analyze(this.source);
      synchronized (annotationCache) {
        annotationCache.put(source, sourceInfo);
      }
    }
    if(targetInfo==null) {
      this.targetInfo = new LanguageInfo();
      this.targetInfo.analyze(this.target);
      synchronized (annotationCache) {
        annotationCache.put(source, sourceInfo);
      }
    }
  }

  public String toJson() {
    return Json.writeValueAsStringHard(this);
  }

  public LemmaPosRule getRule(Interval sourceInterval, Interval targetInterval) {   
    return new LemmaPosRule(computeTemplate(sourceInfo, sourceInterval),
        computeTemplate(targetInfo, targetInterval));
  }

  public LemmaPosSequence computeTemplate(LanguageInfo info, Interval interval) {

    List<LemmaAndPos> res = new ArrayList<>();
    for(int i = interval.start; i < interval.end; ++i) {
      res.add(new LemmaAndPos(info.lemmaTokens.get(i),info.posTags.get(i)));
    }
    return new LemmaPosSequence(res);
  }


  public void setVectorSpaceSimilarity(FeatureSimilarity vsSimilarity) {
    this.featureSimilarity=vsSimilarity;
  }
  
  public void setAlignment(Alignment alignment) {
    this.alignment=alignment;
  }


  public void log() {
    ensureAnnotated();
    LogInfo.begin_track_printAll("Example");
    LogInfo.log("Id: " + id);
    LogInfo.log("Source: " + source);
    LogInfo.logs("Source lemmas: %s", sourceInfo.lemmaTokens);
    LogInfo.logs("Source POS tags: %s", sourceInfo.posTags);
    LogInfo.logs("Source NER tags: %s", sourceInfo.nerTags);
    LogInfo.log("Target: " + target);
    LogInfo.logs("Target lemmas: %s", targetInfo.lemmaTokens);
    LogInfo.logs("Target POS tags: %s", targetInfo.posTags);
    LogInfo.logs("Target NER tags: %s", targetInfo.nerTags);
    LogInfo.log("Value: " + goldValue);
    LogInfo.end_track();
  }

  public void setEvaluation(Params params) {
    setEvaluation(params,false);
  }

  public void setEvaluation(Params params, boolean print) {
  }


  public static long cacheSize() {
    return MemUsage.getBytes(annotationCache);
  }
}
