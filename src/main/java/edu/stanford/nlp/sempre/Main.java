package edu.stanford.nlp.sempre;

import java.lang.reflect.Constructor;


import fig.basic.Option;
import fig.exec.Execution;

/**
 * Entry point for the semantic parser.
 *
 * @author Percy Liang
 */
public class Main implements Runnable {
  @Option public boolean interactive = false;
  @Option public boolean server = false;
  @Option public String masterType = "edu.stanford.nlp.sempre.Master";

  public void run() {
    Builder builder = new Builder();
    builder.build();

    Dataset dataset = new Dataset();
    dataset.read();

    Learner learner = new Learner(builder.parser, builder.params, dataset);
    learner.learn();

    if (server || interactive) {
      Master master = createMaster(masterType, builder);
      if (server)
        master.runServer();
      if (interactive)
        master.runInteractivePrompt();
    }
  }

  public Master createMaster(String masterType, Builder builder) {
    try {
      Class<?> masterClass = Class.forName(masterType);
      Constructor<?> constructor = masterClass.getConstructor(Builder.class);
      return (Master)constructor.newInstance(builder);
    } catch (Throwable t) {
      t.printStackTrace();
    }
    return null;
  }

  public static void main(String[] args) {
    Execution.run(args, "Main", new Main(), Master.getOptionsParser());
  }
}
