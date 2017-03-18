package edu.stanford.nlp.sempre;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import fig.basic.*;
import fig.exec.Execution;

import java.util.*;

/**
 * A simple bottom-up chart-based parser that keeps the |beamSize| top
 * derivations for each chart cell (cat, start, end).  Also supports fast
 * indexing of lexicalized rules using a trie.
 *
 * Note that this code does not rely on the Grammar being binarized,
 * which makes it more complex.
 *
 * @author Percy Liang
 */
public class BeamParser extends Parser {
  public static class Options {
    @Option public int maxNewTreesPerSpan = Integer.MAX_VALUE;
  }
  public static Options opts = new Options();

  Trie trie;  // For non-cat-unary rules

  public BeamParser(Spec spec) {
    super(spec);

    // Index the non-cat-unary rules
    trie = new Trie();
    for (Rule rule : grammar.rules)
      addRule(rule);
    if (Parser.opts.visualizeChartFilling)
      this.chartFillOut = IOUtils.openOutAppendEasy(Execution.getFile("chartfill"));
  }

  public synchronized void addRule(Rule rule) {
    if (!rule.isCatUnary())
      trie.add(rule);
  }

  public ParserState newParserState(Params params, Example ex, boolean computeExpectedCounts) {
    BeamParserState coarseState = null;
    if (Parser.opts.coarsePrune) {
      LogInfo.begin_track("Parser.coarsePrune");
      coarseState = new BeamParserState(this, params, ex, computeExpectedCounts, BeamParserState.Mode.bool, null);
      coarseState.infer();
      coarseState.keepTopDownReachable();
      LogInfo.end_track();
    }
    return new BeamParserState(this, params, ex, computeExpectedCounts, BeamParserState.Mode.full, coarseState);
  }
}

/**
 * Stores BeamParser information about parsing a particular example. The actual
 * parsing code lives here.
 *
 * @author Percy Liang
 * @author Roy Frostig
 */
class BeamParserState extends ChartParserState {
  public final Mode mode;
  // Modes:
  // 1) Bool: just check if cells (cat, start, end) are reachable (to prune chart)
  // 2) Full: compute everything
  public enum Mode { bool, full }

  private final BeamParser parser;
  private final BeamParserState coarseState;  // Used to prune

  public BeamParserState(BeamParser parser, Params params, Example ex, boolean computeExpectedCounts,
                         Mode mode, BeamParserState coarseState) {
    super(parser, params, ex, computeExpectedCounts);
    this.parser = parser;
    this.mode = mode;
    this.coarseState = coarseState;
  }

  public void infer() {
    if (numTokens == 0)
      return;

    if (parser.verbose(2)) LogInfo.begin_track("ParserState.infer");

    // Base case
    for (Derivation deriv : gatherTokenAndPhraseDerivations()) {
      featurizeAndScoreDerivation(deriv);
      addToChart(deriv);
    }

    // Recursive case
    for (int len = 1; len <= numTokens; len++)
      for (int i = 0; i + len <= numTokens; i++)
        build(i, i + len);

    if (parser.verbose(2)) LogInfo.end_track();

    // Visualize
    if (parser.chartFillOut != null && Parser.opts.visualizeChartFilling && this.mode != Mode.bool) {
      parser.chartFillOut.println(Json.writeValueAsStringHard(new ChartFillingData(ex.id, chartFillingList,
              ex.utterance, ex.numTokens())));
      parser.chartFillOut.flush();
    }

    setPredDerivations();

    if (mode == Mode.full) {
      // Compute gradient with respect to the predicted derivations
      ensureExecuted();
      if (computeExpectedCounts) {
        expectedCounts = new HashMap<>();
        ParserState.computeExpectedCounts(predDerivations, expectedCounts);
      }
    }
  }

  // Create all the derivations for the span [start, end).
  protected void build(int start, int end) {
    applyNonCatUnaryRules(start, end, start, parser.trie, new ArrayList<Derivation>(), new IntRef(0));

    Set<String> cellsPruned = new HashSet<>();
    applyCatUnaryRules(start, end, cellsPruned);

    for (Map.Entry<String, List<Derivation>> entry : chart[start][end].entrySet())
      pruneCell(cellsPruned, entry.getKey(), start, end, entry.getValue());
  }

  private static String cellString(String cat, int start, int end) {
    return cat + ":" + start + ":" + end;
  }

  // Return number of new derivations added
  private int applyRule(int start, int end, Rule rule, List<Derivation> children) {
    if (Parser.opts.verbose >= 5) LogInfo.logs("applyRule %s %s %s %s", start, end, rule, children);
    try {
      if (mode == Mode.full) {
        StopWatchSet.begin(rule.getSemRepn());
        DerivationStream results = rule.sem.call(ex,
            new SemanticFn.CallInfo(rule.lhs, start, end, rule, ImmutableList.copyOf(children)));
        StopWatchSet.end();
        while (results.hasNext()) {
          Derivation newDeriv = results.next();
          featurizeAndScoreDerivation(newDeriv);
          addToChart(newDeriv);
        }
        return results.estimatedSize();
      } else if (mode == Mode.bool) {
        Derivation deriv = new Derivation.Builder()
            .cat(rule.lhs).start(start).end(end).rule(rule)
            .children(ImmutableList.copyOf(children))
            .formula(Formula.nullFormula)
            .createDerivation();
        addToChart(deriv);
        return 1;
      } else {
        throw new RuntimeException("Invalid mode");
      }
    } catch (Exception e) {
      LogInfo.errors("Composition failed: rule = %s, children = %s", rule, children);
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  // Don't prune the same cell more than once.
  protected void pruneCell(Set<String> cellsPruned, String cat, int start, int end, List<Derivation> derivations) {
    String cell = cellString(cat, start, end);
    if (cellsPruned.contains(cell)) return;
    cellsPruned.add(cell);
    pruneCell(cell, derivations);
  }

  // Apply all unary rules with RHS category.
  // Before applying each unary rule (rule.lhs -> rhsCat), we can prune the cell of rhsCat
  // because we assume acyclicity, so rhsCat's cell will never grow.
  private void applyCatUnaryRules(int start, int end, Set<String> cellsPruned) {
    for (Rule rule : parser.catUnaryRules) {
      if (!coarseAllows(rule.lhs, start, end))
        continue;
      String rhsCat = rule.rhs.get(0);
      List<Derivation> derivations = chart[start][end].get(rhsCat);
      if (Parser.opts.verbose >= 5)
        LogInfo.logs("applyCatUnaryRules %s %s %s %s", start, end, rule, derivations);
      if (derivations == null) continue;

      pruneCell(cellsPruned, rhsCat, start, end, derivations);  // Prune before applying rules to eliminate cruft!

      for (Derivation deriv : derivations)
        applyRule(start, end, rule, Collections.singletonList(deriv));
    }
  }

  // Strategy: walk along the input on span (start:end) and traverse the trie
  // to get the list of the rules that could apply by matching the RHS.
  // start:end: span we're dealing with.
  // i: current token position
  // node: contains a link to the RHS that could apply.
  // children: the derivations that't we're building up.
  // numNew: Keep track of number of new derivations created
  private void applyNonCatUnaryRules(int start,
      int end,
      int i,
      Trie node,
      ArrayList<Derivation> children,
      IntRef numNew) {
    if (node == null) return;
    if (!coarseAllows(node, start, end)) return;

    if (Parser.opts.verbose >= 5) {
      LogInfo.logs(
          "applyNonCatUnaryRules(start=%d, end=%d, i=%d, children=[%s], %s rules)",
          start, end, i, Joiner.on(", ").join(children), node.rules.size());
    }

    // Base case: our fencepost has walked to the end of the span, so
    // apply the rule on all the children gathered during the walk.
    if (i == end) {
      for (Rule rule : node.rules) {
        if (coarseAllows(rule.lhs, start, end)) {
          numNew.value += applyRule(start, end, rule, children);
        }
      }
      return;
    }

    // Advance terminal token
    applyNonCatUnaryRules(
        start, end, i + 1,
        node.next(ex.token(i)),
        children,
        numNew);

    // Advance non-terminal category
    for (int j = i + 1; j <= end; j++) {
      for (Map.Entry<String, List<Derivation>> entry : chart[i][j].entrySet()) {
        Trie nextNode = node.next(entry.getKey());
        for (Derivation arg : entry.getValue()) {
          children.add(arg);
          applyNonCatUnaryRules(start, end, j, nextNode, children, numNew);
          children.remove(children.size() - 1);
          if (mode != Mode.full) break;  // Only need one hypothesis
          if (numNew.value >= BeamParser.opts.maxNewTreesPerSpan) return;
        }
      }
    }
  }

  // -- Coarse state pruning --

  // Remove any (cat, start, end) which isn't reachable from the
  // (Rule.rootCat, 0, numTokens)
  public void keepTopDownReachable() {
    if (numTokens == 0) return;

    Set<String> reachable = new HashSet<>();
    collectReachable(reachable, Rule.rootCat, 0, numTokens);

    // Remove all derivations associated with (cat, start, end) that aren't reachable.
    for (int start = 0; start < numTokens; start++) {
      for (int end = start + 1; end <= numTokens; end++) {
        List<String> toRemoveCats = new LinkedList<>();
        for (String cat : chart[start][end].keySet()) {
          String key = catStartEndKey(cat, start, end);
          if (!reachable.contains(key)) {
            toRemoveCats.add(cat);
          }
        }
        Collections.sort(toRemoveCats);
        for (String cat : toRemoveCats) {
          if (parser.verbose(4)) {
            LogInfo.logs("Pruning chart %s(%s,%s)", cat, start, end);
          }
          chart[start][end].remove(cat);
        }
      }
    }
  }

  private void collectReachable(Set<String> reachable, String cat, int start, int end) {
    String key = catStartEndKey(cat, start, end);
    if (reachable.contains(key)) return;

    if (!chart[start][end].containsKey(cat)) {
      // This should only happen for the root when there are no parses.
      return;
    }

    reachable.add(key);
    for (Derivation deriv : chart[start][end].get(cat)) {
      for (Derivation subderiv : deriv.children) {
        collectReachable(reachable, subderiv.cat, subderiv.start, subderiv.end);
      }
    }
  }

  private String catStartEndKey(String cat, int start, int end) {
    return cat + ":" + start + ":" + end;
  }

  // For pruning with the coarse state
  protected boolean coarseAllows(Trie node, int start, int end) {
    if (coarseState == null) return true;
    return SetUtils.intersects(
            node.cats,
            coarseState.chart[start][end].keySet());
  }
  protected boolean coarseAllows(String cat, int start, int end) {
    if (coarseState == null) return true;
    return coarseState.chart[start][end].containsKey(cat);
  }
}
