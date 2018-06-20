package edu.stanford.nlp.sempre.roboy;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import edu.stanford.nlp.sempre.SimpleLexicon;
import edu.stanford.nlp.sempre.roboy.config.ConfigManager;
import edu.stanford.nlp.sempre.roboy.utils.SparqlUtils;
import fig.basic.IOUtils;
import fig.basic.LogInfo;

import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.util.*;

public class LexiconGenerator {
    public static Gson gson = new Gson();                   /**< Gson object */

    private String nameEntity = "resource";                 /**< Name for entities */
    private String nameClass = "dbpedia";                   /**< Name for class objects */
    private String propertyClass = "property";              /**< Name for property objects */

    private String dbpediaUrl = ConfigManager.DB_SPARQL;    /**< DBpedia SPARQL endpoint */
    private SparqlUtils sparqlUtil = new SparqlUtils();     /**< SPARQL helper */

    /**
     * Function updating lexicon
     * @param lexemes   set of lexical entries to be added
     */
    public void updateLexicon(Set<String> lexemes) {
        PrintWriter out = IOUtils.openOutAppendHard("./resources_nlu/error_test/newLexicon.txt");

        for (String lexeme: lexemes){
            if (ConfigManager.DEBUG > 0)
                LogInfo.logs("New lexeme added: %s",lexeme);
            out.println(lexeme);
//            SimpleLexicon.getSingleton().add(lexeme);
        }
        out.close();
    }

    /**
     * Function taking care of creating lexemes for lexicon
     * @param underspec  complete list of new terms
     */
    public List<UnderspecifiedInfo> createLexemes(List<UnderspecifiedInfo> underspec)
    {
        List<UnderspecifiedInfo> result = new ArrayList<>();
        Set<String> lexemes = new HashSet<>();
        // Check all underspecified terms
        for (UnderspecifiedInfo single:underspec)
        {
            for (int i = 0; i < single.candidates.size(); i++)
            {
                // Candidate is an entity
                if (single.candidates.get(i).contains(nameEntity))
                {
                    List<String> types = new ArrayList<>();
                    Map<String,String> lexeme = new HashMap<>();
                    Set<String> t = sparqlUtil.returnType(single.candidates.get(i), dbpediaUrl, false);
                    if (t!=null)
                        types.addAll(t);
                    if (types.size() > 0) {
                        Type type = new TypeToken<Map<String, String>>() {}.getType();
                        Map<String, String> c = this.gson.fromJson(single.candidatesInfo.get(i), type);
                        if (single.type == UnderspecifiedInfo.TermType.ENTITY)
                            single.candidatesScores.set(i, single.candidatesScores.get(i) + 0.3);
                        lexeme.put("lexeme", c.get("Label"));
                        lexeme.put("formula", single.candidates.get(i));
                        lexeme.put("features", " {score:" + Double.toString(single.candidatesScores.get(i)) + "} ");
                        lexeme.put("type", "NamedEntity");
                        c.put("type", "NamedEntity");
                        single.candidatesInfo.set(i, gson.toJson(c));
                        lexemes.add(gson.toJson(lexeme));
                    }
                }
                // Candidate is a class/property
                else if (single.candidates.get(i).contains(nameClass))
                {
                    List<String> types = new ArrayList<>();
                    Map<String,String> lexeme = new HashMap<>();
                    Set<String> t = sparqlUtil.returnType(single.candidates.get(i), dbpediaUrl, false);
                    if (t!=null)
                        types.addAll(t);
                    if (types.size() > 0) {
                        if (String.join(" ", t).contains("Class")) {
                            Type type = new TypeToken<Map<String, String>>() {
                            }.getType();
                            Map<String, String> c = this.gson.fromJson(single.candidatesInfo.get(i), type);
                            if (single.type == UnderspecifiedInfo.TermType.TYPE)
                                single.candidatesScores.set(i, single.candidatesScores.get(i) + 0.3);
                            lexeme.put("lexeme", c.get("Label"));
                            lexeme.put("formula", "(rdf:type " + single.candidates.get(i) + ")");
                            lexeme.put("features", " {score:" + Double.toString(single.candidatesScores.get(i)) + "} ");
                            lexeme.put("type", "ClassNoun");
                            c.put("type", "ClassNoun");
                            single.candidatesInfo.set(i, gson.toJson(c));
                            lexemes.add(gson.toJson(lexeme));
                            c.put("URI","(rdf:type ".concat(single.candidates.get(i)).concat(")"));
                            single.candidates.set(i,"(rdf:type " + single.candidates.get(i) + ")");
                            single.candidatesInfo.set(i,gson.toJson(c));
                        }
                        else if (String.join(" ", t).contains("ObjectProperty")) {
                            Type type = new TypeToken<Map<String, String>>() {
                            }.getType();
                            Map<String, String> c = this.gson.fromJson(single.candidatesInfo.get(i), type);
                            if (single.type == UnderspecifiedInfo.TermType.RELATION)
                                single.candidatesScores.set(i, single.candidatesScores.get(i) + 0.3);
                            lexeme.put("lexeme", c.get("Label"));
                            lexeme.put("formula", single.candidates.get(i));
                            lexeme.put("features", " {score:" + Double.toString(single.candidatesScores.get(i)) + "} ");
                            lexeme.put("type", "ObjectProp");
                            c.put("type", "Property");
                            single.candidatesInfo.set(i, gson.toJson(c));
                            lexemes.add(gson.toJson(lexeme));
                            lexeme.put("formula", "!" + single.candidates.get(i));
                            lexemes.add(gson.toJson(lexeme));
                            c.put("URI","!".concat(single.candidates.get(i)));
                            single.candidates.add("!" + single.candidates.get(i));
                            single.candidatesInfo.add(gson.toJson(c));
                            single.candidatesScores.add(single.candidatesScores.get(i));
                        }
                        else if (String.join(" ", t).contains("DataProperty")) {
                            Type type = new TypeToken<Map<String, String>>() {
                            }.getType();
                            Map<String, String> c = this.gson.fromJson(single.candidatesInfo.get(i), type);
                            if (single.type == UnderspecifiedInfo.TermType.RELATION)
                                single.candidatesScores.set(i, single.candidatesScores.get(i) + 0.3);
                            lexeme.put("lexeme", c.get("Label"));
                            lexeme.put("formula", single.candidates.get(i));
                            lexeme.put("features", " {score:" + Double.toString(single.candidatesScores.get(i)) + "} ");
                            lexeme.put("type", "DataProp");
                            lexemes.add(gson.toJson(lexeme));
                            lexeme.put("formula", "!" + single.candidates.get(i));
                            lexemes.add(gson.toJson(lexeme));
                            c.put("URI","!".concat(single.candidates.get(i)));
                            c.put("type", "Property");
                            single.candidatesInfo.set(i, gson.toJson(c));
                            single.candidates.add("!" + single.candidates.get(i));
                            single.candidatesInfo.add(gson.toJson(c));
                            single.candidatesScores.add(single.candidatesScores.get(i));
                        }
                    }
                }
                // Candidate is a class/property
                else if (single.candidates.get(i).contains(propertyClass))
                {
                    List<String> types = new ArrayList<>();
                    Map<String,String> lexeme = new HashMap<>();
                    Set<String> t = sparqlUtil.returnType(single.candidates.get(i), dbpediaUrl, false);
                    if (t!=null)
                        types.addAll(t);
                    if (types.size() > 0) {
                        Type type = new TypeToken<Map<String, String>>() {}.getType();
                        Map<String, String> c = this.gson.fromJson(single.candidatesInfo.get(i), type);
                        if (single.type == UnderspecifiedInfo.TermType.RELATION)
                            single.candidatesScores.set(i, single.candidatesScores.get(i) + 0.1);
                        lexeme.put("lexeme", c.get("Label"));
                        lexeme.put("formula", single.candidates.get(i));
                        lexeme.put("features", " {score:" + Double.toString(single.candidatesScores.get(i)) + "} ");
                        lexeme.put("type", "DataProp");
                        c.put("type", "Property");
                        single.candidatesInfo.set(i, gson.toJson(c));
                        lexemes.add(gson.toJson(lexeme));
                        lexeme.put("type", "ObjProp");
                        lexemes.add(gson.toJson(lexeme));
                        lexeme.put("formula", "!".concat(single.candidates.get(i)));
                        lexemes.add(gson.toJson(lexeme));
                        lexeme.put("type", "DataProp");
                        lexemes.add(gson.toJson(lexeme));
                        c.put("URI","!".concat(single.candidates.get(i)));
                        single.candidates.add("!" + single.candidates.get(i));
                        single.candidatesInfo.add(gson.toJson(c));
                        single.candidatesScores.add(single.candidatesScores.get(i));
                    }
                }
            }
            result.add(single);
        }
        updateLexicon(lexemes);
        return result;
    }

}
