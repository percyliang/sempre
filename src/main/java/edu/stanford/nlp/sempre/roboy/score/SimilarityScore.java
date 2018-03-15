package edu.stanford.nlp.sempre.roboy.score;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import edu.stanford.nlp.sempre.ContextValue;
import edu.stanford.nlp.sempre.roboy.ErrorInfo;
import edu.stanford.nlp.sempre.roboy.lexicons.word2vec.Word2vec;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Word2VecScore creates a score based on word2vec similarity between labels
 *
 * @author emlozin
 */
public class SimilarityScore extends ScoringFunction {
    public static Properties prop = new Properties();   /**< Read properties */
    public static Gson gson = new Gson();               /**< Gson object */

    private double weight;                                  /**< Weight of the score in general score*/
    private final Word2vec vec;                         /**< Word2Vec handler */

    /**
     * A constructor.
     * Initializes Word2Vec needed to calculate scores
     */
    public SimilarityScore(Word2vec vec){
        try {
            InputStream input = new FileInputStream("config.properties");
            prop.load(input);
            JsonReader reader = new JsonReader(new FileReader(prop.getProperty("SCORE_WEIGHTS")));
            Type type = new TypeToken<Map<String, Double>>() {}.getType();
            Map<String, Double> weights = gson.fromJson(reader, type);
            this.weight = weights.get("Similarity");
            this.vec = vec;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Scoring function.
     * Takes ErrorInfo as well as ContextValue objects and calculates score of each
     * candidate for unknown terms.
     */
    public ErrorInfo score(ErrorInfo errorInfo, ContextValue context){
        ErrorInfo result = new ErrorInfo();
        result.setScored(new HashMap<>());
        result.setCandidates(errorInfo.getCandidates());
        result.setFollowUps(errorInfo.getFollowUps());
        // Check for all unknown terms
        for (String key: result.getCandidates().keySet()){
            Map<String, Double> key_scores = new HashMap<>();
            List<String> candidates = result.getCandidates().get(key);
            // Check for all candidates for checked unknown term
            for (String candidate: candidates){
                Map<String, String> c = new HashMap<>();
                c = gson.fromJson(candidate, c.getClass());
                // Check similarity
                double score = 0;
                if (c.get("Label").toLowerCase().contains(key))
                    score = 1;
                //System.out.println("Similarity:" + key + ":" + c.get("Label") + "->" + score);
                key_scores.put(candidate,score*this.weight);
            }
            result.getScored().put(key,key_scores);
        }
        return result;
    }

}
