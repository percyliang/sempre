package edu.stanford.nlp.sempre.roboy.helpers;

import edu.stanford.nlp.sempre.ErrorInfo;
import edu.stanford.nlp.sempre.ErrorValue;
import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.SimpleLexicon;
import edu.stanford.nlp.sempre.roboy.utils.SparqlUtils;

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

import java.lang.reflect.Type;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.reflect.TypeToken;

/**
 * Entity Helper use Microsoft Concept Graph APIs to resolve underspecified types in the lexicon
 *
 * @author emlozin
 */
public class MCGHelper extends KnowledgeHelper {
    int connectTimeoutMs;
    int readTimeoutMs;

    public static Properties prop = new Properties();
    public static Gson gson = new Gson();

    private SparqlUtils sparqlUtil = new SparqlUtils();

    private Map<String, String> entities;

    public final String endpointUrl;
    public final String dbpediaUrl;
    public final List<String> keywords;

    public MCGHelper(){
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
        String entity = new String();
        String formula = dev.getFormula().toString();
        if (formula.contains("OpenType")){
            entity = formula.substring(formula.indexOf("OpenType("),formula.substring(formula.indexOf("OpenType(")).indexOf(")"));
            // Extract the results from XML now.
            String url = (endpointUrl.concat(entity)).concat("&topK=10");
            SparqlUtils.ServerResponse response = sparqlUtil.makeRequest(url);
            Type t = new TypeToken<Map<String, String>>(){}.getType();
            results = gson.fromJson(response.getXml(), t);
            List<Map<String,String>> labels = new ArrayList();
            for (String result : results.keySet()) {
                Map<String,String> single = new HashMap();
                List<String> uri = sparqlUtil.returnURI(result, dbpediaUrl);
                if (uri != null) {
                    single.put("Label", result);
                    single.put("Refcount", String.valueOf(results.get(result)));
                    for (String u: uri) {
                        single.put("URI", u);
                        labels.add(single);
                    }
                }
            }
            String json = gson.toJson(labels.get(0));
            System.out.println(json);
            dev.getErrorInfo().underspecified.put(entity,json);

            for (int j = 0; j < labels.size(); j++) {
                Map<String, String> lexeme = new HashMap();
                lexeme.put("lexeme", labels.get(j).get("Label"));
                lexeme.put("formula", labels.get(j).get("URI"));
                lexeme.put("type", "ClassNoun");
                String lexical_entry = gson.toJson(lexeme);
                //dev.getErrorInfo().underspecified.put(entity, json);
                SimpleLexicon.getSingleton().add(lexical_entry);
                lexeme.put("lexeme", entity);
                lexical_entry = gson.toJson(lexeme);
                //dev.getErrorInfo().underspecified.put(entity, json);
                SimpleLexicon.getSingleton().add(lexical_entry);
                //System.out.println(lexical_entry);
            }

        }
        else{
            entity = "berlin";
            // Extract the results from XML now.
            String url = (endpointUrl.concat(entity)).concat("&topK=10");
            SparqlUtils.ServerResponse response = sparqlUtil.makeRequest(url);
            Type t = new TypeToken<Map<String, String>>(){}.getType();
            results = gson.fromJson(response.getXml(), t);
            List<Map<String,String>> labels = new ArrayList();
            for (String result : results.keySet()) {
                Map<String,String> single = new HashMap();
                List<String> uri = sparqlUtil.returnURI(result, dbpediaUrl);
                if (uri != null) {
                    single.put("Label", result);
                    single.put("Refcount", String.valueOf(results.get(result)));
                    for (String u: uri) {
                        single.put("URI", u);
                        labels.add(single);
                    }
                }
            }
            String json = gson.toJson(labels.get(0));
            System.out.println(json);
            //dev.getErrorInfo().underspecified.put(entity,json);

            for (int j = 0; j < labels.size(); j++) {
                Map<String, String> lexeme = new HashMap();
                lexeme.put("lexeme", labels.get(j).get("Label"));
                lexeme.put("formula", labels.get(j).get("URI"));
                lexeme.put("type", "ClassNoun");
                String lexical_entry = gson.toJson(lexeme);
                //dev.getErrorInfo().underspecified.put(entity, json);
                SimpleLexicon.getSingleton().add(lexical_entry);
                lexeme.put("lexeme", entity);
                lexical_entry = gson.toJson(lexeme);
                //dev.getErrorInfo().underspecified.put(entity, json);
                SimpleLexicon.getSingleton().add(lexical_entry);
                //System.out.println(lexical_entry);
            }
        }
//        String json = gson.toJson(results.get(0));
//        errorInfo.underspecified.put(entity,json);
//
//        if (results.size()>0){
//            for (int i = 0; i < results.size(); i++) {
//                Map<String, String> lexeme = new HashMap();
//                lexeme.put("lexeme", results.get(i).get("Key"));
//                lexeme.put("formula", results.get(i).get("Value"));
//                lexeme.put("type", "ClassNoun");
//                String lexical_entry = gson.toJson(lexeme);
//                errorInfo.underspecified.put(entity, json);
//                SimpleLexicon.getSingleton().add(lexical_entry);
//                lexeme.put("lexeme", entity);
//                lexical_entry = gson.toJson(lexeme);
//                errorInfo.underspecified.put(entity, json);
//                SimpleLexicon.getSingleton().add(lexical_entry);
//            }
//            List<SimpleLexicon.Entry> entries = SimpleLexicon.getSingleton().lookup(entity);
//        }
        return errorInfo;
    }

}
