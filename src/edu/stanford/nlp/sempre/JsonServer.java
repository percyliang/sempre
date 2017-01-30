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
import java.nio.file.Paths;
import java.net.HttpCookie;
import com.sun.net.httpserver.HttpServer;

import edu.stanford.nlp.sempre.Master.Response;
import edu.stanford.nlp.sempre.interactive.BadInteractionException;

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
import java.time.LocalDateTime;


/**
 * JsonServer, most of the interactive stuff run through this. Handles log instead of master.
 *
 * @author Sida Wang
 */
public class JsonServer {
  public static class Options {
    @Option public int port = 8400;
    @Option public int numThreads = 4;
    @Option public int verbose = 1;
    @Option public int maxCandidates = Integer.MAX_VALUE;
    @Option public String queryLogPath = "./int-output/query.log";
    @Option public String responseLogPath = "./int-output/response.log";
    @Option public String fullResponseLogPath;
  }
  public static Options opts = new Options();

  Master master;

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
    private Object queryLogLock = new Object();
    private Object responseLogLock = new Object();
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
            // logs("%s => %s", key, value);
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
        logs("GET %s from %s (%ssessionId=%s)", uri, remoteHost, isNewSession ? "new " : "", sessionId);

      String uriPath = uri.getPath();
      if (uriPath.equals("/")) uriPath += "index.html";
      if (uriPath.equals("/sempre")) {
        handleQuery(sessionId);
      } else {
        // getFile(opts.basePath + uriPath); security
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

    Map<String, Object> makeJson(Master.Response response) {
      Map<String, Object> json = new HashMap<String, Object>();

      if (response.lines != null) {
        json.put("lines", response.lines);
      }
      if (response.getExample()!=null) {
        List<Object> items = new ArrayList<Object>();
        json.put("candidates", items);
        json.put("stats", response.stats);
        List<Derivation> allCandidates = response.getExample().getPredDerivations();

        if (allCandidates != null) {
          if (allCandidates.size() >= JsonServer.opts.maxCandidates) {
            allCandidates = allCandidates.subList(0, JsonServer.opts.maxCandidates);
            response.lines.add(
                String.format("Exceeded max options: (current: %d / max: %d) ", 
                    allCandidates.size(), JsonServer.opts.maxCandidates)
                );
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

      return json;
    }

    // Catch exception if any.
    Master.Response processQuery(Session session, String query) {
      String message = null;
      Master.Response response = master.new Response();
      try {
        response = master.processQuery(session, query);
      } catch (StringIndexOutOfBoundsException e) {
        e.printStackTrace();
        message = e.toString();
        LogInfo.writeToStdout = false;
        LogInfo.init();
      } catch (BadInteractionException e) {
        message = e.getMessage() + " (BadInteractionException)";
      } catch (Throwable t) {
        t.printStackTrace();
        message = t.toString();
      } finally {
        if (!Strings.isNullOrEmpty(message)) {
          response.lines.add(message);
          response.stats.put("error", message);
        }
      }
      return response;
    }

    void handleQuery(String sessionId) throws IOException {
      String query = reqParams.get("q");

      Session session = master.getSession(sessionId);
      session.reqParams = reqParams;
      session.remoteHost = remoteHost;
      session.format = "json";
      
      
      LocalDateTime queryTime = LocalDateTime.now();
      synchronized (queryLogLock) { // write the query log
        Map<String, Object> jsonMap = new LinkedHashMap<>();
        jsonMap.put("q", query);
        jsonMap.put("remote", remoteHost); 
        jsonMap.put("time", queryTime.toString());
        jsonMap.put("sessionId", sessionId);
        reqParams.remove("q");
        jsonMap.putAll(reqParams);
        if (session.isLogging()) {
          logLine(opts.queryLogPath, Json.writeValueAsStringHard(jsonMap));
        } else {
          logLine(opts.queryLogPath + ".sandbox", Json.writeValueAsStringHard(jsonMap));
        }
      }

      // If JSON, don't store cookies.
     
      if (query == null) query = "null";
        logs("Server.handleQuery %s: %s", session.id, query);

      // Print header
      setHeaders("application/json");

      Master.Response masterResponse = null;
      if (query != null) {
         masterResponse = processQuery(session, query);
      }

      Map<String, Object> responseMap = null;
      {
        PrintWriter out = new PrintWriter(new OutputStreamWriter(exchange.getResponseBody()));
        if (masterResponse != null) {
          // Render answer
          Example ex = masterResponse.getExample();
          responseMap = makeJson(masterResponse);
          out.println(Json.writeValueAsStringHard(responseMap));
        }
        out.close();
      }

      synchronized (responseLogLock) { // write the response log log
        Map<String, Object> jsonMap = new LinkedHashMap<>();
        LocalDateTime responseTime = LocalDateTime.now();
        // jsonMap.put("responseTime", responseTime.toString());
        jsonMap.put("time", queryTime.toString());
        jsonMap.put("ms", String.format("%.3f", java.time.Duration.between(queryTime, responseTime).toNanos()/1.0e6));
        jsonMap.put("sessionId", sessionId);
        jsonMap.put("q", query); // backwards compatability...
        jsonMap.put("lines", responseMap.get("lines"));
        if (session.isLogging()) {
          logLine(opts.responseLogPath, Json.writeValueAsStringHard(jsonMap));
          if (!Strings.isNullOrEmpty(opts.fullResponseLogPath)) {
            jsonMap.put("candidates", responseMap.get("candidates"));
            logLine(opts.fullResponseLogPath, Json.writeValueAsStringHard(jsonMap));
          }
        } else {
          logLine(opts.responseLogPath + ".sandbox", Json.writeValueAsStringHard(jsonMap));
          if (!Strings.isNullOrEmpty(opts.fullResponseLogPath)) {
            jsonMap.put("candidates", responseMap.get("candidates"));
            logLine(opts.fullResponseLogPath + ".sandbox", Json.writeValueAsStringHard(jsonMap));
          }
          // LogInfo.log(Json.writeValueAsStringHard(jsonMap));
        }
      }
    }
    
    void logLine(String path, String line) {
      PrintWriter out;
      try {
        out = IOUtils.openOutAppend(path);
        out.println(line);
        out.close();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }
  
  private void logs(String s, Object... args) {};

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
