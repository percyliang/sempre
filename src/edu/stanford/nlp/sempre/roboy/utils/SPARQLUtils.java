package edu.stanford.nlp.sempre.roboy.utils;

import edu.stanford.nlp.sempre.ErrorInfo;
import edu.stanford.nlp.sempre.ErrorValue;
import edu.stanford.nlp.sempre.Example;
import edu.stanford.nlp.sempre.cache.StringCache;
import edu.stanford.nlp.sempre.cache.StringCacheUtils;

import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

import java.io.*;
import java.util.*;
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

/**
 * SPARQL utilities for quering some endpoint
 *
 * @author emlozin
 */

public class SPARQLUtils{
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

        public String getXml(){return this.xml;};
        public ErrorValue getError(){return this.error;};
    }

    // Make a request to the given SPARQL endpoint.
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
        } catch (IOException e) {
            LogInfo.errors("Server exception: %s", e);
            // Sometimes the SPARQL server throws a 408 to signify a server timeout.
            if (e.toString().contains("HTTP response code: 408"))
                return new ServerResponse(ErrorValue.server408);
            if (e.toString().contains("HTTP response code: 500"))
                return new ServerResponse(ErrorValue.server500);
            throw new RuntimeException(e);  // Haven't seen this happen yet...
        }
    }

}
