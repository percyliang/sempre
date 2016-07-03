package edu.stanford.nlp.sempre.interactive;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.Example;
import edu.stanford.nlp.sempre.ParserState;
import fig.basic.LogInfo;
import fig.basic.NumUtils;
import fig.basic.Option;

public class PragmaticListener {
  public static class Options {
    @Option(gloss="Use normalized model") public boolean addModelProb  = false;
    @Option(gloss = "Initial uniform probability") public double uniformWeight = 0.01;
    @Option(gloss="Laplacian smoothing parameter for pragmatics") public double smoothAlpha = 1.0;
    @Option(gloss="labmda") public double lambda = 3.0;
  }
  public static Options opts = new Options();
  
  Map<Integer, Double> countZs; // count of formula
  Map<Integer, Double> countPs; // cummulative counter of what's learned so far, over formula
  Map<Integer, Double> countRule; // count of rules, perhaps
  double totalCount = 0.0;
  int numExamples = 0;
  public PragmaticListener() {
    this.countZs = new HashMap<>();
    this.countPs = new HashMap<>();
  }
  
  private void addZ(int hashcode, double amount) {
    Double current = countZs.get(hashcode);
    if (current == null) {current = 0.0;};
    countZs.put(hashcode, current+amount);
    totalCount+=amount;
  }
  private void addP(int hashcode, double amount) {
    Double current = countPs.get(hashcode);
    if (current == null) {current = 0.0;};
    countPs.put(hashcode, current+amount);
  }
  
  public void addExample(Example ex) {
    List<Derivation> derivations = ex.predDerivations;
    int n = derivations.size();
    if (n == 0) return;
    double [] trueScores = new double[n];
    numExamples++;
    
    for (int i = 0; i < n; i++) {
      Derivation deriv = derivations.get(i);
      // here is a choice between uniform and model distribution
      if (!opts.addModelProb)
        trueScores[i] = Math.log(ParserState.compatibilityToReward(deriv.compatibility));
      else
        trueScores[i] = deriv.getScore() + Math.log(ParserState.compatibilityToReward(deriv.compatibility));
    }
    if (!NumUtils.expNormalize(trueScores)) return;
    for (int i = 0; i < n; i++) {
      Derivation deriv = derivations.get(i);
      int hash = deriv.getFormula().computeHashCode();
      this.addZ(hash, trueScores[i]);
      this.addP(hash, Math.pow(deriv.prob, opts.lambda));
    }
  }
  private double getSumProb(int hashcode) { // running total, based on how well the model explains other stuff
    Double current = countPs.get(hashcode);
    if (current == null) {current = 0.0;};
    return current + opts.uniformWeight;
  }
  private double getPz(int hashcode) { // this should be a good, actual estimate of P(z)
    Double current = countZs.get(hashcode);
    if (current == null) {current = 0.0;};
    return (current.doubleValue() + opts.smoothAlpha) / (totalCount + opts.smoothAlpha*(1+countZs.size()));
  }
  
  public void inferPragmatically(Example ex) {
    LogInfo.log("inferPragmatically");
    List<Derivation> derivations = ex.predDerivations;
    double[] probs = new double[derivations.size()];
    for (int i = 0; i < derivations.size(); i++) {
      Derivation deriv = derivations.get(i);
      int hash = deriv.getFormula().computeHashCode();
      probs[i] = Math.pow(deriv.prob, opts.lambda) / getSumProb(hash) * getPz(hash);
    }
    if (probs.length > 0)
      NumUtils.normalize(probs);
    
    for (int i = 0; i < derivations.size(); i++) {
      Derivation deriv = derivations.get(i);
      deriv.pragmatic_prob = probs[i];
    }
    // throw new RuntimeException("WTF?");
  }
}