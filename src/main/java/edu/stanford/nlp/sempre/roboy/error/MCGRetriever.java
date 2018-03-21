package edu.stanford.nlp.sempre.roboy.error;

import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.roboy.UnspecInfo;
import edu.stanford.nlp.sempre.roboy.config.ConfigManager;
import edu.stanford.nlp.sempre.roboy.ErrorInfo;
import edu.stanford.nlp.sempre.roboy.utils.SparqlUtils;

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
            endpointUrl = ConfigManager.MCG_SEARCH;
            dbpediaUrl = ConfigManager.DB_SPARQL;
            keywords = Arrays.asList(ConfigManager.MCG_KEYWORDS);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public UnspecInfo analyze(UnspecInfo underTerm) {
        String entity = underTerm.term;
        UnspecInfo result = new UnspecInfo(entity, underTerm.type);
        String url = (endpointUrl.concat(entity.replace(" ","+"))).concat("&topK=10");
        SparqlUtils.ServerResponse response = sparqlUtil.makeRequest(url);
        Type t = new TypeToken<Map<String, String>>(){}.getType();
        Map<String,Double> results = gson.fromJson(response.getXml(), t);
        if (results!=null) {
            for (String res : results.keySet()) {
                Map<String, String> single = new HashMap();
                Set<String> uri = sparqlUtil.returnURI(res, dbpediaUrl, false);
                if (uri != null) {
                    single.put("Label", res);
                    single.put("Refcount", String.valueOf(results.get(res)));
                    for (String u : uri) {
                        single.put("URI", u);
                        result.candidates.add(single.get("URI"));
                        result.candidatesInfo.add(gson.toJson(single));
                        if (ConfigManager.DEBUG > 3){
                            LogInfo.logs("MCG candidate: %s", single.toString());
                        }
                    }
                }
            }
        }
        return result;
    }

}
