package edu.stanford.nlp.sempre.paraphrase;

import com.google.common.collect.Maps;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.sempre.*;
import fig.basic.Fmt;
import fig.basic.LogInfo;
import fig.basic.MapUtils;
import fig.basic.NumUtils;
import fig.basic.Option;
import fig.basic.StopWatchSet;
import fig.basic.TDoubleMap;
import fig.exec.Execution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Performs transformation learning that is finds a logical form for the training set
 * and for each test set finds the logical form that is best given paraphrase
 * that lead from the test example to the train example.
 * @author jonathanberant
 *
 */
public class NearestNeighborLearner {

  public static class Options {
    @Option public int verbose = 3;
    @Option public boolean uploadContexts = false;
    @Option public String contextFile;
  }
  public static Options opts = new Options();

  private final Parser trainParser;
  private final Dataset dataset;
  private final ContextModel contextModel;
  private final Params params;
  private final Executor executor;
  private final PrintWriter contextOut;
  private final EntityModel entityModel;

  public NearestNeighborLearner(Parser trainParser, Parser entityModelParser, Dataset dataset, Executor executor) throws IOException {
    this.trainParser = trainParser;
    this.dataset = dataset;
    this.executor = executor;
    params = new Params();  //we are not really doing discrminative learning so params will be zero
    if(opts.uploadContexts) {
      BufferedReader reader = IOUtils.getBufferedFileReader(opts.contextFile);
      this.contextModel = Json.readValueHard(reader, ContextModel.class);
    }
    else
      this.contextModel = new ContextModel();
    this.contextOut = fig.basic.IOUtils.openOut(Execution.getFile("contextToBinaryCounter.txt"));
    entityModel = new HeuristicEntityModel(entityModelParser, this.contextModel);
  }

  public void learn() {
    Map<String, List<Evaluation>> m = Maps.newHashMap();
    learn(m);
  }

  private void learn(Map<String, List<Evaluation>> evaluations) {

    LogInfo.begin_track("Learner.learn()");

    // Averaged over all iterations
    // Group -> evaluation for that group.
    Map<String, Evaluation> meanEvaluations = Maps.newHashMap();

    // Clear
    for (String group : dataset.groups())
      meanEvaluations.put(group, new Evaluation());

    // Test and train
    for (String group : dataset.groups()) {
      Evaluation eval = processExamples(group,dataset.examples(group));
      MapUtils.addToList(evaluations, group, eval);
      meanEvaluations.get(group).add(eval);
      StopWatchSet.logStats();
    }
    LogInfo.end_track();
  }

  private Evaluation processExamples(String group, List<Example> examples) {

    Evaluation evaluation = new Evaluation();
    if(group.equals("train")) {
      learnContexts(group,examples,evaluation);
    }
    else {
      infer(group,examples,evaluation);
    }
    return evaluation;
  }

  private void learnContexts(String group, List<Example> examples, Evaluation eval) {

    if(opts.uploadContexts) {
      LogInfo.log("Context map was uploaded so there is no context learning");
      contextModel.log();
      return;
    }
    if (examples.size() == 0)
      return;

    final String prefix = group;

    Execution.putOutput("group", group);
    LogInfo.begin_track_printAll(
        "Processing %s: %s examples", prefix, examples.size());

    for (int e = 0; e < examples.size(); e++) {
      Example ex = examples.get(e);

      LogInfo.begin_track_printAll(
          "%s: example %s/%s: %s", prefix, e, examples.size(), ex.id);
      ex.log();
      Execution.putOutput("example", e);

      parseExample(ex, trainParser, true);
      Map<String,Double> counts = new HashMap<String,Double>();
      computeExpectedCounts(ex, ex.getPredDerivations(), counts);
      updateWeights(counts);
      logExampleEvaluation(ex);
      accumulateAndLogEvaluation(ex, eval, prefix);

      contextModel.map(ex);
      ex.clearPredDerivations(); //to save memory since the beam size is so huge
      LogInfo.end_track();
    }
    logEvaluationStats(eval, prefix);
    contextModel.normalize();
    contextModel.log();
    Json.writeValueHard(contextOut, contextModel);
    String path = Execution.getFile("params");
    params.write(path);

    LogInfo.end_track();
  }

  private void infer(String group, List<Example> examples, Evaluation eval) {
    if (examples.size() == 0)
      return;

    final String prefix = group;

    Execution.putOutput("group", group);
    LogInfo.begin_track_printAll(
        "Processing %s: %s examples", prefix, examples.size());
    LogInfo.begin_track("Examples");

    for (int e = 0; e < examples.size(); e++) {
      Example ex = examples.get(e);

      LogInfo.begin_track_printAll(
          "%s: example %s/%s: %s", prefix, e, examples.size(), ex.id);
      ex.log();
      Execution.putOutput("example", e);

      //generate a distribution p(c,s,e|x) - now we will have a heuristic for choosing a single pair (c,e)
      TDoubleMap<EntityInstance> entityInstanceDist = entityModel.calcEntityInstanceDist(ex);
      //compute and sort the formulas
      List<Prediction> predictions = contextModel.computePredictions(ex, entityInstanceDist);
      //set evaluation
      setEvaluation(ex, predictions);
      logExampleEvaluation(ex);
      accumulateAndLogEvaluation(ex, eval, prefix);
      LogInfo.end_track();
    }

    LogInfo.end_track();
    logEvaluationStats(eval, prefix);
    LogInfo.end_track();
  } 

  public void setEvaluation(final Example ex, List<Prediction> predictions) {
    final Evaluation eval = new Evaluation();
    int numCandidates = predictions.size();
    LogInfo.begin_track_printAll("ParaphraseLearner.setEvaluation: %d candidates", numCandidates);

    // Make sure at least the top derivation is executed.
    for (Prediction prediction : predictions) {
      prediction.ensureExecuted(executor);
    }

    // Did we get the answer correct?
    int correct_i = -1;  // Index of first correct derivation
    double maxCompatibility = 0.0;
    double[] compatibilities = null;
    if (ex.targetValue != null) {
      compatibilities = new double[numCandidates];
      for (int i = 0; i < numCandidates; i++) {
        Prediction prediction = predictions.get(i);
        compatibilities[i] = prediction.compatibility = ex.targetValue.getCompatibility(prediction.value);

        // Must be fully compatible to count as correct.
        if (compatibilities[i] == 1 && correct_i == -1)
          correct_i = i;
        //record maximum compatibility for partial oracle
        maxCompatibility = Math.max(compatibilities[i], maxCompatibility);
      }
    }

    // Number of scored formulas which have the same top score
    int numTop = 0;
    double topMass = 0;
    if (ex.targetValue != null) {
      while (numTop < numCandidates &&
          compatibilities[numTop] > 0.0d &&
          Math.abs(predictions.get(numTop).score - predictions.get(0).score) < 1e-10) {
        topMass += predictions.get(numTop).score;
        numTop++;
      }
    }
    double correct = 0;
    double partial_correct = 0;
    if (ex.targetValue != null) {
      for (int i = 0; i < numTop; i++) {
        if (compatibilities[i] == 1) correct += predictions.get(i).score / topMass;
        if (compatibilities[i] > 0)
          partial_correct += (compatibilities[i] * predictions.get(i).score) / topMass;
      }
    }

    // Fully correct
    for (int i = 0; i < predictions.size(); i++) {
      Prediction prediction = predictions.get(i);
      if (compatibilities != null && compatibilities[i] == 1) {
        LogInfo.logs(
            "True@%04d: %s [score=%s] value=%s compatibility=%s", i, prediction.formula,
            Fmt.D(prediction.score),prediction.value,prediction.compatibility);
      }
    }
    // Partially correct
    for (int i = 0; i < predictions.size(); i++) {
      Prediction prediction = predictions.get(i);
      if (compatibilities != null && compatibilities[i] > 0 && compatibilities[i] < 1) {
        LogInfo.logs(
            "Part@%04d: %s [score=%s] value=%s compatibility=%s", i, prediction.formula,
            Fmt.D(prediction.score),prediction.value,prediction.compatibility);
      }
    }
    // Anything that's predicted.
    for (int i = 0; i < predictions.size(); i++) {
      Prediction prediction = predictions.get(i);
      // Either print all predictions or this prediction is worse by some amount.
      boolean print = i < 50;
      if (print) {
        LogInfo.logs(
            "Pred@%04d: %s [score=%s] value=%s compatibility=%s", i, prediction.formula,
            Fmt.D(prediction.score),prediction.value,prediction.compatibility);
      }
    }

    eval.add("correct", correct);
    eval.add("oracle", correct_i != -1);
    eval.add("partCorrect", partial_correct);
    eval.add("partOracle", maxCompatibility);

    eval.add("numCandidates", numCandidates);  // From this parse
    if (predictions.size() > 0)
      eval.add("parsedNumCandidates", predictions.size());

    // Finally, set all of these stats as the example's evaluation.
    ex.setEvaluation(eval);
    LogInfo.end_track();
  }


  private void parseExample(Example ex, Parser parser, boolean execAllowed) {
    StopWatchSet.begin("Parser.parse");
    parser.parse(params, ex, execAllowed);
    StopWatchSet.end();
  }

  private void logExampleEvaluation(Example ex) {
    LogInfo.logs("Current: %s", ex.computeTotalEvaluation().summary());
  }

  private void accumulateAndLogEvaluation(Example ex,
      Evaluation evaluation,
      String prefix) {
    evaluation.add(ex.computeTotalEvaluation());
    LogInfo.logs("Cumulative(%s): %s", prefix, evaluation.summary());
  }

  private void logEvaluationStats(Evaluation evaluation, String prefix) {
    LogInfo.logs("Stats for %s: %s", prefix, evaluation.summary());
    evaluation.add(LexiconFn.lexEval);
    evaluation.logStats(prefix);
    evaluation.putOutput(prefix);
  }

  private void computeExpectedCounts(Example ex, List<Derivation> derivations, Map<String, Double> counts) {

    double[] trueScores;
    double[] predScores;

    int n = derivations.size();
    if (n == 0)
      return;

    trueScores = new double[n];
    predScores = new double[n];
    for (int i = 0; i < n; i++) {
      Derivation deriv = derivations.get(i);
      double r = reward(ex.targetValue, deriv.getValue());
      trueScores[i] = deriv.getScore() + r;
      predScores[i] = deriv.getScore();

    }

    if (!NumUtils.expNormalize(trueScores)) return;
    if (!NumUtils.expNormalize(predScores)) return;
    for (int i = 0; i < n; i++) {
      Derivation deriv = derivations.get(i);
      double incr = trueScores[i] - predScores[i];
      deriv.incrementAllFeatureVector(incr, counts);
    }
  }

  private double reward(Value targetValue, Value predValue) {
    if (targetValue != null) {    
      return Math.log(targetValue.getCompatibility(predValue));
    }
    return 0;
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
}
