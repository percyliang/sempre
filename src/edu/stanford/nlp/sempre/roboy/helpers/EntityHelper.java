package edu.stanford.nlp.sempre.roboy.helpers;

import edu.stanford.nlp.sempre.ErrorInfo;
import edu.stanford.nlp.sempre.ErrorValue;
import edu.stanford.nlp.sempre.Example;

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
            endpointUrl = prop.getProperty("Entity_Searcher");
            keywords = Arrays.asList(prop.getProperty("KEYWORDS").split(","));
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
            String url = endpointUrl.concat(entity);
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

    public ErrorInfo analyze(Example ex) {
        ErrorInfo errorInfo = new ErrorInfo();
        List <Map<String,String>> results = new ArrayList<Map<String, String>>();
        String entity = new String();
        for (int i = 0; i < ex.getPredDerivations().size(); i++){
            String formula = ex.getPredDerivations().get(i).getFormula().toString();
            if (formula.contains("OpenType")){
                entity = formula.substring(formula.indexOf("OpenType("),formula.substring(formula.indexOf("OpenType(")).indexOf(")"));
                makeRequest(entity);

                // Extract the results from XML now.
                ServerResponse response = makeRequest(entity);
                results = reader.readEntityXml(response.xml,keywords);

            }
            else{
                entity = "berlin";
                // Extract the results from XML now.
                ServerResponse response = makeRequest(entity);
                results = reader.readEntityXml(response.xml,keywords);
            }
        }
        String json = gson.toJson(results.get(0));
        System.out.println("Underspecified " + entity + "=" + json);
        errorInfo.underspecified.put(entity,json);
        return errorInfo;
    }

}
