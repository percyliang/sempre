package edu.stanford.nlp.sempre.tables.test;

import edu.stanford.nlp.sempre.*;
import fig.exec.Execution;

/**
 * Check 2 things:
 * - Whether the annotated formula actually executes to the correct denotation.
 * - Whether the formula is in the final beam of DPDParser.
 *
 * @author ppasupat
 */
public class DPDParserChecker implements Runnable {
  public static void main(String[] args) {
    Execution.run(args, "DPDParserCheckerMain", new DPDParserChecker(), Master.getOptionsParser());
  }

  @Override
  public void run() {
    DPDParserCheckerProcessor processor = new DPDParserCheckerProcessor();
    CustomExample.getDataset(Dataset.opts.inPaths, processor);
    processor.summarize();
  }

}
