package edu.stanford.nlp.sempre.tables.lambdadcs;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.lambdadcs.LambdaDCSException.Type;
import fig.basic.*;

/**
 * Execute a Formula on the given KnowledgeGraph instance.
 *
 * @author ppasupat
 */
public class LambdaDCSExecutor extends Executor {
  public static class Options {
    @Option(gloss = "Verbosity") public int verbose = 0;
    @Option(gloss = "If the result is empty, return an ErrorValue instead of an empty ListValue")
    public boolean failOnEmptyLists = false;
    @Option(gloss = "Return all ties on (argmax 1 1 ...) and (argmin 1 1 ...)")
    public boolean superlativesReturnAllTopTies = true;
    @Option(gloss = "Aggregates (sum, avg, max, min) throw an error on empty lists")
    public boolean aggregatesFailOnEmptyLists = false;
    @Option(gloss = "Superlatives (argmax, argmin) throw an error on empty lists")
    public boolean superlativesFailOnEmptyLists = false;
    @Option(gloss = "Arithmetics (+, -, *, /) throw an error when both operants have > 1 values")
    public boolean arithmeticsFailOnMultipleElements = false;
    @Option(gloss = "Use caching")
    public boolean useCache = true;
  }
  public static Options opts = new Options();

  public final Evaluation stats = new Evaluation();

  @Override
  public Response execute(Formula formula, ContextValue context) {
    LambdaDCSCoreLogic logic;
    if (opts.verbose < 3) {
      logic = new LambdaDCSCoreLogic(context, stats);
    } else {
      logic = new LambdaDCSCoreLogicWithVerbosity(context, stats);
    }
    StopWatch stopWatch = new StopWatch();
    stopWatch.start();
    Value answer = logic.execute(formula);
    stopWatch.stop();
    stats.addCumulative("execTime", stopWatch.ms);
    if (stopWatch.ms >= 10 && opts.verbose >= 1)
      LogInfo.logs("long time: %s %s", Formulas.betaReduction(formula), answer);
    /*///////// DEBUG! //////////
    if (answer instanceof ErrorValue) {
      if (!((ErrorValue) answer).type.startsWith("notUnary"))
        LogInfo.logs("%s %s", Formulas.betaReduction(formula), answer);
    }
    ////////// END DEBUG /////////*/
    return new Response(answer);
  }

  public void summarize() {
    LogInfo.begin_track("LambdaDCSExecutor: summarize");
    stats.logStats("LambdaDCSExecutor");
    LogInfo.end_track();
  }
}

// ============================================================
// Execution
// ============================================================

/**
 * Main logic of Lambda DCS Executor.
 *
 * Find the denotation of a formula (logical form) with respect to the given knowledge graph.
 *
 * Assume that the denotation is either a unary or a binary,
 * and the final denotation is a unary.
 *
 * Both unaries and binaries are lists (not sets).
 * However, the following formula types will treat them as sets:
 * - and, or
 * - count (= count the number of distinct values)
 *
 * Note that (and (!weight (@type @row)) (@p.num (> (number 90)))) may give a wrong answer,
 * but it can be rewritten as (!weight (and (@type @row) (weight (@p.num (> (number 90))))))
 *
 * @author ppasupat
 */
class LambdaDCSCoreLogic {

  // Note: STAR does not work well with type checking
  static final NameValue STAR = new NameValue("*");

  final KnowledgeGraph graph;
  final Evaluation stats;

  public LambdaDCSCoreLogic(ContextValue context, Evaluation stats) {
    graph = context.graph;
    this.stats = stats;
    if (graph == null)
      throw new RuntimeException("Cannot call LambdaDCSExecutor when context graph is null");
  }

  public Value execute(Formula formula) {
    Formula f = Formulas.betaReduction(formula);
    if (LambdaDCSExecutor.opts.verbose >= 2)
      LogInfo.logs("%s", f);
    try {
      UnaryDenotation denotation = computeUnary(f, TypeHint.UNRESTRICTED_UNARY);
      if (LambdaDCSExecutor.opts.useCache) {
        ExecutorCache.singleton.put(graph, f, denotation);
      }
      ListValue answer = denotation.toListValue(graph);
      if (answer.values.isEmpty()) {
        if (LambdaDCSExecutor.opts.failOnEmptyLists)
          return ErrorValue.empty;
      }
      return answer;
    } catch (final LambdaDCSException e) {
      return new ErrorValue(e.toString());
    }
  }

  public UnaryDenotation computeUnary(Formula formula, UnaryTypeHint typeHint) {
    assert typeHint != null;
    if (formula instanceof LambdaFormula) {
      throw new LambdaDCSException(Type.notUnary, "[Unary] Not a unary " + formula);
    }

    if (LambdaDCSExecutor.opts.useCache) {
      Object object = ExecutorCache.singleton.get(graph, formula);
      if (object != null && object instanceof UnaryDenotation) {
        stats.addCumulative("cacheHit", true);
        return (UnaryDenotation) object;
      } else {
        stats.addCumulative("cacheHit", false);
      }
    }

    if (formula instanceof ValueFormula) {
      // ============================================================
      // ValueFormula
      // ============================================================
      Value value = ((ValueFormula<?>) formula).value;
      if (value instanceof BooleanValue || value instanceof NumberValue ||
          value instanceof StringValue || value instanceof DateValue || value instanceof NameValue) {
        // Special case: *
        if (STAR.equals(value)) return InfiniteUnaryDenotation.STAR_UNARY;
        return typeHint.applyBound(new ExplicitUnaryDenotation(value));
      }

    } else if (formula instanceof JoinFormula) {
      // ============================================================
      // JoinFormula
      // ============================================================
      JoinFormula join = (JoinFormula) formula;
      try {
        // Compute unary, then join binary
        UnaryDenotation childD = computeUnary(join.child, typeHint.newUnrestrictedUnary());
        BinaryDenotation relationD = computeBinary(join.relation, typeHint.asFirstOfBinaryWithSecond(childD));
        return typeHint.applyBound(relationD.joinSecond(childD, graph));
      } catch (LambdaDCSException e1) {
        try {
          // Compute binary, then join unary
          BinaryDenotation relationD = computeBinary(join.relation, typeHint.asFirstOfBinary());
          UnaryDenotation childUpperBound = relationD.joinFirst(typeHint.upperBound, graph);
          UnaryDenotation childD = computeUnary(join.child, typeHint.newRestrictedUnary(childUpperBound));
          return typeHint.applyBound(relationD.joinSecond(childD, graph));
        } catch (LambdaDCSException e2) {
          throw new LambdaDCSException(Type.unknown, "Cannot join | %s | %s", e1, e2);
        }
      }

    } else if (formula instanceof MergeFormula) {
      // ============================================================
      // Merge
      // ============================================================
      MergeFormula merge = (MergeFormula) formula;
      try {
        UnaryDenotation child1D = computeUnary(merge.child1, typeHint);
        UnaryDenotation child2D = computeUnary(merge.child2,
            merge.mode == MergeFormula.Mode.and ? typeHint.restrict(child1D) : typeHint);
        return typeHint.applyBound(child1D.merge(child2D, merge.mode));
      } catch (LambdaDCSException e1) {
        try {
          UnaryDenotation child2D = computeUnary(merge.child2, typeHint);
          UnaryDenotation child1D = computeUnary(merge.child1,
              merge.mode == MergeFormula.Mode.and ? typeHint.restrict(child2D) : typeHint);
          return typeHint.applyBound(child2D.merge(child1D, merge.mode));
        } catch (LambdaDCSException e2) {
          throw new LambdaDCSException(Type.unknown, "Cannot merge | %s | %s", e1, e2);
        }
      }

    } else if (formula instanceof NotFormula) {
      // ============================================================
      // Not
      // ============================================================
      // TODO(ice): (Low priority)

    } else if (formula instanceof AggregateFormula) {
      // ============================================================
      // Aggregate
      // ============================================================
      AggregateFormula aggregate = (AggregateFormula) formula;
      UnaryDenotation childD = computeUnary(aggregate.child, typeHint.newUnrestrictedUnary());
      return typeHint.applyBound(childD.aggregate(aggregate.mode));

    } else if (formula instanceof SuperlativeFormula) {
      // ============================================================
      // Superlative
      // ============================================================
      SuperlativeFormula superlative = (SuperlativeFormula) formula;
      int rank = DenotationUtils.getSinglePositiveInteger(computeUnary(superlative.rank, typeHint.newUnrestrictedUnary()));
      int count = DenotationUtils.getSinglePositiveInteger(computeUnary(superlative.count, typeHint.newUnrestrictedUnary()));
      UnaryDenotation headD = computeUnary(superlative.head, typeHint);
      BinaryDenotation relationD = computeBinary(superlative.relation, typeHint.newRestrictedBinary(headD, null));
      ExplicitBinaryDenotation table = relationD.explicitlyFilterFirst(headD, graph);
      return typeHint.applyBound(DenotationUtils.superlative(rank, count, table, superlative.mode));

    } else if (formula instanceof ArithmeticFormula) {
      // ============================================================
      // Arithmetic
      // ============================================================
      ArithmeticFormula arithmetic = (ArithmeticFormula) formula;
      UnaryDenotation child1D = computeUnary(arithmetic.child1, typeHint.newUnrestrictedUnary());
      UnaryDenotation child2D = computeUnary(arithmetic.child2, typeHint.newUnrestrictedUnary());
      return typeHint.applyBound(DenotationUtils.arithmetic(child1D, child2D, arithmetic.mode));

    } else if (formula instanceof CallFormula) {
      // ============================================================
      // Call
      // ============================================================
      // TODO(ice): (Low priority)

    } else if (formula instanceof VariableFormula) {
      // ============================================================
      // Variable
      // ============================================================
      VariableFormula variable = (VariableFormula) formula;
      Value value = typeHint.get(variable.name);
      return typeHint.applyBound(new ExplicitUnaryDenotation(value));

    } else if (formula instanceof MarkFormula) {
      // ============================================================
      // Mark
      // ============================================================
      MarkFormula mark = (MarkFormula) formula;
      String var = mark.var;
      // Assuming that the type hint has enough information ...
      List<Value> values = new ArrayList<>();
      for (Value varValue : typeHint.upperBound) {
        UnaryDenotation results = computeUnary(mark.body, typeHint.withVar(var, varValue));
        if (results.contains(varValue)) {
          values.add(varValue);
        }
      }
      return typeHint.applyBound(new ExplicitUnaryDenotation(values));

    } else {
      throw new LambdaDCSException(Type.notUnary, "[Unary] Not a unary " + formula);
    }

    // Catch-all error
    throw new LambdaDCSException(Type.unknown, "[Unary] Cannot handle formula " + formula);
  }

  public BinaryDenotation computeBinary(Formula formula, BinaryTypeHint typeHint) {
    assert typeHint != null;
    if (formula instanceof ValueFormula) {
      // ============================================================
      // ValueFormula
      // ============================================================
      Value value = ((ValueFormula<?>) formula).value;
      if (SpecialBinaryDenotation.isSpecial(value))
        return SpecialBinaryDenotation.create(value);
      if (value instanceof BooleanValue || value instanceof NumberValue ||
          value instanceof StringValue || value instanceof DateValue ||
          value instanceof UriValue || value instanceof NameValue ||
          value instanceof ListValue) {
        if (!STAR.equals(value))
          return new PredicateBinaryDenotation(value);
      }

    } else if (formula instanceof ReverseFormula) {
      // ============================================================
      // Reverse
      // ============================================================
      ReverseFormula reverse = (ReverseFormula) formula;
      BinaryDenotation childD = computeBinary(reverse.child, typeHint.reverse());
      return childD.reverse();

    } else if (formula instanceof LambdaFormula) {
      // ============================================================
      // Lambda
      // ============================================================
      // Note: The variable's values become the SECOND argument of the binary pairs
      LambdaFormula lambda = (LambdaFormula) formula;
      String var = lambda.var;
      // Assuming that the type hint has enough information ...
      try {
        List<Pair<Value, Value>> pairs = new ArrayList<>();
        for (Value varValue : typeHint.secondUpperBound) {
          UnaryDenotation results = computeUnary(lambda.body, typeHint.first().withVar(var, varValue));
          for (Value result : results) {
            pairs.add(new Pair<>(result, varValue));
          }
        }
        return new ExplicitBinaryDenotation(pairs);
      } catch (UnsupportedOperationException e) {
        // The type hint does not have enough information. Probably a floating lambda.
        // Just throw an Exception.
      }

    } else {
      throw new LambdaDCSException(Type.notBinary, "[Unary] Not a binary " + formula);
    }

    // Catch-all error
    throw new LambdaDCSException(Type.unknown, "[Binary] Cannot handle formula " + formula);
  }

}

// ============================================================
// Debug Print
// ============================================================

class LambdaDCSCoreLogicWithVerbosity extends LambdaDCSCoreLogic {

  public LambdaDCSCoreLogicWithVerbosity(ContextValue context, Evaluation stats) {
    super(context, stats);
  }

  @Override
  public UnaryDenotation computeUnary(Formula formula, UnaryTypeHint typeHint) {
    LogInfo.begin_track("UNARY %s [%s]", formula, typeHint);
    try {
      UnaryDenotation denotation = super.computeUnary(formula, typeHint);
      LogInfo.logs("%s", denotation);
      LogInfo.end_track();
      return denotation;
    } catch (Exception e) {
      LogInfo.end_track();
      throw e;
    }
  }

  @Override
  public BinaryDenotation computeBinary(Formula formula, BinaryTypeHint typeHint) {
    LogInfo.begin_track("BINARY %s [%s]", formula, typeHint);
    try {
      BinaryDenotation denotation = super.computeBinary(formula, typeHint);
      LogInfo.logs("%s", denotation);
      LogInfo.end_track();
      return denotation;
    } catch (Exception e) {
      LogInfo.end_track();
      throw e;
    }
  }

}
