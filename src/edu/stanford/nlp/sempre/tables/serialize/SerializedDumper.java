package edu.stanford.nlp.sempre.tables.serialize;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.TableTypeSystem;
import fig.basic.*;
import fig.exec.*;

/**
 * Dump examples and parsed derivations.
 *
 * @author ppasupat
 */
public class SerializedDumper implements Runnable {
  public static class Options {
    @Option(gloss = "do not print obviously bad denotations")
    public boolean pruneBadDenotations = true;
  }
  public static Options opts = new Options();

  public static void main(String[] args) {
    Execution.run(args, "SerializedDumperMain", new SerializedDumper(), Master.getOptionsParser());
  }

  Builder builder;
  Dataset dataset;
  Params params;
  PrintWriter out;

  @Override
  public void run() {
    builder = new Builder();
    builder.build();
    dataset = new Dataset();
    dataset.read();
    params = new Params();
    for (String group : dataset.groups()) {
      String filename = Execution.getFile("dumped-" + group + ".gz");
      out = IOUtils.openOutHard(filename);
      processExamples(group, dataset.examples(group));
      out.close();
      LogInfo.logs("Finished dumping to %s", filename);
      StopWatchSet.logStats();
    }
  }

  private void processExamples(String group, List<Example> examples) {
    Evaluation evaluation = new Evaluation();
    if (examples.isEmpty()) return;

    final String prefix = "iter=0." + group;
    Execution.putOutput("group", group);
    LogInfo.begin_track_printAll("Processing %s: %s examples", prefix, examples.size());
    LogInfo.begin_track("Dumping metadata");
    dumpMetadata(group, examples);
    LogInfo.end_track();
    LogInfo.begin_track("Examples");

    for (int e = 0; e < examples.size(); e++) {
      Example ex = examples.get(e);
      LogInfo.begin_track_printAll("%s: example %s/%s: %s", prefix, e, examples.size(), ex.id);
      ex.log();
      Execution.putOutput("example", e);
      StopWatchSet.begin("Parser.parse");
      ParserState state = builder.parser.parse(params, ex, false);
      StopWatchSet.end();
      out.printf("########## Example %s ##########\n", ex.id);
      dumpExample(exampleToLispTree(state));
      LogInfo.logs("Current: %s", ex.evaluation.summary());
      evaluation.add(ex.evaluation);
      LogInfo.logs("Cumulative(%s): %s", prefix, evaluation.summary());
      LogInfo.end_track();
      ex.predDerivations.clear();  // To save memory
    }

    LogInfo.end_track();
    LogInfo.logs("Stats for %s: %s", prefix, evaluation.summary());
    evaluation.logStats(prefix);
    evaluation.putOutput(prefix);
    LogInfo.end_track();
  }

  private void dumpMetadata(String group, List<Example> examples) {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("metadata");
    tree.addChild(LispTree.proto.newList("group", group));
    tree.addChild(LispTree.proto.newList("size", "" + examples.size()));
    tree.print(out);
    out.println();
  }

  // ============================================================
  // Conversion to LispTree
  // ============================================================

  private LispTree exampleToLispTree(ParserState state) {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("example");

    // Basic information
    Example ex = state.ex;
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
    LispTree derivations = LispTree.proto.newList();
    derivations.addChild("derivations");
    List<Derivation> preds = state.predDerivations;
    for (int i = 0; i < preds.size(); i++) {
      Derivation deriv = preds.get(i);
      if (!isPruned(deriv)) {
        derivations.addChild(deriv.toLispTree());
      }
    }
    tree.addChild(derivations);

    return tree;
  }

  // Decide whether we should skip the derivation
  private boolean isPruned(Derivation derivation) {
    // Check if there is at least one answer
    if (derivation.value instanceof ListValue) {
      ListValue list = (ListValue) derivation.value;
      if (list.values.isEmpty()) return true;
    } else return true;
    // Check if the type is not row
    if (derivation.type.meet(TableTypeSystem.ROW_SEMTYPE).isValid()) return true;
    return false;
  }

  // ============================================================
  // Dumping LispTree
  // ============================================================

  private void dumpExample(LispTree tree) {
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
  }
}
