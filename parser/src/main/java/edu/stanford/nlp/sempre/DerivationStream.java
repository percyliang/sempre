package edu.stanford.nlp.sempre;

import java.util.Iterator;

/**
 * Represents a stream of Derivations which are constructed lazily for efficiency.
 * Use either SingleDerivationStream or MultipleDerivationStream.
 * Created by joberant on 3/14/14.
 */
public interface DerivationStream extends Iterator<Derivation> {
  Derivation peek();
  int estimatedSize();
}
