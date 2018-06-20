package edu.stanford.nlp.sempre.roboy.score;

import com.google.gson.Gson;
import edu.stanford.nlp.sempre.ContextValue;
import edu.stanford.nlp.sempre.roboy.UnderspecifiedInfo;
import edu.stanford.nlp.sempre.roboy.config.ConfigManager;
import edu.stanford.nlp.sempre.roboy.lexicons.word2vec.Word2vec;
import fig.basic.LogInfo;

import java.util.*;

/**
 * ContextScore creates a score based on context fitting
 *
 * @author emlozin
 */
public class ContextScore extends ScoringFunction {
    public static Properties prop = new Properties();       /**< Read properties */
    public static Gson gson = new Gson();                   /**< Gson object */

    private double weight;                                  /**< Weight of the score in general score*/
    private final Word2vec vec;                             /**< Word2Vec handler */
    private int depth;                                      /**< Depth of context analysis*/

    /**
     * A constructor.
     * Initializes Word2Vec needed to calculate scores
     */
    public ContextScore(Word2vec vec){
        try {
            this.weight = ConfigManager.SCORING_WEIGHTS.get("Context");
            this.depth = ConfigManager.CONTEXT_DEPTH;
            this.vec = vec;
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
            int depth = Math.min(context.exchanges.size(),this.depth);
            double score = 0, help, max, max2, max3;
            String[] tokensTerm = candidate.get("Label").split(" ");
            for (int i = 0; i < depth; i++) {
                max3 = 0;
                for (String keyword: context.exchanges.get(context.exchanges.size() - 1).genInfo.keywords) {
                    String[] tokensCand = keyword.split(" ");
                    max2 = 0;
                    for (String tokenTerm : tokensCand) {
                        max = 0;
                        for (String tokenCand : tokensTerm) {
                            help = this.vec.getSimilarity(tokenTerm, tokenCand);
                            if (help > max) {
                                max = help;
                            }
                        }
                        max2 = max + max2;
                    }
                    max3 = max2 + max3;
                }
                score = score + max3;
            }
            if (Double.isNaN(score))
                score = 0.0;
            if (ConfigManager.DEBUG > 4)
                LogInfo.logs("Context: %s -> %f", candidate.get("URI"), score);
            result.candidatesScores.add(score*this.weight);
        }
        return result;
    }

}
