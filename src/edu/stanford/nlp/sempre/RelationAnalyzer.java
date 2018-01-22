package edu.stanford.nlp.sempre;

import fig.basic.*;

/**
 * RelationAnalyzer takes an utterance and applies relationship extraction methods
 * to return RelationInfo object.
 *
 * @author emlozin
 */
public abstract class RelationAnalyzer {

    public abstract RelationInfo analyze(String utterance);
}
