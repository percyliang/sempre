package edu.stanford.nlp.sempre.roboy.error;

import edu.stanford.nlp.sempre.ContextValue;
import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.roboy.ErrorInfo;

/**
 * GeneralHelper takes an utterance and derivation in order to provide some general info
 * and provide useful information about input utterance
 *
 * @author emlozin
 */
public abstract class GeneralHelper {

    public abstract ErrorInfo generate(Derivation dev, ContextValue.Exchange context);
}
