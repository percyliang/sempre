package edu.stanford.nlp.sempre;

import fig.basic.Option;
import fig.basic.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * RelationAnalyzer takes an utterance and applies relationship extraction methods
 * to return RelationInfo object.
 *
 * @author emlozin
 */
public abstract class InfoAnalyzer {
    public static class Options {
        @Option public String infoAnalyzer = "corenlp.FullNLPAnalyzer";

        @Option(gloss = "Whether to convert tokens in the utterance to lowercase")
        public boolean lowerCaseTokens = true;
    }

    public class CoreNLPInfo {
        public LanguageInfo lanInfo;
        public RelationInfo relInfo;
        public GeneralInfo senInfo;
        public List<String> sentences;
        public CoreNLPInfo (){
            this.lanInfo = new LanguageInfo();
            this.relInfo = new RelationInfo();
            this.senInfo = new GeneralInfo();
            this.sentences = new ArrayList<>();
        }
    }
    public static Options opts = new Options();

    // We keep a singleton RelationAnalyzer because for any given run we
    // generally will be working with one.
    private static InfoAnalyzer singleton;
    public static InfoAnalyzer getSingleton() {
        if (singleton == null)
            singleton = (InfoAnalyzer) Utils.newInstanceHard(SempreUtils.resolveClassName(opts.infoAnalyzer));
        return singleton;
    }
    public static void setSingleton(InfoAnalyzer analyzer) { singleton = analyzer; }

    public abstract CoreNLPInfo analyze(String utterance);

}
