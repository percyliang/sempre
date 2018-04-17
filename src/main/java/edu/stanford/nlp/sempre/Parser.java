package edu.stanford.nlp.sempre;

import fig.basic.*;

import java.io.PrintWriter;
import java.util.*;

////////////////////////////////////////////////////////////

/**
 * A Parser takes an example, parses the sequence of tokens, and stores the
 * derivations back in the example.  It also computes a gradient with respect
 * to some objective function.  In this light, Parser it is more than a parser.
 *
 * @author Percy Liang
 */
public abstract class Parser {
  public static class Options {
    @Option(gloss = "For debugging, whether to print out all the predicted derivations")
    public boolean printAllPredictions;

    @Option(gloss = "Maximal number of predictions to print")
    public int maxPrintedPredictions = Integer.MAX_VALUE;
    @Option(gloss = "Maximal number of correct predictions to print")
    public int maxPrintedTrue = Integer.MAX_VALUE;

    @Option(gloss = "Use a coarse pass to prune the chart before full parsing")
    public boolean coarsePrune = false;

    @Option(gloss = "How much output to print")
    public int verbose = 0;

    @Option(gloss = "Execute only top formula to be cheap (hack at test time for fast demo)")
    public boolean executeTopFormulaOnly = false;

    @Option(gloss = "Whether to output chart filling visualization (huge file!)")
    public boolean visualizeChartFilling = false;

    @Option(gloss = "Keep this number of derivations per cell (exact use depends on the parser)")
    public int beamSize = 200;

    @Option(gloss = "Whether to update based on partial reward (for learning)")
    public boolean partialReward = true;

    @Option(gloss = "Whether to unroll derivation streams (applies to lazy parsers)")
    public boolean unrollStream = false;

    @Option(gloss = "Inject random noise into the score (to mix things up a bit)")
    public double derivationScoreNoise = 0;

    @Option(gloss = "Source of random noise")
    public Random derivationScoreRandom = new Random(1);

    @Option(gloss = "Prune away error denotations")
    public boolean pruneErrorValues = false;

    @Option(gloss = "Dump all features (for debugging)")
    public boolean dumpAllFeatures = false;

    @Option(gloss = "Call SetEvaluation during parsing")
    public boolean callSetEvaluation = true;
  }

  public static final Options opts = new Options();

  public boolean verbose(int level) { return opts.verbose >= level; }

  // Used to instantiate a parser.
  public static class Spec {
    public final Grammar grammar;
    public final FeatureExtractor extractor;
    public final Executor executor;
    public final ValueEvaluator valueEvaluator;

    public Spec(Grammar grammar, FeatureExtractor extractor, Executor executor, ValueEvaluator valueEvaluator) {
      this.grammar = grammar;
      this.extractor = extractor;
      this.executor = executor;
      this.valueEvaluator = valueEvaluator;
    }
  }

  // Inputs to the parser
  public final Grammar grammar;
  public final FeatureExtractor extractor;
  public final Executor executor;
  public final ValueEvaluator valueEvaluator;

  // Precomputations to make looking up grammar rules faster.
  protected List<Rule> catUnaryRules;  // Unary rules with category on RHS ($A => $B)
  public List<Rule> getCatUnaryRules() { return catUnaryRules; }

  // TODO(joberant): move this to a separate class in charge of visualizing charts
  public PrintWriter chartFillOut = null;  // For printing a machine-readable json file

  public Parser(Spec spec) {
    this.grammar = spec.grammar;
    this.extractor = spec.extractor;
    this.executor = spec.executor;
    this.valueEvaluator = spec.valueEvaluator;

    computeCatUnaryRules();
    LogInfo.logs("%s: %d catUnaryRules (sorted), %d nonCatUnaryRules (in trie)",
        this.getClass().getSimpleName(), catUnaryRules.size(), grammar.rules.size() - catUnaryRules.size());
  }

  // If grammar changes, then we might need to update aspects of the parser.
  public synchronized void addRule(Rule rule) {
    if (rule.isCatUnary())
      catUnaryRules.add(rule);
  }

  protected void computeCatUnaryRules() {
    // Handle catUnaryRules
    catUnaryRules = new ArrayList<>();
    Map<String, List<Rule>> graph = new HashMap<>();  // Node from LHS to list of rules
    for (Rule rule : grammar.rules)
      if (rule.isCatUnary())
        MapUtils.addToList(graph, rule.lhs, rule);

    // Topologically sort catUnaryRules so that B->C occurs before A->B
    Map<String, Boolean> done = new HashMap<>();
    for (String node : graph.keySet())
      traverse(catUnaryRules, node, graph, done);
  }

  // Helper function for transitive closure of unary rules.
  protected void traverse(List<Rule> catUnaryRules, String node,
      Map<String, List<Rule>> graph, Map<String, Boolean> done) {
    Boolean d = done.get(node);
    if (Boolean.TRUE.equals(d)) return;
    if (Boolean.FALSE.equals(d))
      throw new RuntimeException("Found cycle of unaries involving " + node);
    done.put(node, false);
    for (Rule rule : MapUtils.getList(graph, node)) {
      traverse(catUnaryRules, rule.rhs.get(0), graph, done);
      catUnaryRules.add(rule);
    }
    done.put(node, true);
  }

  /**
   * Override this method to change the parser's behavior based on current
   * group name and iteration number. This method will be called at the
   * beginning of each data group.
   */
  public void onBeginDataGroup(int iter, int numIters, String group) {
    // DEFAULT: Do nothing.
  }

  // Main thing for parsers to implement.
  public abstract ParserState newParserState(Params params, Example ex, boolean computeExpectedCounts);
  public Params getSearchParams(Params params) { return params; }

  /**
   * Parse the given example |ex| using the given parameters |params|
   * and populate the fields of |ex| (e.g., predDerivations).  Note:
   * |ex| is modified in place.
   */
  public ParserState parse(Params params, Example ex, boolean computeExpectedCounts) {
    // Execute target formula (if applicable).
    if (ex.targetFormula != null && ex.targetValue == null)
      ex.targetValue = executor.execute(ex.targetFormula, ex.context).value;

    // Parse
    StopWatch watch = new StopWatch();
    watch.start();
    LogInfo.begin_track_printAll("Parser.parse: parse");
    ParserState state = newParserState(params, ex, computeExpectedCounts);
    state.infer();
    LogInfo.end_track();
    watch.stop();
    state.parseTime = watch.getCurrTimeLong();
    state.setEvaluation();

    ex.predDerivations = state.predDerivations;
    Derivation.sortByScore(ex.predDerivations);

    // Evaluate
    if (opts.callSetEvaluation) {
      ex.evaluation = new Evaluation();
      addToEvaluation(state, ex.evaluation);
    }
    // Clean up temporary state used during parsing
    ex.clearTempState();
    for (Derivation deriv : ex.predDerivations)
      deriv.clearTempState();
    return state;
  }

  /**
   * Compute the evaluation based on the results of parsing and add it to |evaluation|
   */
  public void addToEvaluation(ParserState state, Evaluation evaluation) {
    Example ex = state.ex;
    List<Derivation> predDerivations = state.predDerivations;

    boolean printAllPredictions = opts.printAllPredictions;
    int numCandidates = predDerivations.size();
    LogInfo.begin_track_printAll("Parser.setEvaluation: %d candidates", numCandidates);

    // Each derivation has a compatibility score (in [0, 1]) as well as a model probability.
    // Terminology:
    //   True (correct): compatibility = 1
    //   Partial: 0 < compatibility < 1
    //   Wrong: compatibility = 0

    // Did we get the answer correct?
    int correctIndex = -1;  // Index of first correct derivation
    int correctIndexAfterParse = -1;
    double maxCompatibility = 0.0;
    double[] compatibilities = null;
    int numCorrect = 0, numPartialCorrect = 0, numIncorrect = 0;

    if (ex.targetValue != null) {
      compatibilities = new double[numCandidates];
      for (int i = 0; i < numCandidates; i++) {
        Derivation deriv = predDerivations.get(i);
        compatibilities[i] = deriv.compatibility;
        // Must be fully compatible to count as correct.
        if (compatibilities[i] == 1 && correctIndex == -1)
          correctIndex = i;
        // record maximum compatibility for partial oracle
        maxCompatibility = Math.max(compatibilities[i], maxCompatibility);
        // Count
        if (compatibilities[i] == 1) {
          numCorrect++;
        } else if (compatibilities[i] == 0) {
          numIncorrect++;
        } else {
          numPartialCorrect++;
        }
      }
      // What if we only had parsed bottom up?
      for (int i = 0; i < numCandidates; i++) {
        if (compatibilities[i] == 1) {
          correctIndexAfterParse = i;
          break;
        }
      }
    }

    // Compute probabilities
    double[] probs = Derivation.getProbs(predDerivations, 1);
    for (int i = 0; i < numCandidates; i++) {
      Derivation deriv = predDerivations.get(i);
      deriv.prob = probs[i];
    }

    // Number of derivations which have the same top score
    int numTop = 0;
    double topMass = 0;
    if (ex.targetValue != null) {
      while (numTop < numCandidates &&
          Math.abs(predDerivations.get(numTop).score - predDerivations.get(0).score) < 1e-10) {
        topMass += probs[numTop];
        numTop++;
      }
    }
    double correct = 0, partCorrect = 0;
    if (ex.targetValue != null) {
      for (int i = 0; i < numTop; i++) {
        if (compatibilities[i] == 1) correct += probs[i] / topMass;
        if (compatibilities[i] > 0)
          partCorrect += (compatibilities[i] * probs[i]) / topMass;
      }
    }

    // Print features (note this is only with respect to the first correct, is NOT the gradient).
    // Things are not printed if there is only partial compatability.
    if (correctIndex != -1 && correct != 1) {
      Derivation trueDeriv = predDerivations.get(correctIndex);
      Derivation predDeriv = predDerivations.get(0);
      HashMap<String, Double> featureDiff = new HashMap<>();
      trueDeriv.incrementAllFeatureVector(+1, featureDiff);
      predDeriv.incrementAllFeatureVector(-1, featureDiff);
      String heading = String.format("TopTrue (%d) - Pred (%d) = Diff", correctIndex, 0);
      FeatureVector.logFeatureWeights(heading, featureDiff, state.params);

      HashMap<String, Integer> choiceDiff = new LinkedHashMap<>();
      trueDeriv.incrementAllChoices(+1, choiceDiff);
      predDeriv.incrementAllChoices(-1, choiceDiff);
      FeatureVector.logChoices(heading, choiceDiff);
    }

    // Fully correct
    int numPrintedSoFar = 0;
    for (int i = 0; i < predDerivations.size(); i++) {
      Derivation deriv = predDerivations.get(i);
      if (compatibilities != null && compatibilities[i] == 1) {
        boolean print = printAllPredictions || (numPrintedSoFar < opts.maxPrintedTrue);
        if (print) {
          LogInfo.logs(
              "True@%04d: %s [score=%s, prob=%s%s]", i, deriv.toString(),
              Fmt.D(deriv.score), Fmt.D(probs[i]), compatibilities != null ? ", comp=" + Fmt.D(compatibilities[i]) : "");
          numPrintedSoFar++;
          if (opts.dumpAllFeatures) FeatureVector.logFeatureWeights("Features", deriv.getAllFeatureVector(), state.params);
        }
      }
    }
    // Partially correct
    numPrintedSoFar = 0;
    for (int i = 0; i < predDerivations.size(); i++) {
      Derivation deriv = predDerivations.get(i);
      if (compatibilities != null && compatibilities[i] > 0 && compatibilities[i] < 1) {
        boolean print = printAllPredictions || (numPrintedSoFar < opts.maxPrintedTrue);
        if (print) {
          LogInfo.logs(
              "Part@%04d: %s [score=%s, prob=%s%s]", i, deriv.toString(),
              Fmt.D(deriv.score), Fmt.D(probs[i]), compatibilities != null ? ", comp=" + Fmt.D(compatibilities[i]) : "");
          numPrintedSoFar++;
          if (opts.dumpAllFeatures) FeatureVector.logFeatureWeights("Features", deriv.getAllFeatureVector(), state.params);
        }
      }
    }
    // Anything that's predicted.
    for (int i = 0; i < predDerivations.size(); i++) {
      Derivation deriv = predDerivations.get(i);

      // Either print all predictions or this prediction is worse by some amount.
      boolean print = printAllPredictions || ((probs[i] >= probs[0] / 2 || i < 10) && i < opts.maxPrintedPredictions);
      if (print) {
        LogInfo.logs(
            "Pred@%04d: %s [score=%s, prob=%s%s]", i, deriv.toString(),
            Fmt.D(deriv.score), Fmt.D(probs[i]), compatibilities != null ? ", comp=" + Fmt.D(compatibilities[i]) : "");
        // LogInfo.logs("Derivation tree: %s", deriv.toRecursiveString());
        if (opts.dumpAllFeatures) FeatureVector.logFeatureWeights("Features", deriv.getAllFeatureVector(), state.params);
      }
    }

    evaluation.add("correct", correct);
    evaluation.add("oracle", correctIndex != -1);
    evaluation.add("partCorrect", partCorrect);
    evaluation.add("partOracle", maxCompatibility);
    if (correctIndexAfterParse != -1)
      evaluation.add("correctIndexAfterParse", correctIndexAfterParse);

    if (correctIndex != -1) {
      evaluation.add("correctMaxBeamPosition", predDerivations.get(correctIndex).maxBeamPosition);
      evaluation.add("correctMaxUnsortedBeamPosition", predDerivations.get(correctIndex).maxUnsortedBeamPosition);
    }
    evaluation.add("parsed", numCandidates > 0);
    evaluation.add("numCandidates", numCandidates);  // From this parse
    if (numCandidates > 0)
      evaluation.add("parsedNumCandidates", numCandidates);
    evaluation.add("numCorrect", numCorrect);
    evaluation.add("numPartialCorrect", numPartialCorrect);
    evaluation.add("numIncorrect", numIncorrect);

    // Add parsing stats
    evaluation.add(state.evaluation);

    // Add executor stats
    for (Derivation deriv : predDerivations) {
      if (deriv.executorStats != null)
        evaluation.add(deriv.executorStats);
    }

    LogInfo.end_track();
  }
}
