package edu.stanford.nlp.sempre.tables.serialize;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.TableKnowledgeGraph;
import edu.stanford.nlp.sempre.tables.test.CustomExample;
import fig.basic.*;
import fig.exec.*;

/**
 * Dump examples and parsed derivations.
 *
 * This class can be run on its own, in which case the parser in Builder.parser will be used.
 * Or it can be supplied the examples to dump in a streaming fashion.
 * 
 * Syntax of the dumped files:
 * - Filename: dumped-[prefix]-[numbering].gz
 * - Content:
 *   - First line: (metadata (size [number_of_examples]))
 *   - Each example begins with a comment (########## Example [example_id] ##########),
 *     followed by an example LispTree with the following fields:
 *       id, utterance, targetFormula, targetValue, context,
 *       tokens, lemmaTokens, posTags, nerTags, nerValues,
 *       derivations (one derivation per line)
 *
 * @author ppasupat
 */
public class SerializedDumper implements Runnable {
  public static class Options {
    @Option(gloss = "Verbosity") public int verbosity = 0;
    @Option(gloss = "Randomly shuffle dumped derivations")
    public Random shuffleDerivsRandom = new Random(9);
    @Option(gloss = "Skip if the table has more than this number of rows")
    public int maxNumRowsToDump = 200;
    @Option(gloss = "Number of examples per gzip file (0 = single file)")
    public int numExamplesPerFile = 0;
    @Option(gloss = "Custom dump file prefixes to use in standalone mode")
    public String dumpedFilePrefix = "";
  }
  public static Options opts = new Options();

  String prefix;
  String filename;
  PrintWriter out;
  int numExamples = -1, currentIndex = 0;

  public SerializedDumper(String prefix, int numExamples) {
    reset(prefix, numExamples);
  }

  public void reset(String prefix, int numExamples) {
    closeFile();
    this.currentIndex = 0;
    this.prefix = prefix;
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
        openFile(String.format("%s-%06d", prefix, 0));
        writeMetadataLispTree(numExamples);
      }
    } else {
      if (currentIndex % opts.numExamplesPerFile == 0) {
        openFile(String.format("%s-%06d", prefix, currentIndex));
        writeMetadataLispTree(Math.min(opts.numExamplesPerFile, numExamples - currentIndex));
      }
    }
    out.printf("########## Example %s ##########\n", ex.id);
    writeExampleLispTree(exampleToLispTree(ex, derivations));
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
    if (dataset.groups().size() > 1 && !opts.dumpedFilePrefix.isEmpty()) {
      LogInfo.warnings("Cannot use dumpedFilePrefix with more than one group; fall back to group names.");
      opts.dumpedFilePrefix = "";
    }
    for (String group : dataset.groups()) {
      reset(opts.dumpedFilePrefix.isEmpty() ? group : opts.dumpedFilePrefix, dataset.examples(group).size());
      processExamples(dataset.examples(group), builder);
      StopWatchSet.logStats();
    }
  }

  private void processExamples(List<Example> examples, Builder builder) {
    Evaluation evaluation = new Evaluation();
    if (examples.isEmpty()) return;

    final String logPrefix = "iter=0." + prefix;
    Execution.putOutput("group", logPrefix);
    LogInfo.begin_track_printAll("Processing %s: %s examples", logPrefix, examples.size());
    LogInfo.begin_track("Examples");

    for (int e = 0; e < examples.size(); e++) {
      if (!CustomExample.checkFilterExamples(e)) continue;
      Example ex = examples.get(e);
      LogInfo.begin_track_printAll("%s: example %s/%s: %s", logPrefix, e, examples.size(), ex.id);
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
      LogInfo.logs("Cumulative(%s): %s", logPrefix, evaluation.summary());
      LogInfo.end_track();
      // Save memory
      if (ex.predDerivations != null) {
        ex.predDerivations.clear();
        System.gc();
      }
    }

    LogInfo.end_track();
    LogInfo.logs("Stats for %s: %s", logPrefix, evaluation.summary());
    evaluation.logStats(logPrefix);
    evaluation.putOutput(logPrefix);
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
  // Writing LispTree to file
  // ============================================================

  private void writeMetadataLispTree(int size) {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("metadata");
    tree.addChild(LispTree.proto.newList("size", "" + size));
    tree.print(out);
    out.println();
    out.flush();
  }

  private void writeExampleLispTree(LispTree tree) {
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
