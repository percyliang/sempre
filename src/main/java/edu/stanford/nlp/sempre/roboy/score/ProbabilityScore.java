package edu.stanford.nlp.sempre.roboy.score;

import com.google.gson.Gson;
import edu.stanford.nlp.sempre.ContextValue;
import edu.stanford.nlp.sempre.roboy.config.ConfigManager;
import edu.stanford.nlp.sempre.roboy.ErrorInfo;
import edu.stanford.nlp.sempre.roboy.lexicons.word2vec.Word2vec;
import fig.basic.LogInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ProbabilityScore creates a score based on its probability among other candidates of the same group
 *
 * @author emlozin
 */
public class ProbabilityScore extends ScoringFunction {
    public static Gson gson = new Gson();                   /**< Gson object */

    private double weight;                                  /**< Weight of the score in general score*/
    private final Word2vec vec;                             /**< Word2Vec handler */

    /**
     * A constructor.
     * Initializes Word2Vec needed to calculate scores
     */
    public ProbabilityScore(Word2vec vec){
        try {
            this.weight = ConfigManager.SCORING_WEIGHTS.get("Probability");
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
                if (ConfigManager.DEBUG > 3)
                    LogInfo.logs("Probability: %s , %s -> %s", key, c.get("Label"), c.get("Refcount"));
                key_scores.put(candidate,Double.valueOf(c.get("Refcount"))*this.weight);
            }
            result.getScored().put(key,key_scores);
        }
        return result;
    }

}
