package edu.stanford.nlp.sempre.roboy.error;

import com.google.gson.Gson;
import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.roboy.config.ConfigManager;
import edu.stanford.nlp.sempre.roboy.ErrorInfo;
import edu.stanford.nlp.sempre.roboy.utils.SparqlUtils;
import edu.stanford.nlp.sempre.roboy.utils.XMLReader;
import fig.basic.LogInfo;
import org.apache.commons.lang.WordUtils;

import java.util.*;

/**
 * Label Helper checks rdf:label [term] subject to resolve underspecified terms in the lexicon
 *
 * @author emlozin
 */
public class LabelRetriever extends KnowledgeRetriever {
    public static Gson gson = new Gson();

    public static XMLReader reader = new XMLReader();

    private SparqlUtils sparqlUtil = new SparqlUtils();

    public static String endpointUrl = new String();

    public LabelRetriever(){
        try {
            endpointUrl = ConfigManager.DB_SPARQL;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ErrorInfo analyze(Derivation dev) {
        ErrorInfo errorInfo = new ErrorInfo();
        String unknown = new String();
        String formula = dev.getFormula().toString();
        while (formula.contains("Open")){
            int start = formula.indexOf("Open")+"Open".length();
            int end = formula.indexOf("''",start);
            if (start > formula.length() || start < 0 || end < 0 ||end > formula.length())
                return errorInfo;
            unknown = formula.substring(start,end);
            String entity = unknown.substring(unknown.indexOf("'")+1);
            // Extract the results from XML now.
            Set<String> uri = sparqlUtil.returnURI(entity, endpointUrl, false);
            Set<String> uri_cap = sparqlUtil.returnURI( WordUtils.capitalize(entity), endpointUrl, false);
            if (uri == null)
                uri = uri_cap;
            else if (uri_cap!=null)
                uri.addAll(uri_cap);
            Map<String,String> single = new HashMap();
            if (uri != null) {
                single.put("Refcount", String.valueOf(new Double(1.0/uri.size())));
                for (String u: uri) {
                    single.put("URI", u);
                    // Get rid of categories
                    if (!u.contains("Category"))
                    {
                        Set<String> labels = sparqlUtil.returnLabel(u, endpointUrl, false);
                        if (labels == null)
                            labels = new HashSet<>(Arrays.asList(entity));
                        for (String l : labels) {
                            single.put("Label", l);
                            if (errorInfo.getCandidates().containsKey(entity)) {
                                errorInfo.getCandidates().get(entity).add(gson.toJson(single));
                                if (ConfigManager.DEBUG > 3)
                                    LogInfo.logs("Label: %s", gson.toJson(single));
                            } else {
                                errorInfo.getCandidates().put(entity, new ArrayList<>(Arrays.asList(gson.toJson(single))));
                                if (ConfigManager.DEBUG > 3)
                                    LogInfo.logs("Label: %s", gson.toJson(single));
                            }
                        }
                    }
                }
            }
            formula = formula.substring(end);
        }
        return errorInfo;
    }

}
