package edu.stanford.nlp.sempre.tables.test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.TableKnowledgeGraph;
import edu.stanford.nlp.sempre.tables.lambdadcs.LambdaDCSExecutor;
import edu.stanford.nlp.sempre.tables.test.CustomExample.ExampleProcessor;
import fig.basic.*;
import fig.exec.Execution;

public class DPDParserCheckerProcessor implements ExampleProcessor {
  public static class Options {
    @Option(gloss = "Only check annotated formulas (Don't check DPDParser beam)")
    public boolean onlyCheckAnnotatedFormulas = false;
  }
  public static Options opts = new Options();

  int n = 0, annotated = 0, oracle = 0, beamHasCorrectFormula = 0, beamNoCorrectFormula = 0, noBeam = 0;
  final Builder builder;
  final PrintWriter eventsOut;

  public DPDParserCheckerProcessor() {
    builder = new Builder();
    builder.build();
    eventsOut = IOUtils.openOutHard(Execution.getFile("checker.results"));
  }

  @Override
  public void run(CustomExample ex) {
    n++;
    String formulaFlag = "", beamFlag = "";
    if (ex.targetFormula == null) {
      formulaFlag = "no";
    } else {
      annotated++;
      if (isAnnotatedFormulaCorrect(ex)) {
        oracle++;
        formulaFlag = "good";
      } else {
        formulaFlag = "incorrect";
      }
    }
    if (!opts.onlyCheckAnnotatedFormulas) {
      ParserState state = builder.parser.parse(builder.params, ex, false);
      LogInfo.logs("utterance: %s", ex.utterance);
      LogInfo.logs("targetFormula: %s", ex.targetFormula);
      LogInfo.logs("targetValue: %s", ex.targetValue);
      if (state.predDerivations.isEmpty()) {
        noBeam++;
        beamFlag = "no";
      } else {
        Derivation correctDeriv = isCorrectFormulaOnBeam(ex, state.predDerivations);
        if (correctDeriv != null) {
          LogInfo.logs("Found correct formula: %s", correctDeriv);
          beamHasCorrectFormula++;
          beamFlag = "yes";
        } else {
          beamNoCorrectFormula++;
          beamFlag = "reach";
        }
      }
      LogInfo.logs("RESULT: %s %s %s", ex.id, formulaFlag, beamFlag);
      eventsOut.printf("%s\t%s\t%s\n", ex.id.replaceAll("nt-", ""), formulaFlag, beamFlag);
      eventsOut.flush();
    }
    // Save memory
    if (ex.predDerivations != null) {
      ex.predDerivations.clear();
      System.gc();
    }
  }

  // See if all annotated formulas (targetFormula, alternativeFormulas) are correct
  boolean isAnnotatedFormulaCorrect(CustomExample ex) {
    boolean isCorrect = isAnnotatedFormulaCorrect(ex, ex.targetFormula, "targetFormula");
    for (Formula formula : ex.alternativeFormulas) {
      isCorrect = isCorrect && isAnnotatedFormulaCorrect(ex, formula, "alternativeFormula");
    }
    return isCorrect;
  }

  // See if a formula executes to the targetValue
  boolean isAnnotatedFormulaCorrect(CustomExample ex, Formula formula, String prefix) {
    LogInfo.begin_track("isAnnotatedFormulaCorrect(%s): Example %s", prefix, ex.id);
    StopWatch watch = new StopWatch();
    watch.start();
    LogInfo.logs("TRUE: %s", ex.targetValue);
    double result = 0;
    try {
      LogInfo.logs("Inferred Type: %s", TypeInference.inferType(formula));
      Value pred = builder.executor.execute(formula, ex.context).value;
      if (pred instanceof ListValue)
        pred = ((TableKnowledgeGraph) ex.context.graph).getListValueWithOriginalStrings((ListValue) pred);
      LogInfo.logs("Example %s: %s", ex.id, ex.getTokens());
      LogInfo.logs("  targetFormula: %s", formula);
      LogInfo.logs("  canonicalized: %s", TableFormulaCanonicalizer.canonicalizeFormula(formula));
      LogInfo.logs("TRUE: %s", ex.targetValue);
      LogInfo.logs("PRED: %s", pred);
      result = builder.valueEvaluator.getCompatibility(ex.targetValue, pred);
      if (result != 1) {
        LogInfo.warnings("TRUE != PRED. %s Either targetValue or %s is wrong.", ex.id, prefix);
      }
    } catch (Exception e) {
      StringWriter sw = new StringWriter();
      e.printStackTrace(new PrintWriter(sw));
      LogInfo.logs("Example %s: %s", ex.id, ex.getTokens());
      LogInfo.logs("  targetFormula: %s", formula);
      LogInfo.logs("  canonicalized: %s", TableFormulaCanonicalizer.canonicalizeFormula(formula));
      LogInfo.logs("TRUE: %s", ex.targetValue);
      LogInfo.logs("PRED: ERROR %s\n%s", e, sw);
      LogInfo.warnings("TRUE != PRED. %s Something was wrong during the execution.", ex.id);
    }
    watch.stop();
    LogInfo.logs("Parse Time: %s", watch);
    LogInfo.end_track();
    return result == 1;
  }

  Derivation isCorrectFormulaOnBeam(CustomExample ex, List<Derivation> predDerivations) {
    List<Formula> formulas = new ArrayList<>();
    if (ex.targetFormula != null)
      formulas.add(TableFormulaCanonicalizer.canonicalizeFormula(ex.targetFormula));
    if (ex.alternativeFormulas != null)
      for (Formula formula : ex.alternativeFormulas)
        formulas.add(TableFormulaCanonicalizer.canonicalizeFormula(formula));
    for (Derivation deriv : predDerivations) {
      for (Formula formula : formulas)
        if (formula.equals(TableFormulaCanonicalizer.canonicalizeFormula(deriv.formula))) return deriv;
    }
    return null;
  }

  public void summarize() {
    LogInfo.logs("N = %d | Annotated = %d | Oracle = %d", n, annotated, oracle);
    Execution.putOutput("train.oracle.mean", oracle * 1.0 / n);
    Execution.putOutput("train.correct.count", n);
    if (!opts.onlyCheckAnnotatedFormulas) {
      LogInfo.logs("No Beam = %d", noBeam);
      LogInfo.logs("Beam has correct formula = %d", beamHasCorrectFormula);
      LogInfo.logs("Beam doesn't have correct formula = %d", beamNoCorrectFormula);
      Execution.putOutput("train.correct.mean", beamHasCorrectFormula * 1.0 / n);
    }
    if (builder.executor instanceof LambdaDCSExecutor) {
      ((LambdaDCSExecutor) builder.executor).summarize();
    }
    StopWatchSet.logStats();
  }

}