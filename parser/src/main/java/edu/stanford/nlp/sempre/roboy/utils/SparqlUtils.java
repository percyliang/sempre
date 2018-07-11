package edu.stanford.nlp.sempre.roboy.utils;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.roboy.DatabaseInfo;
import edu.stanford.nlp.sempre.cache.StringCache;
import edu.stanford.nlp.sempre.cache.StringCacheUtils;

import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.sempre.roboy.config.ConfigManager;
import fig.basic.*;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.NamedNodeMap;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import com.google.common.collect.Lists;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;

import org.apache.jena.rdf.model.*;
import org.apache.jena.atlas.json.*;
import org.apache.jena.query.*;

/**
 * SPARQL utilities for quering some endpoint
 *
 * @author emlozin
 */

public class SparqlUtils {
    int connectTimeoutMs;
    int readTimeoutMs;

    public static Properties prop = new Properties();
    public static Gson gson = new Gson();
    public static XMLReader reader = new XMLReader();

    public SparqlUtils() {
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {return null;}
                public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType){}
                public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType){}
            }
        };
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        }
        catch(java.security.NoSuchAlgorithmException e) {}
        catch(java.security.KeyManagementException e) {}
    }

    public class ServerResponse {
        public ServerResponse(String xml) {
            this.xml = xml;
        }

        public ServerResponse(ErrorValue error) {
            this.error = error;
        }

        String xml;
        ErrorValue error;
        long timeMs;

        public String getXml() {
            return this.xml;
        }        ;

        public ErrorValue getError() {
            return this.error;
        };
    }

    // Form query based on triple form
    public Set<String> returnURI(String entity, String endpointUrl, boolean object){
        //System.out.println("Entity: "+entity);
        List<Map<String,String>> triples = new ArrayList();
        Map<String,String> t1 = new HashMap();
        t1.put("predicate","rdfs:label");
        t1.put("object","\"".concat(entity).concat("\"@en"));
        triples.add(t1);
        if (object == true) {
            Map<String, String> t2 = new HashMap();
            t2.put("predicate", "rdf:type");
            t2.put("object", "owl:Class");
            triples.add(t2);
        }
        Gson gson = new Gson();
        String json = gson.toJson((triples));
        try {
            String url = String.format("%s?default-graph-uri=http://dbpedia.org&query=%s&format=xml",
                    endpointUrl, URLEncoder.encode(formQuery(json), "UTF-8"));
            System.out.println("SPARQL query: "+formQuery(json));
            System.out.println("Query: "+url);
            ServerResponse response = makeRequest(url);

            List<Map<String,String>> list = new ArrayList<>();
            if (response.getXml() != null)
            {
                list = reader.readArrayXml(response.getXml());
            }
//            System.out.println("Query: "+list.toString());

            if (list.size()>0) {
                Set<String> labels = new HashSet<>();
                for (Map<String,String> map: list){
                    labels.addAll(map.values());
                }
                return labels;
            }
            else
                return null;
        }
        catch(UnsupportedEncodingException e){
            LogInfo.logs("WrongFormat of the input: %s", json);
        }
        return null;
    }

    // Form query based on triple form
    public Set<String> returnLabel(String entity, String endpointUrl, boolean object){
        //System.out.println("Entity: "+entity);
        List<Map<String,String>> triples = new ArrayList();
        Map<String,String> t1 = new HashMap();
        t1.put("predicate","rdfs:label");
        t1.put("subject",entity);
        triples.add(t1);
        Gson gson = new Gson();
        String json = gson.toJson((triples));
        try {
            String url = String.format("%s?default-graph-uri=http://dbpedia.org&query=%s&format=xml",
                    endpointUrl, URLEncoder.encode(formQuery(json,"en"), "UTF-8"));
            if(ConfigManager.DEBUG > 5) {
                LogInfo.logs("SPARQL query: %s", formQuery(json));
                LogInfo.logs("Query: %s", url);
            }
            ServerResponse response = makeRequest(url);

            List<Map<String,String>> list = new ArrayList<>();
            if (response.getXml() != null)
            {
                list = reader.readArrayXml(response.getXml());
            }
//            System.out.println("Query: "+list.toString());

            if (list.size()>0) {
                Set<String> labels = new HashSet<>();
                for (Map<String,String> map: list){
                    labels.addAll(map.values());
                }
                return labels;
            }
            else
                return null;
        }
        catch(UnsupportedEncodingException e){
            LogInfo.logs("WrongFormat of the input: %s", json);
        }
        return null;
    }


    // Form query based on triple form
    public Set<String> returnType(String entity, String endpointUrl, boolean object){
        //System.out.println("Entity: "+entity);
        List<Map<String,String>> triples = new ArrayList();
        Map<String,String> t1 = new HashMap();
        t1.put("predicate","rdf:type");
        t1.put("subject",entity);
        triples.add(t1);
        Gson gson = new Gson();
        String json = gson.toJson((triples));
        try {
            String url = String.format("%s?default-graph-uri=http://dbpedia.org&query=%s&format=xml",
                    endpointUrl, URLEncoder.encode(formQueryType(json), "UTF-8"));

            ServerResponse response = makeRequest(url);

            List<Map<String,String>> list = new ArrayList<>();
            if (response.getXml() != null)
            {
                list = reader.readArrayXml(response.getXml());
            }
            if(ConfigManager.DEBUG > 7) {
                LogInfo.logs("SPARQL query: %s", formQuery(json));
                LogInfo.logs("URL: %s", url);
                System.out.println("Result: "+response.getXml());
                System.out.println("Result: "+list.toString());
            }

            if (list.size()>0) {
                Set<String> labels = new HashSet<>();
                for (Map<String,String> map: list){
                    labels.addAll(map.values());
                }
                return labels;
            }
            else
                return null;
        }
        catch(UnsupportedEncodingException e){
            LogInfo.logs("WrongFormat of the input: %s", json);
        }
        return null;
    }

    // Form query based on triple form
    public String returnDescr(String entity, String endpointUrl){
        //System.out.println("Entity: "+entity);
        List<Map<String,String>> triples = new ArrayList();
        Map<String,String> t1 = new HashMap();
        t1.put("subject", entity);
        t1.put("predicate","rdfs:comment");
        triples.add(t1);
        Gson gson = new Gson();
        String json = gson.toJson((triples));
        try {
            String url = String.format("%s?default-graph-uri=http://dbpedia.org&query=%s&format=xml",
                    endpointUrl, URLEncoder.encode(formQuery(json,"en"), "UTF-8"));
            //System.out.println("SPARQL query: "+formQuery(json,"en"));
//            System.out.println("Query: "+url);
            ServerResponse response = makeRequest(url);

            List<List<String>> list = new ArrayList<>();

            if (response.getXml() != null) {
                list = reader.readLiteralXml(response.getXml());
                if (list!=null && list.size()>0) {
                    //System.out.println("Query: " + list.get(0));
                    return list.get(0).get(0);
                }
                else
                    return null;
            }
        }
        catch(UnsupportedEncodingException e){
            LogInfo.logs("WrongFormat of the input: %s", json);
        }
        return null;
    }

    // Form query based on triple form
    public String formQueryType(String json) {
        StringBuilder query_inside = new StringBuilder();
        StringBuilder var = new StringBuilder();
        String[] keys = {"subject","predicate","object"};

        // Read triples from json
        List<Map<String, String>> triple_list = new ArrayList();
        Type t = new TypeToken<List<Map<String, String>>>(){}.getType();
        triple_list = gson.fromJson(json, t);

        for (Map<String, String> triple : triple_list)
        {
            int index = 0;
            // Read single triples from json
            for (String key :keys) {
                if (triple.containsKey(key)) {
                    query_inside.append(triple.get(key));
                    query_inside.append(" ");
                } else {
                    query_inside.append("?x");
                    query_inside.append(index);
                    query_inside.append(" ");
                    var.append("?x");
                    var.append(index);
                    var.append(" ");
                }
                index++;
            }
            query_inside.append(".\n");
        }
        return DatabaseInfo.getPrefixes(query_inside.toString()).concat(
                String.format("SELECT DISTINCT %s FROM <http://www.w3.org/2002/07/owl#>\n WHERE{\n%s}\nLIMIT 10", var.toString(), query_inside.toString()));
    }

    // Form query based on triple form
    public String formQuery(String json) {
        StringBuilder query_inside = new StringBuilder();
        StringBuilder var = new StringBuilder();
        String[] keys = {"subject","predicate","object"};

        // Read triples from json
        List<Map<String, String>> triple_list = new ArrayList();
        Type t = new TypeToken<List<Map<String, String>>>(){}.getType();
        triple_list = gson.fromJson(json, t);

        for (Map<String, String> triple : triple_list)
        {
            int index = 0;
            // Read single triples from json
            for (String key :keys) {
                if (triple.containsKey(key)) {
                    query_inside.append(triple.get(key));
                    query_inside.append(" ");
                } else {
                    query_inside.append("?x");
                    query_inside.append(index);
                    query_inside.append(" ");
                    var.append("?x");
                    var.append(index);
                    var.append(" ");
                }
                index++;
            }
            query_inside.append(".\n");
        }
        return DatabaseInfo.getPrefixes(query_inside.toString()).concat(
                String.format("SELECT DISTINCT %sWHERE{\n%s}\nLIMIT 10", var.toString(), query_inside.toString()));
    }

    // Form query based on triple form
    public String formQuery(String json, String lang) {
        StringBuilder query_inside = new StringBuilder();
        StringBuilder var = new StringBuilder();
        String[] keys = {"subject","predicate","object"};

        // Read triples from json
        List<Map<String, String>> triple_list = new ArrayList();
        Type t = new TypeToken<List<Map<String, String>>>(){}.getType();
        triple_list = gson.fromJson(json, t);

        for (Map<String, String> triple : triple_list)
        {
            int index = 0;
            // Read single triples from json
            for (String key :keys) {
                if (triple.containsKey(key)) {
                    query_inside.append(triple.get(key));
                    query_inside.append(" ");
                } else {
                    query_inside.append("?x");
                    query_inside.append(index);
                    query_inside.append(" ");
                    var.append("?x");
                    var.append(index);
                    var.append(" ");
                }
                index++;
            }
            query_inside.append(".\n");
        }
        return DatabaseInfo.getPrefixes(query_inside.toString()).concat(
                String.format("SELECT DISTINCT %sWHERE{\n%s\nFILTER (lang(%s) = '%s'). }\nLIMIT 10",
                        var.toString(), query_inside.toString(), var.toString(), lang));
    }

    // Make a request to the given endpoint.
    // Return the XML.
    public ServerResponse makeRequest(String query) {
        if (query == null)
            throw new RuntimeException("No SPARQL endpoint url specified");

        try {
            URLConnection conn = new URL(query).openConnection();
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(readTimeoutMs);
            InputStream in = conn.getInputStream();

            // Read the response
            StringBuilder buf = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null)
                buf.append(line);

            // Check for blatant errors.
            String result = buf.toString();
            if (result.length() == 0)
                return new ServerResponse(ErrorValue.empty);
            if (result.startsWith("<!DOCTYPE html>"))
                return new ServerResponse(ErrorValue.badFormat);

            return new ServerResponse(buf.toString());
        } catch (SocketTimeoutException e) {
            return new ServerResponse(ErrorValue.timeout);
        } catch (FileNotFoundException e) {
            return new ServerResponse(ErrorValue.empty);
        } catch (IOException e) {
            // Sometimes the SPARQL server throws a 408 to signify a server timeout.
            if (e.toString().contains("HTTP response code: 408"))
                return new ServerResponse(ErrorValue.server408);
            if (e.toString().contains("HTTP response code: 400"))
                return new ServerResponse(ErrorValue.server400);
            if (e.toString().contains("HTTP response code: 500"))
                return new ServerResponse(ErrorValue.server500);
            if (e.toString().contains("HTTP response code: 503"))
                return new ServerResponse(ErrorValue.empty);
            throw new RuntimeException(e);  // Haven't seen this happen yet...
        }
    }

}