package edu.stanford.nlp.sempre.roboy.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import fig.basic.LogInfo;

import java.io.*;
import java.lang.reflect.Type;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Tester {

    private Socket clientSocket;
    /**
     * < Client socket for the parser
     */
    private PrintWriter out;
    /**
     * < Output stream for the parser
     */
    private BufferedReader in;
    /**
     * < Input stream from the parser
     */
    private boolean debug = true; /**< Boolean variable for debugging purpose */

    /**
     * A constructor.
     * Creates ParserAnalyzer class and connects the parser to DM using a socket.
     */
    public Tester(int portNumber) {
        this.debug = true;
        try {
            // Create string-string socket
            this.clientSocket = new Socket("localhost", portNumber);
            // Declaring input
            this.in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));
            // Declaring output
            this.out = new PrintWriter(clientSocket.getOutputStream(), true);
        } catch (IOException e) {
            System.err.println("Semantic Parser Client Error: " + e.getMessage());
        }
    }

    public String query(String utterance) {
        if (this.clientSocket != null && this.clientSocket.isConnected()) {
            try {
                String response;
                this.out.println(utterance);
                response = this.in.readLine();
//                if (this.debug) {
//                    System.out.println("> Full response:" + response);
//                }
                return response;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
    public static void main(String[] args) {
        double success = 0;
        double unparsed = 0;
        double fail = 0;
        Tester test = new Tester(5000);
        try {
            PrintWriter writer = new PrintWriter("log-webq.txt", "UTF-8");
            JsonReader reader = new JsonReader(new FileReader("./resources_nlu/rpqa/Q/rpqa-test-q.json"));
            Type type = new TypeToken<List<Map<String, String>>>() {
            }.getType();
            Gson gson = new Gson();
            List<Map<String, String>> testSet = gson.fromJson(reader, type);
            //test.query("(reload resources/roboy-final.grammar)");
            long time = System.nanoTime();
            for (Map<String, String> entry : testSet) {
                String response = test.query(entry.get("utterance"));
                if (response != null) {
                    // Convert JSON string back to Map.
                    type = new TypeToken<Map<String, Object>>() {
                    }.getType();
                    Map<String, String> resMap = gson.fromJson(response, type);
                    if (resMap.get("parse").equals(entry.get("targetFormula"))) {
                        success++;
                        System.out.println("CP: " + resMap.get("parse"));
                    } else if (resMap.get("parse").equals("(no answer)")) {
                        unparsed++;
                        LogInfo.logs("TEST: %s", entry.get("utterance"));
//                        writer.println("TEST: " + entry.get("utterance"));
                        LogInfo.logs("UP: %s", resMap.get("parse"));
//                        writer.println("UP: %s" + resMap.get("answer"));
                    } else {
                        fail++;
                        LogInfo.logs("TEST: %s", entry.get("utterance"));
                        writer.println("TEST: " + entry.get("utterance"));
                        LogInfo.logs("IC: %s", resMap.get("parse"));
                        writer.println("IC PARSE: %s" + resMap.get("parse"));
                        writer.println("IC: %s" + resMap.get("answer"));
                    }
                }
            }
            LogInfo.logs("Success rate: %f", success);
            LogInfo.logs("Unparsed: %f", unparsed);
            LogInfo.logs("Failure: %f", fail);
            LogInfo.logs("Success rate: %f", success/testSet.size());
            LogInfo.logs("Unparsed: %f", unparsed/testSet.size());
            LogInfo.logs("Failure: %f", fail/testSet.size());


            LogInfo.logs("Time: %f", (double) TimeUnit.SECONDS.convert(System.nanoTime() - time, TimeUnit.NANOSECONDS));
        }
        catch(FileNotFoundException e){
            e.printStackTrace();
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

//    public static void main(String[] args) {
//        double success = 0;
//        double unparsed = 0;
//        double fail = 0;
//        Tester test = new Tester(5000);
//        try {
//            PrintWriter writer = new PrintWriter("log-webq.txt", "UTF-8");
//            JsonReader reader = new JsonReader(new FileReader("./freebase/data/free917.test.examples.canonicalized.json"));
//            Type type = new TypeToken<List<Map<String, String>>>() {
//            }.getType();
//            Gson gson = new Gson();
//            List<Map<String, String>> testSet = gson.fromJson(reader, type);
//            for (Map<String, String> entry : testSet) {
//                String response = test.query(entry.get("utterance"));
//                if (response != null) {
//                    // Convert JSON string back to Map.
//                    type = new TypeToken<Map<String, Object>>() {
//                    }.getType();
//                    Map<String, String> resMap = gson.fromJson(response, type);
//                    if (resMap.get("parse").equals(entry.get("targetFormula"))) {
//                        success++;
//                        System.out.println("CP: " + resMap.get("parse"));
//                    } else if (resMap.get("parse").equals("(no answer)")) {
//                        unparsed++;
//                        LogInfo.logs("TEST: %s", entry.get("utterance"));
////                        writer.println("TEST: " + entry.get("utterance"));
//                        LogInfo.logs("UP: %s", resMap.get("parse"));
////                        writer.println("UP: %s" + resMap.get("answer"));
//                    } else {
//                        fail++;
//                        LogInfo.logs("TEST: %s", entry.get("utterance"));
//                        writer.println("TEST: " + entry.get("utterance"));
//                        LogInfo.logs("IC: %s", resMap.get("parse"));
//                        writer.println("IC PARSE: %s" + resMap.get("parse"));
//                        writer.println("IC: %s" + resMap.get("answer"));
//                    }
//                }
//            }
//            LogInfo.logs("Success rate: %f", success/testSet.size());
//            LogInfo.logs("Unparsed: %f", unparsed/testSet.size());
//            LogInfo.logs("Failure: %f", fail/testSet.size());
//        }
//        catch(FileNotFoundException e){
//            e.printStackTrace();
//        }
//        catch(Exception e){
//            e.printStackTrace();
//        }
//    }
//    public static void main(String[] args) {
//        double success = 0;
//        double unparsed = 0;
//        double fail = 0;
//        Tester test = new Tester(5000);
//        try {
//            JsonReader reader = new JsonReader(new FileReader("./resources_nlu/rpqa/1/test.json"));
//            Type type = new TypeToken<List<Map<String, String>>>() {
//            }.getType();
//            Gson gson = new Gson();
//            List<Map<String, String>> testSet = gson.fromJson(reader, type);
//            test.query("(reload resources/roboy-demo.grammar)");
//            for (Map<String, String> entry : testSet) {
//                String response = test.query(entry.get("utterance"));
//                if (response != null) {
//                    // Convert JSON string back to Map.
//                    type = new TypeToken<Map<String, Object>>() {
//                    }.getType();
//                    Map<String, String> resMap = gson.fromJson(response, type);
//                    if (resMap.get("parse").equals(entry.get("targetFormula"))) {
//                        success++;
//                        //System.out.println("CP: " + resMap.get("parse"));
//                    } else if (resMap.get("parse").equals("(no answer)")) {
//                        unparsed++;
//                        System.out.println("TEST:" + entry.get("utterance"));
//                        System.out.println("UP: " + resMap.get("parse"));
//                    } else {
//                        fail++;
//                        System.out.println("TEST:" + entry.get("utterance"));
//                        System.out.println("IC: " + resMap.get("parse"));
//                    }
//                }
//            }
//            LogInfo.logs("Success rate: %f", success/testSet.size());
//            LogInfo.logs("Unparsed: %f", unparsed/testSet.size());
//            LogInfo.logs("Failure: %f", fail/testSet.size());
//
//        }
//        catch(FileNotFoundException e){
//            e.printStackTrace();
//        }
//        }
}