package edu.stanford.nlp.sempre.roboy.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import java.io.*;
import java.lang.reflect.Type;
import java.net.Socket;
import java.util.List;
import java.util.Map;

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
        double full = 0;
        Tester test = new Tester(5000);
        try {
            JsonReader reader = new JsonReader(new FileReader("./data/rpqa-train-a.json"));
            Type type = new TypeToken<List<Map<String, String>>>() {
            }.getType();
            Gson gson = new Gson();
            List<Map<String, String>> testSet = gson.fromJson(reader, type);
            for (Map<String, String> entry : testSet) {
                String response = test.query(entry.get("utterance"));
                if (response != null) {
                    // Convert JSON string back to Map.
                    type = new TypeToken<Map<String, Object>>() {
                    }.getType();
                    Map<String, String> resMap = gson.fromJson(response, type);
                    if (resMap.get("parse").equals(entry.get("targetFormula"))) {
                        success++;
                        //System.out.println("CP: " + resMap.get("parse"));
                    } else if (resMap.get("parse").equals("(no answer)")) {
                        unparsed++;
                        System.out.println("TEST:" + entry.get("utterance"));
                        System.out.println("UP: " + resMap.get("parse"));
                    } else {
                        System.out.println("TEST:" + entry.get("utterance"));
                        System.out.println("IC: " + resMap.get("parse"));
                    }
                    full++;
                }
            }
            System.out.println("Success rate:" + success/testSet.size() + " Unparsed: " + unparsed/testSet.size());

        }
        catch(FileNotFoundException e){
            e.printStackTrace();
        }
        }
}