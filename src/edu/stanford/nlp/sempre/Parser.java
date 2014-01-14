package edu.stanford.nlp.sempre;

import edu.stanford.nlp.sempre.fbalignment.utils.DoubleContainer;
import fig.basic.*;

import java.util.*;

////////////////////////////////////////////////////////////

/**
 * A Parser takes an example, parses the sequence of tokens, and stores the
 * derivations back in the example.
 *
 * @author Percy Liang
 */
public abstract class Parser {
  public static class Options {
    @Option(gloss = "For debugging, whether to print out all the predicted derivations")
    public boolean printAllPredictions;
    @Option(gloss = "Maximal number of predictions to print")
    public int maxPrintedPredictions = Integer.MAX_VALUE;
    @Option(gloss = "Use a coarse pass to prune the chart before full parsing")
    public boolean coarsePrune = true;
    @Option(gloss = "Monotonically increase the number of derivations on the beam across training iterations")
    public boolean monotonicBeam = false;
    @Option(gloss = "How much output to print") public int verbose = 0;
    @Option(gloss = "Execute only top formula (at test time)")
    public boolean executeTopFormulaOnly = false;
    @Option(gloss = "Whether to evaluate with values and formulas")
    public boolean evaluateValuesAndFormulas = true;
  }

  public static final Options opts = new Options();

  public boolean verbose(int level) { return opts.verbose >= level; }

  // Inputs to the parser
  public final Grammar grammar;  // Specifies the set of rules
  public final FeatureExtractor extractor;
  public final Executor executor;

  public Parser(Grammar grammar,
      FeatureExtractor extractor,
      Executor executor) {
    this.grammar = grammar;
    this.extractor = extractor;
    this.executor = executor;
  }

  public abstract int getDefaultBeamSize();
  public abstract ParserState newCoarseParserState(Params params,
      Example ex);
  public abstract ParserState newParserState(Params params,
      Example ex,
      ParserState coarseState);

  /** Helper function for transitive closure of unary rules. */
  protected void traverse(List<Rule> catUnaryRules,
      String node,
      Map<String, List<Rule>> graph,
      Map<String, Boolean> done) {
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

  public void parse(Params params, Example ex) {
    parse(params, ex, true);
  }

  /**
   * Parse the given example |ex| using the given parameters |params|
   * and populate the fields of |ex| (e.g., predDerivations).  NB:
   * |ex| is modified in place.
   * <p/>
   * If |execAllowed| is false, the ParserState and this function will
   * both be told not to execute.  A parser might rely on execution in
   * order to obtain denotation features that it would use to score
   * and sort predicted derivations.  This means that clients passing
   * |execAllowed = false| should have an internal understanding of
   * the parser they are using, since they might later want to:
   * <p/>
   * - Execute,
   * - Possibly recompute features and scores based on the result of
   *   execution,
   * - Possibly re-sort |ex.predDerivations|,
   * - |setEvaluation()|.
   * <p/>
   * As this will not be done for them by the parser.
   */
  public void parse(Params params, Example ex, boolean execAllowed) {
    LogInfo.begin_track("Parser.parse: parse");

    // Step 1: parse coarsely to see which categories should even be
    // constructed.
    ParserState coarseState = null;
    if (opts.coarsePrune) {
      coarseState = newCoarseParserState(params, ex);
      coarseState.setExecAllowed(execAllowed);
      coarseState.infer();
      coarseState.keepTopDownReachable();
    }

    // Step 2: parse, using the coarse information as a way to prune.
    ParserState state = newParserState(params, ex, coarseState);
    state.setExecAllowed(execAllowed);
    state.infer();
    LogInfo.end_track();

    ensureTargetExecuted(ex);

    if (ex.predDerivations == null)
      ex.predDerivations = new ArrayList<Derivation>();

    Map<Derivation, Derivation> existingDerivs =
        new HashMap<Derivation, Derivation>(ex.predDerivations.size());
    for (Derivation deriv : ex.predDerivations)
      existingDerivs.put(deriv, deriv);

    List<Derivation> incomingDerivs = state.getPredDerivations();

    int reusedExecs = 0;
    int notFoundExecs = 0;
    int totalDerivs = 0;
    for (Derivation deriv : incomingDerivs) {
      // Move over useful stuff from identical existing derivations.
      // Helps to avoid things like re-exec.
      Derivation prev = existingDerivs.get(deriv);
      if (prev == null)
        notFoundExecs++;
      if (prev != null && prev.isExecuted() && !deriv.isExecuted()) {
        deriv.setExecResults(prev);
        reusedExecs++;
      }
      totalDerivs++;
    }
    LogInfo.logs(
        "Parser.parse: reusing %d/%d execs, not found %d/%d",
        reusedExecs, totalDerivs, notFoundExecs, totalDerivs);

    ex.predDerivations = new ArrayList<Derivation>(incomingDerivs);
    if (opts.monotonicBeam) {
      for (Derivation deriv : incomingDerivs)
        existingDerivs.remove(deriv);
      ex.predDerivations.addAll(existingDerivs.keySet());
    }

    // Re-score and re-sort because we might have |setExecResults()|
    // or might have a monotonic beam.
    ex.rescoreAndSortPredDerivations(params);

    // Execute predicted derivations to get value.
    if (execAllowed) {
      LogInfo.begin_track("Parser.parse: execute");
      for (Derivation deriv : state.getPredDerivations()) {
        deriv.ensureExecuted(executor);
        if (opts.executeTopFormulaOnly)
          break;
      }
      LogInfo.end_track();
    }

    // For debugging.
    ex.predDerivationsAfterParse = new ArrayList<Derivation>(ex.predDerivations);

    state.setEvaluation();
    if (execAllowed)
      setEvaluation(ex, params);
  }

  // Populate the target.
  protected void ensureTargetExecuted(Example ex) {
    if (ex.targetFormula != null)
      ex.targetValue = executor.execute(ex.targetFormula).value;
  }

  public void setEvaluation(final Example ex, final Params params) {
    final Evaluation eval = new Evaluation();
    boolean printAllPredictions = opts.printAllPredictions;
    int numCandidates = ex.predDerivations.size();
    LogInfo.begin_track_printAll("Parser.setEvaluation: %d candidates", numCandidates);

    // Each derivation has a compatibility score (in [0,1]) as well as a model probability.
    // Terminology:
    //   True (correct): compatibility = 1
    //   Partial: 0 < compatibility < 1
    //   Wrong: compatibility = 0

    List<Derivation> predDerivations = ex.predDerivations;

    // Make sure at least the top derivation is executed.
    for (Derivation deriv : predDerivations) {
      deriv.ensureExecuted(executor);
      break;
    }

    // Did we get the answer correct?
    int correct_i = -1;  // Index of first correct derivation
    int correctIndexAfterParse = -1;
    double maxCompatibility = 0.0;
    double[] compatibilities = null;
    if (ex.targetValue != null) {
      compatibilities = new double[numCandidates];
      for (int i = 0; i < numCandidates; i++) {
        Derivation deriv = predDerivations.get(i);
        compatibilities[i] = deriv.compatibility = ex.targetValue.getCompatibility(deriv.value);

        // Must be fully compatible to count as correct.
        if (compatibilities[i] == 1 && correct_i == -1)
          correct_i = i;
        //record maximum compatibility for partial oracle
        maxCompatibility = Math.max(compatibilities[i], maxCompatibility);
      }
      // What if we only had parsed bottom up?
      for (int i = 0; i < numCandidates; i++) {
        Derivation deriv = ex.predDerivationsAfterParse.get(i);
        if (deriv.compatibility == 1) {
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

    //evaluate values and formulas
    if (opts.evaluateValuesAndFormulas) {
      List<Pair<Value, DoubleContainer>> valueList = computeValueList(ex.predDerivations);
      evaluateValues(eval, ex, valueList);
      List<Pair<Pair<Formula, Value>, DoubleContainer>> formulaList = computeFormulaList(ex.predDerivations);
      evaluateFormulas(eval, formulaList, ex);
    }

    // Number of derivations which have the same top score
    int numTop = 0;
    double topMass = 0;
    if (ex.targetValue != null) {
      while (numTop < numCandidates &&
          compatibilities[numTop] > 0.0d &&
          Math.abs(predDerivations.get(numTop).score - predDerivations.get(0).score) < 1e-10) {
        topMass += probs[numTop];
        numTop++;
      }
    }
    double correct = 0;
    double partial_correct = 0;
    if (ex.targetValue != null) {
      for (int i = 0; i < numTop; i++) {
        if (compatibilities[i] == 1) correct += probs[i] / topMass;
        if (compatibilities[i] > 0)
          partial_correct += (compatibilities[i] * probs[i]) / topMass;
      }
    }

    // Print features (note this is only with respect to the first correct, is NOT the gradient).
    // Things are not printed if there is only partial compatability.
    if (correct_i != -1 && correct != 1) {
      Derivation trueDeriv = predDerivations.get(correct_i);
      Derivation predDeriv = predDerivations.get(0);
      HashMap<String, Double> featureDiff = new HashMap<String, Double>();
      trueDeriv.incrementAllFeatureVector(+1, featureDiff);
      predDeriv.incrementAllFeatureVector(-1, featureDiff);
      String heading = String.format("TopTrue (%d) - Pred (%d) = Diff", correct_i, 0);
      FeatureVector.logFeatureWeights(heading, featureDiff, params);

      HashMap<String, Integer> choiceDiff = new LinkedHashMap<String, Integer>();
      trueDeriv.incrementAllChoices(+1, choiceDiff);
      predDeriv.incrementAllChoices(-1, choiceDiff);
      FeatureVector.logChoices(heading, choiceDiff);
    }

    // Fully correct
    for (int i = 0; i < predDerivations.size(); i++) {
      Derivation deriv = predDerivations.get(i);
      if (compatibilities != null && compatibilities[i] == 1) {
        LogInfo.logs(
            "True@%04d: %s [score=%s, prob=%s%s]", i, deriv.toString(),
            Fmt.D(deriv.score), Fmt.D(probs[i]), compatibilities != null ? ", comp=" + Fmt.D(compatibilities[i]) : "");
      }
    }
    // Partially correct
    for (int i = 0; i < predDerivations.size(); i++) {
      Derivation deriv = predDerivations.get(i);
      if (compatibilities != null && compatibilities[i] > 0 && compatibilities[i] < 1) {
        LogInfo.logs(
            "Part@%04d: %s [score=%s, prob=%s%s]", i, deriv.toString(),
            Fmt.D(deriv.score), Fmt.D(probs[i]), compatibilities != null ? ", comp=" + Fmt.D(compatibilities[i]) : "");
      }
    }
    // Anything that's predicted.
    for (int i = 0; i < predDerivations.size(); i++) {
      Derivation deriv = predDerivations.get(i);
      // Either print all predictions or this prediction is worse by some amount.
      boolean print;
      if (ex.derivConstraint != null)
        print = ex.derivConstraint.satisfies(ex, deriv);
      else
        print = printAllPredictions || ((probs[i] >= probs[0] / 2 || i < 10) && i < opts.maxPrintedPredictions);
      if (print) {
        LogInfo.logs(
            "Pred@%04d: %s [score=%s, prob=%s%s]", i, deriv.toString(),
            Fmt.D(deriv.score), Fmt.D(probs[i]), compatibilities != null ? ", comp=" + Fmt.D(compatibilities[i]) : "");
      }
    }

    eval.add("correct", correct);
    eval.add("oracle", correct_i != -1);
    eval.add("partCorrect", partial_correct);
    eval.add("partOracle", maxCompatibility);
    if (correctIndexAfterParse != -1)
      eval.add("correctIndexAfterParse", correctIndexAfterParse);

    double totBeamJump = 0.0d, totRootBeamJump = 0.0d;
    double count = 1.0d;
    if (!predDerivations.isEmpty()) {
      for (Derivation deriv : predDerivations) {
        DoubleRef beamJump = new DoubleRef(0.0d);
        DoubleRef rootBeamJump = new DoubleRef(0.0d);
        computeMeanBeamJump(deriv, true, beamJump, rootBeamJump);
        totBeamJump += beamJump.value;
        totRootBeamJump += rootBeamJump.value;
      }
      count = predDerivations.size();
    }
    eval.add("meanBeamJump", totBeamJump / count);
    eval.add("meanRootBeamJump", totRootBeamJump / count);

    if (correct_i != -1) {
      eval.add("correctMaxBeamPosition", predDerivations.get(correct_i).maxBeamPosition);
      eval.add("correctMaxUnsortedBeamPosition", predDerivations.get(correct_i).maxUnsortedBeamPosition);
      ex.correctMaxBeamPosition = predDerivations.get(correct_i).maxBeamPosition;
    } else {
      ex.correctMaxBeamPosition = -1;
    }
    eval.add("numCandidates", numCandidates);  // From this parse
    if (predDerivations.size() > 0)
      eval.add("parsedNumCandidates", predDerivations.size());

    for (int i = 0; i < predDerivations.size(); i++) {
      Derivation deriv = predDerivations.get(i);
      if (deriv.executorStats != null)
        eval.add(deriv.executorStats);
    }

    // Finally, set all of these stats as the example's evaluation.
    ex.setEvaluation(eval);

    LogInfo.end_track();
  }

  private void evaluateValues(Evaluation eval, Example ex, List<Pair<Value, DoubleContainer>> valueList) {

    double[] compatibilities = null;
    if (ex.targetValue != null) {
      compatibilities = new double[valueList.size()];
      for (int i = 0; i < valueList.size(); i++) {
        compatibilities[i] = ex.targetValue.getCompatibility(valueList.get(i).getFirst());
        if (opts.verbose >= 3)
          LogInfo.logs("evaluateValues: predValue=%s, targetValue=%s, compatibility=%s, prob=%s", valueList.get(i).getFirst(), ex.targetValue, compatibilities[i], valueList.get(i).getSecond());
      }
    }

    int numTop = 0;
    double topMass = 0;
    while (numTop < valueList.size() && Math.abs(valueList.get(numTop).getSecond().value() - valueList.get(0).getSecond().value()) < 1e-10) {
      topMass += valueList.get(numTop).getSecond().value();
      numTop++;
    }
    if (opts.verbose >= 3)
      LogInfo.logs("evaluateValues: numTop=%s", numTop);


    double correct = 0;
    if (ex.targetValue != null) {
      for (int i = 0; i < numTop; i++) {
        if (compatibilities[i] == 1)
          correct += valueList.get(i).getSecond().value() / topMass;
      }
    }
    if (opts.verbose >= 3)
      LogInfo.logs("evaluateValues, correct=%s", correct);
    eval.add("valueCorrect", correct);
  }

  private void evaluateFormulas(Evaluation eval, List<Pair<Pair<Formula, Value>, DoubleContainer>> formulaList, Example ex) {

    double[] compatibilities = null;
    if (ex.targetValue != null) {
      compatibilities = new double[formulaList.size()];
      for (int i = 0; i < formulaList.size(); i++) {
        compatibilities[i] = ex.targetValue.getCompatibility(formulaList.get(i).getFirst().getSecond());
        if (opts.verbose >= 3)
          LogInfo.logs("evaluateFormulas: predValue=%s, targetValue=%s, compatibility=%s, prob=%s", formulaList.get(i).getFirst().getSecond(), ex.targetValue, compatibilities[i], formulaList.get(i).getSecond());
      }
    }

    int numTop = 0;
    double topMass = 0;
    while (numTop < formulaList.size() && Math.abs(formulaList.get(numTop).getSecond().value() - formulaList.get(0).getSecond().value()) < 1e-10) {
      topMass += formulaList.get(numTop).getSecond().value();
      numTop++;
    }
    if (opts.verbose >= 3)
      LogInfo.logs("evaluateFormulas: numTop=%s", numTop);

    double correct = 0;
    if (ex.targetValue != null) {
      for (int i = 0; i < numTop; i++) {
        if (compatibilities[i] == 1)
          correct += formulaList.get(i).getSecond().value() / topMass;
      }
    }
    if (opts.verbose >= 3)
      LogInfo.logs("evaluateFormulas, correct=%s", correct);
    eval.add("formulaCorrect", correct);
  }

  private List<Pair<Pair<Formula, Value>, DoubleContainer>> computeFormulaList(List<Derivation> predDerivations) {

    Map<Formula, Pair<Pair<Formula, Value>, DoubleContainer>> aggregationMap = new HashMap<Formula, Pair<Pair<Formula, Value>, DoubleContainer>>();
    //construct formula map    
    for (Derivation deriv : predDerivations) {
      if (aggregationMap.containsKey(deriv.formula))
        aggregationMap.get(deriv.formula).getSecond().inc(deriv.prob);
      else
        aggregationMap.put(deriv.formula, Pair.newPair(Pair.newPair(deriv.formula, deriv.value), new DoubleContainer(deriv.prob)));
    }

    List<Pair<Pair<Formula, Value>, DoubleContainer>> formulaList = new ArrayList<Pair<Pair<Formula, Value>, DoubleContainer>>(aggregationMap.values());
    double sum = 0.0;
    for (Pair<Pair<Formula, Value>, DoubleContainer> pair : formulaList)
      sum += pair.getSecond().value();
    if (!formulaList.isEmpty() && Math.abs(1.0 - sum) > 0.0001)
      throw new RuntimeException("Sum of formulas is: " + sum);
    Collections.sort(formulaList, new Pair.ReverseSecondComparator<Pair<Formula, Value>, DoubleContainer>());
    return formulaList;
  }

  private List<Pair<Value, DoubleContainer>> computeValueList(List<Derivation> predDerivations) {

    Map<String, Pair<Value, DoubleContainer>> aggregationMap = new HashMap<String, Pair<Value, DoubleContainer>>();
    // TODO: just need a HashMap from Value to double
    //construct value map
    for (Derivation deriv : predDerivations) {
      String strValue = deriv.value != null ? deriv.value.toString() : "-UNKNOWN-";
      if (aggregationMap.containsKey(strValue))
        aggregationMap.get(strValue).getSecond().inc(deriv.prob);
      else
        aggregationMap.put(strValue, new Pair<Value, DoubleContainer>(deriv.value, new DoubleContainer(deriv.prob)));
    }
    List<Pair<Value, DoubleContainer>> valueList = new ArrayList<Pair<Value, DoubleContainer>>(aggregationMap.values());
    double sum = 0.0;
    for (Pair<Value, DoubleContainer> pair : valueList)
      sum += pair.getSecond().value();
    if (!valueList.isEmpty() && Math.abs(1.0 - sum) > 0.0001)
      throw new RuntimeException("Sum of values is: " + sum);

    Collections.sort(valueList, new Pair.ReverseSecondComparator<Value, DoubleContainer>());
    return valueList;
  }

  private int computeMeanBeamJump(Derivation deriv,
      boolean atRoot,
      DoubleRef res,
      DoubleRef rootRes) {
    double dist = (double) (deriv.preSortBeamPosition - deriv.postSortBeamPosition);
    res.value += dist;
    int n = 1;

    if (deriv.children != null)
      for (Derivation child : deriv.children)
        n += computeMeanBeamJump(child, false, res, rootRes);

    if (atRoot) {
      rootRes.value = dist;
      res.value /= n;
    }

    return n;
  }
}
