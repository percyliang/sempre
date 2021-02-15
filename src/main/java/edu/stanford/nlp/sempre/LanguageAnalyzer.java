package edu.stanford.nlp.sempre;

import fig.basic.*;

/**
 * LanguageAnalyzer takes an utterance and applies various NLP pre-processing steps to
 * to output a LanguageInfo object
 *
 * @author Alex Ratner
 */
public abstract class LanguageAnalyzer {
  public static class Options {
    @Option public String languageAnalyzer = "SimpleAnalyzer";

    @Option(gloss = "Whether to convert tokens in the utterance to lowercase")
    public boolean lowerCaseTokens = true;
  }
  public static Options opts = new Options();

  // We keep a singleton LanguageAnalyzer because for any given run we
  // generally will be working with one.
  private static LanguageAnalyzer singleton;
  public static LanguageAnalyzer getSingleton() {
    if (singleton == null)
      singleton = (LanguageAnalyzer) Utils.newInstanceHard(SempreUtils.resolveClassName(opts.languageAnalyzer));
    return singleton;
  }
  public static void setSingleton(LanguageAnalyzer analyzer) { singleton = analyzer; }

  public abstract LanguageInfo analyze(String utterance);
}
