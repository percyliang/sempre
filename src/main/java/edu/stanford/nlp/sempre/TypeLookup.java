package edu.stanford.nlp.sempre;

/**
 * A TypeLookup object handles the domain-specific part of type inference.
 * TypeInference handles the domain general part.
 *
 * Given an entity or a property, return the appropriate SemType.
 */
public interface TypeLookup {
  // e.g., fb:en.barack_obama => (union fb:people.person ...)
  // Return null if unknown
  SemType getEntityType(String entity);

  // e.g., fb:people.person.place_of_birth => (-> fb:location.location fb:people.person)
  // Return null if unknown
  SemType getPropertyType(String property);
}
