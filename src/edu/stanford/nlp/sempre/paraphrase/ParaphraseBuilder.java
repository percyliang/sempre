package edu.stanford.nlp.sempre.paraphrase;

import com.google.common.base.Strings;

import edu.stanford.nlp.sempre.Executor;
import edu.stanford.nlp.sempre.Params;
import fig.basic.Option;
import fig.basic.Utils;

public class ParaphraseBuilder {
  public static class Options {
    @Option public String inParamsPath;
    @Option public String packageName = "edu.stanford.nlp.sempre";
    @Option public String executor = "SparqlExecutor";
  }
  public static Options opts = new Options();

  public Params params;
  public Executor executor;

  public void build() {
    // Parameters
    if (params == null) {
      params = new Params();
      if (!Strings.isNullOrEmpty(opts.inParamsPath))
        params.read(opts.inParamsPath);
    }

    // Executor
    if (executor == null) {
      executor = (Executor) Utils.newInstanceHard(opts.packageName + "." + opts.executor);
    }
  }
}
