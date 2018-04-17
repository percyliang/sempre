package edu.stanford.nlp.sempre.tables.serialize;

import java.io.BufferedReader;
import java.io.File;
import java.io.PrintWriter;
import java.util.*;

import edu.stanford.nlp.sempre.*;
import fig.basic.*;
import fig.exec.Execution;

public class DumpFilterer implements Runnable {
  public static class Options {
    @Option(gloss = "verbosity") public int verbose = 0;
    @Option(gloss = "input dump directory")
    public String filtererInputDumpDirectory;
  }
  public static Options opts = new Options();

  public static void main(String[] args) {
    Execution.run(args, "DumpFiltererMain", new DumpFilterer(), Master.getOptionsParser());
  }
  
  Builder builder;

  @Override
  public void run() {
    builder = new Builder();
    builder.build();
    String outDir = Execution.getFile("filtered");
    new File(outDir).mkdirs();
    for (Pair<String, String> pathPair : Dataset.opts.inPaths) {
      String group = pathPair.getFirst();
      String path = pathPair.getSecond();
      // Read LispTrees
      LogInfo.begin_track("Reading %s", path);
      int maxExamples = Dataset.getMaxExamplesForGroup(group);
      Iterator<LispTree> trees = LispTree.proto.parseFromFile(path);
      // Go through the examples
      int n = 0;
      while (n < maxExamples) {
        // Format: (example (id ...) (utterance ...) (targetFormula ...) (targetValue ...))
        LispTree tree = trees.next();
        if (tree == null) break;
        if (tree.children.size() < 2 || !"example".equals(tree.child(0).value)) {
          if ("metadata".equals(tree.child(0).value)) continue;
          throw new RuntimeException("Invalid example: " + tree);
        }
        Example ex = Example.fromLispTree(tree, path + ":" + n);
        ex.preprocess();
        LogInfo.logs("Example %s (%d): %s => %s", ex.id, n, ex.getTokens(), ex.targetValue);
        n++;
        processExample(ex);
      }
      LogInfo.end_track();
    }
  }

  private void processExample(Example ex) {
    File inPath = new File(opts.filtererInputDumpDirectory, ex.id + ".gz");
    File outPath = new File(Execution.getFile("filtered"), ex.id + ".gz");
    try {
      BufferedReader reader = IOUtils.openInHard(inPath);
      PrintWriter writer = IOUtils.openOutHard(outPath);
      int inLines = 0, outLines = 0;
      String line;
      while ((line = reader.readLine()) != null) {
        inLines++;
        LispTree tree = LispTree.proto.parseFromString(line);
        if (!"formula".equals(tree.child(1).child(0).value))
          throw new RuntimeException("Invalid tree: " + tree);
        Formula formula = Formulas.fromLispTree(tree.child(1).child(1));
        Value value = builder.executor.execute(formula, ex.context).value;
        double compatibility = builder.valueEvaluator.getCompatibility(ex.targetValue, value);
        if (compatibility == 1.0) {
          writer.println(tree);
          outLines++;
        } else if (opts.verbose >= 2) {
          LogInfo.logs("Filtered out %s <= %s", value, formula);
        }
      }
      LogInfo.logs("Filtered %d => %d", inLines, outLines);
      reader.close();
      writer.close();
    } catch (Exception e) {
      LogInfo.warnings("Got an error: %s", e);
    }
  }

}
