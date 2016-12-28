package edu.stanford.nlp.sempre;

import fig.basic.Evaluation;
import fig.basic.Fmt;
import fig.basic.LogInfo;
import fig.basic.StopWatch;

/**
 * Created by joberant on 28/12/2016.
 * @author: joberant
 */
public abstract class NeuralParser extends Parser {

  public NeuralParser(Spec spec) {
    super(spec);
  }

  public abstract NeuralParserState newParserState(ComputationGraphWrapper cgWrapper,
                                             Example ex, boolean computeExpectedCounts);

  /**
   * Parse the given example |ex| using the computational graph |cgWrapper|
   * and populate the fields of |ex| (e.g., predDerivations).  Note:
   * |ex| is modified in place.
   */
  public NeuralParserState parse(ComputationGraphWrapper cgWrapper, Example ex, boolean computeExpectedCounts) {
    // Execute target formula (if applicable).
    if (ex.targetFormula != null && ex.targetValue == null)
      ex.targetValue = executor.execute(ex.targetFormula, ex.context).value;

    // Parse
    StopWatch watch = new StopWatch();
    watch.start();
    LogInfo.begin_track("Parser.parse: parse");
    NeuralParserState state = newParserState(cgWrapper, ex, computeExpectedCounts);
    state.infer();
    LogInfo.end_track();
    watch.stop();
    state.parseTime = watch.getCurrTimeLong();
    state.setEvaluation();

    ex.predDerivations = state.predDerivations;
    Derivation.sortByScore(ex.predDerivations);

    // Evaluate
    ex.evaluation = new Evaluation();
    addToEvaluation(state, ex.evaluation);

    // Clean up temporary state used during parsing
    ex.clearTempState();
    for (Derivation deriv : ex.predDerivations)
      deriv.clearTempState();

    return state;
  }
}

 abstract class NeuralParserState extends ParserState {

  public final ComputationGraphWrapper cgWrapper;

  public NeuralParserState(Parser parser, Params params, Example ex, boolean computeExpectedCounts) {
    super(parser, params, ex, computeExpectedCounts);
    throw new RuntimeException("Neural parser does not support the Params object");
  }
  public NeuralParserState(Parser parser, ComputationGraphWrapper cgWrapper, Example ex, boolean computeExpectedCounts) {
    super(parser, cgWrapper, ex, computeExpectedCounts);
    this.cgWrapper = cgWrapper;
  }

  protected void featurizeAndScoreDerivation(Derivation deriv) {
    if (deriv.isFeaturizedAndScored()) {
      LogInfo.warnings("Derivation already featurized: %s", deriv);
      return;
    }

    // Compute features.
    parser.extractor.extractLocal(ex, deriv);

    // Compute score with the computation graph.
    deriv.score = cgWrapper.scoreDerivation(deriv);

    if (parser.verbose(5)) {
      LogInfo.logs("featurizeAndScoreDerivation(score=%s) %s %s: %s [rule: %s]",
        Fmt.D(deriv.score), deriv.cat, ex.spanString(deriv.start, deriv.end), deriv, deriv.rule);
    }
    numOfFeaturizedDerivs++;
  }
}

