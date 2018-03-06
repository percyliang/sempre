package edu.stanford.nlp.sempre.freebase;

import java.util.*;
import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.cache.*;
import fig.basic.*;

/**
 * Provides types of Freebase entities and properties.
 * For entities, look them up (requires access to the cache file).
 * For properties, just look them up in the FreebaseInfo schema.
 */
public class FreebaseTypeLookup implements TypeLookup {
  public static class Options {
    @Option(gloss = "Cache path to the types path")
    public String entityTypesPath;
  }
  public static Options opts = new Options();

  // Given those ids, we retrieve the set of types
  private static StringCache entityTypesCache;

  public Set<String> getEntityTypes(String entity) {
    if (opts.entityTypesPath == null)
      return Collections.singleton(FreebaseInfo.ENTITY);

    // Read types from cache
    if (entityTypesCache == null) entityTypesCache = StringCacheUtils.create(opts.entityTypesPath);
    Set<String> types = new HashSet<>();
    String typesStr = entityTypesCache.get(entity);
    if (typesStr != null) {
      Collections.addAll(types, typesStr.split(","));
    } else {
      types.add(FreebaseInfo.ENTITY);
    }
    return types;
  }

  @Override
  public SemType getEntityType(String entity) {
    Set<String> types = getEntityTypes(entity);
    // Remove supertypes
    // TODO(pliang): this is inefficient!
    Set<String> resultTypes = new HashSet<>(types);
    for (String entityType : types) {
      for (String supertype : SemTypeHierarchy.singleton.getSupertypes(entityType)) {
        if (!supertype.equals(entityType))
          resultTypes.remove(supertype);
      }
    }
    return SemType.newUnionSemType(resultTypes);
  }

  @Override
  public SemType getPropertyType(String property) {
    // property = fb:location.location.area
    // arg1Type = fb:location.location       --> becomes retType (head of formula)
    // arg2Type = fb:type.float              --> becomes argType
    FreebaseInfo info = FreebaseInfo.getSingleton();
    String arg1Type = info.getArg1Type(property), arg2Type = info.getArg2Type(property);
    if (arg1Type == null || arg2Type == null) return null;
    return SemType.newFuncSemType(arg2Type, arg1Type);
  }
}
