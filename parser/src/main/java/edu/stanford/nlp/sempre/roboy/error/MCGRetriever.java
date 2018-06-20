package edu.stanford.nlp.sempre.roboy.error;

import edu.stanford.nlp.sempre.roboy.UnderspecifiedInfo;
import edu.stanford.nlp.sempre.roboy.config.ConfigManager;
import edu.stanford.nlp.sempre.roboy.utils.SparqlUtils;

import java.io.PrintWriter;
import java.util.*;

import java.lang.reflect.Type;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import edu.stanford.nlp.sempre.roboy.utils.XMLReader;
import fig.basic.IOUtils;
import fig.basic.LogInfo;

/**
 * Entity Helper use Microsoft Concept Graph APIs to resolve underspecified types in the lexicon
 *
 * @author emlozin
 */
public class MCGRetriever extends KnowledgeRetriever {
    public static Gson gson = new Gson();               /**< Gson object */

    public static XMLReader reader = new XMLReader();   /**< XML reader helper */

    private SparqlUtils sparqlUtil = new SparqlUtils(); /**< SPARQL executor helper */

    public static String endpointUrl = new String();    /**< Endpoint URL for Microsoft Concept Graph*/

    public static String dbpediaUrl = new String();     /**< Endpoint URL for DBpedia*/

    public PrintWriter out;
    /**
     * Constructor
     */
    public MCGRetriever(){
        try {
            endpointUrl = ConfigManager.MCG_SEARCH;
            dbpediaUrl = ConfigManager.DB_SPARQL;
            out = IOUtils.openOutAppendHard("./resources_nlu/error_test/mcgLexicon.txt");
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Analyzer retrieving new candidates
     *
     * @param underTerm   information about the candidates for underspecified term
     */
    public UnderspecifiedInfo analyze(UnderspecifiedInfo underTerm) {
        String entity = underTerm.term;
        UnderspecifiedInfo result = new UnderspecifiedInfo(entity, underTerm.type);
        String url = (endpointUrl.concat(entity.replace(" ","+"))).concat("&topK=10");
        SparqlUtils.ServerResponse response = sparqlUtil.makeRequest(url);
        Type t = new TypeToken<Map<String, String>>(){}.getType();
        Map<String,Double> results = gson.fromJson(response.getXml(), t);
        if (results!=null) {
            for (String res : results.keySet()) {
                Map<String, String> single = new HashMap();
                Set<String> uri = sparqlUtil.returnURI(res, dbpediaUrl, false);
                if (uri != null) {
                    String label = res.replaceAll("\\(","");
                    label = label.replaceAll("\\)","");
                    single.put("Label", label.toLowerCase());
                    single.put("Refcount", String.valueOf(results.get(res)));
                    for (String u : uri) {
                        single.put("URI", u);
                        result.candidates.add(single.get("URI"));
                        result.candidatesInfo.add(gson.toJson(single));
                        if (ConfigManager.DEBUG > 3){
                            LogInfo.logs("MCG candidate: %s", single.toString());
                        }
                        out.println(single.toString());
                    }
                }
            }
        }
        return result;
    }

}
