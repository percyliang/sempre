package edu.stanford.nlp.sempre.roboy.error;

import edu.stanford.nlp.sempre.ContextValue;
import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.ErrorInfo;

import java.util.List;

/**
 * GeneralHelper takes an utterance and derivation in order to provide some general info
 * and provide useful information about input utterance
 *
 * @author emlozin
 */
public class ContextHelper extends GeneralHelper {
    private List<String> keywords;
    private String intent;
    private String sentiment;

    public ErrorInfo generate(Derivation dev, ContextValue.Exchange context){
        ErrorInfo errorInfo = new ErrorInfo();

        return errorInfo;
    }
}
