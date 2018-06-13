package edu.stanford.nlp.sempre.roboy.error;

import edu.stanford.nlp.sempre.roboy.UnderspecifiedInfo;
import edu.stanford.nlp.sempre.roboy.config.ConfigManager;
import edu.stanford.nlp.sempre.roboy.utils.SparqlUtils;

import java.io.PrintWriter;
import java.util.*;

import edu.stanford.nlp.sempre.roboy.utils.XMLReader;

import com.google.gson.Gson;
import fig.basic.IOUtils;
import fig.basic.LogInfo;

/**
 * Entity Helper use DBpedia Lookup APIs or similar API to resolve underspecified entities in the lexicon
 *
 * @author emlozin
 */
public class EntityRetriever extends KnowledgeRetriever {
    public static Gson gson = new Gson();               /**< Gson object */

    public static XMLReader reader = new XMLReader();   /**< XML reader helper */

    private SparqlUtils sparqlUtil = new SparqlUtils(); /**< SPARQL executor helper */

    public static String endpointUrl = new String();    /**< Endpoint URL for DBpedia*/

    public static List<String> keywords = new ArrayList(); /**< List of keywords to extract from XML*/

    public PrintWriter out;

    /**
     * Constructor
     */
    public EntityRetriever(){
        try {
            endpointUrl = ConfigManager.DB_SEARCH;
            keywords = Arrays.asList(ConfigManager.DB_KEYWORDS);
            out = IOUtils.openOutAppendHard("./resources_nlu/error_test/dbLexicon.txt");

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
        String url = endpointUrl.concat(entity);
        url = url.replace(" ","_");
        SparqlUtils.ServerResponse response = sparqlUtil.makeRequest(url);
        List<Map<String, String>> results = new ArrayList<>();
        if (response.getXml()!=null)
            results = reader.readEntityXml(response.getXml(),keywords);
        for (Map<String,String> c: results){
            String label = c.get("Label").toLowerCase();
            label = label.replaceAll("\\(","");
            label = label.replaceAll("\\)","");
            c.put("Label",label.toLowerCase());
            result.candidates.add(c.get("URI"));
            result.candidatesInfo.add(this.gson.toJson(c));
            if (ConfigManager.DEBUG > 3) {
                LogInfo.logs("Entity candidate: %s", c.toString());
            }
            out.println(c.toString());
        }
        return result;
    }

}
