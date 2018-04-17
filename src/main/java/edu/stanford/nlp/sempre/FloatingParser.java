package edu.stanford.nlp.sempre;

import fig.basic.*;
import fig.exec.Execution;

import java.io.PrintWriter;
import java.util.*;

import static fig.basic.LogInfo.logs;

/**
 * A FloatingParser builds Derivations according to a Grammar without having to
 * generate the input utterance.  In contrast, a conventional chart parser (e.g.,
 * BeamParser) constructs parses for each span of the utterance.  This is very
 * inefficient when we're performing a more extractive semantic parsing task
 * where many of the words are unaccounted for.
 *
 * Assume the Grammar is binarized and only has rules of the following form:
 *
 *   $Cat => token
 *   $Cat => $Cat
 *   $Cat => token token
 *   $Cat => token $Cat
 *   $Cat => $Cat token
 *   $Cat => $Cat $Cat
 *
 * Each rule can be either anchored or floating (or technically, both).
 * For floating rules, tokens on the RHS are ignored.
 *
 * Chart cells are either:
 * - anchored: (cat, start, end)    [these are effectively at depth 0]
 * - floating: (cat, depth or size) [depends on anchored cells as base cases]
 *
 * With rules:
 *   cat => cat1 cat2 [binary]
 *   cat => cat1 [unary]
 *
 * Anchored Combinations:
 *   (cat1, start, end) => (cat, start, end)
 *   (cat1, start, mid), (cat2, mid, end) => (cat, start, end)
 *   (cat, start, end) => (cat, 0)   [anchored => floating]
 *
 * Floating Combinations:
 *   [nothing] => (cat, 1)           [from $Cat => token]
 *   (cat1, depth) => (cat, depth + 1)
 *   (cat1, depth1), (cat2, depth2) => (cat, max(depth1, depth2) + 1)
 *
 * If --useSizeInsteadOfDepth is turned on, the floating combinations become:
 *   [nothing] => (cat, 1)           [from $Cat => token]
 *   (cat1, size) => (cat, size + 1)
 *   (cat1, size1), (cat2, size2) => (cat, size1 + size2 + 1)
 *
 * @author Percy Liang
 */
public class FloatingParser extends Parser {
  public static class Options {
    // Floating rules
    @Option(gloss = "Whether rules without the (anchored 1) or (floating 1) tag should be anchored or floating")
    public boolean defaultIsFloating = true;
    @Option(gloss = "Limit on formula depth (or formula size when --useSizeInsteadOfDepth is true)")
    public int maxDepth = 10;
    @Option(gloss = "Put a limit on formula size instead of formula depth")
    public boolean useSizeInsteadOfDepth = false;
    @Option(gloss = "Whether floating rules are allowed to be applied consecutively")
    public boolean consecutiveRules = true;
    @Option(gloss = "Whether floating rule (rule $A (a)) should have depth 0 or 1")
    public boolean initialFloatingHasZeroDepth = false;
    @Option(gloss = "Filter child derivations using the type information from SemanticFn")
    public boolean filterChildDerivations = true;
    // Anchored rules
    @Option(gloss = "Whether anchored spans/tokens can only be used once in a derivation")
    public boolean useAnchorsOnce = false;
    @Option(gloss = "Each span can be anchored this number of times (unused if useAnchorsOnce is active)")
    public int useMaxAnchors = -1;
    // Other options
    @Option(gloss = "Whether to always execute the derivation")
    public boolean executeAllDerivations = false;
    @Option(gloss = "Whether to output a file with all utterances predicted")
    public boolean printPredictedUtterances = false;
    @Option(gloss = "Custom beam size at training time (default = Parser.beamSize)")
    public int trainBeamSize = -1;
    @Option(gloss = "Whether to beta reduce the formula")
    public boolean betaReduce = false;
    @Option(gloss = "DEBUG: Print amount of time spent on each rule")
    public boolean summarizeRuleTime = false;
    @Option(gloss = "Stop the parser if it has used more than this amount of time (in seconds)")
    public int maxFloatingParsingTime = Integer.MAX_VALUE;
  }

  public static Options opts = new Options();

  public boolean earlyStopOnConsistent = false;
  public int earlyStopOnNumDerivs = -1;

  public FloatingParser(Spec spec) {
    super(spec);
  }

  /**
   * Set early stopping criteria
   *
   * @param onConsistent
   *    Stop when a consistent derivation is found. (Only triggered when computeExpectedCounts = true)
   * @param onNumDerivs
   *    Stop when the number of featurized derivations exceed this number (set to -1 to disable)
   * @return
   *    this
   */
  public FloatingParser setEarlyStopping(boolean onConsistent, int onNumDerivs) {
    this.earlyStopOnConsistent = onConsistent;
    this.earlyStopOnNumDerivs = onNumDerivs;
    return this;
  }

  /**
   * computeCatUnaryRules, but do not topologically sort floating rules
   */
  @Override
  protected void computeCatUnaryRules() {
    // Handle anchored catUnaryRules
    catUnaryRules = new ArrayList<>();
    Map<String, List<Rule>> graph = new HashMap<>();  // Node from LHS to list of rules
    for (Rule rule : grammar.rules)
      if (rule.isCatUnary() && rule.isAnchored())
        MapUtils.addToList(graph, rule.lhs, rule);

    // Topologically sort catUnaryRules so that B->C occurs before A->B
    Map<String, Boolean> done = new HashMap<>();
    for (String node : graph.keySet())
      traverse(catUnaryRules, node, graph, done);

    // Add floating catUnaryRules
    for (Rule rule : grammar.rules)
      if (rule.isCatUnary() && rule.isFloating())
        catUnaryRules.add(rule);
  }

  // Helper function for transitive closure of floating rules.
  protected void traverseFloatingRules(List<Rule> orderedFloatingRules,
      String node, Map<String, List<Rule>> graph, Map<String, Boolean> done) {
    Boolean d = done.get(node);
    if (Boolean.TRUE.equals(d)) return;
    if (Boolean.FALSE.equals(d))
      throw new RuntimeException("Found cycle of floating rules involving " + node);
    done.put(node, false);
    for (Rule rule : MapUtils.getList(graph, node)) {
      for (String rhsCat : rule.rhs) {
        if (Grammar.isIntermediate(rhsCat))
          traverseFloatingRules(orderedFloatingRules, rhsCat, graph, done);
      }
      orderedFloatingRules.add(rule);
    }
    done.put(node, true);
  }

  public ParserState newParserState(Params params, Example ex, boolean computeExpectedCounts) {
    return new FloatingParserState(this, params, ex, computeExpectedCounts);
  }
}

/**
 * Stores FloatingParser information about parsing a particular example. The actual
 * parsing code lives here.
 *
 * Currently, many of the fields in ParserState are not used (chart).
 * Those should be refactored out.
 *
 * @author Percy Liang
 */
class FloatingParserState extends ParserState {

  // cell => list of derivations
  // Anchored cells: cat[start,end]
  // Floating cells: cat:depth
  private final Map<Object, List<Derivation>> chart = new HashMap<>();

  private final DerivationPruner pruner;
  private final CatSizeBound catSizeBound;
  private Map<Rule, Long> ruleTime;
  private boolean timeout = false;

  public FloatingParserState(FloatingParser parser, Params params, Example ex, boolean computeExpectedCounts) {
    super(parser, params, ex, computeExpectedCounts);
    pruner = new DerivationPruner(this);
    catSizeBound = new CatSizeBound(FloatingParser.opts.maxDepth, parser.grammar);
  }

  @Override
  protected int getBeamSize() {
    if (computeExpectedCounts && FloatingParser.opts.trainBeamSize > 0)
      return FloatingParser.opts.trainBeamSize;
    return Parser.opts.beamSize;
  }

  // Construct state names.
  private Object floatingCell(String cat, int depth) {
    return (cat + ":" + depth).intern();
  }
  private Object anchoredCell(String cat, int start, int end) {
    return (cat + "[" + start + "," + end + "]").intern();
  }
  private Object cell(String cat, int start, int end, int depth) {
    return (start != -1) ? anchoredCell(cat, start, end) : floatingCell(cat, depth);
  }

  private void addToChart(Object cell, Derivation deriv) {
    if (!deriv.isFeaturizedAndScored())  // A derivation could be belong in multiple cells.
      featurizeAndScoreDerivation(deriv);
    if (Parser.opts.pruneErrorValues && deriv.value instanceof ErrorValue) return;
    if (Parser.opts.verbose >= 4)
      LogInfo.logs("addToChart %s: %s", cell, deriv);
    MapUtils.addToList(chart, cell, deriv);
  }

  private boolean isRootRule(Rule rule) {
    return Rule.rootCat.equals(rule.lhs);
  }

  private boolean applyRule(Rule rule, int start, int end, int depth,
      Derivation child1, Derivation child2, String canonicalUtterance) {
    if (timeout && !isRootRule(rule)) return false;
    applyRuleActual(rule, start, end, depth, child1, child2, canonicalUtterance);
    return true;
  }

  private void applyRuleActual(Rule rule, int start, int end, int depth, Derivation child1, Derivation child2, String canonicalUtterance) {
    if (Parser.opts.verbose >= 5) logs("applyRule %s [%s:%s] depth=%s, %s %s", rule, start, end, depth, child1, child2);
    List<Derivation> children;
    if (child1 == null)  // 0-ary
      children = Collections.emptyList();
    else if (child2 == null)  // 1-ary
      children = Collections.singletonList(child1);
    else {
      // Optional: ensure that each anchor is only used once per derivation.
      if (FloatingParser.opts.useAnchorsOnce) {
        if (FloatingRuleUtils.derivationAnchorsOverlap(child1, child2))
          return;
      } else if (FloatingParser.opts.useMaxAnchors >= 0) {
        if (FloatingRuleUtils.maxNumAnchorOverlaps(child1, child2)
            > FloatingParser.opts.useMaxAnchors)
          return;
      }
      children = ListUtils.newList(child1, child2);
    }

    // optionally: ensure that rule being applied is not the same as one of the children's
    if (!FloatingParser.opts.consecutiveRules) {
      for (Derivation child : children) {
        if (child.rule.equals(rule)) return;
      }
    }

    DerivationStream results = rule.sem.call(ex,
        new SemanticFn.CallInfo(rule.lhs, start, end, rule, children));
    while (results.hasNext()) {
      Derivation newDeriv = results.next();
      if (FloatingParser.opts.betaReduce) newDeriv = newDeriv.betaReduction();
      newDeriv.canonicalUtterance = canonicalUtterance;

      // make sure we execute
      if (FloatingParser.opts.executeAllDerivations && !(newDeriv.type instanceof FuncSemType))
        newDeriv.ensureExecuted(parser.executor, ex.context);

      if (pruner.isPruned(newDeriv)) continue;
      // Avoid repetitive floating cells
      addToChart(cell(rule.lhs, start, end, depth), newDeriv);
      if (depth == -1)  // In addition, anchored cells become floating at level 0
        addToChart(floatingCell(rule.lhs, 0), newDeriv);
    }
  }

  private boolean applyAnchoredRule(Rule rule, int start, int end, Derivation child1, Derivation child2, String canonicalUtterance) {
    return applyRule(rule, start, end, -1, child1, child2, canonicalUtterance);
  }

  private boolean applyFloatingRule(Rule rule, int depth, Derivation child1, Derivation child2, String canonicalUtterance) {
    return applyRule(rule, -1, -1, depth, child1, child2, canonicalUtterance);
  }

  /**
   * Return a collection of Derivation.
   */
  private List<Derivation> getDerivations(Object cell) {
    List<Derivation> derivations = chart.get(cell);
    // logs("getDerivations %s => %s", cell, derivations);
    if (derivations == null) return Derivation.emptyList;
    return derivations;
  }

  /**
   * Return a collection of DerivationGroup.
   *
   * The rule should be applied on all derivations (or all pairs of derivations) in each DerivationGroup.
   */
  private Collection<ChildDerivationsGroup> getFilteredDerivations(Rule rule, Object cell1, Object cell2) {
    List<Derivation> derivations1 = getDerivations(cell1),
        derivations2 = (cell2 == null) ? null : getDerivations(cell2);
    if (!FloatingParser.opts.filterChildDerivations)
      return Collections.singleton(new ChildDerivationsGroup(derivations1, derivations2));
    // Try to filter down the number of partial logical forms
    if (rule.getSem().supportFilteringOnTypeData())
      return rule.getSem().getFilteredDerivations(derivations1, derivations2);
    return Collections.singleton(new ChildDerivationsGroup(derivations1, derivations2));
  }

  private Collection<ChildDerivationsGroup> getFilteredDerivations(Rule rule, Object cell) {
    return getFilteredDerivations(rule, cell, null);
  }

  // Build derivations over span |start|, |end|.
  private void buildAnchored(int start, int end) {
    // Apply unary tokens on spans (rule $A (a))
    for (Rule rule : parser.grammar.rules) {
      if (!rule.isAnchored()) continue;
      if (rule.rhs.size() != 1 || rule.isCatUnary()) continue;
      boolean match = (end - start == 1) && ex.token(start).equals(rule.rhs.get(0));
      if (!match) continue;
      StopWatch stopWatch = new StopWatch().start();
      applyAnchoredRule(rule, start, end, null, null, rule.rhs.get(0));
      ruleTime.put(rule, ruleTime.getOrDefault(rule, 0L) + stopWatch.stop().ms);
    }

    // Apply binaries on spans (rule $A ($B $C)), ...
    for (int mid = start + 1; mid < end; mid++) {
      for (Rule rule : parser.grammar.rules) {
        if (!rule.isAnchored()) continue;
        if (rule.rhs.size() != 2) continue;

        StopWatch stopWatch = new StopWatch().start();
        String rhs1 = rule.rhs.get(0);
        String rhs2 = rule.rhs.get(1);
        boolean match1 = (mid - start == 1) && ex.token(start).equals(rhs1);
        boolean match2 = (end - mid == 1) && ex.token(mid).equals(rhs2);

        if (!Rule.isCat(rhs1) && Rule.isCat(rhs2)) {  // token $Cat
          if (match1) {
            List<Derivation> derivations = getDerivations(anchoredCell(rhs2, mid, end));
            for (Derivation deriv : derivations)
              applyAnchoredRule(rule, start, end, deriv, null, rhs1 + " " + deriv.canonicalUtterance);
          }
        } else if (Rule.isCat(rhs1) && !Rule.isCat(rhs2)) {  // $Cat token
          if (match2) {
            List<Derivation> derivations = getDerivations(anchoredCell(rhs1, start, mid));
            for (Derivation deriv : derivations)
              applyAnchoredRule(rule, start, end, deriv, null, deriv.canonicalUtterance + " " + rhs2);
          }
        } else if (!Rule.isCat(rhs1) && !Rule.isCat(rhs2)) {  // token token
          if (match1 && match2)
            applyAnchoredRule(rule, start, end, null, null, rhs1 + " " + rhs2);
        } else {  // $Cat $Cat
          List<Derivation> derivations1 = getDerivations(anchoredCell(rhs1, start, mid));
          List<Derivation> derivations2 = getDerivations(anchoredCell(rhs2, mid, end));
          for (Derivation deriv1 : derivations1)
            for (Derivation deriv2 : derivations2)
              applyAnchoredRule(rule, start, end, deriv1, deriv2, deriv1.canonicalUtterance + " " + deriv2.canonicalUtterance);
        }
        ruleTime.put(rule, ruleTime.getOrDefault(rule, 0L) + stopWatch.stop().ms);
      }
    }

    // Apply unary categories on spans (rule $A ($B))
    // Important: do this in topologically sorted order and after all the binaries are done.
    for (Rule rule : parser.catUnaryRules) {
      if (!rule.isAnchored()) continue;
      StopWatch stopWatch = new StopWatch().start();
      List<Derivation> derivations = getDerivations(anchoredCell(rule.rhs.get(0), start, end));
      for (Derivation deriv : derivations)
        applyAnchoredRule(rule, start, end, deriv, null, deriv.canonicalUtterance);
      ruleTime.put(rule, ruleTime.getOrDefault(rule, 0L) + stopWatch.stop().ms);
    }
  }

  // Build floating derivations of exactly depth |depth|.
  private void buildFloating(int depth) {
    // Build a floating predicate from thin air
    // (rule $A (a)); note that "a" is ignored
    if (depth == (FloatingParser.opts.initialFloatingHasZeroDepth ? 0 : 1)) {
      for (Rule rule : parser.grammar.rules) {
        if (timeout && !isRootRule(rule)) continue;
        if (!rule.isFloating()) continue;
        if (rule.rhs.size() != 1 || rule.isCatUnary()) continue;
        StopWatch stopWatch = new StopWatch().start();
        applyFloatingRule(rule, depth, null, null, rule.rhs.get(0));
        ruleTime.put(rule, ruleTime.getOrDefault(rule, 0L) + stopWatch.stop().ms);
      }
    }

    // Apply binaries on spans (rule $A ($B $C)), ...
    for (Rule rule : parser.grammar.rules) {
      if (timeout && !isRootRule(rule)) continue;
      if (!rule.isFloating()) continue;
      if (rule.rhs.size() != 2) continue;
      if (catSizeBound.getBound(rule.lhs) < depth) continue;

      StopWatch stopWatch = new StopWatch().start();
      String rhs1 = rule.rhs.get(0);
      String rhs2 = rule.rhs.get(1);

      if (!Rule.isCat(rhs1) && !Rule.isCat(rhs2)) {  // token token
        if (depth == (FloatingParser.opts.initialFloatingHasZeroDepth ? 0 : 1)) {
          applyFloatingRule(rule, depth, null, null, rhs1 + " " + rhs2);
        }

      } else if (!Rule.isCat(rhs1) && Rule.isCat(rhs2)) {  // token $Cat
        List<Derivation> derivations = getDerivations(floatingCell(rhs2, depth - 1));
        for (Derivation deriv : derivations)
          applyFloatingRule(rule, depth, deriv, null, rhs1 + " " + deriv.canonicalUtterance);

      } else if (Rule.isCat(rhs1) && !Rule.isCat(rhs2)) {  // $Cat token
        List<Derivation> derivations = getDerivations(floatingCell(rhs1, depth - 1));
        for (Derivation deriv : derivations)
          applyFloatingRule(rule, depth, deriv, null, deriv.canonicalUtterance + " " + rhs2);

      } else {  // $Cat $Cat
        if (FloatingParser.opts.useSizeInsteadOfDepth) {
          derivLoop:
            for (int depth1 = 0; depth1 < depth; depth1++) {  // sizes must add up to depth-1 (actually size-1)
              int depth2 = depth - 1 - depth1;
              for (ChildDerivationsGroup group : getFilteredDerivations(rule, floatingCell(rhs1, depth1), floatingCell(rhs2, depth2)))
                for (Derivation deriv1 : group.derivations1)
                  for (Derivation deriv2 : group.derivations2)
                    if (!applyFloatingRule(rule, depth, deriv1, deriv2, deriv1.canonicalUtterance + " " + deriv2.canonicalUtterance))
                      break derivLoop;
            }
        } else {
          {
            derivLoop:
              for (int subDepth = 0; subDepth < depth; subDepth++) {  // depth-1 <=depth-1
                for (ChildDerivationsGroup group : getFilteredDerivations(rule, floatingCell(rhs1, depth - 1), floatingCell(rhs2, subDepth)))
                  for (Derivation deriv1 : group.derivations1)
                    for (Derivation deriv2 : group.derivations2)
                      if (!applyFloatingRule(rule, depth, deriv1, deriv2, deriv1.canonicalUtterance + " " + deriv2.canonicalUtterance))
                        break derivLoop;
              }
          }
          {
            derivLoop:
              for (int subDepth = 0; subDepth < depth - 1; subDepth++) {  // <depth-1 depth-1
                for (ChildDerivationsGroup group : getFilteredDerivations(rule, floatingCell(rhs1, subDepth), floatingCell(rhs2, depth - 1)))
                  for (Derivation deriv1 : group.derivations1)
                    for (Derivation deriv2 : group.derivations2)
                      if (!applyFloatingRule(rule, depth, deriv1, deriv2, deriv1.canonicalUtterance + " " + deriv2.canonicalUtterance))
                        break derivLoop;
              }
          }
        }
      }
      ruleTime.put(rule, ruleTime.getOrDefault(rule, 0L) + stopWatch.stop().ms);
    }

    // Apply unary categories on spans (rule $A ($B))
    for (Rule rule : parser.catUnaryRules) {
      if (timeout && !isRootRule(rule)) continue;
      if (!rule.isFloating()) continue;
      if (catSizeBound.getBound(rule.lhs) < depth) continue;
      StopWatch stopWatch = new StopWatch().start();
      derivLoop:
        for (ChildDerivationsGroup group : getFilteredDerivations(rule, floatingCell(rule.rhs.get(0), depth - 1)))
          for (Derivation deriv : group.derivations1)
            if (!applyFloatingRule(rule, depth, deriv, null, deriv.canonicalUtterance))
              break derivLoop;
      ruleTime.put(rule, ruleTime.getOrDefault(rule, 0L) + stopWatch.stop().ms);
    }
  }

  void addToDerivations(Object cell, List<Derivation> derivations) {
    List<Derivation> myDerivations = chart.get(cell);
    if (myDerivations != null)
      derivations.addAll(myDerivations);
  }

  /**
   * Build derivations in a thread to allow timeout.
   */
  class DerivationBuilder implements Runnable {
    @Override public void run() {
      // Base case ($TOKEN, $PHRASE)
      for (Derivation deriv : gatherTokenAndPhraseDerivations()) {
        addToChart(anchoredCell(deriv.cat, deriv.start, deriv.end), deriv);
        addToChart(floatingCell(deriv.cat, 0), deriv);
      }

      Set<String> categories = new HashSet<>();
      for (Rule rule : parser.grammar.rules)
        categories.add(rule.lhs);

      if (Parser.opts.verbose >= 1)
        LogInfo.begin_track_printAll("Anchored");
      // Build up anchored derivations (like the BeamParser)
      int numTokens = ex.numTokens();
      for (int len = 1; len <= numTokens; len++) {
        for (int i = 0; i + len <= numTokens; i++)  {
          buildAnchored(i, i + len);
          for (String cat : categories) {
            String cell = anchoredCell(cat, i, i + len).toString();
            pruneCell(cell, chart.get(cell));
          }
        }
      }
      if (Parser.opts.verbose >= 1)
        LogInfo.end_track();

      // Build up floating derivations
      for (int depth = (FloatingParser.opts.initialFloatingHasZeroDepth ? 0 : 1); depth <= FloatingParser.opts.maxDepth; depth++) {
        if (Parser.opts.verbose >= 1)
          LogInfo.begin_track_printAll("%s = %d", FloatingParser.opts.useSizeInsteadOfDepth ? "SIZE" : "DEPTH", depth);
        buildFloating(depth);
        for (String cat : categories) {
          String cell = floatingCell(cat, depth).toString();
          pruneCell(cell, chart.get(cell));
        }
        if (Parser.opts.verbose >= 1)
          LogInfo.end_track();
        // Early stopping
        if (computeExpectedCounts && ((FloatingParser) parser).earlyStopOnConsistent) {
          // Consistent derivation found?
          String cell = floatingCell(Rule.rootCat, depth).toString();
          List<Derivation> rootDerivs = chart.get(cell);
          if (rootDerivs != null) {
            for (Derivation rootDeriv : rootDerivs) {
              rootDeriv.ensureExecuted(parser.executor, ex.context);
              if (parser.valueEvaluator.getCompatibility(ex.targetValue, rootDeriv.value) == 1) {
                LogInfo.logs("Early stopped: consistent derivation found at depth = %d", depth);
                return;
              }
            }
          }
        }
        if (((FloatingParser) parser).earlyStopOnNumDerivs > 0) {
          // Too many derivations generated?
          if (numOfFeaturizedDerivs > ((FloatingParser) parser).earlyStopOnNumDerivs) {
            LogInfo.logs("Early stopped: number of derivations exceeded at depth = %d", depth);
            return;
          }
        }
      }
    }
  }

  public void buildDerivations() {
    DerivationBuilder derivBuilder = new DerivationBuilder();
    if (FloatingParser.opts.maxFloatingParsingTime == Integer.MAX_VALUE) {
      derivBuilder.run();
    } else {
      Thread parsingThread = new Thread(derivBuilder);
      parsingThread.start();
      try {
        parsingThread.join(FloatingParser.opts.maxFloatingParsingTime * 1000);
        if (parsingThread.isAlive()) {
          // This will only interrupt first or second passes, not the final candidate collection.
          LogInfo.warnings("Parsing time exceeded %d seconds. Will now interrupt ...", FloatingParser.opts.maxFloatingParsingTime);
          timeout = true;
          parsingThread.interrupt();
          parsingThread.join();
        }
      } catch (InterruptedException e) {
        e.printStackTrace();
        LogInfo.fails("FloatingParser error: %s", e);
      }
    }
    evaluation.add("timeout", timeout);
  }

  // ============================================================
  // Main entry point
  // ============================================================

  @Override public void infer() {
    LogInfo.begin_track_printAll("FloatingParser.infer()");
    ruleTime = new HashMap<>();

    buildDerivations();

    if (FloatingParser.opts.summarizeRuleTime) summarizeRuleTime();

    // Collect final predicted derivations
    addToDerivations(anchoredCell(Rule.rootCat, 0, numTokens), predDerivations);
    for (int depth = 0; depth <= FloatingParser.opts.maxDepth; depth++)
      addToDerivations(floatingCell(Rule.rootCat, depth), predDerivations);

    // Compute gradient with respect to the predicted derivations
    ensureExecuted();
    if (computeExpectedCounts) {
      expectedCounts = new HashMap<>();
      ParserState.computeExpectedCounts(predDerivations, expectedCounts);
    }

    // Example summary
    if (Parser.opts.verbose >= 2) {
      LogInfo.begin_track_printAll("Summary of Example %s", ex.getUtterance());
      for (Derivation deriv : predDerivations)
        LogInfo.logs("Generated: canonicalUtterance=%s, value=%s", deriv.canonicalUtterance, deriv.value);
      LogInfo.end_track();
    }

    if (FloatingParser.opts.printPredictedUtterances) {
      PrintWriter writer = IOUtils.openOutAppendEasy(Execution.getFile("canonical_utterances"));
      PrintWriter fWriter = IOUtils.openOutAppendEasy(Execution.getFile("utterances_formula.tsv"));
      Derivation.sortByScore(predDerivations);
      for (Derivation deriv: predDerivations) {
        if (deriv.score > -10) {
          writer.println(String.format("%s\t%s", deriv.canonicalUtterance, deriv.score));
          fWriter.println(String.format("%s\t%s", deriv.canonicalUtterance, deriv.formula.toString()));
        }
      }
      writer.close();
      fWriter.close();
    }

    LogInfo.end_track();
  }

  @Override
  protected void setEvaluation() {
    super.setEvaluation();
    evaluation.add("numCells", chart.size());
  }

  @SuppressWarnings("unused")
  private void visualizeAnchoredChart(Set<String> categories) {
    for (String cat : categories) {
      for (int len = 1; len <= numTokens; ++len) {
        for (int i = 0; i + len <= numTokens; ++i) {
          List<Derivation> derivations = getDerivations(anchoredCell(cat, i, i + len));
          for (Derivation deriv : derivations) {
            LogInfo.logs("ParserState.visualize: %s(%s:%s): %s", cat, i, i + len, deriv);
          }
        }
      }
    }
  }

  private void summarizeRuleTime() {
    List<Map.Entry<Rule, Long>> entries = new ArrayList<>(ruleTime.entrySet());
    entries.sort(new ValueComparator<>(true));
    LogInfo.begin_track_printAll("Rule time");
    for (Map.Entry<Rule, Long> entry : entries) {
      LogInfo.logs("%9d : %s", entry.getValue(), entry.getKey());
    }
    LogInfo.end_track();
  }
}
