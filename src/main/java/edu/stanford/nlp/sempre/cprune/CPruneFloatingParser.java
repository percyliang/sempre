package edu.stanford.nlp.sempre.cprune;

import java.util.List;

import edu.stanford.nlp.sempre.*;
import fig.basic.LogInfo;

/**
 * A parser that first tries to exploit the macro grammar and only fall back to full search when needed.
 */
public class CPruneFloatingParser extends FloatingParser {

  FloatingParser exploreParser;

  public CPruneFloatingParser(Spec spec) {
    super(spec);
    exploreParser = new FloatingParser(spec).setEarlyStopping(true, CollaborativePruner.opts.maxDerivations);
  }

  @Override
  public void onBeginDataGroup(int iter, int numIters, String group) {
    if (CollaborativePruner.uidToCachedNeighbors == null) {
      CollaborativePruner.customGrammar.init(grammar);
      CollaborativePruner.loadNeighbors();
    }
    CollaborativePruner.stats.reset(iter + "." + group);
  }

  @Override
  public ParserState newParserState(Params params, Example ex, boolean computeExpectedCounts) {
    return new CPruneFloatingParserState(this, params, ex, computeExpectedCounts);
  }

}

class CPruneFloatingParserState extends ParserState {

  public CPruneFloatingParserState(Parser parser, Params params, Example ex, boolean computeExpectedCounts) {
    super(parser, params, ex, computeExpectedCounts);
  }

  @Override
  public void infer() {
    LogInfo.begin_track("CPruneFloatingParser.infer()");
    boolean exploitSucceeds = exploit();
    if (computeExpectedCounts) {
      LogInfo.begin_track("Summary of Collaborative Pruning");
      LogInfo.logs("Exploit succeeds: " + exploitSucceeds);
      LogInfo.logs("Exploit success rate: " + CollaborativePruner.stats.successfulExploit + "/" + CollaborativePruner.stats.totalExploit);
      LogInfo.end_track();
    }
    // Explore only on the first training iteration
    if (CollaborativePruner.stats.iter.equals("0.train") && computeExpectedCounts && !exploitSucceeds
        && (CollaborativePruner.stats.totalExplore <= CollaborativePruner.opts.maxExplorationIters)) {
      explore();
      LogInfo.logs("Consistent pattern: " + CollaborativePruner.getConsistentPattern(ex));
      LogInfo.logs("Explore success rate: " + CollaborativePruner.stats.successfulExplore + "/" + CollaborativePruner.stats.totalExplore);
    }
    LogInfo.end_track();
  }

  public void explore() {
    LogInfo.begin_track("Explore");
    CollaborativePruner.initialize(ex, CollaborativePruner.Mode.EXPLORE);
    ParserState exploreParserState = ((CPruneFloatingParser) parser).exploreParser.newParserState(params, ex, computeExpectedCounts);
    exploreParserState.infer();
    predDerivations.clear();
    predDerivations.addAll(exploreParserState.predDerivations);
    expectedCounts = exploreParserState.expectedCounts;
    if (computeExpectedCounts) {
      for (Derivation deriv : predDerivations)
        CollaborativePruner.updateConsistentPattern(parser.valueEvaluator, ex, deriv);
    }
    CollaborativePruner.stats.totalExplore += 1;
    if (CollaborativePruner.foundConsistentDerivation)
      CollaborativePruner.stats.successfulExplore += 1;
    LogInfo.end_track();
  }

  public boolean exploit() {
    LogInfo.begin_track("Exploit");
    CollaborativePruner.initialize(ex, CollaborativePruner.Mode.EXPLOIT);
    Grammar miniGrammar = new MiniGrammar(CollaborativePruner.predictedRules);
    Parser exploitParser = new FloatingParser(new Parser.Spec(miniGrammar, parser.extractor, parser.executor, parser.valueEvaluator));
    ParserState exploitParserState = exploitParser.newParserState(params, ex, computeExpectedCounts);
    exploitParserState.infer();
    predDerivations.clear();
    predDerivations.addAll(exploitParserState.predDerivations);
    expectedCounts = exploitParserState.expectedCounts;
    if (computeExpectedCounts) {
      for (Derivation deriv : predDerivations)
        CollaborativePruner.updateConsistentPattern(parser.valueEvaluator, ex, deriv);
    }
    boolean succeeds = CollaborativePruner.foundConsistentDerivation;
    CollaborativePruner.stats.totalExploit += 1;
    if (succeeds)
      CollaborativePruner.stats.successfulExploit += 1;
    LogInfo.end_track();
    return succeeds;
  }
}

// ============================================================
// Helper classes
// ============================================================

class MiniGrammar extends Grammar {

  public MiniGrammar(List<Rule> rules) {
    this.rules.addAll(rules);
    if (CollaborativePruner.opts.verbose >= 2) {
      LogInfo.begin_track("MiniGrammar Rules");
      for (Rule rule : rules)
        LogInfo.logs("%s %s", rule, rule.isAnchored() ? "[A]" : "[F]");
      LogInfo.end_track();
    }
  }

}
