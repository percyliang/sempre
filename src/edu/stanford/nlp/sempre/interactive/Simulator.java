package edu.stanford.nlp.sempre.interactive;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.testng.collections.Lists;

import edu.stanford.nlp.sempre.Json;
import edu.stanford.nlp.sempre.JsonServer;
import fig.basic.LogInfo;
import fig.basic.Option;
import fig.basic.OptionsParser;
import fig.exec.Execution;
import fig.basic.Evaluation;

/**
 * utilites for simulating a session through the server
 * @author Sida Wang
 */
public class Simulator implements Runnable {

  @Option public static String serverURL = "http://localhost:8410";
  @Option public static int numThreads = 4;
  @Option public static int verbose = 1;
  @Option public static String reqParams = "grammar=0&cite=0&learn=0";
  @Option public static List<String> logFiles = Lists.newArrayList("./shrdlurn/commandInputs/sidaw.json.log");

  public void readQueries() {
    LogInfo.begin_track("setsTest");
    //T.printAllRules();
    //A.assertAll();
    ExecutorService executor = new ThreadPoolExecutor(JsonServer.opts.numThreads, JsonServer.opts.numThreads,
        5000, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<Runnable>());
    
    long startTime = System.nanoTime();
    for (String fileName : logFiles) {
      try (Stream<String> stream = Files.lines(Paths.get(fileName))) {
        LogInfo.logs("Reading %s", fileName);

        stream.forEach(l -> 
        executor.execute(() -> {
          long startTimeQ = System.nanoTime();
          Map<String, Object> json = Json.readMapHard(l);
          Object command = json.get("q");
          if (command == null) // to be backwards compatible
            command = json.get("log"); 
          Object sessionId = json.get("sessionId");
          if (sessionId == null) // to be backwards compatible
            sessionId = json.get("id"); 
          try {
            String response = sempreQuery(command.toString(), sessionId.toString());
            SimulationAnalyzer.add(sessionId, command.toString(), response);
            //Thread.sleep(10);
          } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
          long endTimeQ = System.nanoTime();
          double queryTime = (endTimeQ - startTimeQ)/1.0e9;
          // evaluation.add("queryTime",queryTime);
          if (queryTime > 0.1) {
            LogInfo.logs("slow query (%f): %s", queryTime, l);
          }
        }));
        executor.shutdown();
        try {
          boolean finshed = executor.awaitTermination(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }

      } catch (IOException e) {
        e.printStackTrace();
      }
      
      long endTime = System.nanoTime();
      LogInfo.logs("Took %d ns or %.4f s", (endTime - startTime), (endTime - startTime)/1.0e9); 
    }
    SimulationAnalyzer.flush();
    LogInfo.end_track();
  }

  public static String sempreQuery(String query, String sessionId) throws UnsupportedEncodingException {
    String params = "q=" + URLEncoder.encode(query, "UTF-8");
    params += String.format("&sessionId=%s&%s", sessionId, reqParams);
    // params = URLEncoder.encode(params);
    String url = String.format("%s/sempre?", serverURL);
    // LogInfo.log(params);
    // LogInfo.log(query);
    String response  = executePost(url + params, "");
    //LogInfo.log(response);
    return response;
  }

  public static String executePost(String targetURL, String urlParameters) {
    HttpURLConnection connection = null;

    try {
      //Create connection
      URL url = new URL(targetURL);
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type", 
          "application/x-www-form-urlencoded");

      connection.setRequestProperty("Content-Length", 
          Integer.toString(urlParameters.getBytes().length));
      connection.setRequestProperty("Content-Language", "en-US");  

      connection.setUseCaches(false);
      connection.setDoOutput(true);

      //Send request
      DataOutputStream wr = new DataOutputStream (
          connection.getOutputStream());
      wr.writeBytes(urlParameters);
      wr.close();

      //Get Response  
      InputStream is = connection.getInputStream();
      BufferedReader rd = new BufferedReader(new InputStreamReader(is));
      StringBuilder response = new StringBuilder(); // or StringBuffer if Java version 5+
      String line;
      while ((line = rd.readLine()) != null) {
        response.append(line);
        response.append('\r');
      }
      rd.close();
      return response.toString();
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  public static void main(String[] args) {
    OptionsParser parser = new OptionsParser();
    Simulator simulator = new Simulator();
    // parser.register("", opts);
    Execution.run(args, "Simulator", simulator, parser);
  }

  @Override
  public void run() {
    LogInfo.logs("setting numThreads %d", numThreads);
    readQueries();
  }
}
