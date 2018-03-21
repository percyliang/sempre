package edu.stanford.nlp.sempre.roboy.error;

import edu.stanford.nlp.sempre.roboy.UnspecInfo;

/**
 * KnowledgeRetriever takes an underspecified term and applies various error-retrieval
 * mechanisms.
 *
 * @author emlozin
 */
public abstract class KnowledgeRetriever {

    public abstract UnspecInfo analyze(UnspecInfo under);
}
