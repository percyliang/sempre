package edu.stanford.nlp.sempre;

import java.util.*;

import fig.basic.Evaluation;
import fig.basic.LogInfo;
import fig.basic.Parallelizer;
import fig.basic.StopWatchSet;
import fig.exec.Execution;

/**
 * Parallel version of the Learner.
 *
 * Most of the codes are copied from the paraphrase package.
 *
 * @author ppasupat
 */
public class LearnerParallelProcessor implements Parallelizer.Processor<Example> {

  private final Parser parser;
  private final String prefix;
  private final boolean computeExpectedCounts;
  private Params params;         // this is common to threads and should be synchronized
  private Evaluation evaluation; // this is common to threads and should be synchronized

  public LearnerParallelProcessor(Parser parser, Params params, String prefix, boolean computeExpectedCounts, Evaluation evaluation) {
    this.prefix = prefix;
    this.parser = parser;
    this.computeExpectedCounts = computeExpectedCounts;
    this.params = params;
    this.evaluation = evaluation;
  }

  @Override
  public void process(Example ex, int i, int n) {
    LogInfo.begin_track_printAll(
        "%s: example %s/%s: %s", prefix, i, n, ex.id);
    ex.log();
    Execution.putOutput("example", i);

    StopWatchSet.begin("Parser.parse");
    ParserState state = parser.parse(params, ex, computeExpectedCounts);
    StopWatchSet.end();

    if (computeExpectedCounts) {
      Map<String, Double> counts = new HashMap<>();
      SempreUtils.addToDoubleMap(counts, state.expectedCounts);

      // Gathered enough examples, update parameters
      StopWatchSet.begin("Learner.updateWeights");
      LogInfo.begin_track("Updating learner weights");
      if (Learner.opts.verbose >= 2)
        SempreUtils.logMap(counts, "gradient");
      double sum = 0;
      for (double v : counts.values()) sum += v * v;
      LogInfo.logs("L2 norm: %s", Math.sqrt(sum));
      synchronized (params) {
        params.update(counts);
      }
      counts.clear();
      LogInfo.end_track();
      StopWatchSet.end();
    }

    LogInfo.logs("Current: %s", ex.evaluation.summary());
    synchronized (evaluation) {
      evaluation.add(ex.evaluation);
      LogInfo.logs("Cumulative(%s): %s", prefix, evaluation.summary());
    }

    LogInfo.end_track();

    // To save memory
    ex.clean();
  }

}
