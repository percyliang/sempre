package edu.stanford.nlp.sempre.roboy.score;

import edu.stanford.nlp.sempre.ContextValue;
import edu.stanford.nlp.sempre.ErrorInfo;

import java.util.Map;
import java.util.HashMap;

/**
 * ScoringFunction takes a list of candidates for unknown terms and scores it to
 * create a rank list
 *
 * @author emlozin
 */
public interface ScoringFunction {
    public Map<String, Map<String, Double>> current_score = new HashMap<>();   /**< Current calculated scores */
    /**
     * Scoring function.
     * Takes ErrorInfo as well as ContextValue objects and calculates score of each
     * candidate for unknown terms.
     */
    public Map<String, Map<String, Double>> score(ErrorInfo errorInfo, ContextValue context);
}
