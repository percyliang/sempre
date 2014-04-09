package edu.stanford.nlp.sempre.paraphrase;

import edu.stanford.nlp.sempre.ErrorValue;
import edu.stanford.nlp.sempre.Executor;
import edu.stanford.nlp.sempre.FormulaGenerationInfo;
import edu.stanford.nlp.sempre.Formulas;
import edu.stanford.nlp.sempre.FreebaseInfo;
import edu.stanford.nlp.sempre.ListValue;
import edu.stanford.nlp.sempre.NumberValue;

public class ParaphraseFeatureExtractor {

  private Executor executor;

  public ParaphraseFeatureExtractor(Executor executor) {
    this.executor = executor;
  }

  public void extractParaphraseDerivationFeatures(ParaphraseDerivation pDerivation) {
    extractionDenotationFeatures(pDerivation);
    extractFormulaFeatures(pDerivation);
    extractWhTypeFeature(pDerivation);
    extractNamedEntityFeature(pDerivation);
  }

  private void extractNamedEntityFeature(ParaphraseDerivation pDerivation) {
    if(!ParaphraseFeatureMatcher.containsDomain("NamedEntity")) return;
    String namedEntity = pDerivation.langInfo.nerTags.get(pDerivation.fgInfo.entityInfo1.span.first);
    pDerivation.featureVector.add("NamedEntity",namedEntity);
  }

  private void extractWhTypeFeature(ParaphraseDerivation pDerivation) {
    
    if(!ParaphraseFeatureMatcher.containsDomain("WhType")) return;

    if(pDerivation.langInfo.posTags.get(0).startsWith("W")) {
      pDerivation.featureVector.add("WhType", 
          "token0="+pDerivation.langInfo.tokens.get(0)+","+
            "type="+FreebaseInfo.getSingleton().coarseType(pDerivation.fgInfo.bInfo.expectedType1));
    }
  }

  private void extractFormulaFeatures(ParaphraseDerivation pDerivation) {
    if (!ParaphraseFeatureMatcher.containsDomain("Formula")) return;
    FormulaGenerationInfo fgInfo = pDerivation.fgInfo;
    pDerivation.featureVector.add("Formula", "binPopularity",Math.log(fgInfo.bInfo.popularity+1));
    pDerivation.featureVector.add("Formula", "entPopularity",Math.log(pDerivation.fgInfo.entityInfo1.popularity+1));
    pDerivation.featureVector.add("Formula", "binary="+fgInfo.bInfo.formula);
    if(fgInfo.isUnary) {
      pDerivation.featureVector.add("Formula", "uPopularity",Math.log(fgInfo.uInfo.popularity+1));
      pDerivation.featureVector.add("Formula", "unary");
    }
    if(fgInfo.isInject) {
      pDerivation.featureVector.add("Formula", "entPopularity",Math.log(pDerivation.fgInfo.entityInfo2.popularity+1));
      pDerivation.featureVector.add("Formula", "injected="+fgInfo.injectedInfo.formula);
      pDerivation.featureVector.add("Formula", "injectedType="+fgInfo.injectedInfo.expectedType2);
      pDerivation.featureVector.add("Formula", "inject");
    }
  }

  private void extractionDenotationFeatures(ParaphraseDerivation pDerivation) {
    if (!ParaphraseFeatureMatcher.containsDomain("Denotation")) return;
    pDerivation.ensureExecuted(executor);
    if(pDerivation.value instanceof ErrorValue) {
      pDerivation.featureVector.add("Denotation", "error");
      return;
    }

    if (!(pDerivation.value instanceof ListValue))
      throw new RuntimeException("Derivation value is not a list: " + pDerivation.value);

    ListValue list = (ListValue) pDerivation.value;
    if (Formulas.isCountFormula(pDerivation.formula)) {
      if (list.values.size() != 1) {
        throw new RuntimeException(
            "Evaluation of count formula " + pDerivation.formula + " has size " + list.values.size());
      }
      int count = (int)((NumberValue)list.values.get(0)).value;
      pDerivation.featureVector.add("Denotation", "count-size" + (count == 0 ? "=0" : ">0"));
    } else {
      int size = list.values.size();
      pDerivation.featureVector.add("Denotation", "size" + (size < 3 ? "=" + size : ">=" + 3));
    }
  }
}
