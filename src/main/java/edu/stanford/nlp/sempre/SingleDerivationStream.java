package edu.stanford.nlp.sempre;

/**
 * Encapsulates the production of at most one Derivation.
 */
public abstract class SingleDerivationStream implements DerivationStream {
  private Derivation nextDeriv;  // Next one to return.
  private boolean consumed;

  // Override this class: should create a new Derivation.
  // Return null if there is none.
  public abstract Derivation createDerivation();

  @Override public boolean hasNext() {
    if (nextDeriv != null) return true;  // Still one in the queue
    if (consumed) return false;          // No more
    nextDeriv = createDerivation();      // Ask for one
    consumed = true;
    return nextDeriv != null;
  }

  @Override public Derivation peek() {
    if (!hasNext()) throw new RuntimeException("No more derivations!");
    //if (nextDeriv == null) throw new RuntimeException("No more derivations!");
    return nextDeriv;
  }

  @Override public Derivation next() {
    if (!hasNext()) throw new RuntimeException("No more derivations!");
    Derivation deriv = nextDeriv;
    nextDeriv = null;
    return deriv;
  }

  @Override public void remove() { throw new RuntimeException("Cannot remove from DerivationStream"); }

  @Override public int estimatedSize() { return 1; }

  public static SingleDerivationStream constant(final Derivation deriv) {
    return new SingleDerivationStream() {
      public Derivation createDerivation() { return deriv; }
    };
  }
}
