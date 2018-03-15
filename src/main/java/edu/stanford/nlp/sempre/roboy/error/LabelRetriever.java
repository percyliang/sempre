package edu.stanford.nlp.sempre.roboy.error;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.roboy.ErrorInfo;
import edu.stanford.nlp.sempre.roboy.utils.SparqlUtils;
import edu.stanford.nlp.sempre.roboy.utils.XMLReader;
import fig.basic.LogInfo;
import org.apache.commons.lang.WordUtils;

import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Label Helper checks rdf:label [term] subject to resolve underspecified terms in the lexicon
 *
 * @author emlozin
 */
public class LabelRetriever extends KnowledgeRetriever {
    public static Properties prop = new Properties();
    public static Gson gson = new Gson();

    public static XMLReader reader = new XMLReader();

    private SparqlUtils sparqlUtil = new SparqlUtils();

    public static String endpointUrl = new String();
    public static List<String> keywords = new ArrayList();

    public LabelRetriever(){
        try {
            InputStream input = new FileInputStream("config.properties");
            prop.load(input);
            endpointUrl = prop.getProperty("DB_SPARQL");
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
            int end = formula.indexOf("\''",start);
            if (start > formula.length() || start < 0 || end < 0 ||end > formula.length())
                return errorInfo;
            unknown = formula.substring(start,end);
            String entity = unknown.substring(unknown.indexOf("\'")+1);
            // Extract the results from XML now.
            Set<String> uri = sparqlUtil.returnURI(entity, endpointUrl, false);
            Set<String> uri_cap = sparqlUtil.returnURI( WordUtils.capitalize(entity), endpointUrl, false);
            if (uri == null)
                uri = uri_cap;
            else if (uri_cap!=null)
                uri.addAll(uri_cap);
            Map<String,String> single = new HashMap();
            if (uri != null) {
                single.put("Label", entity);
                single.put("Refcount", String.valueOf(new Double(1.0/uri.size())));
                for (String u: uri) {
                    single.put("URI", u);
                    if (errorInfo.getCandidates().containsKey(entity)) {
                        errorInfo.getCandidates().get(entity).add(gson.toJson(single));
                        //LogInfo.logs("Label: %s",gson.toJson(single));
                    }
                    else{
                        errorInfo.getCandidates().put(entity, new ArrayList<>(Arrays.asList(gson.toJson(single))));
                        //LogInfo.logs("Label: %s",gson.toJson(single));
                    }
                }
            }
            formula = formula.substring(end);
        }
        return errorInfo;
    }

}
