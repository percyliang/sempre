package edu.stanford.nlp.sempre.roboy.error;

import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.roboy.ErrorInfo;

/**
 * KnowledgeRetriever takes an utterance and derivation and applies various error-retrieval
 * mechanisms.
 *
 * @author emlozin
 */
public abstract class KnowledgeRetriever {

    public abstract ErrorInfo analyze(Derivation dev);
}
