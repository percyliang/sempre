package edu.stanford.nlp.sempre.roboy.score;

import com.google.gson.Gson;
import edu.stanford.nlp.sempre.ContextValue;
import edu.stanford.nlp.sempre.roboy.UnspecInfo;
import edu.stanford.nlp.sempre.roboy.config.ConfigManager;
import edu.stanford.nlp.sempre.roboy.ErrorInfo;
import edu.stanford.nlp.sempre.roboy.lexicons.word2vec.Word2vec;
import fig.basic.LogInfo;

import java.util.*;

/**
 * Word2VecScore creates a score based on word2vec similarity between labels
 *
 * @author emlozin
 */
public class Word2VecScore extends ScoringFunction {
    public static Gson gson = new Gson();               /**< Gson object */

    private double weight;                              /**< Weight of the score in general score*/
    private final Word2vec vec;                         /**< Word2Vec handler */

    /**
     * A constructor.
     * Initializes Word2Vec needed to calculate scores
     */
    public Word2VecScore(Word2vec vec){
        try {
            this.weight = ConfigManager.SCORING_WEIGHTS.get("Word2Vec");
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
    public UnspecInfo score(UnspecInfo info, ContextValue context){
        UnspecInfo result = new UnspecInfo(info.term, info.type);
        result.candidates = info.candidates;
        result.candidatesInfo = info.candidatesInfo;
        // Check for all candidates for checked unknown term
        for (String canString: info.candidatesInfo){
            Map<String, String> candidate = new HashMap<>();
            candidate = this.gson.fromJson(canString, candidate.getClass());
            // Check similarity
            String[] tokensTerm = info.term.split(" ");
            String[] tokensCand = candidate.get("Label").split(" ");
            double score = 0, help, max;
            for (String tokenTerm: tokensTerm){
                max = 0;
                for (String tokenCand: tokensCand){
                    help = this.vec.getSimilarity(tokenTerm, tokenCand);
                    if (help > max){
                        max = help;
                    }
                }
                score = score + max;
            }
            if (Double.isNaN(score))
                score = 0.0;
            if (ConfigManager.DEBUG > 4)
                LogInfo.logs("Word2Vec: %s -> %s", candidate.get("URI"), score);
            result.candidatesScores.add(score*this.weight);
        }
        return result;
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
                double score = this.vec.getSimilarity(key, c.get("Label"));
                if (Double.isNaN(score))
                    score = 0.0;

                if (ConfigManager.DEBUG > 5)
                    LogInfo.logs("Word2Vec: %s , %s -> %s", key, c.get("Label"), c.get("Refcount"));
                key_scores.put(candidate,score*this.weight);
            }
            result.getScored().put(key,key_scores);
        }
        return result;
    }

}
