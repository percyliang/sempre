package edu.stanford.nlp.sempre.roboy.score;

import com.google.gson.Gson;
import edu.stanford.nlp.sempre.ContextValue;
import edu.stanford.nlp.sempre.roboy.UnderspecifiedInfo;
import edu.stanford.nlp.sempre.roboy.config.ConfigManager;
import edu.stanford.nlp.sempre.roboy.lexicons.word2vec.Word2vec;
import fig.basic.LogInfo;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Word2VecScore creates a score based on word2vec similarity between labels
 *
 * @author emlozin
 */
public class SimilarityScore extends ScoringFunction {
    public static Gson gson = new Gson();               /**< Gson object */

    private double weight;                              /**< Weight of the score in general score*/

    /**
     * A constructor.
     * Initializes Word2Vec needed to calculate scores
     */
    public SimilarityScore(Word2vec vec){
        try {
            this.weight = ConfigManager.SCORING_WEIGHTS.get("Similarity");
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
            double score = 0;
            List<String> tokens = Arrays.asList(candidate.get("Label").toLowerCase().split(" "));
            if (tokens.contains(result.term))
                score = 1;
            result.candidatesScores.add(score*this.weight);
            if (ConfigManager.DEBUG > 4)
                LogInfo.logs("Similarity: %s -> %s", candidate.get("URI"), score);
        }
        return result;
    }

}
