package edu.stanford.nlp.sempre.interactive;

import static org.testng.AssertJUnit.assertEquals;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import fig.basic.*;
import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.Master.Response;
import edu.stanford.nlp.sempre.Parser.Spec;

import org.testng.Assert;
import org.testng.asserts.SoftAssert;
import org.testng.asserts.Assertion;
import org.testng.annotations.Test;
import org.testng.collections.Lists;

import com.google.common.collect.Sets;

/**
 * Test server threading
 * @author Sida Wang
 */
public class StressTest {
  Assertion hard = new Assertion();
  Assertion soft = new SoftAssert();


  @Test public void realQueryTest() {
    
    //String fileName = "int-output/interactive.log";
    while(true) {
      LogInfo.begin_track("setsTest");
      //T.printAllRules();
      //A.assertAll();
      long startTime = System.nanoTime();
      String fileName = "/Users/sidaw/turk_acl17/int-output-0119/json/sidaw.log.json";
      {
        try (Stream<String> stream = Files.lines(Paths.get(fileName))) {
          LogInfo.logs("Reading %s", fileName);
          if (fileName.endsWith(".json") || fileName.endsWith(".log")) {
            stream.forEach(l -> {
              Map<String, Object> json = Json.readMapHard(l);
              String command = json.get("log").toString();
              try {
                sempreQuery(command);
                //Thread.sleep(10);
              } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
              }
            });
          }
        } catch (IOException e) {
          e.printStackTrace();
        } finally {
  
          long endTime = System.nanoTime();
          LogInfo.logs("Stresstest time = %d ns or %.4f s", (endTime - startTime), (endTime - startTime)/1.0e9); 
  
        }
      }
      LogInfo.end_track();
    }
  }
  
  @Test public void stressTest() throws IOException, InterruptedException {
    LogInfo.begin_track("stressTest");

    List<String> queries = Lists.newArrayList("(:q \"add red; add yellow; select left\")",
        "(:accept \"add red\" \"(: add red here)\")",
        "(:q \"repeat 3 [repeat 3 [select top; add red]]\")"
    );
    Random rand = new Random();
    // continue to spam the server
    while (LogInfo.stdin.readLine() != null) {
      sempreQuery(queries.get(rand.nextInt(queries.size())));
      Thread.sleep(10);
    }
    LogInfo.end_track();
  }
  
  private static void sempreQuery(String query) throws UnsupportedEncodingException {
    String params = "q=" + URLEncoder.encode(query, "UTF-8");
    // params = URLEncoder.encode(params);
    String url = "http://localhost:8410/sempre?";
    LogInfo.log(params);
    String response  = executePost(url + params,"");
    LogInfo.log(response);
  }

  private static String executePost(String targetURL, String urlParameters) {
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




}
