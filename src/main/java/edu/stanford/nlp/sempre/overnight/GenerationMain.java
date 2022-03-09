package edu.stanford.nlp.sempre.overnight;

import fig.basic.Option;
import fig.exec.Execution;
import edu.stanford.nlp.sempre.*;

/**
 * Created by joberant on 1/27/15.
 * Generating canonical utterances from grammar with various depths
 */
public class GenerationMain implements Runnable {
  @Option
  public boolean interactive = false;
  @Option
  public boolean varyMaxDepth = false;

  @Override
  public void run() {
    Builder builder = new Builder();
    builder.build();

    Dataset dataset = new Dataset();
    dataset.read();

    int currDepth = varyMaxDepth ? 1 : FloatingParser.opts.maxDepth;
    int maxDepth = FloatingParser.opts.maxDepth;

    for (; currDepth < maxDepth + 1; currDepth++) {
      FloatingParser.opts.maxDepth = currDepth;
      //LogInfo.logs("Curr depth=%s", currDepth);
      //LogInfo.logs("file = %s", FloatingParser.opts.predictedUtterancesFile);
      //PrintWriter writer = IOUtils.openOutAppendEasy(Execution.getFile(FloatingParser.opts.predictedUtterancesFile));
      //writer.println(String.format("Depth=%s", currDepth));
      //writer.println(String.format("--------", currDepth));
      //writer.close();
      Learner learner = new Learner(builder.parser, builder.params, dataset);
      learner.learn();
    }


    if (interactive) {
      Master master = new Master(builder);
      master.runInteractivePrompt();
    }
  }

  public static void main(String[] args) {
    Execution.run(args, "GenerationMain", new GenerationMain(), Master.getOptionsParser());
  }
}
