package edu.stanford.nlp.sempre;

// Encapsulates the production of possibly many Derivations.
// The subclass has to maintain the cursor to keep track of which is coming next.
public abstract class MultipleDerivationStream implements DerivationStream {
  private Derivation nextDeriv;  // Next one to return.
  int numGenerated = 0;

  // Override this class: should create a new Derivation.
  // Return null if there are no more.
  public abstract Derivation createDerivation();

  @Override public boolean hasNext() {
    if (nextDeriv != null) return true;  // Still one in the queue
    nextDeriv = createDerivation();  // Ask for another
    return nextDeriv != null;
  }

  @Override
  public Derivation next() {
    if (nextDeriv == null) throw new RuntimeException("No more derivations!");
    Derivation deriv = nextDeriv;
    if (FeatureExtractor.containsDomain("derivRank")) {
      numGenerated++;
      if (numGenerated <= 3)
        deriv.addFeature("derivRank", deriv.rule.sem.toString() + " " + numGenerated);
      else if (numGenerated <= 5)
        deriv.addFeature("derivRank", deriv.rule.sem.toString() + " 4:5");
      else if (numGenerated <= 10)
        deriv.addFeature("derivRank", deriv.rule.sem.toString() + " 6:10");
      else
        deriv.addFeature("derivRank", deriv.rule.sem.toString() + " 11:");
    }
    nextDeriv = createDerivation();
    return deriv;
  }

  @Override
  public Derivation peek() {
    if (nextDeriv == null) throw new RuntimeException("No more derivations!");
    return nextDeriv;
  }

  @Override public void remove() { throw new RuntimeException("Cannot remove from DerivationStream"); }

  // Default: but can overload this if desired
  @Override public int estimatedSize() { return 2; }
}
