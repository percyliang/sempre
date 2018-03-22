package edu.stanford.nlp.sempre.roboy.utils;

import edu.stanford.nlp.sempre.ErrorValue;
import edu.stanford.nlp.sempre.roboy.DatabaseInfo;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import fig.basic.LogInfo;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.google.gson.Gson;

/**
 * XML document reader for interpretation of server
 *
 * @author emlozin
 */
public class XMLReader{
    int connectTimeoutMs;
    int readTimeoutMs;

    private static DatabaseInfo dbInfo;

    public static Properties prop = new Properties();
    public static Gson gson = new Gson();

    public XMLReader() {
        this.dbInfo = DatabaseInfo.getSingleton();
    }

    public class ServerResponse {
        public ServerResponse(String xml) { this.xml = xml; }
        public ServerResponse(ErrorValue error) { this.error = error; }
        String xml;
        ErrorValue error;
        long timeMs;
    }

    public static NodeList extractResultsFromXml(String keyword, ServerResponse response) {
        return extractResultsFromXml(keyword, response.xml);
    }

    private static NodeList extractResultsFromXml(String keyword, String xml) {
        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        NodeList results = null;
        try {
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(new InputSource(new StringReader(xml)));
            results = doc.getElementsByTagName(keyword);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (SAXException e) {
            // throw new RuntimeException(e);
            return null;
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
        return results;
    }


    // Helper for parsing DOM.
    // Return the inner text of of a child element of node with tag |tag|.
    public static String getTagValue(String tag, Element elem) {
        NodeList nodes = elem.getElementsByTagName(tag);
        if (nodes.getLength() == 0) return null;
        if (nodes.getLength() > 1)
            throw new RuntimeException("Multiple instances of " + tag);
        nodes = nodes.item(0).getChildNodes();
        if (nodes.getLength() == 0) return null;
        Node value = nodes.item(0);
        return value.getNodeValue();
    }

    public static Map<String, String> getTagValue(List<String> keywords, Node node) {
        Map<String, String> result = new HashMap();
        try {
            NodeList child_nodes;
            if (node.hasChildNodes()){
                child_nodes = node.getChildNodes();
                for (int j = 0; j < child_nodes.getLength(); j++) {
                    if (child_nodes.item(j).getTextContent() != null
                            && keywords.toString().contains(child_nodes.item(j).getNodeName())){
                        result.put(child_nodes.item(j).getNodeName(),
                                dbInfo.uri2id(child_nodes.item(j).getTextContent()));
                    }
                }
            }
            return result;

        }
        catch (RuntimeException exception){
            System.out.println("Exception. Oops");
            return null;
        }
    }

    private static Map<String,String> nodeToValue(Node result) {
        Map<String,String> results = new HashMap();
        // Read bindings
        NodeList bindings = ((Element) result).getElementsByTagName("binding");

        // For each binding j (contributes some information to one column)...
        for (int j = 0; j < bindings.getLength(); j++) {
            Element binding = (Element) bindings.item(j);

            String var = "?" + binding.getAttribute("name");
            String uri = getTagValue("uri", binding);
            if (!(uri == null)){
                uri = new String(uri.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                if (uri.contains(":"))
                    uri = dbInfo.uri2id(uri);
                results.put(var,uri);
            }
        }
        return results;
    }

    private static List<String> nodeToLiteral(Node result) {
        List<String> results = new ArrayList<>();
        // Read bindings
        NodeList bindings = ((Element) result).getElementsByTagName("binding");

        // For each binding j (contributes some information to one column)...
        for (int j = 0; j < bindings.getLength(); j++) {
            Element binding = (Element) bindings.item(j);

            String var = "?" + binding.getAttribute("name");
            String uri = getTagValue("literal", binding);
            if (!(uri == null || (var.contains("_") || (uri.contains("#"))))){
                results.add(uri);
            }
        }
        return results;
    }

    // Helper for parsing DOM.
    // Return the inner text of of a child element of node with tag |tag|.
    public static Map<String, String> getAttValue(List<String> keywords, Node node) {
        Map<String, String> result = new HashMap();
        try {
            NodeList child_nodes;
            if (node.hasChildNodes()){
                child_nodes = node.getChildNodes();
                for (int j = 0; j < child_nodes.getLength(); j++) {
                    if (child_nodes.item(j).getTextContent() != null
                            && keywords.toString().contains(child_nodes.item(j).getNodeName())){
                        result.put(child_nodes.item(j).getNodeName(),
                                child_nodes.item(j).getTextContent());
                    }
                }
            }
            return result;

        }
        catch (RuntimeException exception){
            System.out.println("Exception. Oops");
            return null;
        }
    }

    public static List<Map<String,String>> readEntityXml(String xml, List<String> keywords) {
        List<Map<String, String>> output = new ArrayList<Map<String, String>>();
        NodeList results = extractResultsFromXml(keywords.get(0),xml);
        double probability = 0;
        for (int i = 0; i < results.getLength(); i++) {
            Map<String, String> result = getTagValue(keywords.subList(1, keywords.size()), results.item(i));
            if (result!=null && result.size() > 0) {
                output.add(result);
                probability += Double.parseDouble(result.get(keywords.get(keywords.size()-1)));
            }
        }
        // normalize
        for (Map<String, String> entry: output){
            entry.put(keywords.get(keywords.size()-1),Double.toString(
                    Double.parseDouble(entry.get(keywords.get(keywords.size()-1)))/probability));
        }
        return output;
    }

    public static List<Map<String,String>> readArrayXml(String xml) {
        List<Map<String, String>> output = new ArrayList<Map<String, String>>();
        NodeList results = extractResultsFromXml("result",xml);
        for (int i = 0; i < results.getLength(); i++) {
            Map<String, String> result = nodeToValue(results.item(i));
            if (result!=null && result.size() > 0 && !output.contains(result)) {
                output.add(result);
                //System.out.println(result.toString());
            }
        }
        return output;
    }

    public static List<List<String>> readLiteralXml(String xml) {
        List<List<String>> output = new ArrayList<>();
        NodeList results = extractResultsFromXml("result",xml);
        for (int i = 0; i < results.getLength(); i++) {
            List<String> result = nodeToLiteral(results.item(i));
            if (result!=null && result.size() > 0 && !output.contains(result)) {
                output.add(result);
            }
        }
        return output;
    }
}
