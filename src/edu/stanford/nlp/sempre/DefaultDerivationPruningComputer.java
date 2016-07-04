package edu.stanford.nlp.sempre;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import fig.basic.LogInfo;

/**
 * Common pruning strategies that can be used in many semantic parsing tasks.
 *
 * @author ppasupat
 */
public class DefaultDerivationPruningComputer extends DerivationPruningComputer {

  public DefaultDerivationPruningComputer(DerivationPruner pruner) {
    super(pruner);
  }

  public static final String atomic = "atomic";
  public static final String emptyDenotation = "emptyDenotation";
  public static final String nonLambdaError = "nonLambdaError";
  public static final String tooManyValues = "tooManyValues";
  public static final String doubleSummarizers = "doubleSummarizers";
  public static final String sameMerge = "sameMerge";
  public static final String mistypedMerge = "mistypedMerge";
  public static final String unsortedMerge = "unsortedMerge";
  public static final String badSummarizerHead = "badSummarizerHead";

  @Override
  public Collection<String> getAllStrategyNames() {
    return Arrays.asList(
        atomic,
        emptyDenotation, nonLambdaError, tooManyValues,
        doubleSummarizers, sameMerge, mistypedMerge, unsortedMerge, badSummarizerHead);
  }

  // ============================================================
  // Formula-based pruning
  // ============================================================

  @Override
  public String isPrunedWithoutExecution(Derivation deriv) {
    // atomic: Prune atomic formula at root.
    //   e.g., Prevent "Who was taller, Lincoln or Obama" --> fb:en.lincoln generated from lexicon without any computation   
    if (containsStrategy(atomic)) {
      if (deriv.isRoot(pruner.ex.numTokens()) && deriv.formula instanceof ValueFormula)
        return atomic;
    }
    return null;
  }

  // ============================================================
  // Denotation-based Pruning
  // ============================================================

  @Override
  public String isPrunedGeneral(Derivation deriv) {
    // emptyDenotation: Prune if the denotation is empty
    if (containsStrategy(emptyDenotation)) {
      if (deriv.value instanceof ListValue && ((ListValue) deriv.value).values.isEmpty())
        return emptyDenotation;
    }
    // nonLambdaError: Prune if the denotation is an error and the formula is not a partial formula
    if (containsStrategy(nonLambdaError) && !isLambdaFormula(deriv.formula)) {
      if (deriv.value instanceof ErrorValue) {
        if (DerivationPruner.opts.pruningVerbosity >= 5)
          LogInfo.logs("NonLambdaError: %s => %s", deriv.formula, deriv.value);
        return nonLambdaError;
      }
    }
    // tooManyValues: Prune if the denotation has too many values (at $ROOT only)
    if (containsStrategy(tooManyValues) && deriv.isRoot(pruner.ex.numTokens())) {
      if (!(deriv.value instanceof ListValue) ||
          ((ListValue) deriv.value).values.size() > DerivationPruner.opts.maxNumValues)
        return tooManyValues;
    }
    return null;
  }

  // Helper function: return true if the result is clearly a binary
  private boolean isLambdaFormula(Formula formula) {
    if (formula instanceof LambdaFormula) return true;
    if (formula instanceof ValueFormula &&
        CanonicalNames.isBinary(((ValueFormula<?>) formula).value)) return true;
    return false;
  }

  // ============================================================
  // Recursively prune formulas
  // ============================================================

  @Override
  public String isPrunedRecursive(Derivation deriv, Formula subformula, Map<String, Object> state) {
    // doubleSummarizers: Prune when two summarizers (aggregate or superlative) are directly nested
    // e.g., in (sum (avg ...)) and (min (argmax ...)), the outer operation is redundant
    if (containsStrategy(doubleSummarizers)) {
      Formula innerFormula = null;
      if (subformula instanceof SuperlativeFormula)
        innerFormula = ((SuperlativeFormula) subformula).head;
      else if (subformula instanceof AggregateFormula)
        innerFormula = ((AggregateFormula) subformula).child;
      if (innerFormula != null &&
          (innerFormula instanceof SuperlativeFormula || innerFormula instanceof AggregateFormula))
        return doubleSummarizers;
    }
    // sameMerge: Prune merge formulas with two identical children
    if (containsStrategy(sameMerge) && subformula instanceof MergeFormula) {
      MergeFormula merge = (MergeFormula) subformula;
      if (merge.child1.equals(merge.child2))
        return sameMerge;
    }
    // mistypedMerge: Prune merge formulas with children of different types
    if (containsStrategy(mistypedMerge) && subformula instanceof MergeFormula) {
      MergeFormula merge = (MergeFormula) subformula;
      SemType type1 = TypeInference.inferType(merge.child1, true);
      SemType type2 = TypeInference.inferType(merge.child2, true);
      if (!type1.meet(type2).isValid())
        return mistypedMerge;
    }
    // unsortedMerge: Prune merge formulas where the children's string forms are not lexicographically sorted.
    //   Will remove redundant (and Y X) when (and Y X) is already present.
    if (containsStrategy(unsortedMerge) && subformula instanceof MergeFormula) {
      MergeFormula merge = (MergeFormula) subformula;
      String child1 = merge.child1.toString(), child2 = merge.child2.toString();
      if (child1.compareTo(child2) >= 0)
        return unsortedMerge;
    }
    // badSummarizerHead: Prune if the head of a superlative or a non-count aggregate
    // is empty or is a single object
    if (containsStrategy(badSummarizerHead) && DerivationPruner.opts.ensureExecuted) {
      Formula innerFormula = null;
      boolean isCount = false;
      if (subformula instanceof SuperlativeFormula)
        innerFormula = ((SuperlativeFormula) subformula).head;
      else if (subformula instanceof AggregateFormula) {
        innerFormula = ((AggregateFormula) subformula).child;
        if (((AggregateFormula) subformula).mode == AggregateFormula.Mode.count)
          isCount = true;
      }
      if (innerFormula != null) {
        try {
          TypeInference.inferType(innerFormula);
          Value innerValue = pruner.parser.executor.execute(innerFormula, pruner.ex.context).value;
          if (innerValue instanceof ListValue) {
            int size = ((ListValue) innerValue).values.size();
            if (size == 0 || (size == 1 && !(DerivationPruner.opts.allowCountOne && isCount)))
              return badSummarizerHead;
          }
        } catch (Exception e) {
          // TypeInference fails; probably because of free variables. No need to do anything.
        }
      }
    }
    return null;
  }


}
