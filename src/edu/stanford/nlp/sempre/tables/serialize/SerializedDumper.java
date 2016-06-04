package edu.stanford.nlp.sempre.tables.serialize;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.TableKnowledgeGraph;
import fig.basic.*;
import fig.exec.*;

/**
 * Dump examples and parsed derivations.
 *
 * This class can be run on its own, in which case the parser in Builder.parser will be used.
 * Or it can be supplied the examples to dump in a streaming fashion.
 *
 * @author ppasupat
 */
public class SerializedDumper implements Runnable {
  public static class Options {
    @Option(gloss = "Verbosity") public int verbosity = 0;
    @Option(gloss = "Randomly shuffle dumped derivations")
    public Random shuffleDerivsRandom = new Random(9);
    @Option(gloss = "Skip if the table has more than this number of rows")
    public int maxNumRowsToDump = 60;
    @Option(gloss = "Number of examples per gzip file (0 = single file)")
    public int numExamplesPerFile = 0;
  }
  public static Options opts = new Options();

  String group;
  String filename;
  PrintWriter out;
  int numExamples = -1, currentIndex = 0;

  public SerializedDumper(String group, int numExamples) {
    reset(group, numExamples);
  }

  public void reset(String group, int numExamples) {
    closeFile();
    this.currentIndex = 0;
    this.group = group;
    this.numExamples = numExamples;
  }

  public void openFile(String filenameSuffix) {
    if (out != null) closeFile();
    filename = Execution.getFile("dumped-" + filenameSuffix + ".gz");
    LogInfo.logs("Opening %s", filename);
    if (new File(filename).exists())
      LogInfo.warnings("File %s exists; will overwrite!", filename);
    out = IOUtils.openOutHard(filename);
  }

  public void closeFile() {
    if (out != null) {
      out.close();
      LogInfo.logs("Finished dumping to %s", filename);
      out = null;
    }
  }

  public void dumpExample(Example ex) {
    dumpExample(ex, ex.predDerivations);
  }

  public void dumpExample(Example ex, List<Derivation> derivations) {
    if (numExamples < 0)
      throw new RuntimeException("numExamples must be specified via reset(group, numExamples)");
    if (currentIndex >= numExamples)
      throw new RuntimeException("current example index exceeds numExamples");
    if (opts.numExamplesPerFile == 0) {
      if (currentIndex == 0) {
        openFile(group);
        actuallyDumpMetadata(-1, numExamples);
      }
    } else {
      if (currentIndex % opts.numExamplesPerFile == 0) {
        openFile(String.format("%s-%06d", group, currentIndex));
        actuallyDumpMetadata(currentIndex, Math.min(opts.numExamplesPerFile, numExamples - currentIndex));
      }
    }
    out.printf("########## Example %s ##########\n", ex.id);
    actuallyDumpExample(exampleToLispTree(ex, derivations));
    out.flush();
    currentIndex++;
    if (currentIndex == numExamples || (opts.numExamplesPerFile > 0 && currentIndex % opts.numExamplesPerFile == 0))
      closeFile();
  }

  // ============================================================
  // Stand-alone mode
  // ============================================================

  private SerializedDumper() { }

  public static void main(String[] args) {
    Execution.run(args, "SerializedDumperMain", new SerializedDumper(), Master.getOptionsParser());
  }

  @Override
  public void run() {
    Builder builder = new Builder();
    builder.build();
    Dataset dataset = new Dataset();
    dataset.read();
    for (String group : dataset.groups()) {
      reset(group, dataset.examples(group).size());
      processExamples(dataset.examples(group), builder);
      StopWatchSet.logStats();
    }
  }

  private void processExamples(List<Example> examples, Builder builder) {
    Evaluation evaluation = new Evaluation();
    if (examples.isEmpty()) return;

    final String prefix = "iter=0." + group;
    Execution.putOutput("group", group);
    LogInfo.begin_track_printAll("Processing %s: %s examples", prefix, examples.size());
    LogInfo.begin_track("Examples");

    for (int e = 0; e < examples.size(); e++) {
      Example ex = examples.get(e);
      LogInfo.begin_track_printAll("%s: example %s/%s: %s", prefix, e, examples.size(), ex.id);
      ex.log();
      Execution.putOutput("example", e);
      StopWatchSet.begin("Parser.parse");
      if (((TableKnowledgeGraph) ex.context.graph).numRows() > opts.maxNumRowsToDump) {
        LogInfo.logs("SKIPPING Example %s (number of rows = %d > %d)",
            ex.id, ((TableKnowledgeGraph) ex.context.graph).numRows(), opts.maxNumRowsToDump);
        new DummyParserState(builder.parser, builder.params, ex, false);
      } else {
        builder.parser.parse(builder.params, ex, false);
      }
      StopWatchSet.end();
      dumpExample(ex);
      LogInfo.logs("Current: %s", ex.evaluation.summary());
      evaluation.add(ex.evaluation);
      LogInfo.logs("Cumulative(%s): %s", prefix, evaluation.summary());
      LogInfo.end_track();
      // Save memory
      if (ex.predDerivations != null) {
        ex.predDerivations.clear();
        System.gc();
      }
    }

    LogInfo.end_track();
    LogInfo.logs("Stats for %s: %s", prefix, evaluation.summary());
    evaluation.logStats(prefix);
    evaluation.putOutput(prefix);
    LogInfo.end_track();
  }

  public static class DummyParserState extends ParserState {

    public DummyParserState(Parser parser, Params params, Example ex, boolean computeExpectedCounts) {
      super(parser, params, ex, computeExpectedCounts);
      ex.predDerivations = new ArrayList<>();
      ex.evaluation = new Evaluation();
    }

    @Override public void infer() { }    // Unused.

  }

  // ============================================================
  // Conversion to LispTree
  // ============================================================

  private LispTree exampleToLispTree(Example ex, List<Derivation> preds) {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("example");

    // Basic information
    if (ex.id != null)
      tree.addChild(LispTree.proto.newList("id", ex.id));
    if (ex.utterance != null)
      tree.addChild(LispTree.proto.newList("utterance", ex.utterance));
    if (ex.targetFormula != null)
      tree.addChild(LispTree.proto.newList("targetFormula", ex.targetFormula.toLispTree()));
    if (ex.targetValue != null)
      tree.addChild(LispTree.proto.newList("targetValue", ex.targetValue.toLispTree()));
    if (ex.context != null)
      tree.addChild(ex.context.toLispTree());

    // Language info
    if (ex.languageInfo != null) {
      if (ex.languageInfo.tokens != null)
        tree.addChild(LispTree.proto.newList("tokens", LispTree.proto.newList(ex.languageInfo.tokens)));
      if (ex.languageInfo.lemmaTokens != null)
        tree.addChild(LispTree.proto.newList("lemmaTokens", LispTree.proto.newList(ex.languageInfo.lemmaTokens)));
      if (ex.languageInfo.posTags != null)
        tree.addChild(LispTree.proto.newList("posTags", LispTree.proto.newList(ex.languageInfo.posTags)));
      if (ex.languageInfo.nerTags != null)
        tree.addChild(LispTree.proto.newList("nerTags", LispTree.proto.newList(ex.languageInfo.nerTags)));
      if (ex.languageInfo.nerValues != null)
        tree.addChild(LispTree.proto.newList("nerValues", LispTree.proto.newList(ex.languageInfo.nerValues)));
    }

    // Derivations
    List<LispTree> derivations = new ArrayList<>();
    for (int i = 0; i < preds.size(); i++) {
      Derivation deriv = preds.get(i);
      if (!isPruned(deriv)) {
        derivations.add(deriv.toLispTree());
      }
    }
    Collections.shuffle(derivations, opts.shuffleDerivsRandom);
    LispTree derivationsTree = LispTree.proto.newList();
    derivationsTree.addChild("derivations");
    for (LispTree derivation : derivations)
      derivationsTree.addChild(derivation);
    tree.addChild(derivationsTree);
    return tree;
  }

  // Decide whether we should skip the derivation
  private boolean isPruned(Derivation derivation) {
    if (!(derivation.value instanceof ListValue)) return true;
    ListValue list = (ListValue) derivation.value;
    // Check if there is at least one answer
    if (list.values.isEmpty()) return true;
    return false;
  }

  // ============================================================
  // Dumping LispTree
  // ============================================================

  private void actuallyDumpMetadata(int offset, int size) {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("metadata");
    tree.addChild(LispTree.proto.newList("group", group));
    if (offset >= 0)
      tree.addChild(LispTree.proto.newList("offset", "" + offset));
    tree.addChild(LispTree.proto.newList("size", "" + size));
    tree.print(out);
    out.println();
    out.flush();
  }

  private void actuallyDumpExample(LispTree tree) {
    out.println("(example");
    for (LispTree subtree : tree.children.subList(1, tree.children.size())) {
      if (!subtree.isLeaf() && "derivations".equals(subtree.children.get(0).value)) {
        if (subtree.children.size() == 1) {
          out.println("  (derivations)");
        } else {
          out.println("  (derivations");
          for (LispTree derivation : subtree.children.subList(1, subtree.children.size())) {
            out.write("    ");
            derivation.print(Integer.MAX_VALUE, Integer.MAX_VALUE, out);
            out.write("\n");
          }
          out.println("  )");
        }
      } else {
        out.write("  ");
        subtree.print(Integer.MAX_VALUE, Integer.MAX_VALUE, out);
        out.write("\n");
      }
    }
    out.println(")");
    out.flush();
  }
}
