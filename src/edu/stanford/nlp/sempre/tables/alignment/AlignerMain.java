package edu.stanford.nlp.sempre.tables.alignment;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.sempre.*;
import fig.basic.*;
import fig.exec.*;

public class AlignerMain implements Runnable {
  public static class Options {
    @Option public String inputFile;
    @Option public int maxInputLines = Integer.MAX_VALUE;
    @Option public int maxIters = 10;
    @Option public Direction direction = Direction.wordToPred;
    @Option public NullWordHandling nullWordHandling = NullWordHandling.trained;
    @Option public double nullWordProb = 0.1;
  }
  public static Options opts = new Options();

  public static enum Direction {
    wordToPred, predToWord, product, agreement, group, productGroup
  }

  public static enum NullWordHandling {
    fixed, varied, trained, none
  }

  public static final double epsilon = 1e-6;

  public static void main(String[] args) {
    Execution.run(args, "AlignerMain", new AlignerMain(), Master.getOptionsParser());
  }

  @Override
  public void run() {
    BitextData data = readInput();
    AlignmentComputer computer = null;
    switch (opts.direction) {
      case wordToPred: computer = new IBM1AlignmentComputer(data, false); break;
      case predToWord: computer = new IBM1AlignmentComputer(data, true); break;
      case product: computer = new ProductAlignmentComputer(
          new IBM1AlignmentComputer(data, false), new IBM1AlignmentComputer(data, true)); break;
      case agreement: computer = new AgreementAlignmentComputer(data); break;
      case group: computer = new GroupAlignmentComputer(data); break;
      case productGroup: computer = new ProductAlignmentComputer(
          new IBM1AlignmentComputer(data, false), new GroupAlignmentComputer(data)); break;
      default: throw new RuntimeException("Unknown direction " + opts.direction);
    }
    writeOutput(computer.align());
  }

  // ============================================================
  // Read input
  // ============================================================

  // Each line is: id [tab] count [tab] word word ... [tab] predicate predicate ...

  private BitextData readInput() {
    LogInfo.begin_track("Reading data from %s", opts.inputFile);
    BitextData data = new BitextData();
    int count = 0;
    try {
      BufferedReader reader = IOUtils.openInHard(opts.inputFile);
      String line = null;
      while ((line = reader.readLine()) != null) {
        String[] tokens = line.split("\t");
        data.add(tokens[0], Integer.parseInt(tokens[1]), split(tokens[2]), split(tokens[3]));
        if (++count >= opts.maxInputLines) break;
      }
      reader.close();
    } catch (Exception e) {
      e.printStackTrace();
      LogInfo.fail(e);
    }
    LogInfo.logs("Read %d data lines", count);
    LogInfo.logs("# words = %d | # preds = %d", data.allWords().size(), data.allPreds().size());
    LogInfo.end_track();
    return data;
  }

  private List<String> split(String x) {
    String[] tokens = x.split(" ");
    for (int i = 0; i < tokens.length; i++) tokens[i] = tokens[i].intern();
    return Arrays.asList(tokens);
  }

  // ============================================================
  // Write output
  // ============================================================

  private void writeOutput(DoubleMap alignment) {
    String filename = Execution.getFile("alignment");
    LogInfo.begin_track("Writing to %s", filename);
    try (PrintWriter out = new PrintWriter(filename)) {
      printHeaderComment(out);
      for (Map.Entry<Pair<String, String>, Double> entry : alignment.entrySet()) {
        double value = entry.getValue();
        if (value < epsilon) continue;
        out.printf("%s\t%s\t%.6f\n", entry.getKey().getFirst(), entry.getKey().getSecond(), value);
      }
    } catch (Exception e) {
      e.printStackTrace();
      LogInfo.fail(e);
    }
    LogInfo.end_track();
  }

  private void printHeaderComment(PrintWriter out) {
    if (opts.nullWordHandling != NullWordHandling.fixed)
      out.printf("# input=%s direction=%s nullProb=%s\n", opts.inputFile, opts.direction, opts.nullWordHandling);
    else
      out.printf("# input=%s direction=%s nullProb=%s\n", opts.inputFile, opts.direction, opts.nullWordProb);
  }
}
