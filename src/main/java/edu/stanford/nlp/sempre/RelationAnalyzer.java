package edu.stanford.nlp.sempre;

import fig.basic.*;

/**
 * RelationAnalyzer takes an utterance and applies relationship extraction methods
 * to return RelationInfo object.
 *
 * @author emlozin
 */
public abstract class RelationAnalyzer {
    public static class Options {
        @Option public String relationAnalyzer = "corenlp.OpenIEAnalyzer";

        @Option(gloss = "Whether to convert tokens in the utterance to lowercase")
        public boolean lowerCaseTokens = true;
    }
    public static Options opts = new Options();

    // We keep a singleton RelationAnalyzer because for any given run we
    // generally will be working with one.
    private static RelationAnalyzer singleton;
    public static RelationAnalyzer getSingleton() {
        if (singleton == null)
            singleton = (RelationAnalyzer) Utils.newInstanceHard(SempreUtils.resolveClassName(opts.relationAnalyzer));
        return singleton;
    }
    public static void setSingleton(RelationAnalyzer analyzer) { singleton = analyzer; }

    public abstract RelationInfo analyze(String utterance);

}
