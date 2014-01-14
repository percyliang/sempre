package edu.stanford.nlp.sempre.vis;

import com.google.common.base.Joiner;
import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.Example;
import edu.stanford.nlp.sempre.Vis;
import fig.basic.LogInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Visualize confusion matrices for binary-classifier-based derivation rankings
 * in the semantic parser.
 *
 * @author Roy Frostig
 */
public class ConfusionMatrices {

  private final List<String> execPaths;

  public ConfusionMatrices(List<String> execPaths) {
    this.execPaths = execPaths;
  }

  public boolean logs(int iter, String group) {
    List<File> files = Vis.getFilesPerExec(execPaths, iter, group);

    if (files == null)
      return false;

    LogInfo.logs("Reading files: %s", files);
    final int n = files.size();

    List<ConfusionMatrix> softMs = new ArrayList<ConfusionMatrix>(n);
    List<ConfusionMatrix> hardMs = new ArrayList<ConfusionMatrix>(n);
    for (int i = 0; i < n; i++) {
      softMs.add(new ConfusionMatrix());
      hardMs.add(new ConfusionMatrix());
    }

    final double ct = 0.5d;
    final double pt = 0.5d;

    for (List<Example> row : Vis.zipExamples(files)) {
      for (int i = 0; i < n; i++) {
        Example ex = row.get(i);
        ConfusionMatrix softM = softMs.get(i);
        ConfusionMatrix hardM = hardMs.get(i);
        updateConfusionMatrix(softMs.get(i), ex, -1.0d, -1.0d);
        updateConfusionMatrix(hardMs.get(i), ex, ct, pt);
      }
    }

    LogInfo.begin_track("Soft");
    logsMatrices(softMs);
    LogInfo.end_track();

    LogInfo.begin_track("Hard (compatThresh=%.2f, probThresh=%.2f)", ct, pt);
    logsMatrices(hardMs);
    LogInfo.end_track();

    return true;
  }

  private static class ConfusionMatrix {
    double
        tp = 0.0d, fn = 0.0d,
        fp = 0.0d, tn = 0.0d;
  }

  private void logsMatrices(List<ConfusionMatrix> ms) {
    final int n = ms.size();
    String[] putLine1 = new String[n];
    String[] putLine2 = new String[n];
    // Figure out width :/
    double max = 0.0d;
    for (int i = 0; i < n; i++) {
      ConfusionMatrix m = ms.get(i);
      max = Math.max(
          max,
          Math.max(
              Math.max(m.tp, m.fn),
              Math.max(m.fp, m.tn)));
    }
    int w = (int) Math.floor(Math.log10(max)) + 4;
    for (int i = 0; i < n; i++) {
      putLine1[i] = String.format("[%" + w + ".2f %" + w + ".2f]", ms.get(i).tp, ms.get(i).fn);
      putLine2[i] = String.format("[%" + w + ".2f %" + w + ".2f]", ms.get(i).fp, ms.get(i).tn);
    }
    LogInfo.log(Joiner.on("     ").join(putLine1));
    LogInfo.log(Joiner.on("     ").join(putLine2));
  }

  private void updateConfusionMatrix(ConfusionMatrix m,
                                     Example ex,
                                     double compatDecisionThreshold,
                                     double probDecisionThreshold) {
    List<Derivation> derivations = ex.getPredDerivations();
    double[] probs = Derivation.getProbs(derivations, 1.0d);
    for (int i = 0; i < derivations.size(); i++) {
      Derivation deriv = derivations.get(i);
      double gold, pred;
      if (compatDecisionThreshold == -1.0d)
        gold = deriv.getCompatibility();
      else
        gold = (deriv.getCompatibility() > compatDecisionThreshold) ? 1.0d : 0.0d;
      if (probDecisionThreshold == -1.0d)
        pred = probs[i];
      else
        pred = (probs[i] > probDecisionThreshold) ? 1.0d : 0.0d;
      m.tp += gold * pred;
      m.fn += gold * (1.0d - pred);
      m.fp += (1.0d - gold) * pred;
      m.tn += (1.0d - gold) * (1.0d - pred);
    }
  }

  public void logsAll() {
    boolean done = false;
    for (int iter = 0; !done; iter++)
      for (String group : new String[]{"train", "dev"})
        if (done = !logs(iter, group))
          break;
  }
}
