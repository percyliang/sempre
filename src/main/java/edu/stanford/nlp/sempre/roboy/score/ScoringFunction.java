package edu.stanford.nlp.sempre.roboy.score;

import edu.stanford.nlp.sempre.ContextValue;
import edu.stanford.nlp.sempre.roboy.UnderspecifiedInfo;

/**
 * ScoringFunction takes a list of candidates for unknown terms and scores it to
 * create a rank list
 *
 * @author emlozin
 */
public abstract class ScoringFunction {
    /**
     * Scoring function.
     * Takes UnderspecifiedInfo as well as ContextValue objects and calculates score of each
     * candidate for unknown terms.
     */
    public abstract UnderspecifiedInfo score(UnderspecifiedInfo info, ContextValue context);
}
