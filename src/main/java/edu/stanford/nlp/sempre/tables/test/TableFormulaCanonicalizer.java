package edu.stanford.nlp.sempre.tables.test;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.TableTypeSystem;
import fig.basic.LogInfo;

public class TableFormulaCanonicalizer {
  private TableFormulaCanonicalizer() {}

  public static Formula canonicalizeFormula(Formula formula) {
    return canonicalizePredicates(Formulas.betaReduction(formula));
  }

  // Canonicalize the following:
  // * !___ => (reverse ___) except !=
  // * (cell.cell.date (date ___ -1 -1)) => (cell.cell.number (number ___))
  // * variable names => x
  // * (lambda x (relation (var x))) => relation
  // * (lambda x ((reverse relation) (var x))) => (reverse relation)
  // * (reverse (lambda x (relation (var x)))) => (reverse relation)
  // * (reverse (lambda x ((reverse relation) (var x))) => relation
  // * sort the children of merge formulas
  static Formula canonicalizePredicates(Formula formula) {
    if (formula instanceof ValueFormula) {
      ValueFormula<?> valueF = (ValueFormula<?>) formula;
      if (valueF.value instanceof NameValue) {
        String id = ((NameValue) valueF.value).id;
        if (id.startsWith("!") && !"!=".equals(id)) {
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
      if (child1.toString().compareTo(child2.toString()) <= 0)
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
      Formula singleRelation;
      if ((singleRelation = isSingleRelationLambda(formula)) != null)
        return singleRelation;
      ReverseFormula reverse = (ReverseFormula) formula;
      return new ReverseFormula(canonicalizeFormula(reverse.child));
    } else if (formula instanceof LambdaFormula) {
      Formula singleRelation;
      if ((singleRelation = isSingleRelationLambda(formula)) != null)
        return singleRelation;
      LambdaFormula lambda = (LambdaFormula) formula;
      return new LambdaFormula("x", canonicalizeFormula(lambda.body));
    } else {
      throw new RuntimeException("Unsupported formula " + formula);
    }
  }

  // Detect the following patterns
  // * (lambda x (relation (var x))) => relation
  // * (lambda x (!relation (var x))) => (reverse relation)
  // * (lambda x ((reverse relation) (var x))) => (reverse relation)
  // * reverse of any case above
  // Otherwise, return null
  static Formula isSingleRelationLambda(Formula formula) {
    boolean isReversed = false;
    ValueFormula<?> valueF;
    NameValue relation;
    // Outer layer
    if (formula instanceof ReverseFormula) {
      isReversed = !isReversed;
      formula = ((ReverseFormula) formula).child;
    }
    if (!(formula instanceof LambdaFormula)) return null;
    formula = ((LambdaFormula) formula).body;
    if (!(formula instanceof JoinFormula)) return null;
    JoinFormula join = (JoinFormula) formula;
    if (!(join.child instanceof VariableFormula)) return null;
    // Detect relation
    if (join.relation instanceof ValueFormula) {
      valueF = (ValueFormula<?>) join.relation;
    } else if (join.relation instanceof ReverseFormula) {
      ReverseFormula reverse = (ReverseFormula) join.relation;
      if (!(reverse.child instanceof ValueFormula)) return null;
      isReversed = !isReversed;
      valueF = (ValueFormula<?>) reverse.child;
    } else {
      return null;
    }
    if (!(valueF.value instanceof NameValue)) return null;
    relation = (NameValue) valueF.value;
    if (!CanonicalNames.isBinary(relation)) return null;
    if (CanonicalNames.isReverseProperty(relation)) {
      isReversed = !isReversed;
      relation = CanonicalNames.reverseProperty(relation);
    }
    // Return the answer
    if (isReversed) {
      return new ReverseFormula(new ValueFormula<NameValue>(relation));
    } else {
      return new ValueFormula<NameValue>(relation);
    }
  }

  // ============================================================
  // Test
  // ============================================================

  public static void main(String[] args) {
    LogInfo.logs("%s", isSingleRelationLambda(Formula.fromString("(lambda x (fb:a.b.c (var x)))")));
    LogInfo.logs("%s", isSingleRelationLambda(Formula.fromString("(lambda x (!fb:a.b.c (var x)))")));
    LogInfo.logs("%s", isSingleRelationLambda(Formula.fromString("(lambda x ((reverse fb:a.b.c) (var x)))")));
    LogInfo.logs("%s", isSingleRelationLambda(Formula.fromString("(lambda x ((reverse fb:a.b.c) (fb:d.e.f (var x))))")));
    LogInfo.logs("%s", isSingleRelationLambda(Formula.fromString("(lambda x (!= (var x)))")));
    LogInfo.logs("%s", isSingleRelationLambda(Formula.fromString("(reverse (lambda x (fb:a.b.c (var x))))")));
    LogInfo.logs("%s", isSingleRelationLambda(Formula.fromString("(reverse (lambda x (!fb:a.b.c (var x))))")));
    LogInfo.logs("%s", isSingleRelationLambda(Formula.fromString("(reverse (lambda x ((reverse fb:a.b.c) (var x))))")));
  }
}
