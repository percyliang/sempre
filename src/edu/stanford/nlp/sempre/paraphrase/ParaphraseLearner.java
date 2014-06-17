package edu.stanford.nlp.sempre.paraphrase;

import java.io.IOException;
import java.util.List;


import edu.stanford.nlp.sempre.Evaluation;
import edu.stanford.nlp.sempre.Executor;
import edu.stanford.nlp.sempre.Params;
import fig.basic.LogInfo;
import fig.basic.Option;
import fig.basic.Parallelizer;
import fig.basic.StopWatchSet;
import fig.basic.Utils;
import fig.exec.Execution;

/**
 * Input: Two sets of examples (1) paraphrase data set - positive and negative examples
 * (2) a semantic parsing data set - question-answer pairs
 * Output: A model for paraphrase that can be used to judge whether a pair of utternaces are paraphrases
 * and also can be used for semantic parsing by paraphrasing a target question to a question that was generated
 * from a database formula
 * @author jonathanberant
 *
 */
public class ParaphraseLearner {

  public static class Options {
    @Option(gloss = "Number of iterations to train")
    public int maxTrainIters = 0;  
    @Option(gloss = "Whether to train or only to infer")
    public boolean inferOnly;
    @Option(gloss = "Number of threads")
    public int numOfThreads=8;
    @Option(gloss = "Whether to use binary logstic regression updates")
    public boolean binaryLogistic=false;
    @Option(gloss = "Whether to update based on partial reward.")
    public boolean partialReward = true;
  }
  public static Options opts = new Options();

  //paraphrasing components
  private final Params params;
  private final ParaphraseDataset paraphraseDataset;
  private final ParaphraseParser paraParser;


  public ParaphraseLearner(Params p, ParaphraseDataset pDataset, Executor executor) throws IOException {
    this.params=p;
    this.paraphraseDataset=pDataset;
    this.paraParser = new ParaphraseParser(executor);
  }

  public void learn() {
    learn(-1);
  }

  public void learn(int iters) {
    LogInfo.begin_track("Learner.learn()");

    if (iters < 0)
      iters = opts.maxTrainIters;

    for (int iter = 0; iter <= iters; iter++) {

      if(iter == iters && opts.inferOnly) continue; //when only inferring no need to go over training set again
      LogInfo.begin_track("Iteration %s/%s", iter, iters);
      Execution.putOutput("iter", iter);

      LogInfo.begin_track("Learn from parsing dataset");
      for (String group : paraphraseDataset.parsingGroups()) {
        boolean lastIter = iter == iters;
        boolean updateWeights = group.equals("train") && !lastIter && !opts.inferOnly;  // Don't train on last iteration
        processParsingExamples(
            iter,
            group,
            paraphraseDataset.parsingExamples(group),
            updateWeights);
        StopWatchSet.logStats();
      }
      LogInfo.end_track();

      // Write out parameters
      String path = Execution.getFile("params." + iter);
      if (path != null) {
        params.write(path);
        Utils.systemHard("ln -sf params." + iter + " " + Execution.getFile("params"));
      }
      LogInfo.end_track();
    }

    LogInfo.end_track();
  }

  private Evaluation processParsingExamples(int iter, String group,
      List<ParsingExample> parsingExamples, boolean updateWeights) {

    Evaluation totalEval = new Evaluation();

    if (parsingExamples.size() == 0)
      return totalEval;

    final String prefix = "parsing_iter=" + iter + "." + group;
    Execution.putOutput("group", group);

    LogInfo.begin_track_printAll(
        "Processing %s: %s examples", prefix, parsingExamples.size());
    LogInfo.begin_track("Examples");


    Parallelizer<ParsingExample> paral = new Parallelizer<>(opts.numOfThreads);
    ParsingExampleProcessor processor = new ParsingExampleProcessor(paraParser, params, prefix, updateWeights, totalEval); 
    LogInfo.begin_threads();
    paral.process(parsingExamples, processor);
    LogInfo.end_threads();

    params.finalizeWeights();

    LogInfo.end_track();
    logEvaluationStats(totalEval, prefix);
    LogInfo.end_track();
    return totalEval;
  }

  private void logEvaluationStats(Evaluation evaluation, String prefix) {
    LogInfo.logs("Stats for %s: %s", prefix, evaluation.summary());
    evaluation.logStats(prefix);
    evaluation.putOutput(prefix);
  }
}
