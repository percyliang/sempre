package edu.stanford.nlp.sempre.paraphrase;

import edu.stanford.nlp.sempre.FeatureVector;
import edu.stanford.nlp.sempre.Params;
import edu.stanford.nlp.sempre.fbalignment.utils.MathUtils;
import fig.basic.Option;

/**
 * Interface for computing similarity from all kinds of surface features.
 * @author jonathanberant
 *
 */
public interface FeatureSimilarityComputer {
  
  public static class Options {
    @Option(gloss="Whether to use the vsm model") public String mode;
  }
  public static Options opts = new Options();
  
  public void computeSimilarity(ParaphraseExample ex, Params params);
}

class FeatureSimilarityComputerFactory {
  
  public static FeatureSimilarityComputer getFeatureSimilarityComputer() { 
    if(FeatureSimilarityComputer.opts.mode.equals("lexical_overlap"))
      return new LexicalOverlap();
    if(FeatureSimilarityComputer.opts.mode.equals("edit_distance"))
      return new EditDistance();
    if(FeatureSimilarityComputer.opts.mode.equals("wan"))
      return new WanFeatures();
    throw new RuntimeException("Illegal mode: " + FeatureSimilarityComputer.opts.mode);
  }
}

class LexicalOverlap implements FeatureSimilarityComputer {
  @Override
  public void computeSimilarity(ParaphraseExample ex, Params params) {
    ex.ensureAnnotated();
    double jaccard = MathUtils.jaccard(ex.sourceInfo.lemmaTokens, ex.targetInfo.lemmaTokens);
    FeatureVector fv = new FeatureVector();
    fv.add("Jaccard", "jaccard", jaccard);
    ex.setVectorSpaceSimilarity(new FeatureSimilarity(fv,ex.source,ex.target,params));
  }
  
}

class EditDistance implements FeatureSimilarityComputer {
  @Override
  public void computeSimilarity(ParaphraseExample ex, Params params) {
    ex.ensureAnnotated();
    int editDistance = ParaphraseUtils.editDistance(ex.sourceInfo.lemmaTokens, ex.targetInfo.lemmaTokens);
    FeatureVector fv = new FeatureVector();
    fv.add("EditDistance", "distance", (double) editDistance);
    ex.setVectorSpaceSimilarity(new FeatureSimilarity(fv,ex.source,ex.target,params));
  }
}

class WanFeatures implements FeatureSimilarityComputer {

  @Override
  public void computeSimilarity(ParaphraseExample ex, Params params) {
    
    ex.ensureAnnotated();
    
    FeatureVector fv = new FeatureVector();
    double precision = MathUtils.coverage(ex.sourceInfo.tokens, ex.targetInfo.tokens);
    double recall = MathUtils.coverage(ex.targetInfo.tokens, ex.sourceInfo.tokens);
    double f1 = (precision+recall<=0d)? 0d :  
        (2 * precision * recall) / (precision+recall);
    double lemmaPrecision = MathUtils.coverage(ex.sourceInfo.lemmaTokens, ex.targetInfo.lemmaTokens);
    double lemmaRecall = MathUtils.coverage(ex.targetInfo.lemmaTokens, ex.sourceInfo.lemmaTokens);
    addIfBetweenZeroAndOne(fv, "Wan", "precision", precision);
    addIfBetweenZeroAndOne(fv, "Wan", "recall", recall);
    addIfBetweenZeroAndOne(fv, "Wan", "f1", f1);
    addIfBetweenZeroAndOne(fv, "Wan", "lemmaPrecision", lemmaPrecision);
    addIfBetweenZeroAndOne(fv, "Wan", "lemmaRecall", lemmaRecall);
    fv.add("Wan", "lengthDiff", ex.sourceInfo.numTokens()-ex.targetInfo.numTokens());
    fv.add("Wan", "AbsLengthDiff", Math.abs(ex.sourceInfo.numTokens()-ex.targetInfo.numTokens()));
    int editDistance = ParaphraseUtils.editDistance(ex.sourceInfo.tokens, ex.targetInfo.tokens);
    int lemmaEditDistance = ParaphraseUtils.editDistance(ex.sourceInfo.lemmaTokens, ex.targetInfo.lemmaTokens);
    fv.add("Wan", "editDistance", editDistance);
    fv.add("Wan", "lemmaEditDistance", lemmaEditDistance);
    double precisionBleu = MathUtils.bleu(ex.sourceInfo.tokens, ex.targetInfo.tokens);
    double recallBleu = MathUtils.bleu(ex.targetInfo.tokens, ex.sourceInfo.tokens);
    double lemmaPecisionBleu = MathUtils.bleu(ex.sourceInfo.lemmaTokens, ex.targetInfo.lemmaTokens);
    double lemmaRecallBleu = MathUtils.bleu(ex.targetInfo.lemmaTokens, ex.sourceInfo.lemmaTokens);
    addIfBetweenZeroAndOne(fv, "Wan", "precisionBlue", precisionBleu);
    addIfBetweenZeroAndOne(fv, "Wan", "recallBleu", recallBleu);
    addIfBetweenZeroAndOne(fv, "Wan", "lemmaPecisionBleu", lemmaPecisionBleu);
    addIfBetweenZeroAndOne(fv, "Wan", "lemmaRecallBleu", lemmaRecallBleu);
    
    ex.setVectorSpaceSimilarity(new FeatureSimilarity(fv,ex.source,ex.target,params));
  }
  
  public void addIfBetweenZeroAndOne(FeatureVector fv, String domain, String name, double value) {
    if(value>1.0000000001)
      throw new RuntimeException("Illegal feature value, feature="+name+", value="+value);
    if(value<-0.0000000001)
      throw new RuntimeException("Illegal feature value, feature="+name+", value="+value);
    if(value>0.000000001)
      fv.add(domain, name, value);
      
  }
}

