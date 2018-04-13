package edu.stanford.nlp.sempre.roboy.score;

import edu.stanford.nlp.sempre.ContextValue;
import edu.stanford.nlp.sempre.roboy.ErrorInfo;
import edu.stanford.nlp.sempre.roboy.UnspecInfo;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * ScoringFunction takes a list of candidates for unknown terms and scores it to
 * create a rank list
 *
 * @author emlozin
 */
public abstract class ScoringFunction {
    private double weight;          /**< Weight of the score in general score*/

    /**
     * Scoring function.
     * Takes UnspecInfo as well as ContextValue objects and calculates score of each
     * candidate for unknown terms.
     */
    public abstract UnspecInfo score(UnspecInfo info, ContextValue context);
}
