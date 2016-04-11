package edu.stanford.nlp.sempre.tables;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import fig.basic.LispTree;
import fig.basic.LogInfo;

public class TableDerivationPruningComputer extends DerivationPruningComputer {

  public TableDerivationPruningComputer(DerivationPruner pruner) {
    super(pruner);
  }

  @Override
  public boolean isPruned(Derivation deriv) {
    return pruneJoins(deriv) || pruneSubsetMerge(deriv);
  }

  // ============================================================
  // Pruning based on Joins
  // ============================================================

  /**
   * Consider strings of joins such as (relation1 (relation2 (...)))
   *
   * - forwardBackward: prune (relation (!relation (...))), etc.
   * - doubleNext: prune (next (next (...))), (!next (next (...))), etc.
   */
  private boolean pruneJoins(Derivation deriv) {
    if (!containsStrategy("forwardBackward") && !containsStrategy("doubleNext")) return false;
    Formula formula = deriv.formula, current = formula;
    String rid1 = null, rid2 = null;
    while (current instanceof JoinFormula) {
      rid2 = rid1;
      rid1 = getRelationIdIfPossible(((JoinFormula) current).relation);
      if (rid1 != null && rid2 != null) {
        // Prune (!relation (relation (...)))
        if (containsStrategy("forwardBackward")) {
          if (rid1.equals("!" + rid2) || rid2.equals("!" + rid1)) {
            if (DerivationPruner.opts.pruningVerbosity >= 2)
              LogInfo.logs("PRUNED [forwardBackward] %s", formula);
            return true;
          }
        }
        // Prune (next (next (...)))
        if (containsStrategy("doubleNext")) {
          if ((rid1.equals("fb:row.row.next") && rid2.equals("fb:row.row.next")) ||
              (rid1.equals("!fb:row.row.next") && rid2.equals("!fb:row.row.next"))) {
            if (DerivationPruner.opts.pruningVerbosity >= 2)
              LogInfo.logs("PRUNED [doubleNext] %s", formula);
            return true;
          }
        }
      }
      current = ((JoinFormula) current).child;
    }
    return false;
  }

  /**
   * Helper method: Convert formula to relation id if possible.
   */
  protected String getRelationIdIfPossible(Formula formula) {
    if (formula instanceof ReverseFormula) {
      String childId = getRelationIdIfPossible(((ReverseFormula) formula).child);
      if (childId != null && !childId.isEmpty()) {
        return childId.charAt(0) == '!' ? childId.substring(1) : "!" + childId;
      }
    } else if (formula instanceof ValueFormula) {
      Value v = ((ValueFormula<?>) formula).value;
      if (v instanceof NameValue) {
        return ((NameValue) v).id;
      }
    }
    return null;
  }

  // ============================================================
  // Merge formulas
  // ============================================================

  private final Formula TYPE_ROW = Formulas.fromLispTree(
      LispTree.proto.parseFromString("(fb:type.object.type fb:type.row)"));

  private boolean pruneSubsetMerge(Derivation deriv) {
    if (!containsStrategy("subsetMerge") && !containsStrategy("typeRowMerge")) return false;
    if (!(deriv.formula instanceof MergeFormula)) return false;
    MergeFormula merge = (MergeFormula) deriv.formula;
    if (containsStrategy("subsetMerge")) {
      Value d1 = pruner.parser.executor.execute(merge.child1, pruner.ex.context).value;
      Value d2 = pruner.parser.executor.execute(merge.child2, pruner.ex.context).value;
      if (d1 instanceof ListValue && d2 instanceof ListValue) {
        Set<Value> v1 = new HashSet<>(((ListValue) d1).values);
        Set<Value> v2 = new HashSet<>(((ListValue) d2).values);
        if (v1.size() >= v2.size() && v1.containsAll(v2)) {
          if (DerivationPruner.opts.pruningVerbosity >= 2)
            LogInfo.logs("PRUNED [mergeSubset] %s", deriv.formula);
          return true;
        }
        if (v2.size() > v1.size() && v2.containsAll(v1)) {
          if (DerivationPruner.opts.pruningVerbosity >= 2)
            LogInfo.logs("PRUNED [mergeSubset] %s", deriv.formula);
          return true;
        }
      }
    }
    if (containsStrategy("typeRowMerge")) {
      if (TYPE_ROW.equals(merge.child1) || TYPE_ROW.equals(merge.child2)) {
        if (DerivationPruner.opts.pruningVerbosity >= 2)
          LogInfo.logs("PRUNED [typeRowMerge] %s", deriv.formula);
        return true;
      }
    }
    return false;
  }

}
