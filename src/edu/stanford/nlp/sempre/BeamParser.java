package edu.stanford.nlp.sempre;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import fig.basic.*;

import java.util.*;

/**
 * A simple bottom-up chart-based parser that keeps the |beamSize| top
 * derivations for each chart cell (cat, start, end).  Also supports fast
 * indexing of lexicalized rules using a trie.
 * <p/>
 * In the future, when we have more parsers, some of this code should be
 * refactored into Parser.
 *
 * @author Percy Liang
 */
public class BeamParser extends Parser {
  public static class Options {
    @Option public int beamSize = 500;
    @Option public int maxNewTreesPerSpan = Integer.MAX_VALUE;
  }
  public static Options opts = new Options();

  Trie trie;  //for non-catunary rules

  public BeamParser(Grammar grammar, FeatureExtractor extractor, Executor executor) {
    super(grammar, extractor, executor);
    // Index the non-cat-unary rules
    trie = new Trie();
    for (Rule rule : grammar.rules)
      if (!rule.isCatUnary())
        trie.add(rule);
  }

  public int getDefaultBeamSize() {
    return BeamParser.opts.beamSize;
  }

  public ParserState newCoarseParserState(Params params, Example ex) {
    return new BeamParserState(
        ParserState.Mode.bool,
        this, params, ex, null);
  }

  public ParserState newParserState(Params params,
      Example ex,
      ParserState coarseState) {
    return new BeamParserState(
        ParserState.Mode.full,
        this, params, ex, coarseState);
  }
}

/**
 * Stores BeamParser information about parsing a particular example. The actual
 * parsing code lives here.
 *
 * @author Percy Liang
 * @author Roy Frostig
 */
class BeamParserState extends ParserState {
  private final BeamParser parser;

  public BeamParserState(Mode mode,
      BeamParser parser,
      Params params,
      Example ex,
      ParserState coarseState) {
    super(mode, parser, params, ex, coarseState);
    this.parser = parser;
  }

  // Create all the derivations for the span [start, end).
  @Override
  protected void build(int start, int end) {
    applyNonCatUnaryRules(start, end, start, parser.trie, new ArrayList<Derivation>(), new IntRef(0));

    Set<String> cellsPruned = new HashSet<String>();
    applyCatUnaryRules(start, end, cellsPruned);

    for (Map.Entry<String, List<Derivation>> entry : getChart()[start][end].entrySet())
      pruneCell(cellsPruned, entry.getKey(), start, end, entry.getValue());
  }

  private void pruneCell(Set<String> cellsPruned, String cat, int start, int end, List<Derivation> derivations) {
    String cell = cellString(cat, start, end);
    if (cellsPruned.contains(cell)) return;
    cellsPruned.add(cell);

    // Keep stats
    if (derivations.size() > maxCellSize) {
      maxCellSize = derivations.size();
      maxCellDescription = String.format("[%s %s]", cat, getExample().spanString(start, end));
      if (maxCellSize > 5000)
        LogInfo.logs("BeamParser.pruneCell %s: %s entries", maxCellDescription, maxCellSize);
    }

    // The extra code blocks in here that set |deriv.maxXBeamPosition|
    // are there to track, over the course of parsing, the lowest
    // position at which any of a derivation's constituents ever
    // placed on any of the relevant beams.

    // Max beam position (before sorting)
    for (int i = 0; i < derivations.size(); i++) {
      Derivation deriv = derivations.get(i);
      deriv.maxUnsortedBeamPosition = i;
      if (deriv.children != null) {
        for (Derivation child : deriv.children)
          deriv.maxUnsortedBeamPosition = Math.max(deriv.maxUnsortedBeamPosition, child.maxUnsortedBeamPosition);
      }
      if (deriv.preSortBeamPosition == -1) {
        // Need to be careful to only do this once since |pruneCell()|
        // might be called several times for the same beam and the
        // second time around we have already sorted once.
        deriv.preSortBeamPosition = i;
      }
    }

    Derivation.sortByScore(derivations);

    // Max beam position (after sorting)
    for (int i = 0; i < derivations.size(); i++) {
      Derivation deriv = derivations.get(i);
      deriv.maxBeamPosition = i;
      if (deriv.children != null) {
        for (Derivation child : deriv.children)
          deriv.maxBeamPosition = Math.max(deriv.maxBeamPosition, child.maxBeamPosition);
      }
      deriv.postSortBeamPosition = i;
    }

    // Keep only the top hypotheses
    int beamSize = getBeamSize();
    while (derivations.size() > beamSize) {
      derivations.remove(derivations.size() - 1);
      fallOffBeam = true;
    }

    // Reduce memory
    if (derivations instanceof ArrayList)
      ((ArrayList) derivations).trimToSize();
  }

  static String cellString(String cat, int start, int end) {
    return cat + ":" + start + ":" + end;
  }

  // Return number of new derivations added
  private int applyRule(int start, int end, Rule rule, List<Derivation> children) {
    if (Parser.opts.verbose >= 5)
      LogInfo.logs("applyRule %s %s %s %s", start, end, rule, children);
    try {
      if (getMode() == Mode.full) {
        StopWatchSet.begin(rule.getSemRepn());
        List<Derivation> results = rule.sem.call(
            getExample(),
            new SemanticFn.CallInfo(rule.lhs, start, end, rule, ImmutableList.copyOf(children)));
        StopWatchSet.end();
        for (Derivation newDeriv : results) {
          featurizeAndScoreDerivation(newDeriv);
          addToChart(newDeriv);
        }
        return results.size();
      } else if (getMode() == Mode.bool) {
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

  // Apply all unary rules with RHS category.
  // Before applying each unary rule (rule.lhs -> rhsCat), we can prune the cell of rhsCat
  // because we assume acyclicity, so rhsCat's cell will never grow.
  private void applyCatUnaryRules(int start, int end, Set<String> cellsPruned) {
    for (Rule rule : parser.catUnaryRules) {
      if (!coarseAllows(rule.lhs, start, end))
        continue;
      String rhsCat = rule.rhs.get(0);
      List<Derivation> derivations = getChart()[start][end].get(rhsCat);
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
        if(coarseAllows(rule.lhs, start, end)) {
          numNew.value += applyRule(start, end, rule, children);
        }
      }
      return;
    }

    // Advance terminal token
    applyNonCatUnaryRules(
        start, end, i + 1,
        node.next(getExample().token(i)),
        children,
        numNew);

    // Advance non-terminal category
    for (int j = i + 1; j <= end; j++) {
      for (Map.Entry<String, List<Derivation>> entry : getChart()[i][j].entrySet()) {
        Trie nextNode = node.next(entry.getKey());
        for (Derivation arg : entry.getValue()) {
          children.add(arg);
          applyNonCatUnaryRules(start, end, j, nextNode, children, numNew);
          children.remove(children.size() - 1);
          if (getMode() != Mode.full) break;  // Only need one hypothesis
          if (numNew.value >= BeamParser.opts.maxNewTreesPerSpan) return;
        }
      }
    }
  }
}

