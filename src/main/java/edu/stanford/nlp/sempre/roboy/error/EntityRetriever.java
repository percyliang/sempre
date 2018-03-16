package edu.stanford.nlp.sempre.roboy.error;

import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.roboy.config.ConfigManager;
import edu.stanford.nlp.sempre.roboy.ErrorInfo;
import edu.stanford.nlp.sempre.roboy.utils.SparqlUtils;

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
    public static Gson gson = new Gson();

    public static XMLReader reader = new XMLReader();

    public static String endpointUrl = new String();
    public static List<String> keywords = new ArrayList();

    public EntityRetriever(){
        try {
            endpointUrl = ConfigManager.DB_SEARCH;
            keywords = Arrays.asList(ConfigManager.DB_KEYWORDS);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ErrorInfo analyze(Derivation dev) {
        ErrorInfo errorInfo = new ErrorInfo();
        List <Map<String,String>> results = new ArrayList<Map<String, String>>();
        String unknown = new String();
        String formula = dev.getFormula().toString();
        while (formula.contains("Open")){
            int start = formula.indexOf("Open")+"Open".length();
            int end = formula.indexOf("\''",start);
            if (start > formula.length() || start < 0 || end < 0 ||end > formula.length())
                return errorInfo;
            unknown = formula.substring(start,end);
            String entity = unknown.substring(unknown.indexOf("\'")+1);
            // Extract the results from XML now.
            String url = endpointUrl.concat(entity);
            url = url.replace(" ","_");
            SparqlUtils sparql = new SparqlUtils();
            SparqlUtils.ServerResponse response = sparql.makeRequest(url);
            if (response.getXml()!=null)
                results = reader.readEntityXml(response.getXml(),keywords);
            for (Map<String,String> c: results){
                if (errorInfo.getCandidates().containsKey(entity)) {
                    errorInfo.getCandidates().get(entity).add(gson.toJson(c));
                    if (ConfigManager.DEBUG > 3) {
                        LogInfo.logs("Entity: %s", gson.toJson(c));
                    }
                }
                else {
                    errorInfo.getCandidates().put(entity, new ArrayList<>(Arrays.asList(gson.toJson(c))));
                    if (ConfigManager.DEBUG > 3) {
                        LogInfo.logs("Entity: %s", gson.toJson(c));
                    }
                }
            }
            formula = formula.substring(end);
        }
        return errorInfo;
    }

}
