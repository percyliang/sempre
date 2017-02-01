package edu.stanford.nlp.sempre;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import fig.basic.*;
import fig.exec.Execution;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The main learning loop for a neural learner.  Goes over a dataset multiple times, calling the
 * parser and updating parameters.
 *
 * @author joberant
 */
public class NeuralLearner {
  public static class Options {
    @Option(gloss = "Number of iterations to train")
    public int maxTrainIters = 3;

    @Option(gloss = "When using mini-batch updates for SGD, this is the batch size")
    public int batchSize = 1;  // Default is SGD

    @Option(gloss = "Write predDerivations to examples file (huge)")
    public boolean outputPredDerivations = false;

    @Option(gloss = "Dump all features and compatibility scores")
    public boolean dumpFeaturesAndCompatibility = false;

    @Option(gloss = "Verbosity") public int verbose = 0;

    @Option(gloss = "whether to update weights")
    public boolean updateWeights = true;
  }
  public static Options opts = new Options();

  private NeuralParser parser;
  private final Dataset dataset;
  private final PrintWriter eventsOut;  // For printing a machine-readable log
  private final List<SemanticFn> semFuncsToUpdate;
  private final ComputationGraphWrapper cgWrapper;

  public NeuralLearner(NeuralParser parser, Dataset dataset) {
    this.parser = parser;
    this.dataset = dataset;
    this.cgWrapper = new ComputationGraphWrapper();
    cgWrapper.InitDynet(ComputationGraphWrapper.opts.numDenseFeatures);
    this.eventsOut = IOUtils.openOutAppendEasy(Execution.getFile("learner.events"));

    // Collect all semantic functions to update.
    semFuncsToUpdate = new ArrayList<>();
    for (Rule rule : parser.grammar.getRules()) {
      SemanticFn currSemFn = rule.getSem();
      boolean toAdd = true;
      for (SemanticFn semFuncToUpdate : semFuncsToUpdate) {
        if (semFuncToUpdate.getClass().equals(currSemFn.getClass())) {
          toAdd = false;
          break;
        }
      }
      if (toAdd)
        semFuncsToUpdate.add(currSemFn);
    }
  }

  public void learn() {
    learn(opts.maxTrainIters, Maps.newHashMap());
  }

  /**
   * @param evaluations Evaluations per iteration per group.
   */
  public void learn(int numIters, Map<String, List<Evaluation>> evaluations) {
    LogInfo.begin_track("Learner.learn()");
    // For each iteration, go through the groups and parse (updating if train).
    for (int iter = 0; iter <= numIters; iter++) {

      LogInfo.begin_track("Iteration %s/%s", iter, numIters);
      Execution.putOutput("iter", iter);

      // Averaged over all iterations
      // Group -> evaluation for that group.
      Map<String, Evaluation> meanEvaluations = Maps.newHashMap();

      // Clear
      for (String group : dataset.groups())
        meanEvaluations.put(group, new Evaluation());

      // Test and train
      for (String group : dataset.groups()) {
        boolean lastIter = (iter == numIters);
        boolean updateWeights = opts.updateWeights && group.equals("train") && !lastIter;  // Don't train on last iteration
        Evaluation eval = processExamples(
          iter,
          group,
          dataset.examples(group),
          updateWeights);
        MapUtils.addToList(evaluations, group, eval);
        meanEvaluations.get(group).add(eval);
        StopWatchSet.logStats();
      }

      // Write out parameters
      String path = Execution.getFile("params." + iter);
      if (path != null) {
        // todo(nnsm): serialize the model somewhere.
        Utils.systemHard("ln -sf params." + iter + " " + Execution.getFile("params"));
      }

      LogInfo.end_track();
    }
    LogInfo.end_track();
  }

  private Evaluation processExamples(int iter, String group,
                                     List<Example> examples,
                                     boolean computeExpectedCounts) {
    Evaluation evaluation = new Evaluation();

    if (examples.size() == 0)
      return evaluation;

    final String prefix = "iter=" + iter + "." + group;

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

      ParserState state = parseExample(cgWrapper, ex, computeExpectedCounts);
      LogInfo.logs("Hash collisions=%d, non-collisions=%d, ratio=%f",
        cgWrapper.getHashCollisions(), cgWrapper.getHashNonCollisions(), cgWrapper.getCollisionRatio());

      if (computeExpectedCounts) {
        //todo(joberant): Define loss over predictions in state and update parameters based on loss.
        cgWrapper.addRewardWeightedCondLikelihood(state.predDerivations);
      }

      LogInfo.logs("Current: %s", ex.evaluation.summary());
      evaluation.add(ex.evaluation);
      LogInfo.logs("Cumulative(%s): %s", prefix, evaluation.summary());

      printLearnerEventsIter(ex, iter, group);
      LogInfo.end_track();

      // Write out examples and predictions
      if (opts.outputPredDerivations && Builder.opts.parser.equals("FloatingParser")) {
        ExampleUtils.writeParaphraseSDF(iter, group, ex, opts.outputPredDerivations);
      }

      // To save memory
      ex.predDerivations.clear();
    }

    LogInfo.end_track();
    logEvaluationStats(evaluation, prefix);
    printLearnerEventsSummary(evaluation, iter, group);
    ExampleUtils.writeEvaluationSDF(iter, group, evaluation, examples.size());
    LogInfo.end_track();
    return evaluation;
  }

  private ParserState parseExample(ComputationGraphWrapper cgWrapper,
                                   Example ex, boolean computeExpectedCounts) {
    StopWatchSet.begin("Parser.parse");
    NeuralParserState res = this.parser.parse(cgWrapper, ex, computeExpectedCounts);
    StopWatchSet.end();
    return res;
  }

  // Print summary over all examples
  private void logEvaluationStats(Evaluation evaluation, String prefix) {
    LogInfo.logs("Stats for %s: %s", prefix, evaluation.summary());
    // evaluation.add(LexiconFn.lexEval);
    evaluation.logStats(prefix);
    evaluation.putOutput(prefix);
  }

  private void printLearnerEventsIter(Example ex, int iter, String group) {
    if (eventsOut == null)
      return;
    List<String> fields = new ArrayList<>();
    fields.add("iter=" + iter);
    fields.add("group=" + group);
    fields.add("utterance=" + ex.utterance);
    fields.add("targetValue=" + ex.targetValue);
    if (ex.predDerivations.size() > 0) {
      Derivation deriv = ex.predDerivations.get(0);
      fields.add("predValue=" + deriv.value);
      fields.add("predFormula=" + deriv.formula);
    }
    fields.add(ex.evaluation.summary("\t"));
    eventsOut.println(Joiner.on('\t').join(fields));
    eventsOut.flush();

    // Print out features and the compatibility across all the derivations
    if (opts.dumpFeaturesAndCompatibility) {
      for (Derivation deriv : ex.predDerivations) {
        fields = new ArrayList<>();
        fields.add("iter=" + iter);
        fields.add("group=" + group);
        fields.add("utterance=" + ex.utterance);
        Map<String, Double> features = new HashMap<>();
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
    List<String> fields = new ArrayList<>();
    fields.add("iter=" + iter);
    fields.add("group=" + group);
    fields.add(evaluation.summary("\t"));
    eventsOut.println(Joiner.on('\t').join(fields));
    eventsOut.flush();
  }
}
