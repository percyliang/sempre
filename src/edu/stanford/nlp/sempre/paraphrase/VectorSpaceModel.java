package edu.stanford.nlp.sempre.paraphrase;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.google.common.base.Strings;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.sempre.BooleanValue;
import edu.stanford.nlp.sempre.FeatureVector;
import edu.stanford.nlp.sempre.LanguageInfo;
import edu.stanford.nlp.sempre.Params;
import edu.stanford.nlp.sempre.LanguageInfo.LanguageUtils;
import fig.basic.ListUtils;
import fig.basic.LogInfo;
import fig.basic.MapUtils;
import fig.basic.NumUtils;
import fig.basic.Option;
import fig.basic.MemUsage;
import fig.basic.Pair;

/**
 * Provides similarity score for a pair of phrases |x| and |x'| by representing them
 * a pair of vectors and computing similarity with some parameters - e.g.
 * s(x,x')=xWx' where W is a matrix (diagonal or not...) etc.
 * @author jonathanberant
 */
public class VectorSpaceModel implements MemUsage.Instrumented,FeatureSimilarityComputer {

  public enum PhraseRep {
    ADDITIVE,
    CW_ADDITIVE,
    AVG,
    CW_AVG;

    public static PhraseRep parse(String str) {
      if("additive".equals(str))
        return ADDITIVE;
      if("cw_additive".equals(str))
        return CW_ADDITIVE;
      if("avg".equals(str))
        return AVG;
      if("cw_avg".equals(str))
        return CW_AVG;
      throw new RuntimeException("Illegal mode: " + str);
    }
  }

  public enum SimilarityFunc {
    DIAGNONAL,
    DOT_PROD,
    FULL_MATRIX;

    public static SimilarityFunc parse(String str) {
      if("diagonal".equals(str))
        return DIAGNONAL;
      if("full_matrix".equals(str))
        return FULL_MATRIX;
      if("dot_product".equals(str))
        return DOT_PROD;
      throw new RuntimeException("Illegal mode: " + str);
    }
  }

  public static class Options {
    @Option(gloss = "Path to file containing word vectors, one per line") public String wordVectorFile;
    @Option(gloss = "Vector dimension") public int vecCapacity=50;
    @Option(gloss = "VSM model phrase representation") public String phraseRep="cw_avg";
    @Option(gloss = "VSM model similarity function") public String similarityFunc="full_matrix";
    @Option(gloss = "verbose") public int verbose = 0;
  }
  public static Options opts = new Options();
  private static VectorSpaceModel vsm;

  private Map<String, double[]> wordVectors;
  private static Map<String,double[]> phraseVectorCache; //not clear this is necessary for efficiency
  private PhraseRep vsmPhraseRep;
  private SimilarityFunc vsmSimilarityFunc;

  public static VectorSpaceModel getSingleton() {
    if(vsm==null) 
      vsm = new VectorSpaceModel();
    return vsm;
  }

  private VectorSpaceModel() {
    vsmPhraseRep=PhraseRep.parse(opts.phraseRep);
    vsmSimilarityFunc=SimilarityFunc.parse(opts.similarityFunc);
    wordVectors = new HashMap<String, double[]>();
    phraseVectorCache = new HashMap<String, double[]>();
    if (Strings.isNullOrEmpty(opts.wordVectorFile))
      return;

    String header = null;
    for (String line : IOUtils.readLines(opts.wordVectorFile)) {
      String[] tokens = line.split("\\s+");
      // Some word embedding files have a header which includes the number of
      // words and the number of dimensions.  Ignore this.
      if (header == null && tokens.length == 2) {
        header = line;
        continue;
      }
      if (tokens.length - 1 != opts.vecCapacity)
        throw new RuntimeException("Expected " + opts.vecCapacity + " tokens, but got " + (tokens.length-1) + ": " + line);
      double[] vector = new double[opts.vecCapacity];
      for (int i = 1; i < tokens.length; ++i)
        vector[i-1]=Double.parseDouble(tokens[i]);
      wordVectors.put(tokens[0], vector);
    }
  }

  public void computeSimilarity(ParaphraseExample ex, Params params) {

    ex.ensureAnnotated();
    //get source and target representations
    double[] sourceVec,targetVec;
    synchronized (phraseVectorCache) {
      sourceVec = phraseVectorCache.containsKey(ex.source) ? phraseVectorCache.get(ex.source) : computeUtteranceVec(ex.sourceInfo);
      targetVec = phraseVectorCache.containsKey(ex.target) ? phraseVectorCache.get(ex.target) : computeUtteranceVec(ex.targetInfo);
      MapUtils.putIfAbsent(phraseVectorCache, ex.source, sourceVec);
      MapUtils.putIfAbsent(phraseVectorCache, ex.target, targetVec);
    }
    //combine them
    FeatureVector fv;
    if(vsmSimilarityFunc==SimilarityFunc.DIAGNONAL)
      fv = getDiagonalMatrixFeatures(sourceVec,targetVec);
    else if(vsmSimilarityFunc==SimilarityFunc.FULL_MATRIX)
      fv = getFullMatrixFeatures(sourceVec,targetVec);
    else //dot product
      fv = getDotProductFeature(sourceVec,targetVec);
    //set stuff
    ex.setVectorSpaceSimilarity(new FeatureSimilarity(fv,ex.source,ex.target,params));
  }

  private FeatureVector getDotProductFeature(double[] sourceVec, double[] targetVec) {    
    FeatureVector res = new FeatureVector();
    res.add("VS","dot_product",ListUtils.dot(sourceVec, targetVec));
    return res;
  }

  private FeatureVector getFullMatrixFeatures(double[] sourceVec,
      double[] targetVec) {
    //FeatureVector res = new FeatureVector();
    FeatureVector res = new FeatureVector(opts.vecCapacity*opts.vecCapacity);
    int featureNum=0;
    for(int i = 0; i < sourceVec.length; ++i) {
      for(int j = 0; j < targetVec.length; j++) {
        res.addDenseFeature(featureNum++, sourceVec[i]*targetVec[j]);
        //res.add("VS", "d"+i+",d"+j, sourceVec[i]*targetVec[j]);
      }
    }
    return res;
  }

  private FeatureVector getDiagonalMatrixFeatures(double[] source,
      double[] target) {
    FeatureVector res = new FeatureVector(opts.vecCapacity);
    int featureNum=0;
    for(int i = 0; i < source.length; ++i) {
      res.addDenseFeature(featureNum++, source[i]*target[i]);
    }
    return res;
  }

  /**
   * The vec representation of a phrase is the some of its words
   * @param langInfo
   * @return
   */
  private double[] computeUtteranceVec(LanguageInfo langInfo) {

    double[] res = new double[opts.vecCapacity];
    int numOfAddedTokens=0;
    for(int i = 0; i < langInfo.numTokens(); ++i) {
      String pos = langInfo.posTags.get(i);
      if((vsmPhraseRep==PhraseRep.CW_ADDITIVE || vsmPhraseRep==PhraseRep.CW_AVG)
          && !LanguageUtils.isContentWord(pos))
        continue;

      double[] tokenVec = wordVectors.get(langInfo.tokens.get(i));
      if(tokenVec!=null) {
        ListUtils.addMut(res, tokenVec);
        numOfAddedTokens++;
      }
    }
    if((vsmPhraseRep==PhraseRep.AVG || vsmPhraseRep==PhraseRep.CW_AVG)
        && numOfAddedTokens > 0) {
      double inverse = (double) 1 / numOfAddedTokens;
      res = ListUtils.mult(inverse, res);
    }
    return res;
  }

  /**
   * Holds the similarity features (e.g. component wise product or outer product etc.)
   * @author jonathanberant
   *
   */

  @Override
  public long getBytes() {
    return MemUsage.objectSize(MemUsage.pointerSize*2)+
        MemUsage.getBytes(wordVectors)+MemUsage.getBytes(phraseVectorCache);
  }

  public static long cacheSize() {
    return MemUsage.getBytes(phraseVectorCache);
  }

  private void printWordSimilarity(ParaphraseExample paraExample, Params params) {
    double alpha = 0.02;
    List<Pair<String,Double>> scoreList = new LinkedList<Pair<String,Double>>();
    paraExample.ensureAnnotated();
    for(int i = 0; i < paraExample.sourceInfo.numTokens(); ++i) {
      String sourcePos = paraExample.sourceInfo.posTags.get(i);
      if((vsmPhraseRep==PhraseRep.CW_ADDITIVE || vsmPhraseRep==PhraseRep.CW_AVG)
          && !LanguageUtils.isContentWord(sourcePos))
        continue;

      double[] sourceTokenVec = wordVectors.get(paraExample.sourceInfo.tokens.get(i));
      if(sourceTokenVec!=null) {
        for(int j = 0; j < paraExample.targetInfo.numTokens(); ++j) {
          String targetPos = paraExample.targetInfo.posTags.get(j);
          if((vsmPhraseRep==PhraseRep.CW_ADDITIVE || vsmPhraseRep==PhraseRep.CW_AVG)
              && !LanguageUtils.isContentWord(targetPos))
            continue;
          String token1 = paraExample.sourceInfo.tokens.get(i);
          String token2 = paraExample.targetInfo.tokens.get(j);
          if((token1.equals("czech") || token1.equals("republic")) && 
              (token2.equals("czech") || token2.equals("republic")))
            continue;

          double[] targetTokenVec = wordVectors.get(paraExample.targetInfo.tokens.get(j));
          if(targetTokenVec!=null) {
            FeatureVector fv;
            if(vsmSimilarityFunc==SimilarityFunc.FULL_MATRIX)
              fv = getFullMatrixFeatures(sourceTokenVec, targetTokenVec);
            else if(vsmSimilarityFunc==SimilarityFunc.DIAGNONAL)
              fv = getDiagonalMatrixFeatures(sourceTokenVec, targetTokenVec);
            else
              fv = getDotProductFeature(sourceTokenVec, targetTokenVec);
            double score = fv.dotProduct(params);
            scoreList.add(Pair.newPair(paraExample.sourceInfo.tokens.get(i)+","+paraExample.targetInfo.tokens.get(j), alpha*score));
          }
        }
      }
    }
    double[] scores = new double[scoreList.size()];
    String[] tokens = new String[scoreList.size()];
    for(int i = 0; i < scoreList.size();++i) {
      tokens[i] = scoreList.get(i).getFirst();
      scores[i] = scoreList.get(i).getSecond();
    }
    NumUtils.expNormalize(scores);
    for(int i = 0; i < scores.length; ++i)
      LogInfo.log(tokens[i]+"\t"+scores[i]);
  }

  public static void main(String[] args) throws IOException {
    opts.wordVectorFile = "/Users/jonathanberant/Projects/semparse/lib/wordreprs/cbow-lowercase-50.vectors";
    ParaphraseExample paraExample =new ParaphraseExample("what do people in the czech republic speak?",
        "the official language of czech republic ?",new BooleanValue(true));
    if(args[0].equals("full_matrix")) {
      opts.similarityFunc="full_matrix";
      VectorSpaceModel vsm = VectorSpaceModel.getSingleton();
      Params params = new Params();
      params.read("/Users/jonathanberant/Research/temp/918params"); //full matrix
      vsm.printWordSimilarity(paraExample,params);
    }
    else if(args[0].equals("diagonal")) {
      opts.similarityFunc="diagonal";
      VectorSpaceModel vsm = VectorSpaceModel.getSingleton();
      Params params = new Params();
      params.read("/Users/jonathanberant/Research/temp/949params"); //diagonal
      vsm.printWordSimilarity(paraExample,params);
    }
    else {
      opts.similarityFunc="dot_product";
      VectorSpaceModel vsm = VectorSpaceModel.getSingleton();
      Params params = new Params();
      params.read("/Users/jonathanberant/Research/temp/954params"); //diagonal
      vsm.printWordSimilarity(paraExample,params);
    }
  }
}
