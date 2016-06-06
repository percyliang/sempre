package edu.stanford.nlp.sempre.tables.alignment;

import java.io.PrintWriter;
import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.TableFormulaEvaluator;
import edu.stanford.nlp.sempre.tables.alignment.BitextData.BitextDataGroup;
import fig.basic.*;
import fig.exec.*;

public class AlignerMain implements Runnable {
  public static class Options {
    @Option public int verbose = 0;
    @Option public AlignerName aligner = AlignerName.IBM1_Z_TO_X;
    @Option(gloss = "probability that the target aligns to null source")
    public NullWordHandling nullWordHandling = NullWordHandling.UNIFORM;
    @Option(gloss = "if nullWordHandling == FIXED, use this as the null word probability")
    public double fixedNullWordProb = 0.1;
  }
  public static Options opts = new Options();

  public static enum AlignerName {
    IBM1_X_TO_Z,   // IBM model 1 from x to z
    IBM1_Z_TO_X,   // IBM model 1 from z to x
  }
  public static enum NullWordHandling {
    FIXED,      // Fixed as a constant
    UNIFORM,    // = 1 / (source length + 1)
    TRAINED,    // Treat as a word
  }

  public static final double epsilon = 1e-6;

  public static void main(String[] args) {
    Execution.run(args, "AlignerMainMain", new AlignerMain(), Master.getOptionsParser());
  }

  protected TableFormulaEvaluator evaluator;
  protected AlignmentComputer alignmentComputer;
  protected BitextData bitextData;

  @Override
  public void run() {
    Builder builder = new Builder();
    builder.build();
    evaluator = (TableFormulaEvaluator) builder.valueEvaluator;
    Dataset dataset = new Dataset();
    dataset.read();
    // Train the aligner. Only the "train" group is used.
    bitextData = new BitextData(dataset.examples("train"));
    switch (opts.aligner) {
      case IBM1_X_TO_Z: alignmentComputer = new IBM1XToZAlignmentComputer(); break;
      case IBM1_Z_TO_X: alignmentComputer = new IBM1ZToXAlignmentComputer(); break;
      default: throw new RuntimeException("Unknown aligner: " + opts.aligner);
    }
    // Align and dump the results
    alignmentComputer.align(bitextData);
    dumpModel();
    //computeAllScores();
    evaluate(dataset.examples("dev"), "dev");
    evaluate(dataset.examples("test"), "test");
  }

  /**
   * Dump the model parameters into a file.
   */
  protected void dumpModel() {
    String filename = Execution.getFile("alignment");
    LogInfo.begin_track("Writing to %s", filename);
    try (PrintWriter out = new PrintWriter(filename)) {
      alignmentComputer.dump(out);
    } catch (Exception e) {
      e.printStackTrace();
      LogInfo.fail(e);
    }
    LogInfo.end_track();
  }

  /**
   * Compute the candidate scores for all examples in the dataset.
   */
  protected void computeAllScores() {
    PrintWriter out = IOUtils.openOutHard(Execution.getFile("aligned-formulas.gz"));
    for (BitextDataGroup group : bitextData.bitextDataGroups) {
      List<Pair<Formula, Double>> scores = alignmentComputer.score(group);
      Collections.sort(scores, new Pair.ReverseSecondComparator<Formula, Double>());
      // Log to stdout
      LogInfo.begin_track("%s", group.id);
      LogInfo.logs("Tokens: %s", group.tokens);
      for (Pair<Formula, Double> pair : scores)
        LogInfo.logs("%10.3f : %s", pair.getSecond(), pair.getFirst());
      LogInfo.end_track();
      // Dump to gzip file
      out.printf("########## Example %s ##########\n", group.id);
      out.println("(example");
      out.println("  " + LispTree.proto.newList("id", group.id));
      out.println("  " + LispTree.proto.newList("utterance", group.ex.utterance));
      out.println("  " + LispTree.proto.newList("targetValue", group.ex.targetValue.toLispTree()));
      out.println("  " + group.ex.context.toLispTree());
      out.println("  (derivations");
      for (Pair<Formula, Double> pair : scores) {
        if (pair.getSecond().isInfinite()) continue;
        LispTree tree = LispTree.proto.newList();
        tree.addChild(LispTree.proto.newLeaf("derivation"));
        tree.addChild(LispTree.proto.newList("formula", pair.getFirst().toLispTree()));
        tree.addChild(LispTree.proto.newList("score", String.format("%.3f", pair.getSecond())));
        out.println("    " + tree);
      }
      out.println("  )");
      out.println(")");
    }
    out.close();
  }

  protected void evaluate(List<Example> examples, String group) {
    if (examples == null || examples.isEmpty()) return;
    LogInfo.begin_track("AlignerMain.evaluate(%s)", group);
    Evaluation totalEvaluation = new Evaluation();
    for (Example ex : examples) {
      LogInfo.begin_track("%s", ex.id);
      LogInfo.logs("Utterance: %s", ex.utterance);
      List<Pair<Formula, Double>> scores = alignmentComputer.score(new BitextDataGroup(ex));
      Collections.sort(scores, new Pair.ReverseSecondComparator<Formula, Double>());
      boolean correct = false, oracle = false;
      if (scores.isEmpty()) {
        LogInfo.logs("The beam is empty.");
      } else {
        Formula bestFormula = scores.get(0).getFirst();
        evaluator.log(ex, bestFormula);
        double compatibility = evaluator.getCompatibilityAnnotationStrict(ex, bestFormula);
        if (compatibility > 0)
          correct = true;
        for (Pair<Formula, Double> pair : scores) {
          if (evaluator.getCompatibilityAnnotationStrict(ex, pair.getFirst()) > 0) {
            oracle = true;
            break;
          }
        }
      }
      Evaluation evaluation = new Evaluation();
      evaluation.add("correct", correct);
      evaluation.add("oracle", oracle);
      evaluation.add("numCandidates", scores.size());
      if (!scores.isEmpty())
        evaluation.add("parsedNumCandidates", scores.size());
      LogInfo.logs("Current: %s", evaluation.summary());
      totalEvaluation.add(evaluation);
      LogInfo.logs("Cumulative(%s): %s", group, totalEvaluation.summary());
      LogInfo.end_track();
    }
    totalEvaluation.logStats(group);
    totalEvaluation.putOutput(group);
    LogInfo.end_track();
  }

}
