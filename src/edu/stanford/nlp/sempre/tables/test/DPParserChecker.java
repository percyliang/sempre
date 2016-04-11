package edu.stanford.nlp.sempre.tables.test;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.TableKnowledgeGraph;
import edu.stanford.nlp.sempre.tables.TableTypeSystem;
import edu.stanford.nlp.sempre.tables.lambdadcs.LambdaDCSExecutor;
import edu.stanford.nlp.sempre.tables.test.CustomExample.ExampleProcessor;
import fig.basic.*;
import fig.exec.Execution;

/**
 * Check 2 things:
 * - Whether the annotated formula actually executes to the correct denotation.
 * - Whether the formula is in the final beam of DPParser.
 *
 * @author ppasupat
 */
public class DPParserChecker implements Runnable {
  public static class Options {
    @Option(gloss = "Only check annotated formulas (Don't check DPParser beam)")
    public boolean onlyCheckAnnotatedFormulas = false;
  }
  public static Options opts = new Options();

  public static void main(String[] args) {
    Execution.run(args, "DPParserCheckerMain", new DPParserChecker(), Master.getOptionsParser());
  }

  @Override
  public void run() {
    DPParserCheckerProcessor processor = new DPParserCheckerProcessor();
    CustomExample.getDataset(Dataset.opts.inPaths, processor);
    processor.summarize();
  }

  static class DPParserCheckerProcessor implements ExampleProcessor {
    int n = 0, annotated = 0, oracle = 0, beamHasCorrectFormula = 0, beamNoCorrectFormula = 0, noBeam = 0;
    final Builder builder;
    final PrintWriter eventsOut;

    public DPParserCheckerProcessor() {
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
        } else if (ex.targetFormula != null && isCorrectFormulaOnBeam(ex, state.predDerivations)) {
          beamHasCorrectFormula++;
          beamFlag = "yes";
        } else {
          beamNoCorrectFormula++;
          beamFlag = "reach";
        }
        LogInfo.logs("RESULT: %s %s %s", ex.id, formulaFlag, beamFlag);
        eventsOut.printf("%s\t%s\t%s\n", ex.id.replaceAll("nt-", ""), formulaFlag, beamFlag);
        eventsOut.flush();
      }
    }

    // See if the annotated formula is correct
    boolean isAnnotatedFormulaCorrect(CustomExample ex) {
      LogInfo.begin_track("isAnnotatedFormulaCorrect: Example %s", ex.id);
      StopWatch watch = new StopWatch();
      watch.start();
      LogInfo.logs("TRUE: %s", ex.targetValue);
      double result = 0;
      try {
        LogInfo.logs("Inferred Type: %s", TypeInference.inferType(ex.targetFormula));
        Value pred = builder.executor.execute(ex.targetFormula, ex.context).value;
        if (pred instanceof ListValue)
          pred = addOriginalStrings((ListValue) pred, (TableKnowledgeGraph) ex.context.graph);
        LogInfo.logs("Example %s: %s", ex.id, ex.getTokens());
        LogInfo.logs("  targetFormula: %s", ex.targetFormula);
        LogInfo.logs("TRUE: %s", ex.targetValue);
        LogInfo.logs("PRED: %s", pred);
        result = builder.valueEvaluator.getCompatibility(ex.targetValue, pred);
        if (result != 1) {
          LogInfo.warnings("TRUE != PRED. %s Either targetValue or targetFormula is wrong.", ex.id);
        }
      } catch (Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        LogInfo.logs("Example %s: %s", ex.id, ex.getTokens());
        LogInfo.logs("  targetFormula: %s", ex.targetFormula);
        LogInfo.logs("TRUE: %s", ex.targetValue);
        LogInfo.logs("PRED: ERROR %s\n%s", e, sw);
        LogInfo.warnings("TRUE != PRED. %s Something was wrong during the execution.", ex.id);
      }
      watch.stop();
      LogInfo.logs("Parse Time: %s", watch);
      LogInfo.end_track();
      return result == 1;
    }

    boolean isCorrectFormulaOnBeam(CustomExample ex, List<Derivation> predDerivations) {
      Formula target = canonicalizeFormula(Formulas.betaReduction(ex.targetFormula));
      for (Derivation deriv : predDerivations) {
        if (target.equals(canonicalizeFormula(Formulas.betaReduction(deriv.formula)))) return true;
      }
      return false;
    }

    // Canonicalize the following:
    // * !___ => (reverse ___)
    // * (cell.cell.date (date ___ -1 -1)) => (cell.cell.number (number ___))
    // * (cell.cell.first (number ___)) => (cell.cell.number (number ___))
    // * variable names => x
    Formula canonicalizeFormula(Formula formula) {
      if (formula instanceof ValueFormula) {
        ValueFormula<?> valueF = (ValueFormula<?>) formula;
        if (valueF.value instanceof NameValue) {
          String id = ((NameValue) valueF.value).id;
          if (id.startsWith("!")) {
            return new ReverseFormula(new ValueFormula<Value>(new NameValue(id.substring(1))));
          } else {
            return new ValueFormula<Value>(new NameValue(id));
          }
        }
        return valueF;
      } else if (formula instanceof JoinFormula) {
        JoinFormula join = (JoinFormula) formula;
        if (join.relation instanceof ValueFormula && join.child instanceof ValueFormula) {
          Value relation = ((ValueFormula<?>) join.relation).value,
              child = ((ValueFormula<?>) join.child).value;
          if (relation.equals(TableTypeSystem.CELL_DATE_VALUE) && child instanceof DateValue) {
            DateValue date = (DateValue) (((ValueFormula<?>) join.child).value);
            if (date.month == -1 && date.day == -1) {
              return new JoinFormula(new ValueFormula<Value>(TableTypeSystem.CELL_NUMBER_VALUE),
                  new ValueFormula<Value>(new NumberValue(date.year)));
            }
          }
        }
        return new JoinFormula(canonicalizeFormula(join.relation),
            canonicalizeFormula(join.child));
      } else if (formula instanceof MergeFormula) {
        MergeFormula merge = (MergeFormula) formula;
        Formula child1 = canonicalizeFormula(merge.child1),
            child2 = canonicalizeFormula(merge.child2);
        if (child1.hashCode() < child2.hashCode())
          return new MergeFormula(merge.mode, child1, child2);
        else
          return new MergeFormula(merge.mode, child2, child1);
      } else if (formula instanceof AggregateFormula) {
        AggregateFormula aggregate = (AggregateFormula) formula;
        return new AggregateFormula(aggregate.mode, canonicalizeFormula(aggregate.child));
      } else if (formula instanceof SuperlativeFormula) {
        SuperlativeFormula superlative = (SuperlativeFormula) formula;
        return new SuperlativeFormula(superlative.mode, superlative.rank, superlative.count,
            canonicalizeFormula(superlative.head), canonicalizeFormula(superlative.relation));
      } else if (formula instanceof ArithmeticFormula) {
        ArithmeticFormula arithmetic = (ArithmeticFormula) formula;
        return new ArithmeticFormula(arithmetic.mode, canonicalizeFormula(arithmetic.child1),
            canonicalizeFormula(arithmetic.child2));
      } else if (formula instanceof VariableFormula) {
        return new VariableFormula("x");
      } else if (formula instanceof MarkFormula) {
        MarkFormula mark = (MarkFormula) formula;
        return new MarkFormula("x", canonicalizeFormula(mark.body));
      } else if (formula instanceof ReverseFormula) {
        ReverseFormula reverse = (ReverseFormula) formula;
        return new ReverseFormula(canonicalizeFormula(reverse.child));
      } else if (formula instanceof LambdaFormula) {
        LambdaFormula lambda = (LambdaFormula) formula;
        return new LambdaFormula("x", canonicalizeFormula(lambda.body));
      } else {
        throw new RuntimeException("Unsupported formula " + formula);
      }
    }

    // Add original strings to each NameValue in ListValue
    ListValue addOriginalStrings(ListValue answers, TableKnowledgeGraph graph) {
      List<Value> values = new ArrayList<>();
      for (Value value : answers.values) {
        if (value instanceof NameValue) {
          NameValue name = (NameValue) value;
          if (name.description == null)
            value = new NameValue(name.id, graph.getOriginalString(((NameValue) value).id));
        }
        values.add(value);
      }
      return new ListValue(values);
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

}
