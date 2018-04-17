package edu.stanford.nlp.sempre.geo880;

import edu.stanford.nlp.sempre.SemType;
import edu.stanford.nlp.sempre.SemTypeHierarchy;
import edu.stanford.nlp.sempre.TypeLookup;
import fig.basic.IOUtils;
import fig.basic.Option;
import fig.basic.LogInfo;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Type lookup for the geo880 domain, Mostly for distinguishing locations and numbers.
 * We also use a type hierarchy provided by a file to match |location.us_state| and |location.location| etc.
 * Created by joberant on 05/12/2016.
 */
public class Geo880TypeLookup implements TypeLookup{
  public static class Options {
    @Option(gloss = "Verbosity") public int verbose = 0;
    @Option(gloss = "A path to a file that specified the type hierarchy.")
    public String typeHierarchyPath;

  }
  public static Options opts = new Options();
  public static final String LOCATION = "fb:location.location";
  public static final String CITY = "fb:location.citytown";
  public static final String STATE = "fb:location.us_state";
  public static final String RIVER = "fb:location.river";
  public static final String LAKE = "fb:location.lake";
  public static final String MOUNTAIN = "fb:location.mountain";
  public static final String COUNTRY = "fb:location.country";

  public Geo880TypeLookup() {
    SemTypeHierarchy semTypeHierarchy = SemTypeHierarchy.singleton;
    if (opts.typeHierarchyPath != null) {
      try {
        for (String line : IOUtils.readLines(opts.typeHierarchyPath)) {
          String[] tokens = line.split("\\s+");

          // Check the file only contains relations about supertypes.
          assert tokens[1].endsWith("included_types");
          semTypeHierarchy.addSupertype(tokens[0], tokens[0]);
          semTypeHierarchy.addSupertype(tokens[2], tokens[2]);
          semTypeHierarchy.addSupertype(tokens[0], tokens[2]);
        }
      } catch (IOException e) {
        e.printStackTrace();
        throw new RuntimeException("Could not read lines from: " + opts.typeHierarchyPath);
      }
    }
  }

  @Override
  public SemType getEntityType(String entity) {
    // Entites are of the form fb:state.florida.
    int colonIndex = entity.indexOf(':');
    int dotIndex = entity.indexOf('.');
    String type = entity.substring(colonIndex+1, dotIndex);

    if (type.equals("place")) {
      type = LOCATION;
    }
    else if (type.equals("city")) {
      type = CITY;
    }
    else if (type.equals("state")) {
      type = STATE;
    }
    else if (type.equals("river")) {
      type = RIVER;
    }
    else if (type.equals("lake")) {
      type = LAKE;
    }
    else if (type.equals("mountain")) {
      type = MOUNTAIN;
    }
    else if (type.equals("country")) {
      type = COUNTRY;
    }
    else {
      throw new RuntimeException("Illegal entity: " + entity);
    }
    SemType result = SemType.newUnionSemType(type);
    if (opts.verbose >= 1) {
      LogInfo.logs("Entity=%s, Type=%s", entity, result);
    }
    return result;
  }

  @Override
  public SemType getPropertyType(String property) {
    // Properties are of the form fb:location.location.population.
    String arg1 = property.substring(0, property.lastIndexOf('.'));
    String suffix = property.substring(property.lastIndexOf('.') + 1);
    String arg2 = LOCATION;
    if (suffix.equals("density") || suffix.equals("elevation") ||
        suffix.equals("population") || suffix.equals("size") ||
        suffix.equals("area") || suffix.equals("length")) {
      arg2 = "fb:type.number";
    }
    SemType result = SemType.newFuncSemType(arg2, arg1);
    if (opts.verbose >= 1) {
      LogInfo.logs("Property=%s, Type=%s", property, result);
    }
    return result;
  }
}
