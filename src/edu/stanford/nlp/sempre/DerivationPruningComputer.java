package edu.stanford.nlp.sempre;

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

  protected boolean containsStrategy(String name) {
    return pruner.containsStrategy(name);
  }

  public abstract boolean isPruned(Derivation deriv);

}
