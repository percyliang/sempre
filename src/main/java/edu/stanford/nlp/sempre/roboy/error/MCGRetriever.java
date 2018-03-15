package edu.stanford.nlp.sempre.roboy.error;

import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.roboy.ErrorInfo;
import edu.stanford.nlp.sempre.roboy.utils.SparqlUtils;

import java.io.*;
import java.util.*;

import java.lang.reflect.Type;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fig.basic.LogInfo;

/**
 * Entity Helper use Microsoft Concept Graph APIs to resolve underspecified types in the lexicon
 *
 * @author emlozin
 */
public class MCGRetriever extends KnowledgeRetriever {
    public static Properties prop = new Properties();
    public static Gson gson = new Gson();

    private SparqlUtils sparqlUtil = new SparqlUtils();

    public final String endpointUrl;
    public final String dbpediaUrl;
    public final List<String> keywords;

    public MCGRetriever(){
        try {
            InputStream input = new FileInputStream("config.properties");
            prop.load(input);
            endpointUrl = prop.getProperty("MCG_SEARCH");
            dbpediaUrl = prop.getProperty("DB_SPARQL");
            keywords = Arrays.asList(prop.getProperty("MCG_KEYWORDS").split(","));
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ErrorInfo analyze(Derivation dev) {
        ErrorInfo errorInfo = new ErrorInfo();
        Map<String,Double> results = new HashMap();
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
            String url = (endpointUrl.concat(entity.replace(" ","+"))).concat("&topK=10");
            SparqlUtils.ServerResponse response = sparqlUtil.makeRequest(url);
            Type t = new TypeToken<Map<String, String>>(){}.getType();
            results = gson.fromJson(response.getXml(), t);
            List<Map<String,String>> labels = new ArrayList();
            for (String result : results.keySet()) {
                Map<String,String> single = new HashMap();
                Set<String> uri = sparqlUtil.returnURI(result, dbpediaUrl, false);
                if (uri != null) {
                    single.put("Label", result);
                    single.put("Refcount", String.valueOf(results.get(result)));
                    for (String u: uri) {
                        single.put("URI", u);
                        labels.add(single);
                    }
                }
            }
            for (Map<String,String> c: labels){
                if (errorInfo.getCandidates().containsKey(entity)) {
                    if (!errorInfo.getCandidates().get(entity).contains(gson.toJson(c))) {
                        errorInfo.getCandidates().get(entity).add(gson.toJson(c));
                        //LogInfo.logs("MCG: %s", gson.toJson(c));
                    }
                    //else
                    //    LogInfo.logs("Repeat MCG: %s", gson.toJson(c));
                }
                else {
                    errorInfo.getCandidates().put(entity, new ArrayList<>(Arrays.asList(gson.toJson(c))));
                    //LogInfo.logs("MCG: %s",gson.toJson(c));
                }
            }
            formula = formula.substring(end);
        }
        return errorInfo;
    }

}
