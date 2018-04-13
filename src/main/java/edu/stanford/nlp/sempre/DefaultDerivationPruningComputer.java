package edu.stanford.nlp.sempre;

import java.util.*;

import fig.basic.LispTree;
import fig.basic.Option;

/**
 * Common pruning strategies that can be used in many semantic parsing tasks.
 *
 * @author ppasupat
 */
public class DefaultDerivationPruningComputer extends DerivationPruningComputer {
  public static class Options {
    @Option(gloss = "(for badSummarizerHead) allow count on sets of size 1")
    public boolean allowCountOne = false;
  }
  public static Options opts = new Options();

  public DefaultDerivationPruningComputer(DerivationPruner pruner) {
    super(pruner);
  }

  public static final String atomic = "atomic";
  public static final String emptyDenotation = "emptyDenotation";
  public static final String nonLambdaError = "nonLambdaError";
  public static final String tooManyValues = "tooManyValues";
  public static final String doubleSummarizers = "doubleSummarizers";
  public static final String multipleSuperlatives = "multipleSuperlatives";
  public static final String sameMerge = "sameMerge";
  public static final String mistypedMerge = "mistypedMerge";
  public static final String unsortedMerge = "unsortedMerge";
  public static final String badSummarizerHead = "badSummarizerHead";

  @Override
  public Collection<String> getAllStrategyNames() {
    return Arrays.asList(
        atomic,
        emptyDenotation, nonLambdaError, tooManyValues,
        doubleSummarizers, multipleSuperlatives,
        sameMerge, mistypedMerge, unsortedMerge, badSummarizerHead);
  }

  @Override
  public String isPruned(Derivation deriv) {
    // atomic: Prune atomic formula at root.
    //   e.g., Prevent "Who was taller, Lincoln or Obama" --> fb:en.lincoln generated from lexicon without any computation   
    if (containsStrategy(atomic)) {
      if (deriv.isRoot(ex.numTokens()) && deriv.formula instanceof ValueFormula)
        return atomic;
    }
    // emptyDenotation: Prune if the denotation is empty
    if (containsStrategy(emptyDenotation)) {
      deriv.ensureExecuted(parser.executor, ex.context);
      if (deriv.value instanceof ListValue && ((ListValue) deriv.value).values.isEmpty())
        return emptyDenotation;
    }
    // nonLambdaError: Prune if the denotation is an error and the formula is not a partial formula
    if (containsStrategy(nonLambdaError) && !isLambdaFormula(deriv.formula)) {
      deriv.ensureExecuted(parser.executor, ex.context);
      if (deriv.value instanceof ErrorValue)
        return nonLambdaError;
    }
    // tooManyValues: Prune if the denotation has too many values (at $ROOT only)
    if (containsStrategy(tooManyValues) && deriv.isRoot(ex.numTokens())) {
      if (!(deriv.value instanceof ListValue) ||
          ((ListValue) deriv.value).values.size() > DerivationPruner.opts.maxNumValues)
        return tooManyValues;
    }
    // doubleSummarizers: Prune when two summarizers (aggregate or superlative) are directly nested
    // e.g., in (sum (avg ...)) and (min (argmax ...)), the outer operation is redundant
    if (containsStrategy(doubleSummarizers)) {
      Formula innerFormula = null;
      if (deriv.formula instanceof SuperlativeFormula)
        innerFormula = ((SuperlativeFormula) deriv.formula).head;
      else if (deriv.formula instanceof AggregateFormula)
        innerFormula = ((AggregateFormula) deriv.formula).child;
      if (innerFormula != null &&
          (innerFormula instanceof SuperlativeFormula || innerFormula instanceof AggregateFormula))
        return doubleSummarizers;
    }
    // multipleSuperlatives: Prune when more than one superlatives are used
    // (don't need to be adjacent)
    if (containsStrategy(multipleSuperlatives)) {
      List<LispTree> stack = new ArrayList<>();
      int count = 0;
      stack.add(deriv.formula.toLispTree());
      while (!stack.isEmpty()) {
        LispTree tree = stack.remove(stack.size() - 1);
        if (tree.isLeaf()) {
          if ("argmax".equals(tree.value) || "argmin".equals(tree.value)) {
            count++;
            if (count >= 2)
              return multipleSuperlatives;
          }
        } else {
          for (LispTree subtree : tree.children)
            stack.add(subtree);
        }
      }
    }
    // sameMerge: Prune merge formulas with two identical children
    if (containsStrategy(sameMerge) && deriv.formula instanceof MergeFormula) {
      MergeFormula merge = (MergeFormula) deriv.formula;
      if (merge.child1.equals(merge.child2))
        return sameMerge;
    }
    // mistypedMerge: Prune merge formulas with children of different types
    if (containsStrategy(mistypedMerge) && deriv.formula instanceof MergeFormula) {
      MergeFormula merge = (MergeFormula) deriv.formula;
      SemType type1 = TypeInference.inferType(merge.child1, true);
      SemType type2 = TypeInference.inferType(merge.child2, true);
      if (!type1.meet(type2).isValid())
        return mistypedMerge;
    }
    // unsortedMerge: Prune merge formulas where the children's string forms are not lexicographically sorted.
    //   Will remove redundant (and Y X) when (and Y X) is already present.
    if (containsStrategy(unsortedMerge) && deriv.formula instanceof MergeFormula) {
      MergeFormula merge = (MergeFormula) deriv.formula;
      String child1 = merge.child1.toString(), child2 = merge.child2.toString();
      if (child1.compareTo(child2) >= 0)
        return unsortedMerge;
    }
    // badSummarizerHead: Prune if the head of a superlative or a non-count aggregate
    // is empty or is a single object
    if (containsStrategy(badSummarizerHead)) {
      Formula innerFormula = null;
      boolean isCount = false;
      if (deriv.formula instanceof SuperlativeFormula)
        innerFormula = ((SuperlativeFormula) deriv.formula).head;
      else if (deriv.formula instanceof AggregateFormula) {
        innerFormula = ((AggregateFormula) deriv.formula).child;
        if (((AggregateFormula) deriv.formula).mode == AggregateFormula.Mode.count)
          isCount = true;
      }
      if (innerFormula != null) {
        try {
          TypeInference.inferType(innerFormula);
          Value innerValue = parser.executor.execute(innerFormula, ex.context).value;
          if (innerValue instanceof ListValue) {
            int size = ((ListValue) innerValue).values.size();
            if (size == 0 || (size == 1 && !(opts.allowCountOne && isCount)))
              return badSummarizerHead;
          }
        } catch (Exception e) {
          // TypeInference fails; probably because of free variables. No need to do anything.
        }
      }
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

}
