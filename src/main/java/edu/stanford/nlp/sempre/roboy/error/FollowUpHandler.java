package edu.stanford.nlp.sempre.roboy.error;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import edu.stanford.nlp.sempre.roboy.ErrorInfo;
import edu.stanford.nlp.sempre.roboy.utils.SparqlUtils;
import fig.basic.LogInfo;

import java.io.*;
import java.lang.reflect.Type;
import java.net.Socket;
import java.net.URLEncoder;
import java.util.*;


/**
 * Follow-up question class. Connects parser back to DM and gets answer needed for the parser.
 */
public class FollowUpHandler {

    private Socket clientSocket;  /**< Client socket for the follow-up question */
    private PrintWriter out;      /**< Output stream for the follow-up question */
    private BufferedReader in;    /**< Input stream from the follow-up question */
    private boolean debug = true; /**< Boolean variable for debugging purpose */
    private Gson gson;
    public  String dbpediaUrl;
    private SparqlUtils sparqlUtil = new SparqlUtils();

    /**
     * A constructor.
     * Creates FollowUpHandler class and connects the parser to DM using a socket.
     */
    public FollowUpHandler(int portNumber) {
        this.debug = true;
        this.gson = new Gson();
        try {
            InputStream input = new FileInputStream("config.properties");
            Properties prop = new Properties();
            prop.load(input);
            dbpediaUrl = prop.getProperty("DB_SPARQL");
            // Create string-string socket
            this.clientSocket = new Socket("localhost", portNumber);
            // Declaring input
            this.in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));
            // Declaring output
            this.out = new PrintWriter(clientSocket.getOutputStream(), true);
        }
        catch (IOException e) {
            System.err.println("Follow-Up Client Error: " + e.getMessage());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Function forming questions.
     *
     * @param term          term that is underspecified
     * @param candidate     list of potential candidates
     * @return Follow-up question
     */
    public List<String> formQuestion(String term, List<String> candidate) {
        List<String> result = new ArrayList<>();
        for (String c:candidate) {
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> c_map = this.gson.fromJson(c, type);
            String desc = sparqlUtil.returnDescr(c_map.get("URI"),dbpediaUrl);
            if (desc!=null) {
                LogInfo.logs("Abstract: %s", desc);
                if (desc.contains(".")) {
                    desc = desc.substring(0, desc.indexOf("."));
                }
                LogInfo.logs("Abstract after: %s", desc);
                if (desc.contains("(")) {
                    String new_desc = desc.substring(0, desc.indexOf("("));
                    LogInfo.logs("Abstract after: %s", desc);
                    new_desc = new_desc.concat(desc.substring(desc.indexOf(")")+2));
                    desc = new_desc;
                    LogInfo.logs("Abstract after: %s", desc);
                }
                desc = desc.replaceAll(" is ", ", ");
                desc = desc.replaceAll(" was ", ", ");
                LogInfo.logs("Abstract after: %s", desc);

                result.add(String.format("Did you mean %s as %s the %s?", term, c_map.get("Label"), desc));
            }
            else
                result.add(String.format("Did you mean %s as the %s?", term, c_map.get("Label")));
        }
        return result;
    }

    /**
     * An asking function.
     * Sends input questions to the DM and saves user response.
     *
     * @param errorInfo
     * @return Best candidate
     */
    public String askFollowUp(ErrorInfo errorInfo) {
        if (this.clientSocket!=null && this.clientSocket.isConnected()) {
            try {
                // Check each question
                for (String t: errorInfo.getFollowUps().keySet()){
                    List<String> candidate = errorInfo.getFollowUps().get(t);
                    List<String> questions = formQuestion(t,candidate);
                    for (int i = 0; i < questions.size(); i++) {
                        this.out.println(questions.get(i));
                        String response = this.in.readLine();
                        if (response.contains("yes"))
                            return candidate.get(i);
                    }
                }
                return null;
            }
            catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        return null;
    }

}