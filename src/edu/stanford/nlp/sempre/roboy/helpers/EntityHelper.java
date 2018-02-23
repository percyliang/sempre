package edu.stanford.nlp.sempre.roboy.helpers;

import edu.stanford.nlp.sempre.ErrorInfo;
import edu.stanford.nlp.sempre.ErrorValue;
import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.SimpleLexicon;
import edu.stanford.nlp.sempre.roboy.utils.SPARQLUtils;

import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

import java.io.*;
import java.util.*;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import com.google.common.collect.Lists;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import edu.stanford.nlp.sempre.roboy.utils.XMLReader;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.reflect.TypeToken;

/**
 * Entity Helper use Entity Searcher APIs to resolve underspecified entities in the lexicon
 *
 * @author emlozin
 */
public class EntityHelper extends KnowledgeHelper {
    int connectTimeoutMs;
    int readTimeoutMs;

    public static Properties prop = new Properties();
    public static Gson gson = new Gson();

    private Map<String, String> entities;
    public static XMLReader reader = new XMLReader();

    public static String endpointUrl = new String();
    public static List<String> keywords = new ArrayList();

    public EntityHelper(){
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
        if (formula.contains("OpenEntity")){
            int start = formula.indexOf("OpenEntity(")+"OpenEntity(".length();
            int end = formula.indexOf(")");
            entity = formula.substring(start,end);
            System.out.println("Checking entity:" + entity);
            // Extract the results from XML now.
            String url = endpointUrl.concat(entity);
            url = url.replace(" ","_");
            SPARQLUtils sparql = new SPARQLUtils();
            SPARQLUtils.ServerResponse response = sparql.makeRequest(url);
            results = reader.readEntityXml(response.getXml(),keywords);
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
            System.out.println(json);
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
