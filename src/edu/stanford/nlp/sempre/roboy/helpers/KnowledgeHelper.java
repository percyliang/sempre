package edu.stanford.nlp.sempre.roboy.helpers;

import fig.basic.*;
import edu.stanford.nlp.sempre.SempreUtils;
import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.ErrorInfo;

/**
 * KnowledgeHelper takes an utterance and derivation and applies various error-retrieval
 * mechanisms.
 *
 * @author Emilia Lozinska
 */
public abstract class KnowledgeHelper {
    public static class Options {
        @Option public String knowledgeHelper = "SimpleAnalyzer";
    }
    public static Options opts = new Options();

    public abstract ErrorInfo analyze(Derivation dev);
}
