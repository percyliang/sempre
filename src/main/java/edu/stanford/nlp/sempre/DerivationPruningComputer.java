package edu.stanford.nlp.sempre;

import java.util.Collection;

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
  protected final Parser parser;
  protected final Example ex;

  public DerivationPruningComputer(DerivationPruner pruner) {
    this.pruner = pruner;
    this.parser = pruner.parser;
    this.ex = pruner.ex;
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
   * Prune the derivation.
   *
   * To add pruning strategies, override this method.
   * Return the strategy name to prune the formula, and null otherwise.
   */
  public abstract String isPruned(Derivation deriv);

}
