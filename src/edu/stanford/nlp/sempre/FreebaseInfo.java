package edu.stanford.nlp.sempre;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import edu.stanford.nlp.sempre.FbFormulasInfo.BinaryFormulaInfo;
import edu.stanford.nlp.sempre.FbFormulasInfo.UnaryFormulaInfo;
import fig.basic.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

/**
 * Class for keeping info from Freebase schema
 * @author jonathanberant
 */
public class FreebaseInfo {

  private static FreebaseInfo FreebaseInfo;
  public static FreebaseInfo getSingleton() {
    if (FreebaseInfo == null) FreebaseInfo = new FreebaseInfo();
    return FreebaseInfo;
  }

  public static class Options {
    @Option(gloss = "ttl file with schema information")
    public String schemaPath = "lib/fb_data/93.exec/schema.ttl";
  }
  public static Options opts = new Options();

  public final static String BOOLEAN = "fb:type.boolean";
  public final static String INT = "fb:type.int";
  public final static String FLOAT = "fb:type.float";
  public final static String DATE = "fb:type.datetime";
  public final static String TEXT = "fb:type.text";

  public final static String PERSON = "fb:people.person";
  public final static String LOC = "fb:location.location";
  public final static String ORG = "fb:organization.organization";

  // Non-standard abstract types
  public final static String NUMBER  = "fb:type.number";
  public final static String ENTITY  = "fb:type.entity";
  public final static String CVT  = "fb:type.cvt";

  public final static String TYPE = "fb:type.object.type";
  public final static String PROF = "fb:people.person.profession";
  public final static String NAME = "fb:type.object.name";
  public final static String ALIAS = "fb:common.topic.alias";

  private BiMap<String, String> masterToReverseMap = HashBiMap.create(); //mapping from master property to its reverse
  private Map<String, Set<String>> typeToIncludedTypesMap = new HashMap<String, Set<String>>();
  private Map<String, Set<String>> typeToSubTypesMap = new HashMap<String, Set<String>>();
  private Set<String> cvts = new HashSet<String>();
  private Map<String, String> type1Map = new HashMap<String, String>();  // property => type of arg1
  private Map<String, String> type2Map = new HashMap<String, String>();  // property => type of arg2
  private Map<String, String> unit2Map = new HashMap<String, String>();  // property => unit of arg2 (if exists)
  private Map<String, List<String>> bDescriptionsMap = new HashMap<String, List<String>>(); //property => descriptions
  private Map<String, Integer> bPopularityMap = new HashMap<String, Integer>(); //property => popularity
  //unary maps
  private Map<String, Integer> professionPopularityMap = new HashMap<String, Integer>(); //property => popularity
  private Map<String, Integer> typePopularityMap = new HashMap<String, Integer>(); //property => popularity
  private Map<String, List<String>> professionDescriptionsMap = new HashMap<String, List<String>>(); //property => descriptions
  private Map<String, List<String>> typeDescriptionsMap = new HashMap<String, List<String>>(); //property => descriptions

  private FreebaseInfo() {
    try {
      readSchema();
    } catch (NumberFormatException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    } 
    // For each type, add |type| and common topic to the set of supertypes.
    for (Map.Entry<String, Set<String>> e : typeToIncludedTypesMap.entrySet())
      addDefaultSupertypes(e.getKey(), e.getValue());

    // Non common topic supertypes.
    addSupertype(INT, INT);
    addSupertype(INT, NUMBER);
    addSupertype(FLOAT, FLOAT);
    addSupertype(FLOAT, NUMBER);
    addSupertype(DATE, DATE);
  }


  /**
   * Go over schema twice - once to populate all fields except descriptions, the second time we populate descriptions after we now what
   * are the properties we are interested in
   * @throws NumberFormatException
   * @throws IOException
   */
  public void readSchema() throws NumberFormatException, IOException {

    LogInfo.begin_track("Loading Freebase schema: %s", opts.schemaPath);
    BufferedReader in = IOUtils.openInHard(opts.schemaPath);

    String line;
    while ((line = in.readLine()) != null) {
      String[] tokens = edu.stanford.nlp.sempre.freebase.Utils.parseTriple(line);
      if (tokens == null) continue;
      String arg1 = tokens[0];
      String property = tokens[1];
      String arg2 = tokens[2];

      if (property.equals("fb:type.property.reverse_property")) {
        // Duplicates logically really shouldn't happen but the Freebase RDF
        // reverse properties are not 1:1.  We should monitor this and make
        // sure we don't lose any alignments.
        if (masterToReverseMap.containsKey(arg1)) {
          //LogInfo.errors("arg1 exists multiple times: %s", line);
          continue;
        }
        if (masterToReverseMap.inverse().containsKey(arg2)) {
          //LogInfo.errors("arg2 exists multiple times: %s", line);
          continue;
        }
        masterToReverseMap.put(arg1, arg2);
      } else if (property.equals("fb:freebase.type_hints.included_types")) {
        Set<String> set = typeToIncludedTypesMap.get(arg1);
        if (set == null) {
          typeToIncludedTypesMap.put(arg1, set = new HashSet<String>());
        }
        set.add(arg2);
        set = typeToSubTypesMap.get(arg2);
        if (set == null) {
          typeToSubTypesMap.put(arg2, set = new HashSet<String>());
        }
        set.add(arg1);
      } else if (property.equals("fb:freebase.type_hints.mediator")) {
        if (arg2.equals("\"true\"^^xsd:boolean")) cvts.add(arg1);
        else if (arg2.equals("\"false\"^^xsd:boolean")) cvts.remove(arg1);
        else throw new RuntimeException("Invalid xsd:boolean: " + arg2);
      } else if (property.equals("fb:type.property.schema")) {
        type1Map.put(arg1, arg2);
      } else if (property.equals("fb:type.property.expected_type")) {
        type2Map.put(arg1, arg2);
      } else if (property.equals("fb:type.property.unit")) {
        unit2Map.put(arg1, arg2);
      } else if (property.equals("fb:user.custom.type.property.num_instances")) {
        bPopularityMap.put(arg1, edu.stanford.nlp.sempre.freebase.Utils.parseInt(arg2));
      } else if (property.equals("fb:user.custom.people.person.profession.num_instances")) {
        professionPopularityMap.put(arg1, edu.stanford.nlp.sempre.freebase.Utils.parseInt(arg2));
      } else if (property.equals("fb:user.custom.type.object.type.num_instances")) {
        typePopularityMap.put(arg1, edu.stanford.nlp.sempre.freebase.Utils.parseInt(arg2));
      }   
    }
    in.close();
    //second iteration - populate descriptions assumes all properties have the fb:type.property.num_instances field
    in = IOUtils.openInHard(opts.schemaPath);
    while ((line = in.readLine()) != null) {
      String[] tokens = edu.stanford.nlp.sempre.freebase.Utils.parseTriple(line);
      if (tokens == null) continue;
      String arg1 = tokens[0];
      String property = tokens[1];
      String arg2 = tokens[2];
      
      if(property.equals(NAME) || property.equals(ALIAS)) {
        if(bPopularityMap.containsKey(arg1)) {
          MapUtils.addToList(bDescriptionsMap, arg1, edu.stanford.nlp.sempre.freebase.Utils.parseStr(arg2).toLowerCase());
        }
        else if(professionPopularityMap.containsKey(arg1)) {
          MapUtils.addToList(professionDescriptionsMap, arg1, edu.stanford.nlp.sempre.freebase.Utils.parseStr(arg2).toLowerCase());
        }
        else if(typePopularityMap.containsKey(arg1)) {
          MapUtils.addToList(typeDescriptionsMap, arg1, edu.stanford.nlp.sempre.freebase.Utils.parseStr(arg2).toLowerCase());
        }  
      } 
    }
    LogInfo.logs("%d CVTs, (%d,%d) property types, %d property units", cvts.size(), type1Map.size(), type2Map.size(), unit2Map.size());
    LogInfo.end_track();
  }

  public Map<Formula,BinaryFormulaInfo> createBinaryFormulaInfoMap() {

    Map<Formula,FbFormulasInfo.BinaryFormulaInfo> res = new HashMap<Formula, FbFormulasInfo.BinaryFormulaInfo>();
    for(String property: bPopularityMap.keySet()) {
      Formula f = Formulas.fromLispTree(LispTree.proto.parseFromString(property));
      BinaryFormulaInfo info = new BinaryFormulaInfo(f, type1Map.get(property), type2Map.get(property), unit2Map.get(property),"",bDescriptionsMap.get(property),bPopularityMap.get(property));
      if(!info.isComplete()) {
        continue;
      }
      res.put(f, info);
    }
    return res;
  }

  public Map<Formula,UnaryFormulaInfo> createUnaryFormulaInfoMap() {

    Map<Formula,FbFormulasInfo.UnaryFormulaInfo> res = new HashMap<Formula, FbFormulasInfo.UnaryFormulaInfo>();
    //professions
    for(String profession: professionPopularityMap.keySet()) {
      Formula f  = new JoinFormula(PROF, new ValueFormula<Value>(new NameValue(profession)));
      UnaryFormulaInfo info = new UnaryFormulaInfo(f, professionPopularityMap.get(profession), 
          MapUtils.get(professionDescriptionsMap,profession,new LinkedList<String>()),
          Collections.singleton(PERSON));
      if(!info.isComplete()) {
        continue;
      }
      res.put(f, info);
    }
    //types
    for(String type: typePopularityMap.keySet()) {
      Formula f  = new JoinFormula(TYPE, new ValueFormula<Value>(new NameValue(type)));
      UnaryFormulaInfo info = new UnaryFormulaInfo(f, typePopularityMap.get(type),
          MapUtils.get(typeDescriptionsMap,type,new LinkedList<String>()),
          Collections.singleton(type));
      if(!info.isComplete()) {
        continue;
      }
      res.put(f, info);
    }
    return res;
  }

  public boolean fbPropertyHasOpposite(String fbProperty) {
    return masterToReverseMap.containsKey(fbProperty) || masterToReverseMap.inverse().containsKey(fbProperty);
  }
  //check if has opposite before using
  public String getOppositeFbProperty(String fbPropety) {
    if (masterToReverseMap.containsKey(fbPropety))
      return masterToReverseMap.get(fbPropety);
    if (masterToReverseMap.inverse().containsKey(fbPropety))
      return masterToReverseMap.inverse().get(fbPropety);
    throw new RuntimeException("Property does not have an opposite: " + fbPropety);
  }

  private Set<String> addDefaultSupertypes(String type, Set<String> supertypes) {
    supertypes.add(type);
    supertypes.add("fb:common.topic");
    return supertypes;
  }

  public void addSupertype(String subtype, String supertype) {
    Set<String> supertypes = typeToIncludedTypesMap.get(subtype);
    if (supertypes == null)
      typeToIncludedTypesMap.put(subtype, supertypes = new HashSet<String>());
    supertypes.add(supertype);
    
    Set<String> subTypes = typeToSubTypesMap.get(supertype);
    if (subTypes == null)
      typeToSubTypesMap.put(supertype, subTypes = new HashSet<String>());
    subTypes.add(subtype);
  }

  //Get the measurement unit associated with arg2 of property.
  // If something is not a number, then return something crude (e.g. fb:type.cvt).
  // Return null if we don't know anything.
  public String getUnit2(String property) {
    String type = type2Map.get(property);
    if (type == null) {
      //LogInfo.errors("No type information for property: %s", property);
      return null;
    }
    if (type.equals(INT) || type.equals(FLOAT)) {
      String unit = unit2Map.get(property);
      if (unit == null) {
        //LogInfo.errors("No unit information for property: %s", property);
        return NumberValue.unitless;
      }
      return unit;
    }
    if (type.equals(BOOLEAN) || type.equals(TEXT) || type.equals(DATE))  // Use the type as the unit
      return type;
    if (isCvt(type)) return CVT;  // CVT
    return ENTITY;  // Entity
  }

  public boolean isCvt(String type) {
    return cvts.contains(type);
  }

  /*
  public void computeTransitiveClosureInefficiently() {

    boolean added;
    do {
      added = false;
      for (String subType : typeToIncludedTypesMap.keySet()) {

        Set<String> superTypes = typeToIncludedTypesMap.get(subType);
        Set<String> typesToAdd = new HashSet<String>();

        for (String superType : superTypes) {
          Set<String> superSuperTypes = typeToIncludedTypesMap.get(superType);
          if (superSuperTypes != null) {
            typesToAdd.addAll(superSuperTypes);
          }
        }
        typesToAdd.removeAll(superTypes);
        if (typesToAdd.size() > 0) {
          LogInfo.log("Adding to subtype: " + subType + "with supertypes " + superTypes + " the new types: " + typesToAdd);
          superTypes.addAll(typesToAdd);
          added = true;
        }
      }
    } while (added == true);
  }*/

  public Set<String> getIncludedTypesInclusive(String subtype) {
    Set<String> set = typeToIncludedTypesMap.get(subtype);
    if (set == null) {
      return addDefaultSupertypes(subtype, new HashSet<String>());
    }
    return set;
  }
  
  public Set<String> getSubTypesExclusive(String subtype) {
    Set<String> set = typeToSubTypesMap.get(subtype);
    if (set == null) {
      return new HashSet<String>();
    }
    return set;
  }

  public String coarseType(String type) {
    Set<String> superTypes = typeToIncludedTypesMap.get(type);
    if (superTypes != null) {
      if (superTypes.contains(PERSON)) return PERSON;
      if (superTypes.contains(LOC)) return LOC;
      if (superTypes.contains(ORG)) return ORG;
      if (superTypes.contains(NUMBER)) return NUMBER;
      if (superTypes.contains(DATE)) return DATE;
    }
    return "OTHER";  // Shouldn't really happen
  }

  // Return whether |property| is the name of a reverse property.
  // Convention: ! is the prefix for reverses.
  public static boolean isReverseProperty(String property) {
    return property.startsWith("!") && !property.equals("!=");
  }

  // fb:en.barack_obama => http://rdf.freebase.com/ns/en/barack_obama
  public static final String freebaseNamespace = "http://rdf.freebase.com/ns/";

  public static String id2uri(String id) {
    assert id.startsWith("fb:") : id;
    return freebaseNamespace + id.substring(3).replaceAll("\\.", "/");
  }
  public static String uri2id(String uri) {
    if (!uri.startsWith(freebaseNamespace)) {
      LogInfo.logs("Warning: invalid Freebase uri: %s", uri);
      // Don't do any conversion; this is not necessarily the best thing to do.
      return uri;
    }
    return "fb:" + uri.substring(freebaseNamespace.length()).replaceAll("/", ".");
  }
  
  public static boolean isPrimitive(String type) {
    return type.equals(BOOLEAN) ||
        type.equals(INT) ||
        type.equals(FLOAT) ||
        type.equals(DATE) || 
        type.equals(TEXT);
  }
}
