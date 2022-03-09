package edu.stanford.nlp.sempre.tables;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.AggregateFormula.Mode;
import edu.stanford.nlp.sempre.tables.lambdadcs.LambdaDCSException;

public class TableDerivationPruningComputer extends DerivationPruningComputer {

  public TableDerivationPruningComputer(DerivationPruner pruner) {
    super(pruner);
  }

  public static final String lambdaDCSError = "lambdaDCSError";
  public static final String emptyDenotation = DefaultDerivationPruningComputer.emptyDenotation;
  public static final String badSummarizerHead = DefaultDerivationPruningComputer.badSummarizerHead;
  public static final String sameMark = "sameMark";
  public static final String forwardBackward = "forwardBackward";
  public static final String doubleNext = "doubleNext";
  public static final String doubleCompares = "doubleCompares";
  public static final String emptyJoin = "emptyJoin";
  public static final String subsetMerge = "subsetMerge";
  public static final String typeRowMerge = "typeRowMerge";
  public static final String aggregateInfinite = "aggregateInfinite";
  public static final String aggregateUncomparable = "aggregateUncomparable";
  public static final String aggregateVariable = "aggregateVariable";
  public static final String superlativeIdentity = "superlativeIdentity";

  @Override
  public Collection<String> getAllStrategyNames() {
    return Arrays.asList(
        lambdaDCSError, emptyDenotation, badSummarizerHead, sameMark,
        forwardBackward, doubleNext, doubleCompares, emptyJoin, subsetMerge, typeRowMerge,
        aggregateInfinite, aggregateUncomparable, aggregateVariable, superlativeIdentity);
  }

  private static final String NEXT = TableTypeSystem.ROW_NEXT_VALUE.id, PREV = "!" + NEXT;
  private static final ValueFormula<Value> STAR = new ValueFormula<>(new NameValue("*"));
  private final Formula TYPE_ROW = Formula.fromString("(fb:type.object.type fb:type.row)");
  private final Formula IDENTITY = Formula.fromString("(reverse (lambda x (var x)))");

  @Override
  public String isPruned(Derivation deriv) {
    // lambdaDCSError: Prune unrecoverable LambdaDCSException
    if (containsStrategy(lambdaDCSError)) {
      deriv.ensureExecuted(parser.executor, ex.context);
      if (deriv.value instanceof ErrorValue && LambdaDCSException.isUnrecoverable(((ErrorValue) deriv.value).type))
        return lambdaDCSError;
    }
    // emptyDenotation: Prune if the denotation is empty (for ScopedValue)
    if (containsStrategy(emptyDenotation)) {
      deriv.ensureExecuted(parser.executor, ex.context);
      if (deriv.value instanceof PairListValue && ((PairListValue) deriv.value).pairs.isEmpty()) {
        return emptyDenotation;
      }
      if (deriv.value instanceof ScopedValue) {
        Value head = ((ScopedValue) deriv.value).head, relation = ((ScopedValue) deriv.value).relation;
        if ((head instanceof ListValue && ((ListValue) head).values.isEmpty()) ||
            (relation instanceof PairListValue && ((PairListValue) relation).pairs.isEmpty()))
          return emptyDenotation;
      }
    }
    // badSummarizerHead: Prune if the head of a ScopedValue is empty or is a single object
    if (containsStrategy(badSummarizerHead)) {
      deriv.ensureExecuted(parser.executor, ex.context);
      if (deriv.value instanceof ScopedValue) {
        Value head = ((ScopedValue) deriv.value).head;
        if ((head instanceof ListValue) && ((ListValue) head).values.size() == 1)
          return badSummarizerHead;
      }
    }
    // sameMark: Prune if mark does not filter out anything
    if (containsStrategy(sameMark)) {
      if (deriv.formula instanceof MergeFormula) {
        MergeFormula merge = (MergeFormula) deriv.formula;
        if (merge.mode == MergeFormula.Mode.and) {
          Value head = null;
          if (merge.child1 instanceof MarkFormula) {
            head = parser.executor.execute(merge.child2, ex.context).value;
          } else if (merge.child2 instanceof MarkFormula) {
            head = parser.executor.execute(merge.child1, ex.context).value;
          }
          if (head != null && head.equals(deriv.value)) {
            return sameMark;
          }
        }
      }
    }
    // Prune JoinFormulas
    if (containsStrategy(forwardBackward) || containsStrategy(doubleNext)
        || containsStrategy(doubleCompares) || containsStrategy(emptyJoin)) {
      Formula current = deriv.formula;
      String rid1 = null, rid2 = null;
      while (current instanceof JoinFormula) {
        rid2 = rid1;
        rid1 = Formulas.getBinaryId(((JoinFormula) current).relation);
        if (rid1 != null && rid2 != null) {
          // forwardBackward: Prune (!relation (relation (...)))
          if (containsStrategy(forwardBackward) && (rid1.equals("!" + rid2) || rid2.equals("!" + rid1)))
            return forwardBackward;
          // doubleNext: Prune (next (next (...)))
          if (containsStrategy(doubleNext) &&
              (rid1.equals(NEXT) || rid1.equals(PREV)) && (rid2.equals(NEXT) || rid2.equals(PREV)))
            return doubleNext;
          // doubleCompares: Prune (< (> ( ...)))
          if (containsStrategy(doubleCompares) &&
              CanonicalNames.COMPARATORS.contains(rid1) && CanonicalNames.COMPARATORS.contains(rid2))
            return doubleCompares;
          // emptyJoin: prune if the composition two consecutive joins always produce an empty set
          if (containsStrategy(emptyJoin)) {
            Formula test = new JoinFormula(rid1, new JoinFormula(rid2, STAR));
            try {
              Value value = parser.executor.execute(test, ex.context).value;
              if (value instanceof ListValue && ((ListValue) value).values.isEmpty())
                return emptyJoin;
            } catch (RuntimeException e) {
              // Do nothing. Don't prune the formula.
            }
          }
        }
        current = ((JoinFormula) current).child;
      }
    }
    // Prune merge formulas
    if (containsStrategy(subsetMerge) && deriv.formula instanceof MergeFormula) {
      MergeFormula merge = (MergeFormula) deriv.formula;
      Formula child1 = merge.child1, child2 = merge.child2;
      // subsetMerge: Prune merge formulas where one child is a subset of the other
      if (containsStrategy(subsetMerge)) {
        Value d1 = parser.executor.execute(child1, ex.context).value;
        Value d2 = parser.executor.execute(child2, ex.context).value;
        if (d1 instanceof ListValue && d2 instanceof ListValue) {
          Set<Value> v1 = new HashSet<>(((ListValue) d1).values);
          Set<Value> v2 = new HashSet<>(((ListValue) d2).values);
          if ((v1.size() >= v2.size() && v1.containsAll(v2)) ||
              (v2.size() > v1.size() && v2.containsAll(v1)))
            return subsetMerge;
        }
      }
      // typeRowMerge: Prune merge formulas where one child is (@type @row) [generally redundant]
      if (containsStrategy(typeRowMerge) &&
          ((TYPE_ROW.equals(merge.child1) && !(merge.child2 instanceof MarkFormula)) ||
              (TYPE_ROW.equals(merge.child2) && !(merge.child1 instanceof MarkFormula))))
        return typeRowMerge;
    }
    // Prune aggregate formulas
    else if (deriv.formula instanceof AggregateFormula) {
      AggregateFormula aggregate = (AggregateFormula) deriv.formula;
      Formula child = aggregate.child;
      // aggregateInfinite: Prune aggregates when the child is an infinite set
      if (containsStrategy(aggregateInfinite) && child instanceof JoinFormula) {
        String rid = Formulas.getBinaryId(((JoinFormula) child).relation);
        if (CanonicalNames.COMPARATORS.contains(rid) || "!=".equals(rid))
          return aggregateInfinite;
      }
      // aggregateUncomparable: Prune aggregates when the child's type is not number or date
      if (containsStrategy(aggregateUncomparable) && aggregate.mode != AggregateFormula.Mode.count) {
        SemType type = TypeInference.inferType(child, true);
        if (!type.meet(SemType.numberOrDateType).isValid() ||
            (!type.meet(SemType.numberType).isValid() && (aggregate.mode == Mode.sum || aggregate.mode == Mode.avg)))
          return aggregateUncomparable;
      }
      // aggregateVariable: Prune aggregates when the child is variable
      if (containsStrategy(aggregateVariable) && child instanceof VariableFormula) {
        return aggregateVariable;
      }
    }
    // Prune superlative formulas
    else if (deriv.formula instanceof SuperlativeFormula) {
      SuperlativeFormula superlative = (SuperlativeFormula) deriv.formula;
      Formula relation = superlative.relation;
      // superlativeIdentity: Prune superlatives when the relation is exactly (lambda x (var x))
      if (containsStrategy(superlativeIdentity) && IDENTITY.equals(relation)) {
        return superlativeIdentity;
      }
    }
    // For ScopedFormula: recurse into the relation part
    if (deriv.formula instanceof ScopedFormula) {
      Formula relation = ((ScopedFormula) deriv.formula).relation;
      if (relation instanceof LambdaFormula) {
        relation = ((LambdaFormula) relation).body;
        Derivation relationDeriv = new Derivation.Builder().formula(relation).createDerivation();
        String matchedStrategy;
        for (DerivationPruningComputer computer : pruner.getPruningComputers()) {
          if ((matchedStrategy = computer.isPruned(relationDeriv)) != null) {
            return matchedStrategy;
          }
        }
      }
    }
    return null;
  }

}
