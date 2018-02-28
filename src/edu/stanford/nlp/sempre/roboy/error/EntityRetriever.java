package edu.stanford.nlp.sempre.roboy.error;

import edu.stanford.nlp.sempre.ErrorInfo;
import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.SimpleLexicon;
import edu.stanford.nlp.sempre.roboy.utils.SparqlUtils;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.sempre.roboy.utils.XMLReader;

import com.google.gson.Gson;

/**
 * Entity Helper use Entity Searcher APIs to resolve underspecified entities in the lexicon
 *
 * @author emlozin
 */
public class EntityRetriever extends KnowledgeRetriever {
    public static Properties prop = new Properties();
    public static Gson gson = new Gson();

    private Map<String, String> entities;
    public static XMLReader reader = new XMLReader();

    public static String endpointUrl = new String();
    public static List<String> keywords = new ArrayList();

    public EntityRetriever(){
        try {
            InputStream input = new FileInputStream("config.properties");
            prop.load(input);
            endpointUrl = prop.getProperty("DB_SEARCH");
            keywords = Arrays.asList(prop.getProperty("DB_KEYWORDS").split(","));
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ErrorInfo analyze(Derivation dev) {
        List <Map<String,String>> results = new ArrayList<Map<String, String>>();
        String entity = new String();
        String formula = dev.getFormula().toString();
        //System.out.println("Formula:" + formula);
        while (formula.contains("OpenType")){
            int start = formula.indexOf("OpenType(")+"OpenType(".length();
            int end = formula.indexOf(")",formula.indexOf("OpenType("));
            if (start > formula.length() || start < 0 || end < 0 ||end > formula.length())
                return dev.getErrorInfo();
            entity = formula.substring(start,end);
            //System.out.println("Checking entity:" + entity);
            // Extract the results from XML now.
            String url = endpointUrl.concat(entity);
            url = url.replace(" ","_");
            SparqlUtils sparql = new SparqlUtils();
            SparqlUtils.ServerResponse response = sparql.makeRequest(url);
            results = reader.readEntityXml(response.getXml(),keywords);
            formula = formula.substring(end);
        }
//        else{
//            entity = "berlin";
//            // Extract the results from XML now.
//            ServerResponse response = makeRequest(entity);
//            results = reader.readEntityXml(response.xml,keywords);
//        }

        if (results.size() > 0){
            String json = gson.toJson(results.get(0));
            dev.getErrorInfo().underspecified.put(entity,json);
            //System.out.println(json);
            for (int j = 0; j < results.size(); j++) {
                Map<String, String> lexeme = new HashMap();
                lexeme.put("lexeme", results.get(j).get("Label"));
                lexeme.put("formula", results.get(j).get("URI"));
                lexeme.put("type", "NamedEntity");
                String lexical_entry = gson.toJson(lexeme);
                //dev.getErrorInfo().underspecified.put(entity, json);
                SimpleLexicon.getSingleton().add(lexical_entry);
                lexeme.put("lexeme", entity);
                lexical_entry = gson.toJson(lexeme);
                //dev.getErrorInfo().underspecified.put(entity, json);
                SimpleLexicon.getSingleton().add(lexical_entry);
            }
            List<SimpleLexicon.Entry> entries = SimpleLexicon.getSingleton().lookup(entity);
        }
        return dev.getErrorInfo();
    }

}
