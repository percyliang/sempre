package edu.stanford.nlp.sempre.roboy.score;

import com.google.gson.Gson;
import edu.stanford.nlp.sempre.ContextValue;
import edu.stanford.nlp.sempre.roboy.UnderspecifiedInfo;
import edu.stanford.nlp.sempre.roboy.config.ConfigManager;
import edu.stanford.nlp.sempre.roboy.lexicons.word2vec.Word2vec;
import fig.basic.LogInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * ProbabilityScore creates a score based on its probability among other candidates of the same group
 *
 * @author emlozin
 */
public class ProbabilityScore extends ScoringFunction {
    public static Gson gson = new Gson();               /**< Gson object */

    private double weight;                                  /**< Weight of the score in general score*/

    /**
     * A constructor.
     * Initializes Word2Vec needed to calculate scores
     */
    public ProbabilityScore(Word2vec vec){
        try {
            this.weight = ConfigManager.SCORING_WEIGHTS.get("Probability");
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Scoring function.
     * Takes UnderspecifiedInfo as well as ContextValue objects and calculates score of each
     * candidate for unknown terms.
     */
    public UnderspecifiedInfo score(UnderspecifiedInfo info, ContextValue context){
        UnderspecifiedInfo result = new UnderspecifiedInfo(info.term, info.type);
        result.candidates = info.candidates;
        result.candidatesInfo = info.candidatesInfo;
        // Check for all candidates for checked unknown term
        for (String canString: info.candidatesInfo){
            Map<String, String> candidate = new HashMap<>();
            candidate = this.gson.fromJson(canString, candidate.getClass());
            // Check similarity
            if (ConfigManager.DEBUG > 4)
                LogInfo.logs("Probability: %s -> %s", candidate.get("URI"), Double.valueOf(candidate.get("Refcount")));
            result.candidatesScores.add(Double.valueOf(candidate.get("Refcount"))*this.weight);
        }
        return result;
    }

}
