package edu.stanford.nlp.sempre.tables;

import edu.stanford.nlp.sempre.*;
import fig.basic.LogInfo;

public class TableDerivationPruningComputer extends DerivationPruningComputer {

  public TableDerivationPruningComputer(DerivationPruner pruner) {
    super(pruner);
  }

  @Override
  public boolean isPruned(Derivation deriv) {
    return pruneJoins(deriv);
  }

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

}
