package edu.stanford.nlp.sempre.tables;

import edu.stanford.nlp.sempre.SemType;
import edu.stanford.nlp.sempre.TypeLookup;
import fig.basic.LogInfo;
import fig.basic.Option;

/**
 * Look up types for entities and properties in TableKnowledgeGraph.
 * (Delegate all decisions to TableTypeSystem.)
 *
 * @author ppasupat
 */
public class TableTypeLookup implements TypeLookup {
  public static class Options {
    @Option(gloss = "Verbosity") public int verbose = 0;
  }
  public static Options opts = new Options();

  @Override
  public SemType getEntityType(String entity) {
    if (opts.verbose >= 1)
      LogInfo.logs("TableTypeLookup.getEntityType %s", entity);
    SemType type = TableTypeSystem.getEntityTypeFromId(entity);
    if (type == null && opts.verbose >= 1)
      LogInfo.logs("TableTypeLookup.getEntityType FAIL %s", entity);
    return type;
  }

  @Override
  public SemType getPropertyType(String property) {
    if (opts.verbose >= 1)
      LogInfo.logs("TableTypeLookup.getPropertyType %s", property);
    SemType type = TableTypeSystem.getPropertyTypeFromId(property);
    if (type == null && opts.verbose >= 1)
      LogInfo.logs("TableTypeLookup.getPropertyType FAIL %s", property);
    return type;
  }

}
