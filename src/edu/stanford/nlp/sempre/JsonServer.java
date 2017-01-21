package edu.stanford.nlp.sempre;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import fig.basic.*;
import fig.html.HtmlElement;
import fig.html.HtmlUtils;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.HttpCookie;
import com.sun.net.httpserver.HttpServer;

import edu.stanford.nlp.sempre.Master.Response;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;

import java.io.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.math.BigInteger;
import java.security.SecureRandom;

import static fig.basic.LogInfo.logs;

/**
 * This class implements a simple HTTP server which provides a web interface
 * into SEMPRE just like Master.runInteractivePrompt() exposes a command-line
 * tool.  Most of the work is dispatched to Master.processLine().
 * Cookies are used to store the session ID.
 *
 * @author Percy Liang, Sida Wang
 */
public class JsonServer {
  public static class Options {
    @Option public int port = 8400;
    @Option public int numThreads = 4;
    @Option public String title = "SEMPRE Demo";
    @Option public String headerPath;
    @Option public String basePath = "demo-www";
    @Option public int verbose = 1;
    @Option public int htmlVerbose = 1;
    @Option public int maxCandidates = Integer.MAX_VALUE;
  }
  public static Options opts = new Options();

  Master master;
  public static final HtmlUtils H = new HtmlUtils();

  class Handler implements HttpHandler {
    public void handle(HttpExchange exchange) {
      try {
        new ExchangeState(exchange);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  class ExchangeState {
    // Input
    HttpExchange exchange;
    Map<String, String> reqParams = new HashMap<>();
    String remoteHost;

    // For header
    HttpCookie cookie;
    boolean isNewSession;
 
    // For writing main content

    public ExchangeState(HttpExchange exchange) throws IOException {
      this.exchange = exchange;

      URI uri = exchange.getRequestURI();
      this.remoteHost = exchange.getRemoteAddress().getHostName();

      // Don't use uri.getQuery: it can't distinguish between '+' and '-'
      String[] tokens = uri.toString().split("\\?");
      if (tokens.length == 2) {
        for (String s : tokens[1].split("&")) {
          String[] kv = s.split("=", 2);
          try {
            String key = URLDecoder.decode(kv[0], "UTF-8");
            String value = URLDecoder.decode(kv[1], "UTF-8");
            logs("%s => %s", key, value);
            reqParams.put(key, value);
          } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
          }
        }
      }
      // do not decode sessionId, keep it filename and lisptree friendly
      String sessionId = URLEncoder.encode(MapUtils.get(reqParams, "sessionId", ""),  "UTF-8");
      if (sessionId != null) { 
        isNewSession = false;
      } else {
        isNewSession = true; 
      }
     
      if (opts.verbose >= 2)
        LogInfo.logs("GET %s from %s (%ssessionId=%s)", uri, remoteHost, isNewSession ? "new " : "", sessionId);

      String uriPath = uri.getPath();
      if (uriPath.equals("/")) uriPath += "index.html";
      if (uriPath.equals("/sempre")) {
        handleQuery(sessionId);
      } else {
        // getFile(opts.basePath + uriPath);
      }
      exchange.close();
    }

    String getMimeType(String path) {
      String[] tokens = path.split("\\.");
      String ext = tokens[tokens.length - 1];
      if (ext.equals("html")) return "text/html";
      if (ext.equals("css")) return "text/css";
      if (ext.equals("jpeg")) return "image/jpeg";
      if (ext.equals("gif")) return "image/gif";
      return "text/plain";
    }

    void setHeaders(String mimeType) throws IOException {
      Headers headers = exchange.getResponseHeaders();
      headers.set("Content-Type", mimeType);
      headers.set("Access-Control-Allow-Origin", "*");
      if (isNewSession && cookie != null)
        headers.set("Set-Cookie", cookie.toString());
      exchange.sendResponseHeaders(200, 0);
    }

   
    String makeJson(Master.Response response) {
      Map<String, Object> json = new HashMap<String, Object>();

      if (response.lines != null) {
        json.put("lines", response.lines);
      }
      if (response.getExample()!=null) {
        List<Object> items = new ArrayList<Object>();
        json.put("candidates", items);
        List<Derivation> allCandidates = response.getExample().getPredDerivations();
        
        if (allCandidates != null) {
          if (allCandidates.size() >= JsonServer.opts.maxCandidates) {
            allCandidates = allCandidates.subList(0, JsonServer.opts.maxCandidates);
            response.lines.add("Exceeded max options: " + allCandidates.size() + " / max: " + JsonServer.opts.maxCandidates );
          }
          
          for (Derivation deriv : allCandidates) {
            Map<String, Object> item = new HashMap<String, Object>();
            Value value = deriv.getValue();
            if (value instanceof StringValue)
              item.put("value", ((StringValue)value).value);
            else if (value instanceof ErrorValue)
              item.put("value", ((ErrorValue)value).sortString());
            else if (value != null)
              item.put("value", value.sortString());
            else
              item.put("value", "[[]]");
            item.put("score", deriv.score);
            item.put("prob", deriv.prob);
            item.put("anchored", deriv.allAnchored); // used only anchored rules
            item.put("formula", deriv.formula.toLispTree().toString());
            items.add(item);
          }
        }
      }

      return Json.writeValueAsStringHard(json);
    }


    // Catch exception if any.
    Master.Response processQuery(Session session, String query) {
      try {
        return master.processQuery(session, query);
      } catch (Throwable t) {
        t.printStackTrace();
        Master.Response response = master.new Response();
        response.lines.add(t.toString());
        return response;
      }
    }

    // If query is not already the last query, make it the last query.
    boolean ensureQueryIsLast(Session session, String query) {
      if (query != null && !query.equals(session.getLastQuery())) {
        Master.Response response = processQuery(session, query);
        if (response == null) return false;
      }
      return true;
    }

    void handleQuery(String sessionId) throws IOException {
      String query = reqParams.get("q");

      // If JSON, don't store cookies.
      Session session = master.getSession(sessionId);
      session.remoteHost = remoteHost;
      session.format = "json";

      if (query == null) query = session.getLastQuery();
      if (query == null) query = "";
      logs("Server.handleQuery %s: %s", session.id, query);

      // Print header
     
      setHeaders("application/json");
     
      PrintWriter out = new PrintWriter(new OutputStreamWriter(exchange.getResponseBody()));

      Master.Response masterResponse = null;
      if (query != null)
        masterResponse = processQuery(session, query);

      if (masterResponse != null) {
        // Render answer
        Example ex = masterResponse.getExample();
        out.println(makeJson(masterResponse));
      }

      out.close();
    }

    void getFile(String path) throws IOException {
      if (!new File(path).exists()) {
        LogInfo.logs("File doesn't exist: %s", path);
        exchange.sendResponseHeaders(404, 0);  // File not found
        return;
      }

      setHeaders(getMimeType(path));
      if (opts.verbose >= 2)
        LogInfo.logs("Sending %s", path);
      OutputStream out = new BufferedOutputStream(exchange.getResponseBody());
      InputStream in = new FileInputStream(path);
      IOUtils.copy(in, out);
    }
  }

  public JsonServer(Master master) {
    this.master = master;
  }

  public void run() {
    try {
      String hostname = fig.basic.SysInfoUtils.getHostName();
      HttpServer server = HttpServer.create(new InetSocketAddress(opts.port), 10);
      
      ExecutorService pool = new ThreadPoolExecutor(opts.numThreads, opts.numThreads,
          5000, TimeUnit.MILLISECONDS,
          new LinkedBlockingQueue<Runnable>());
      //Executors.newFixedThreadPool(opts.numThreads);
      server.createContext("/", new Handler());
      server.setExecutor(pool);
      server.start();
      LogInfo.logs("JSON Server (%d threads) started at http://%s:%s/sempre", opts.numThreads, hostname, opts.port);
      LogInfo.log("Press Ctrl-D to terminate.");
      while (LogInfo.stdin.readLine() != null) { }
      LogInfo.log("Shutting down server...");
      server.stop(0);
      LogInfo.log("Shutting down executor pool...");
      pool.shutdown();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
