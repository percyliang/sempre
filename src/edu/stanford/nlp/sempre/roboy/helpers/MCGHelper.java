package edu.stanford.nlp.sempre.roboy.helpers;

import edu.stanford.nlp.sempre.ErrorInfo;
import edu.stanford.nlp.sempre.ErrorValue;
import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.SimpleLexicon;

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

    private Map<String, String> entities;

    public static String endpointUrl = new String();
    public static List<String> keywords = new ArrayList();

    public MCGHelper(){
        try {
            InputStream input = new FileInputStream("config.properties");
            prop.load(input);
            endpointUrl = prop.getProperty("MCG_SEARCH");
            keywords = Arrays.asList(prop.getProperty("MCG_KEYWORDS").split(","));
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public class ServerResponse {
        public ServerResponse(String xml) { this.xml = xml; }
        public ServerResponse(ErrorValue error) { this.error = error; }
        String xml;
        ErrorValue error;
        long timeMs;
    }

    // Make a request to the given SPARQL endpoint.
    // Return the XML.
    public ServerResponse makeRequest(String entity) {
        if (entity == null)
            throw new RuntimeException("No entity specified");
        try {
            String url = (endpointUrl.concat(entity)).concat("&topK=10");
            URLConnection conn = new URL(url).openConnection();
            conn.setConnectTimeout(this.connectTimeoutMs);
            conn.setReadTimeout(this.readTimeoutMs);
            InputStream in = conn.getInputStream();

            // Read the response
            StringBuilder buf = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null)
                buf.append(line);
            return new ServerResponse(buf.toString());
        } catch (SocketTimeoutException e) {
            return new ServerResponse(ErrorValue.timeout);
        } catch (IOException e) {
            // Sometimes the SPARQL server throws a 408 to signify a server timeout.
            if (e.toString().contains("HTTP response code: 408"))
                return new ServerResponse(ErrorValue.server408);
            if (e.toString().contains("HTTP response code: 500"))
                return new ServerResponse(ErrorValue.server500);
            throw new RuntimeException(e);  // Haven't seen this happen yet...
        }
    }

    public ErrorInfo analyze(Derivation dev) {
        ErrorInfo errorInfo = new ErrorInfo();
        List <Map<String,Double>> results = new ArrayList<Map<String, Double>>();
        String entity = new String();
        String formula = dev.getFormula().toString();
        if (formula.contains("OpenType")){
            entity = formula.substring(formula.indexOf("OpenType("),formula.substring(formula.indexOf("OpenType(")).indexOf(")"));
            makeRequest(entity);
            // Extract the results from XML now.
            ServerResponse response = makeRequest(entity);
            System.out.println(response.xml);
            Type t = new TypeToken<Map<String, Double>>(){}.getType();
            results.add(gson.fromJson(response.xml, t));

        }
        else{
            entity = "berlin";
            // Extract the results from XML now.
            ServerResponse response = makeRequest(entity);
            Type t = new TypeToken<Map<String, String>>(){}.getType();
            results.add(gson.fromJson(response.xml, t));
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
