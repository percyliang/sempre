package edu.stanford.nlp.sempre.roboy.error;

import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.roboy.ErrorInfo;
import edu.stanford.nlp.sempre.roboy.utils.SparqlUtils;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.sempre.roboy.utils.XMLReader;

import com.google.gson.Gson;
import fig.basic.LogInfo;

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
        ErrorInfo errorInfo = new ErrorInfo();
        List <Map<String,String>> results = new ArrayList<Map<String, String>>();
        String entity = new String();
        String formula = dev.getFormula().toString();
        while (formula.contains("OpenType")){
            int start = formula.indexOf("OpenType(")+"OpenType(".length();
            int end = formula.indexOf(")",formula.indexOf("OpenType("));
            if (start > formula.length() || start < 0 || end < 0 ||end > formula.length())
                return errorInfo;
            entity = formula.substring(start,end);
            //System.out.println("Checking entity:" + entity);
            // Extract the results from XML now.
            String url = endpointUrl.concat(entity);
            url = url.replace(" ","_");
            SparqlUtils sparql = new SparqlUtils();
            SparqlUtils.ServerResponse response = sparql.makeRequest(url);
            results = reader.readEntityXml(response.getXml(),keywords);
            for (Map<String,String> c: results){
                if (errorInfo.getCandidates().containsKey(entity)) {
                    errorInfo.getCandidates().get(entity).add(gson.toJson(c));
//                    LogInfo.logs("Entity: %s",gson.toJson(c));
                }
                else {
                    errorInfo.getCandidates().put(entity, new ArrayList<>(Arrays.asList(gson.toJson(c))));
//                    LogInfo.logs("Entity: %s",gson.toJson(c));
                }
            }
            formula = formula.substring(end);
        }
        return errorInfo;
    }

}
