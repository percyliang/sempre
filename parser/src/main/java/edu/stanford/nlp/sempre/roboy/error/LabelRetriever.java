package edu.stanford.nlp.sempre.roboy.error;

import com.google.gson.Gson;
import edu.stanford.nlp.sempre.roboy.UnderspecifiedInfo;
import edu.stanford.nlp.sempre.roboy.config.ConfigManager;
import edu.stanford.nlp.sempre.roboy.utils.SparqlUtils;
import edu.stanford.nlp.sempre.roboy.utils.XMLReader;
import fig.basic.IOUtils;
import fig.basic.LogInfo;
import org.apache.commons.lang.WordUtils;

import java.io.PrintWriter;
import java.util.*;

/**
 * Label Helper checks rdf:label [term] subject to resolve underspecified terms in the lexicon
 *
 * @author emlozin
 */
public class LabelRetriever extends KnowledgeRetriever {
    public static Gson gson = new Gson();               /**< Gson object */

    public static XMLReader reader = new XMLReader();   /**< XML reader helper */

    private SparqlUtils sparqlUtil = new SparqlUtils(); /**< SPARQL executor helper */

    public static String endpointUrl = new String();    /**< Endpoint URL */

    public PrintWriter out;
    /**
     * Constructor
     */
    public LabelRetriever(){
        try {
            endpointUrl = ConfigManager.DB_SPARQL;
            out = IOUtils.openOutAppendHard("./resources_nlu/error_test/lbLexicon.txt");
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
        // Extract the results from XML now.
        Set<String> uri = sparqlUtil.returnURI(entity, endpointUrl, false);
        Set<String> uri_cap = sparqlUtil.returnURI( WordUtils.capitalize(entity), endpointUrl, false);
        if (uri == null)
            uri = uri_cap;
        else if (uri_cap!=null)
            uri.addAll(uri_cap);
        if (uri != null) {
            Map<String,String> single = new HashMap();
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
                        result.candidates.add(u);
                        result.candidatesInfo.add(this.gson.toJson(single));
                        if (ConfigManager.DEBUG > 3){
                            LogInfo.logs("Label candidate: %s", single.toString());
                        }
                        out.println(single.toString());
                    }
                }
            }
        }
        return result;
    }

}
