package edu.stanford.nlp.sempre.paraphrase;

import edu.stanford.nlp.sempre.Evaluation;
import edu.stanford.nlp.sempre.Params;
import edu.stanford.nlp.sempre.Value;
import fig.basic.LogInfo;
import fig.basic.NumUtils;
import fig.basic.Parallelizer;
import fig.basic.StopWatchSet;
import fig.exec.Execution;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParsingExampleProcessor implements Parallelizer.Processor<ParsingExample> {

  private final ParaphraseParser paraParser;
  private final String prefix;
  private final boolean updateWeights;
  private Params params; //this is common to threads and should be synchronized
  private Evaluation totalEval; //this is common to threads and should be synchronized
  private int gcRounds=0;

  public ParsingExampleProcessor(ParaphraseParser paraParser, Params params, String prefix, boolean updateWeights, Evaluation totalEval) {
    this.prefix = prefix;
    this.paraParser = paraParser;
    this.updateWeights=updateWeights;
    this.params = params;
    this.totalEval = totalEval;
  }

  @Override
  public void process(ParsingExample ex, int i, int n) {

    LogInfo.begin_track_printAll("%s: example %s/%s:", prefix, i, n);
    ex.log();
    Execution.putOutput("example", i);

    StopWatchSet.begin("ParaphraseParser.parseQuestion");
    paraParser.parseQuestion(ex, params);
    StopWatchSet.end();

    if (updateWeights) {
      Map<String,Double> counts = computeExpectedCounts(ex, ex.predParaDeriv);
      updateWeights(counts);
    }
    LogInfo.logs("Current: %s", ex.getEvaluation().summary());
    accumulateAndLogEvaluation(ex.getEvaluation(), totalEval, prefix);
    ex.clear(); 
    gcRounds++;
    if(gcRounds % 50 ==0)
      System.gc(); //due to memory problems
    LogInfo.end_track();
  }

  //identical only with ParsingExample and ProofDerivations
  private Map<String,Double> computeExpectedCounts(ParsingExample ex, List<ParaphraseDerivation> paraphraseDerivations) {

    Map<String,Double> res = new HashMap<>();
    double[] trueScores;
    double[] predScores;

    int n = paraphraseDerivations.size();
    if (n == 0)
      return res;

    trueScores = new double[n];
    predScores = new double[n];
    for (int i = 0; i < n; i++) {
      ParaphraseDerivation paraphraseDerivation = paraphraseDerivations.get(i);
      double r = reward(ex.targetValue, paraphraseDerivation.value);
      if (ParaphraseLearner.opts.binaryLogistic) {
        trueScores[i] = Math.exp(r);
        predScores[i] = 1.0d / (1.0d + Math.exp(-paraphraseDerivation.score));
      }
      else {
        trueScores[i] = paraphraseDerivation.score + r;
        predScores[i] = paraphraseDerivation.score;
      }
    }

    if(!ParaphraseLearner.opts.binaryLogistic) {
      if (!NumUtils.expNormalize(trueScores)) return res;
      if (!NumUtils.expNormalize(predScores)) return res;
    }
    // Update parameters
    for (int i = 0; i < n; i++) {
      ParaphraseDerivation paraphraseDerivation = paraphraseDerivations.get(i);
      double incr = trueScores[i] - predScores[i];
      paraphraseDerivation.incrementAllFeatureVector(incr, res);
    }
    return res;
  }

  private double reward(Value targetValue, Value proofValue) {
    if(ParaphraseLearner.opts.partialReward)
      return Math.log(targetValue.getCompatibility(proofValue));
    return Math.log(targetValue.getCompatibility(proofValue) == 1 ? 1 : 0);
  }

  private void updateWeights(Map<String, Double> counts) {
    StopWatchSet.begin("Learner.updateWeights");
    LogInfo.begin_track("Updating weights");
    double sum = 0;
    for (double v : counts.values()) sum += v * v;
    LogInfo.logs("L2 norm: %s", Math.sqrt(sum));
    params.update(counts);
    counts.clear();
    LogInfo.end_track();
    StopWatchSet.end();
  }

  private void accumulateAndLogEvaluation(Evaluation exEvaluation,
      Evaluation totalEvaluation,
      String prefix) {
    totalEvaluation.add(exEvaluation);
    LogInfo.logs("Cumulative(%s): %s", prefix, totalEvaluation.summary());
  }
}
