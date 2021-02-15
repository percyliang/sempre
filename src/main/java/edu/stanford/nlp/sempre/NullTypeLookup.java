package edu.stanford.nlp.sempre;

/**
 * Default implementation of TypeLookup: just return null (I don't know what
 * the type is).
 */
public class NullTypeLookup implements TypeLookup {
  @Override
  public SemType getEntityType(String entity) {
    return null;
  }

  @Override
  public SemType getPropertyType(String property) {
    return null;
  }
}
