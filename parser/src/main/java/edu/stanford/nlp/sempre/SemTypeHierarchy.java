package edu.stanford.nlp.sempre;

import java.util.*;
import fig.basic.*;

/**
 * Maintain a hierarchy (DAG) over strings.
 *   subtype < type < supertype
 *
 * @author Percy Liang
 */
public class SemTypeHierarchy {
  public static class Options {
    @Option(gloss = "Throw an error if the type is not registered in the type hierarchy.")
      public boolean failOnUnknownTypes = false;
  }
  public static Options opts = new Options();

  public static final SemTypeHierarchy singleton = new SemTypeHierarchy();

  // type => list of all supertypes (assume we don't have that many supertypes)
  private Map<String, Set<String>> supertypesMap = new HashMap<>();  // type => supertypes of type
  private Map<String, Set<String>> subtypesMap = new HashMap<>();    // type => subtype of type
  // Note: don't always need this, so can maybe remove later

  public SemTypeHierarchy() {
    // Add basic types.
    addSupertype(CanonicalNames.BOOLEAN, CanonicalNames.BOOLEAN);
    addSupertype(CanonicalNames.BOOLEAN, CanonicalNames.ANY);
    addSupertype(CanonicalNames.INT, CanonicalNames.INT);
    addSupertype(CanonicalNames.INT, CanonicalNames.NUMBER);
    addSupertype(CanonicalNames.INT, CanonicalNames.ANY);
    addSupertype(CanonicalNames.FLOAT, CanonicalNames.FLOAT);
    addSupertype(CanonicalNames.FLOAT, CanonicalNames.NUMBER);
    addSupertype(CanonicalNames.FLOAT, CanonicalNames.ANY);
    addSupertype(CanonicalNames.DATE, CanonicalNames.DATE);
    addSupertype(CanonicalNames.DATE, CanonicalNames.ANY);
    addSupertype(CanonicalNames.TEXT, CanonicalNames.TEXT);
    addSupertype(CanonicalNames.TEXT, CanonicalNames.ANY);
    addSupertype(CanonicalNames.NUMBER, CanonicalNames.NUMBER);
    addSupertype(CanonicalNames.NUMBER, CanonicalNames.ANY);
    addSupertype(CanonicalNames.ENTITY, CanonicalNames.ENTITY);
    addSupertype(CanonicalNames.ENTITY, CanonicalNames.ANY);
    addSupertype(CanonicalNames.ANY, CanonicalNames.ANY);
  }

  // Add standard supertypes of entity
  public void addEntitySupertypes(String type) {
    // LogInfo.logs("addEntitySupertypes %s", type);
    addSupertype(type, type);
    addSupertype(type, CanonicalNames.ENTITY);
    addSupertype(type, CanonicalNames.ANY);
  }

  // Add: subtype < supertype
  public void addSupertype(String subtype, String supertype) {
    MapUtils.addToSet(supertypesMap, subtype, supertype);
    MapUtils.addToSet(subtypesMap, supertype, subtype);
  }

  public Set<String> getSupertypes(String type) {
    Set<String> set = supertypesMap.get(type);
    if (set == null) {
      if (opts.failOnUnknownTypes)
        LogInfo.fails("SemTypeHierarchy.getSupertypes: don't know about type %s", type);
      addEntitySupertypes(type);
      set = supertypesMap.get(type);
    }
    return set;
  }

  public Set<String> getSubtypes(String type) {
    Set<String> set = subtypesMap.get(type);
    if (set == null) {
      if (opts.failOnUnknownTypes)
        LogInfo.fails("SemTypeHierarchy.getSubtypes: don't know about type %s", type);
      addEntitySupertypes(type);
      set = supertypesMap.get(type);
    }
    return set;
  }
}
