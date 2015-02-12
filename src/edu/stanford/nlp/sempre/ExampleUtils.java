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
    out.println("# " + p.L(p.L("iter", iter), p.L("group", group), p.L("numExamples", examples.size()), p.L("evaluation", evaluation.toLispTree())));
    for (Example ex : examples) {
      out.println("");
      out.println("example " + ex.id);
      out.println("description " + p.L(p.L("utterance", ex.utterance), p.L("targetValue", ex.targetValue.toLispTree()), p.L("evaluation", ex.evaluation.toLispTree())));

      if (outputPredDerivations) {
        for (Derivation deriv : ex.predDerivations) {
          StringBuilder buf = new StringBuilder();
          buf.append("item " + escapeSpace(p.L(p.L("formula", deriv.formula.toLispTree()), p.L("value", deriv.value.toLispTree())).toString()));  // Description
          buf.append(" " + deriv.compatibility);
          Map<String, Double> features = deriv.getAllFeatureVector();
          for (Map.Entry<String, Double> e : features.entrySet()) {
            buf.append(" " + escapeSpace(e.getKey()) + ":" + e.getValue());
          }
          out.println(buf.toString());
        }
      }
    }
    out.close();
    LogInfo.end_track();
  }
}
