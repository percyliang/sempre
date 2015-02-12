package edu.stanford.nlp.sempre;

import fig.basic.*;

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
 *   $Cat => token
 *   $Cat => $Cat
 *   $Cat => token token
 *   $Cat => token $Cat
 *   $Cat => $Cat token
 *   $Cat => $Cat $Cat
 * Each rule is either anchored or floating or both.
 *
 * Chart cells are either:
 * - anchored: (cat, start, end) [these are effectively at depth 0]
 * - floating: (cat, depth) [depends on anchored cells as base cases]
 *
 * Rules:
 *   cat => cat1 cat2 [binary]
 *   cat => cat1 [unary]
 * Combinations:
 *   (cat1, start, end) => (cat, start, end)
 *   (cat1, depth) => (cat, depth)
 *
 *   (cat1, start, mid), (cat2, mid, end) => (cat, start, end)
 *   (cat1, start, end), (cat2, depth) => (cat, depth + 1)
 *   (cat1, depth), (cat2, start, end) => (cat, depth + 1)
 *   (cat1, depth1), (cat2, depth2) => (cat, max(depth1, depth2)+1)
 *
 * @author Percy Liang
 */
public class FloatingParser extends Parser {
  public static class Options {
    @Option public int maxDepth = 10;
    @Option public boolean defaultIsFloating = true;
    @Option (gloss = "Flag specifying whether anchored spans/tokens can only be used once in a derivation")
    public boolean useAnchorsOnce = false;
  }
  public static Options opts = new Options();

  public FloatingParser(Spec spec) { super(spec); }

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
  // cell => list of derivations, formula set
  // Examples of state:
  //   (category, depth)
  //   (category, depth, set of tokens)
  private final Map<Object, List<Derivation>> chart = new HashMap<>();

  public FloatingParserState(FloatingParser parser, Params params, Example ex, boolean computeExpectedCounts) {
    super(parser, params, ex, computeExpectedCounts);
  }

  // Construct state names.
  private Object floatingCell(String cat, int depth) {
    return cat + ":" + depth;
  }
  private Object anchoredCell(String cat, int start, int end) {
    return cat + "[" + start + "," + end + "]";
  }
  private Object cell(String cat, int start, int end, int depth) {
    return (start != -1) ? anchoredCell(cat, start, end) : floatingCell(cat, depth);
  }

  private void addToChart(Object cell, Derivation deriv) {
    if (Parser.opts.verbose >= 3)
      LogInfo.logs("addToChart %s: %s", cell, deriv);
    if (!deriv.isFeaturizedAndScored())  // A derivation could be belong in multiple cells.
      featurizeAndScoreDerivation(deriv);
    MapUtils.addToList(chart, cell, deriv);
  }

  private void applyRule(Rule rule, int start, int end, int depth, Derivation child1, Derivation child2, String canonicalUtterance) {
    if (Parser.opts.verbose >= 5) logs("applyRule %s [%s:%s] depth=%s, %s %s", rule, start, end, depth, child1, child2);
    List<Derivation> children;
    if (child1 == null)  // 0-ary
      children = Collections.emptyList();
    else if (child2 == null)  // 1-ary
      children = Collections.singletonList(child1);
    else {
      children = ListUtils.newList(child1, child2);
      // optionally: ensure that specific anchors are only used once per final derivation
      if (FloatingParser.opts.useAnchorsOnce &&
          FloatingRuleUtils.derivationAnchorsOverlap(child1, child2))
        return;
    }

    DerivationStream results = rule.sem.call(ex,
        new SemanticFn.CallInfo(rule.lhs, start, end, rule, children));
    while (results.hasNext()) {
      Derivation newDeriv = results.next();
      newDeriv.canonicalUtterance = canonicalUtterance;
      // Avoid repetitive floating cells
      /*
         if (depth > 0) {
         if (MapUtils.getSet(setChart, rule.lhs).contains(newDeriv.formula)) {
         continue;
         }
         if (rule.lhs == Rule.rootCat)
         LogInfo.logs("adding %s to root for floating", newDeriv);
         MapUtils.addToSet(setChart, rule.lhs, newDeriv.formula);
         }
         */
      addToChart(cell(rule.lhs, start, end, depth), newDeriv);
      if (depth == -1)  // In addition, anchored cells become floating at level 0
        addToChart(floatingCell(rule.lhs, 0), newDeriv);
    }
  }

  private void applyAnchoredRule(Rule rule, int start, int end, Derivation child1, Derivation child2, String canonicalUtterance) {
    applyRule(rule, start, end, -1, child1, child2, canonicalUtterance);
  }

  private void applyFloatingRule(Rule rule, int depth, Derivation child1, Derivation child2, String canonicalUtterance) {
    applyRule(rule, -1, -1, depth, child1, child2, canonicalUtterance);
  }

  private List<Derivation> getDerivations(Object cell) {
    List<Derivation> derivations = chart.get(cell);
    // logs("getDerivations %s => %s", cell, derivations);
    if (derivations == null) return Derivation.emptyList;
    return derivations;
  }

  // Build derivations over span |start|, |end|.
  private void buildAnchored(int start, int end) {
    // Apply unary tokens on spans (rule $A (a))
    for (Rule rule : parser.grammar.rules) {
      if (!rule.isAnchored()) continue;
      if (rule.rhs.size() != 1 || rule.isCatUnary()) continue;
      boolean match = (end - start == 1) && ex.token(start).equals(rule.rhs.get(0));
      if (match)
        applyAnchoredRule(rule, start, end, null, null, rule.rhs.get(0));
    }

    // Apply binaries on spans (rule $A ($B $C)), ...
    for (int mid = start + 1; mid < end; mid++) {
      for (Rule rule : parser.grammar.rules) {
        if (!rule.isAnchored()) continue;
        if (rule.rhs.size() != 2) continue;

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
      }
    }

    // Apply unary categories on spans (rule $A ($B))
    // Important: do this in topologically sorted order and after all the binaries are done.
    for (Rule rule : parser.catUnaryRules) {
      if (!rule.isAnchored()) continue;
      List<Derivation> derivations = getDerivations(anchoredCell(rule.rhs.get(0), start, end));
      for (Derivation deriv : derivations) {
        applyAnchoredRule(rule, start, end, deriv, null, deriv.canonicalUtterance);
      }
    }
  }

  // Build floating derivations of exactly depth |depth|.
  private void buildFloating(int depth) {
    // Apply unary tokens on spans (rule $A (a))
    if (depth == 1) {
      for (Rule rule : parser.grammar.rules) {
        if (!rule.isFloating()) continue;
        if (rule.rhs.size() != 1 || rule.isCatUnary()) continue;
        applyFloatingRule(rule, depth, null, null, rule.rhs.get(0));
      }
    }

    // Apply binaries on spans (rule $A ($B $C)), ...
    for (Rule rule : parser.grammar.rules) {
      if (!rule.isFloating()) continue;
      if (rule.rhs.size() != 2) continue;

      String rhs1 = rule.rhs.get(0);
      String rhs2 = rule.rhs.get(1);

      if (!Rule.isCat(rhs1) && !Rule.isCat(rhs2)) {  // token token
        if (depth == 1)
          applyFloatingRule(rule, depth, null, null, rhs1 + " " + rhs2);
      } else if (!Rule.isCat(rhs1) && Rule.isCat(rhs2)) {  // token $Cat
        List<Derivation> derivations = getDerivations(floatingCell(rhs2, depth - 1));
        for (Derivation deriv : derivations)
          applyFloatingRule(rule, depth, deriv, null, rhs1 + " " + deriv.canonicalUtterance);
      } else if (Rule.isCat(rhs1) && !Rule.isCat(rhs2)) {  // $Cat token
        List<Derivation> derivations = getDerivations(floatingCell(rhs1, depth - 1));
        for (Derivation deriv : derivations)
          applyFloatingRule(rule, depth, deriv, null, deriv.canonicalUtterance + " " + rhs2);
      } else {  // $Cat $Cat
        for (int subDepth = 0; subDepth < depth; subDepth++) {  // depth-1 <=depth-1
          List<Derivation> derivations1 = getDerivations(floatingCell(rhs1, depth - 1));
          List<Derivation> derivations2 = getDerivations(floatingCell(rhs2, subDepth));
          for (Derivation deriv1 : derivations1)
            for (Derivation deriv2 : derivations2)
              applyFloatingRule(rule, depth, deriv1, deriv2, deriv1.canonicalUtterance + " " + deriv2.canonicalUtterance);
        }
        for (int subDepth = 0; subDepth < depth - 1; subDepth++) {  // <depth-1 depth-1
          List<Derivation> derivations1 = getDerivations(floatingCell(rhs1, subDepth));
          List<Derivation> derivations2 = getDerivations(floatingCell(rhs2, depth - 1));
          for (Derivation deriv1 : derivations1)
            for (Derivation deriv2 : derivations2)
              applyFloatingRule(rule, depth, deriv1, deriv2, deriv1.canonicalUtterance + " " + deriv2.canonicalUtterance);
        }
      }
    }

    // Apply unary categories on spans (rule $A ($B))
    // Important: do this in topologically sorted order and after all the binaries are done.
    for (Rule rule : parser.catUnaryRules) {
      if (!rule.isFloating()) continue;
      List<Derivation> derivations = getDerivations(floatingCell(rule.rhs.get(0), depth - 1));
      for (Derivation deriv : derivations)
        applyFloatingRule(rule, depth, deriv, null, deriv.canonicalUtterance);
    }
  }

  void addToDerivations(Object cell, List<Derivation> derivations) {
    List<Derivation> myDerivations = chart.get(cell);
    if (myDerivations != null)
      derivations.addAll(myDerivations);
  }

  @Override public void infer() {
    LogInfo.begin_track("FloatingParser.infer()");

    // Base case ($TOKEN, $PHRASE)
    for (Derivation deriv : gatherTokenAndPhraseDerivations()) {
      addToChart(anchoredCell(deriv.cat, deriv.start, deriv.end), deriv);
      addToChart(floatingCell(deriv.cat, 0), deriv);
    }

    Set<String> categories = new HashSet<>();
    for (Rule rule : parser.grammar.rules)
      categories.add(rule.lhs);

    // Build up anchored derivations (like the BeamParser)
    int numTokens = ex.numTokens();
    for (int len = 1; len <= numTokens; len++) {
      for (int i = 0; i + len <= numTokens; i++)  {
        buildAnchored(i, i + len);
        for (String cat : categories) {
          String cell = anchoredCell(cat, i, i + len).toString();
          /*if (cat.equals("$Word") || cat.equals("$Fragment")) {
            LogInfo.logs("Looking at cell: %s", cell);
            if (chart.get(cell) == null) LogInfo.logs("???");
            else {
              for (Derivation deriv : chart.get(cell))
                LogInfo.logs("Found cell of interest: %s", deriv.toString());
            }
          }*/
          pruneCell(cell, chart.get(cell));
        }
      }
    }

    // Build up floating derivations
    for (int depth = 1; depth <= FloatingParser.opts.maxDepth; depth++) {
      buildFloating(depth);
      for (String cat : categories) {
        String cell = floatingCell(cat, depth).toString();
        /*if (cat.equals("$Word") || cat.equals("$Fragment")) {
          LogInfo.logs("Looking at floating cell: %s", cell);
          if (chart.get(cell) == null) LogInfo.logs("???");
          else {
            for (Derivation deriv : chart.get(cell))
              LogInfo.logs("Found cell of interest: %s", deriv.toString());
          }
        }*/
        pruneCell(cell, chart.get(cell));
      }
    }

    // Collect final predicted derivations
    addToDerivations(anchoredCell(Rule.rootCat, 0, numTokens), predDerivations);
    for (int depth = 1; depth <= FloatingParser.opts.maxDepth; depth++)
      addToDerivations(floatingCell(Rule.rootCat, depth), predDerivations);

    // Compute gradient with respect to the predicted derivations
    ensureExecuted();
    if (computeExpectedCounts) {
      expectedCounts = new HashMap<>();
      ParserState.computeExpectedCounts(predDerivations, expectedCounts);
    }

    // Example summary
    if (Parser.opts.verbose >= 1) {
      LogInfo.begin_track("Summary of Example %s", ex.getUtterance());
      for (Derivation deriv : predDerivations)
        LogInfo.logs("Generated: canonicalUtterance=%s, value=%s", deriv.canonicalUtterance, deriv.value);
      LogInfo.end_track();
    }

    LogInfo.end_track();
  }

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
}
