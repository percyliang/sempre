package edu.stanford.nlp.sempre;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import fig.basic.*;
import fig.exec.Execution;

import java.io.PrintWriter;
import java.util.*;

/**
 * The main learning loop.  Goes over a dataset multiple times, calling the
 * parser and updating parameters.
 *
 * @author Percy Liang
 */
public class Learner {
  public static class Options {
    @Option(gloss = "Number of iterations to train")
    public int maxTrainIters = 0;
    @Option(gloss = "When using mini-batch updates for SGD, this is the batch size")
    public int batchSize = 1;  // Default is SGD
    @Option(gloss = "Don't update feature weights that start with this")
    public List<String> staticFeaturePrefixes = new ArrayList<String>();
    @Option(gloss = "Write predDerivations to examples file (huge)")
    public boolean outputPredDerivations = false;
    @Option(gloss = "Multiply beam size by this factor after each iteration")
    public double beamSizeIncreaseFactor = 1;
    @Option(gloss = "Take beams of this size in each iteration (repeating the " +
        "last value.  Overrides beamSizeIncreaseFactor when set.")
    public List<Integer> beamSizePerIteration = null;
    @Option(gloss = "Dump all features and compatibility scores")
    public boolean dumpFeaturesAndCompatibility = false;

    @Option(gloss = "Always update when using listwise model.")
    public boolean alwaysUpdate = false;
    @Option(gloss = "Whether to update based on partial reward.")
    public boolean partialReward = false;

    // DELETE?
    @Option(gloss = "Use binary logistic regression model")
    public boolean binaryLogistic = false;
    @Option(gloss = "Only update weights when beam has at least one correct prediction.")
    public boolean binaryLogisticThrottledUpdates = false;
  }

  private static final double EPSILON = 0.000000001d;

  private final Parser parser;
  private final Params params;
  private final Dataset dataset;
  private final FeatureMatcher updateFeatureMatcher;  // Update parameters for features that match according to this.
  private final PrintWriter eventsOut;  // For printing a machine-readable log

  public static Options opts = new Options();

  public Learner(Parser parser, Params params, Dataset dataset) {
    this.parser = parser;
    this.params = params;
    this.dataset = dataset;
    this.eventsOut = IOUtils.openOutAppendEasy(Execution.getFile("learner.events"));

    // Only update features that don't match any of the static feature prefixes.
    this.updateFeatureMatcher = new FeatureMatcher() {
      @Override
      public boolean matches(String feature) {
        for (String prefix : opts.staticFeaturePrefixes)
          if (feature.startsWith(prefix))
            return false;
        return true;
      }
    };
  }

  public void learn() {
    learn(-1);
  }

  public void learn(int iters) {
    Map<String, List<Evaluation>> m = Maps.newHashMap();
    learn(iters, m);
  }

  public void learn(Map<String, List<Evaluation>> evaluations) {
    learn(-1, evaluations);
  }

  /**
   * @param evaluations Evaluations per iteration per group.
   */
  public void learn(int iters, Map<String, List<Evaluation>> evaluations) {
    LogInfo.begin_track("Learner.learn()");

    if (iters < 0)
      iters = opts.maxTrainIters;

    // For each iteration, go through the groups and parse (updating if train).
    for (int iter = 0; iter <= iters; iter++) {
      LogInfo.begin_track("Iteration %s/%s", iter, iters);
      Execution.putOutput("iter", iter);

      // Averaged over all iterations
      // Group -> evaluation for that group.
      Map<String, Evaluation> meanEvaluations = Maps.newHashMap();

      // Clear
      for (String group : dataset.groups())
        meanEvaluations.put(group, new Evaluation());

      // Test and train
      for (String group : dataset.groups()) {
        boolean lastIter = iter == iters;
        boolean updateWeights = group.equals("train") && !lastIter;  // Don't train on last iteration
        Evaluation eval = processExamples(
            iter,
            group,
            dataset.examples(group),
            updateWeights,
            lastIter);
        MapUtils.addToList(evaluations, group, eval);
        meanEvaluations.get(group).add(eval);
        StopWatchSet.logStats();
      }

      // Write out parameters
      String path = Execution.getFile("params." + iter);
      if (path != null) {
        params.write(path);
        Utils.systemHard("ln -sf params." + iter + " " + Execution.getFile("params"));
      }

      // Write out examples and predictions
      for (String group : dataset.groups())
        Vis.writeExamples(iter, group, dataset.examples(group), opts.outputPredDerivations);

      LogInfo.end_track();
    }
    LogInfo.logs("Learner: number of touched binaries: %s",FbFormulasInfo.numOfTouchedBinaries());
    LogInfo.end_track();
  }

  public void onlineLearnExample(Example ex) {
    LogInfo.begin_track("onlineLearnExample: %s derivations", ex.predDerivations.size());
    HashMap<String, Double> counts = new HashMap<String, Double>();
    updateCounts(ex, counts);
    params.update(counts);
    LogInfo.end_track();
  }

  // How much reward do we get?
  private double reward(Value targetValue, Value predValue) {
    if (targetValue != null) {
      if(opts.partialReward)
        return Math.log(targetValue.getCompatibility(predValue));
      return Math.log(targetValue.getCompatibility(predValue) == 1 ? 1 : 0);
    }
    return 0;
  }

  private void computeExpectedCounts(Example ex, List<Derivation> derivations, Map<String, Double> counts) {
    final boolean addDummy = opts.alwaysUpdate && !opts.binaryLogistic;

    double[] trueScores;
    double[] predScores;

    int n = derivations.size();
    if (n == 0)
      return;

    trueScores = new double[n + (addDummy ? 1 : 0)];
    predScores = new double[n + (addDummy ? 1 : 0)];
    for (int i = 0; i < n; i++) {
      Derivation deriv = derivations.get(i);
      double r = reward(ex.targetValue, deriv.value);
      if (opts.binaryLogistic) {
        trueScores[i] = Math.exp(r);
        predScores[i] = 1.0d / (1.0d + Math.exp(-deriv.score));
      } else {
        trueScores[i] = deriv.score + r;
        predScores[i] = deriv.score;
      }
    }
    if (addDummy) {
      trueScores[n] = Math.log(EPSILON);
      predScores[n] = 0.0d;
    }

    if (!opts.binaryLogistic) {
      if (!NumUtils.expNormalize(trueScores)) return;
      if (!NumUtils.expNormalize(predScores)) return;
    } else if (opts.binaryLogisticThrottledUpdates) {
      // Only update if there's a correct prediction on the beam, so
      // as to provide comparison with non-binary model.
      boolean ok = false;
      for (int i = 0; i < n; i++) {
        if (trueScores[i] > 0.0d) {
          ok = true;
          break;
        }
      }
      if (!ok)
        return;
    }
    //LogInfo.logs("TRUE: %s", Fmt.D(trueScores));
    //LogInfo.logs("PRED: %s", Fmt.D(predScores));

    // Update parameters
    for (int i = 0; i < n; i++) {
      Derivation deriv = derivations.get(i);
      double incr = trueScores[i] - predScores[i];
      //LogInfo.logs("incr=%s, feature=%s",incr,deriv.getAllFeatureVector().get("alignmentScores :: binary.top"));
      deriv.incrementAllFeatureVector(incr, counts, updateFeatureMatcher);
    }
    //LogInfo.logs("Gradient=%s",counts);
  }

  private Evaluation processExamples(int iter, String group,
                                     List<Example> examples,
                                     boolean doUpdateWeights,
                                     boolean lastIter) {
    setBeamSizeOnExamples(examples, iter, lastIter);

    Evaluation evaluation = new Evaluation();

    if (examples.size() == 0)
      return evaluation;

    final String prefix = "iter=" + iter + "." + group;

    Execution.putOutput("group", group);
    LogInfo.begin_track_printAll(
        "Processing %s: %s examples", prefix, examples.size());
    LogInfo.begin_track("Examples");

    Map<String, Double> counts = new HashMap<String, Double>();
    int batchSize = 0;
    for (int e = 0; e < examples.size(); e++) {
      Example ex = examples.get(e);

      LogInfo.begin_track_printAll(
          "%s: example %s/%s: %s", prefix, e, examples.size(), ex.id);
      ex.log();
      Execution.putOutput("example", e);

      parseExample(ex, true);
      if (doUpdateWeights) {
        updateCounts(ex, counts);
        batchSize++;
        if (batchSize >= opts.batchSize) {
          // Gathered enough examples, update parameters
          updateWeights(counts);
          batchSize = 0;
        }
      }
      logExampleEvaluation(ex);
      accumulateAndLogEvaluation(ex, evaluation, prefix);
      printLearnerEventsIter(ex, iter, group);
      LogInfo.end_track();
      ex.predDerivations.clear();
      ex.predDerivationsAfterParse.clear();
    }
    if (doUpdateWeights && batchSize > 0)
      updateWeights(counts);

    LogInfo.end_track();
    logEvaluationStats(evaluation, prefix);
    printLearnerEventsSummary(evaluation, iter, group);
    LogInfo.end_track();
    return evaluation;
  }

  private void setBeamSizeOnExamples(List<Example> examples,
                                     int iter,
                                     boolean lastIter) {
    for (Example ex : examples) {
      // Set beam size
      if (ex.beamSize == -1) {
        ex.beamSize = parser.getDefaultBeamSize();
      } else if (ex.correctMaxBeamPosition != -1) {
        //ex.beamSize = (ex.correctMaxBeamPosition+1) * 2;
      } else if (!lastIter) {
        // Increase the beam to try to get something correct.
        if (opts.beamSizePerIteration != null) {
          int i = Math.min(iter, opts.beamSizePerIteration.size() - 1);
          ex.beamSize = opts.beamSizePerIteration.get(i);
        } else {
          ex.beamSize *= opts.beamSizeIncreaseFactor;
        }
      }
    }
  }

  private void parseExample(Example ex, boolean execAllowed) {
    StopWatchSet.begin("Parser.parse");
    parser.parse(params, ex, execAllowed);
    StopWatchSet.end();
  }

  private void updateCounts(Example ex, Map<String, Double> counts) {
    computeExpectedCounts(ex, ex.predDerivations, counts);
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

  private void logExampleEvaluation(Example ex) {
    LogInfo.logs("Current: %s", ex.computeTotalEvaluation().summary());
  }

  private void accumulateAndLogEvaluation(Example ex,
                                          Evaluation evaluation,
                                          String prefix) {
    evaluation.add(ex.computeTotalEvaluation());
    LogInfo.logs("Cumulative(%s): %s", prefix, evaluation.summary());
  }

  // Print summary over all examples
  private void logEvaluationStats(Evaluation evaluation, String prefix) {
    LogInfo.logs("Stats for %s: %s", prefix, evaluation.summary());
    evaluation.add(LexiconFn.lexEval);
    evaluation.logStats(prefix);
    evaluation.putOutput(prefix);
  }

  private void printLearnerEventsIter(Example ex, int iter, String group) {
    if (eventsOut == null)
      return;
    List<String> fields = new ArrayList<String>();
    fields.add("iter=" + iter);
    fields.add("group=" + group);
    fields.add("utterance=" + ex.utterance);
    fields.add("targetValue=" + ex.targetValue);
    if (ex.predDerivations.size() > 0) {
      Derivation deriv = ex.predDerivations.get(0);
      fields.add("predValue=" + deriv.value);
      fields.add("predFormula=" + deriv.formula);
    }
    fields.add(ex.computeTotalEvaluation().summary("\t"));
    eventsOut.println(Joiner.on('\t').join(fields));
    eventsOut.flush();

    // Print out features and the compatibility across all the derivations
    if (opts.dumpFeaturesAndCompatibility) {
      for (Derivation deriv : ex.predDerivations) {
        fields = new ArrayList<String>();
        fields.add("iter=" + iter);
        fields.add("group=" + group);
        fields.add("utterance=" + ex.utterance);
        Map<String, Double> features = new HashMap<String, Double>();
        deriv.incrementAllFeatureVector(1, features);
        for (String f : features.keySet()) {
          double v = features.get(f);
          fields.add(f + "=" + v);
        }
        fields.add("comp=" + deriv.compatibility);
        eventsOut.println(Joiner.on('\t').join(fields));
      }
    }
  }

  private void printLearnerEventsSummary(Evaluation evaluation,
                                         int iter,
                                         String group) {
    if (eventsOut == null)
      return;
    List<String> fields = new ArrayList<String>();
    fields.add("iter=" + iter);
    fields.add("group=" + group);
    fields.add(evaluation.summary("\t"));
    eventsOut.println(Joiner.on('\t').join(fields));
    eventsOut.flush();
  }
/*
  private void l2Reg(Map<String, Double> counts) {
    Set<String> features = new HashSet<String>(params.weights.keySet());
    features.addAll(counts.keySet());
    for (String feature : features) {
      MapUtils.incr(counts, feature, opts.l2RegCoefficient * -params.getWeight(feature));
    }
  }
  
  private void l1Reg(Map<String, Double> counts) {
    Set<String> features = new HashSet<String>(params.weights.keySet());
    features.addAll(counts.keySet());
    for (String feature : features) {
      MapUtils.incr(counts, feature, opts.l1RegCoefficient * - Math.signum(params.getWeight(feature)));
    }
  }*/
}
