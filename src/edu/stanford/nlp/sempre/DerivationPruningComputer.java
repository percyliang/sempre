package edu.stanford.nlp.sempre;

import java.util.Collection;
import java.util.Map;

/**
 * Used to prune formulas during parsing.
 *
 * Extend this class to add custom pruning criteria,
 * then add the class name to the |pruningComputers| options of DerivationPruner.
 *
 * @author ppasupat
 */
public abstract class DerivationPruningComputer {

  protected final DerivationPruner pruner;

  public DerivationPruningComputer(DerivationPruner pruner) {
    this.pruner = pruner;
  }

  /**
   * Return a collection of all strategy names used in this class.
   */
  abstract public Collection<String> getAllStrategyNames();

  // Shorthand
  protected boolean containsStrategy(String name) {
    return pruner.containsStrategy(name);
  }

  /**
   * Prune the derivation without executing the formula.
   *
   * To add pruning strategies, override this method.
   * Return the strategy name to prune the formula, and null otherwise.
   * This method should not execute the formula.
   */
  public String isPrunedWithoutExecution(Derivation deriv) {
    return null;
  }

  /**
   * Prune the derivation (general).
   *
   * The formula will already be executed (i.e., deriv.value is set).
   *
   * To add pruning strategies, override this method.
   * Return the strategy name to prune the formula, and null otherwise.
   */
  public String isPrunedGeneral(Derivation deriv) {
    return null;
  }

  /**
   * Prune the derivation based on the subformula.
   *
   * DerivationPruner will traverse the formula and call this method on each subformula.
   * The method can store temporary data in |state|.
   *
   * To add pruning strategies, override this method.
   * Return the strategy name to prune the formula, and null otherwise.
   */
  public String isPrunedRecursive(Derivation deriv, Formula subformula, Map<String, Object> state) {
    return null;
  }
}
