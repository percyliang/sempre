package edu.stanford.nlp.sempre.roboy;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import edu.stanford.nlp.sempre.roboy.DbFormulasInfo.BinaryFormulaInfo;
import edu.stanford.nlp.sempre.roboy.DbFormulasInfo.UnaryFormulaInfo;
import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.roboy.config.ConfigManager;
import fig.basic.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.Properties;
import java.io.FileReader;
import java.lang.reflect.Type;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.reflect.TypeToken;


/**
 * Class for keeping info from Database schema
 * @author jonathanberant
 * @maintainer emlozin
 */
public final class DatabaseInfo {
  private static DatabaseInfo singleton;
  public static DatabaseInfo getSingleton() {
    if (singleton == null) singleton = new DatabaseInfo();
    return singleton;
  }

  public static class Options {
    public static String defaultDB; //"lib/fb_data/93.exec/schema2.ttl";

    public static void setDefault(String formula){
      //LogInfo.logs("Default %s", formula);
      defaultDB = formula;};
    public static String getDefault(){return defaultDB;};
  }
  public static Options opts = new Options();

  // any
  // - number (boolean, int, float, date)
  // - text
  // - entity (people, loc, org, ...)
  // - cvt


  // mapping from master property to its opposite (e.g., fb:people.person.place_of_birth => fb:location.location.people_born_here)
  private BiMap<String, String> masterToOppositeMap = HashBiMap.create();

  private Set<String> cvts = new HashSet<>();
  private Map<String, String> type1Map = new HashMap<>();  // property => type of arg1
  private Map<String, String> type2Map = new HashMap<>();  // property => type of arg2
  private Map<String, String> unit2Map = new HashMap<>();  // property => unit of arg2 (if exists)
  private Map<String, List<String>> bDescriptionsMap = new HashMap<>(); // property => descriptions
  private Map<String, Integer> bPopularityMap = new HashMap<>(); // property => popularity
  // unary maps
  private Map<String, Integer> professionPopularityMap = new HashMap<>(); // property => popularity
  private Map<String, Integer> typePopularityMap = new HashMap<>(); // property => popularity
  private Map<String, List<String>> professionDescriptionsMap = new HashMap<>(); // property => descriptions
  private Map<String, List<String>> typeDescriptionsMap = new HashMap<>(); // property => descriptions

  private Map<String, String> nameMap = new HashMap<String, String>(); // id => name of id

  public String getArg1Type(String property) { return type1Map.get(property); }
  public String getArg2Type(String property) { return type2Map.get(property); }


  public static Properties prop = new Properties();
  public static Gson gson = new Gson();
  public static Map<String, String> glossary = new HashMap();
  public static Map<String, Map<String,String> > mapTypes = new HashMap();

  private DatabaseInfo() {
    try {
      glossary = ConfigManager.DB_GLOSSARY;
      mapTypes = ConfigManager.DB_TYPES;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public Map<Formula, BinaryFormulaInfo> createBinaryFormulaInfoMap() {

    Map<Formula, DbFormulasInfo.BinaryFormulaInfo> res = new HashMap<>();
    for (String property : bPopularityMap.keySet()) {
      Formula f = Formulas.fromLispTree(LispTree.proto.parseFromString(property));
      BinaryFormulaInfo info = new BinaryFormulaInfo(f, type1Map.get(property), type2Map.get(property), unit2Map.get(property), "", bDescriptionsMap.get(property), bPopularityMap.get(property));
      if (!info.isComplete()) {
        continue;
      }
      res.put(f, info);
    }
    return res;
  }

  public Map<Formula, UnaryFormulaInfo> createUnaryFormulaInfoMap() {

    Map<Formula, DbFormulasInfo.UnaryFormulaInfo> res = new HashMap<Formula, DbFormulasInfo.UnaryFormulaInfo>();
    // professions
    for (String profession : professionPopularityMap.keySet()) {
      Formula f  = new JoinFormula(getType("fb:","PROF"), new ValueFormula<Value>(new NameValue(profession)));
      UnaryFormulaInfo info = new UnaryFormulaInfo(f, professionPopularityMap.get(profession),
          MapUtils.get(professionDescriptionsMap, profession, new LinkedList<String>()),
          Collections.singleton(getType("fb:","PERSON")));
      if (!info.isComplete()) {
        continue;
      }
      res.put(f, info);
    }
    // types
    for (String type : typePopularityMap.keySet()) {
      Formula f  = new JoinFormula(getType("fb:","TYPE"), new ValueFormula<Value>(new NameValue(type)));
      UnaryFormulaInfo info = new UnaryFormulaInfo(f, typePopularityMap.get(type),
          MapUtils.get(typeDescriptionsMap, type, new LinkedList<String>()),
          Collections.singleton(type));
      if (!info.isComplete()) {
        continue;
      }
      res.put(f, info);
    }
    return res;
  }

  // fb:people.person.place_of_birth => true
  public boolean propertyHasOpposite(String property) {
    return masterToOppositeMap.containsKey(property) || masterToOppositeMap.inverse().containsKey(property);
  }
  // fb:people.person.place_of_birth => fb:location.location.people_born_here
  public String getOppositeFbProperty(String property) {
    if (masterToOppositeMap.containsKey(property))
      return masterToOppositeMap.get(property);
    if (masterToOppositeMap.inverse().containsKey(property))
      return masterToOppositeMap.inverse().get(property);
    throw new RuntimeException("Property does not have an opposite: " + property);
  }

  public String getUnit1(String property) { return typeToUnit(type1Map.get(property), property); }
  public String getUnit2(String property) { return typeToUnit(type2Map.get(property), property); }

  // Get the measurement unit associated with this type.
  // If something is not a number, then return something crude (e.g. fb:type.cvt).
  // Return null if we don't know anything.
  public String typeToUnit(String type, String property) {
    if (type == null) {
      // LogInfo.errors("No type information for property: %s", property);
      return null;
    }
    if (type.equals(getType(property,"ID")) || type.equals(getType(property,"FLOAT"))) {
      String unit = unit2Map.get(property);
      if (unit == null) {
        // LogInfo.errors("No unit information for property: %s", property);
        return NumberValue.unitless;
      }
      return unit;
    }
    if (type.equals(getType(property,"BOOLEAN")) || type.equals(getType(property,"TEXT")) || type.equals(getType(property,"DATE")))  // Use the type as the unit
      return type;
    if (isCvt(type)) return getType("fb:","CVT");  // CVT
    return getType(property,"ENTITY");  // Entity
  }

  public boolean isCvt(String type) {
    return cvts.contains(type);
  }

  public String getPropertyName(String property) {
    List<String> names = bDescriptionsMap.get(property);
    if (names == null) return null;
    return names.get(0);
  }

  public String getName(String id) { return nameMap.get(id); }

  public static boolean isReverseProperty(String property) {
    return CanonicalNames.isReverseProperty(property);
  }
  public static String reverseProperty(String property) {
    return CanonicalNames.reverseProperty(property);
  }

  // fb:en.barack_obama => http://rdf.roboy.com/ns/en/barack_obama

  public static String id2uri(String id) {
    for (String key : glossary.keySet()) {
      if (id.startsWith(key)) {
        return glossary.get(key) + id.substring(key.length()).replaceAll("\\.", "/");
      }
    }
    return id;
  }

  public static String uri2id(String uri) {
    for (String key : glossary.keySet()) {
      if (uri.contains(glossary.get(key)) && !uri.contains(",") && !uri.contains("(")) {
          uri = uri.replaceAll(glossary.get(key),key + ":").replaceAll("/", ".");
      }
//      if (uri.contains(",")) {
//        try {
//          uri = URLEncoder.encode(uri, "UTF-8");
//        } catch(Exception e) {
//          LogInfo.errors("Exception: %s", e);
//        }
//      }
    }
    if (uri.contains("/") && (uri.contains(",") || uri.contains("("))) {
       uri = "<".concat(uri).concat(">");
    }
    // LogInfo.logs("Warning: invalid Database uri!: %s", uri);
    // Don't do any conversion; this is not necessarily the best thing to do.
    return uri;
  }

  public static String getPrefixes(String query) {
    String prefix = new String();
    for (String key : glossary.keySet()) {
      if (query.contains(key)) {
        prefix = prefix + "PREFIX " + key + ": <" + glossary.get(key) + "> \n";
      }
    }
    return prefix;
  }

  public static String getType(String head, String id) {
    if (head == null || id == null)
      return null;
    if (mapTypes.size() == 0){
      try {
        JsonReader reader = new JsonReader(new FileReader(prop.getProperty("TYPES")));
        Type type = new TypeToken<Map<String, Map<String, String>>>(){}.getType();
        mapTypes = gson.fromJson(reader, type);
//        LogInfo.logs("%s", mapTypes.toString());
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
//    LogInfo.logs("Hello there: %s -- %s", head, id);
    if (head.contains(mapTypes.get("fb").get("PREFIX"))||
        opts.getDefault().contains(mapTypes.get("fb").get("PREFIX"))){
//      LogInfo.logs("Freebase: %s", id);
      return mapTypes.get("fb").get(id);
    }
    else if (head.contains(mapTypes.get("dbpedia").get("PREFIX")) ||
         head.contains("resource:") || head.contains("property:") ||
            opts.getDefault().contains(mapTypes.get("dbpedia").get("PREFIX")) ||
            opts.getDefault().contains("resource:") || opts.getDefault().contains("property:")){
//      LogInfo.logs("DBpedia: %s -> %s", id, mapTypes.get("dbpedia").get(id));
      return mapTypes.get("dbpedia").get(id);
    }
    else{
        return head;
    }
  }


}
