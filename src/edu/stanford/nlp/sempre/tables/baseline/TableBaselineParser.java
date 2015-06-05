package edu.stanford.nlp.sempre.tables.baseline;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.*;
import fig.basic.*;

/**
 * Baseline parser for table.
 *
 * Choose the answer from a table cell.
 *
 * @author ppasupat
 */
public class TableBaselineParser extends Parser {

  public TableBaselineParser(Spec spec) {
    super(spec);
  }

  @Override
  public ParserState newParserState(Params params, Example ex, boolean computeExpectedCounts) {
    return new TableBaselineParserState(this, params, ex, computeExpectedCounts);
  }

}

/**
 * Actual logic for generating candidates.
 */
class TableBaselineParserState extends ParserState {

  public TableBaselineParserState(Parser parser, Params params, Example ex, boolean computeExpectedCounts) {
    super(parser, params, ex, computeExpectedCounts);
  }

  @Override
  public void infer() {
    LogInfo.begin_track("TableBaselineParser.infer()");
    // Add all entities and possible normalizations to the list of candidates
    TableKnowledgeGraph graph = (TableKnowledgeGraph) ex.context.graph;
    for (Formula f : graph.getAllFormulas(FuzzyMatchFn.FuzzyMatchFnMode.ENTITY)) {
      buildAllDerivations(f);
    }
    // Execute + Compute expected counts
    ensureExecuted();
    if (computeExpectedCounts) {
      expectedCounts = new HashMap<>();
      ParserState.computeExpectedCounts(predDerivations, expectedCounts);
    }
    LogInfo.end_track();
  }

  private void buildAllDerivations(Formula f) {
    generateDerivation(f);
    // Try number and date normalizations as well
    generateDerivation(new JoinFormula(Formula.fromString("!" + TableTypeSystem.CELL_NUMBER_VALUE.id), f));
    generateDerivation(new JoinFormula(Formula.fromString("!" + TableTypeSystem.CELL_DATE_VALUE.id), f));
  }

  private void generateDerivation(Formula f) {
    Derivation deriv = new Derivation.Builder()
    .cat(Rule.rootCat).start(-1).end(-1)
    .formula(f).children(Collections.emptyList())
    .type(TypeInference.inferType(f))
    .createDerivation();
    deriv.ensureExecuted(parser.executor, ex.context);
    if (deriv.value instanceof ErrorValue) return;
    if (deriv.value instanceof ListValue && ((ListValue) deriv.value).values.isEmpty()) return;
    if (!deriv.isFeaturizedAndScored()) featurizeAndScoreDerivation(deriv);
    predDerivations.add(deriv);
  }

}
