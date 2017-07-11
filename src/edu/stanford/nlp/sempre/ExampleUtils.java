package edu.stanford.nlp.sempre;

import fig.basic.*;
import fig.exec.Execution;

import java.io.*;
import java.util.*;

/**
 * Output examples in various forms.
 *
 * @author Percy Liang
 */
public final class ExampleUtils {
  private ExampleUtils() { }

  // Output JSON file with just the basic input/output.
  public static void writeJson(List<Example> examples) {
    PrintWriter out = IOUtils.openOutHard(Execution.getFile("examples.json"));
    for (Example ex : examples)
      out.println(ex.toJson());
    out.close();
  }

  public static void writeJson(List<Example> examples, String outPath) throws IOException {
    PrintWriter out = IOUtils.openOutHard(outPath);
    out.println("[");
    for (int i = 0; i < examples.size(); ++i) {
      Example ex = examples.get(i);
      out.print(ex.toJson());
      out.println(i < examples.size() - 1 ? "," : "");
    }
    out.println("]");
    out.close();
  }

  private static String escapeSpace(String s) {
    return s.replaceAll(" ", "&nbsp;");
  }

  // Output examples in Simple Dataset Format (Ranking).
  public static void writeSDF(int iter, String group,
                              Evaluation evaluation,
                              List<Example> examples,
                              boolean outputPredDerivations) {
    String basePath = "preds-iter" + iter + "-" + group + ".examples";
    String outPath = Execution.getFile(basePath);
    if (outPath == null || examples.size() == 0) return;
    LogInfo.begin_track("Writing examples to %s", basePath);
    PrintWriter out = IOUtils.openOutHard(outPath);

    LispTree p = LispTree.proto;
    out.println("# SDF version 1.1");
    out.println("# " + p.L(p.L("iter", iter), p.L("group", group), p.L("numExamples", examples.size()), p.L("evaluation", evaluation.toLispTree())));
    for (Example ex : examples) {
      out.println("");
      out.println("example " + ex.id);
      out.println("description " + p.L(p.L("utterance", ex.utterance), p.L("targetValue", ex.targetValue.toLispTree()), p.L("evaluation", ex.evaluation.toLispTree())));

      if (outputPredDerivations) {
        for (Derivation deriv : ex.predDerivations) {
          StringBuilder buf = new StringBuilder();
          buf.append("item");
          LispTree description = p.newList();
          if (deriv.canonicalUtterance != null)
            description.addChild(p.L("canonicalUtterance", deriv.canonicalUtterance));
          description.addChild(p.L("formula", deriv.formula.toLispTree()));
          description.addChild(p.L("value", deriv.value.toLispTree()));
          buf.append("\t" + description);
          buf.append("\t" + deriv.compatibility);
          Map<String, Double> features = deriv.getAllFeatureVector();
          buf.append("\t");
          boolean first = true;
          for (Map.Entry<String, Double> e : features.entrySet()) {
            if (!first)
              buf.append(' ');
            first = false;
            buf.append(e.getKey() + ":" + e.getValue());
          }
          out.println(buf.toString());
        }
      }
    }
    out.close();
    LogInfo.end_track();
  }

  public static void writeParaphraseSDF(int iter, String group, Example ex,
                                        boolean outputPredDerivations) {
    String basePath = "preds-iter" + iter + "-" + group + ".examples";
    String outPath = Execution.getFile(basePath);
    if (outPath == null) return;
    PrintWriter out = IOUtils.openOutAppendHard(outPath);

    out.println("example " + ex.id);

    if (outputPredDerivations) {
      int i = 0;
      for (Derivation deriv : ex.predDerivations) {
        if (deriv.canonicalUtterance != null)
          out.println("Pred@" + i + ":\t" + ex.utterance + "\t" + deriv.canonicalUtterance + "\t" + deriv.compatibility + "\t" + deriv.formula + "\t" + deriv.prob);
        i++;
      }
    }
    out.close();
  }

  public static void writeEvaluationSDF(int iter, String group,
                                        Evaluation evaluation, int numExamples) {
    String basePath = "preds-iter" + iter + "-" + group + ".examples";
    String outPath = Execution.getFile(basePath);
    if (outPath == null) return;
    PrintWriter out = IOUtils.openOutAppendHard(outPath);

    LispTree p = LispTree.proto;
    out.println("");
    out.println("# SDF version 1.1");
    out.println("# " + p.L(p.L("iter", iter), p.L("group", group), p.L("numExamples", numExamples), p.L("evaluation", evaluation.toLispTree())));
    out.close();
  }

  public static void writePredictionTSV(int iter, String group, Example ex) {
    String basePath = "preds-iter" + iter + "-" + group + ".tsv";
    String outPath = Execution.getFile(basePath);
    if (outPath == null) return;
    PrintWriter out = IOUtils.openOutAppendHard(outPath);

    List<String> fields = new ArrayList<>();
    fields.add(ex.id);

    if (!ex.predDerivations.isEmpty()) {
      Derivation deriv = ex.predDerivations.get(0);
      if (deriv.value instanceof ListValue) {
        List<Value> values = ((ListValue) deriv.value).values;
        for (Value v : values) {
          fields.add(v.pureString().replaceAll("\\s+", " ").trim());
        }
      }
    }

    out.println(String.join("\t", fields));
    out.close();
  }

  //read lisptree and write json
  public static void main(String[] args) {
    Dataset dataset = new Dataset();
    Pair<String, String> pair = Pair.newPair("train", args[0]);
    Dataset.opts.splitDevFromTrain = false;
    dataset.readFromPathPairs(Collections.singletonList(pair));
    List<Example> examples = dataset.examples("train");
    try {
      writeJson(examples, args[1]);
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }
}
