package edu.stanford.nlp.sempre.roboy.utils;

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

/**
 * Entity Helper use Entity Searcher APIs to resolve underspecified entities in the lexicon
 *
 * @author emlozin
 */
public class XMLReader{
    int connectTimeoutMs;
    int readTimeoutMs;

    public static Properties prop = new Properties();
    public static Gson gson = new Gson();

    public class ServerResponse {
        public ServerResponse(String xml) { this.xml = xml; }
        public ServerResponse(ErrorValue error) { this.error = error; }
        String xml;
        ErrorValue error;
        long timeMs;
    }

    public static NodeList extractResultsFromXml(ServerResponse response) {
        return extractResultsFromXml(response.xml);
    }

    private static NodeList extractResultsFromXml(String xml) {
        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        NodeList results = null;
        try {
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(new InputSource(new StringReader(xml)));
            results = doc.getElementsByTagName("Result");
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
        NodeList results = extractResultsFromXml(xml);
        for (int i = 0; i < results.getLength(); i++) {
            Map<String, String> result = getTagValue(keywords, results.item(i));
            if (result!=null && result.size() > 0)
                output.add(result);
        }
        return output;
    }

}
