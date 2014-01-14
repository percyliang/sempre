package edu.stanford.nlp.sempre.vis;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.Example;
import edu.stanford.nlp.sempre.Params;
import edu.stanford.nlp.sempre.Vis;
import fig.basic.IOUtils;
import fig.basic.LogInfo;
import fig.basic.MapUtils;
import fig.basic.Pair;
import fig.exec.Execution;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Visualize predicate derivations from one or more executions of the semantic
 * parser.
 *
 * @author Roy Frostig
 */
public class ExampleDerivations {
  private final List<String> execPaths;
  private final int topN;

  public ExampleDerivations(List<String> execPaths, int topN) {
    this.execPaths = execPaths;
    this.topN = topN;
  }

  private PrintWriter tmpLog;
  private void pushLog(PrintWriter newLog) {
    tmpLog = LogInfo.getFileOut();
    LogInfo.setFileOut(newLog);
  }
  private void popLog() {
    LogInfo.setFileOut(tmpLog);
  }

  public boolean write(int iter, String group) {
    List<File> files = Vis.getFilesPerExec(execPaths, iter, group);

    if (files == null)
      return false;

    LogInfo.logs("Reading files: %s", files);
    List<Params> params = getParamsPerExec(iter);
    assert files.size() == params.size();

    String basePath = "vis.preds-iter" + iter + "-" + group + ".examples";
    String outPath = Execution.getFile(basePath);
    PrintWriter out = IOUtils.openOutHard(outPath);
    LogInfo.log("Writing " + basePath);
    pushLog(out);

    int i = 0;
    for (List<Example> row : Vis.zipExamples(files)) {
      Example first = row.get(0);
      LogInfo.begin_track("Example %d: %s [%s]", i, first.getUtterance(), first.getId());
      List<Map<Derivation, Integer>> rowDerivIndices = getRowDerivIndices(row);
      List<Derivation> firstDerivs = first.getPredDerivations();

      LogInfo.logs("STAT %s", first.getEvaluation().summary());

      for (int j = 0; j < firstDerivs.size(); j++) {
        Derivation firstDeriv = firstDerivs.get(j);
        LogInfo.begin_track("Ex %d, derivation %d", i, j);
        if (firstDeriv.getCompatibility() == 1.0d)
          LogInfo.log("DERIV CORRECT");
        else
          LogInfo.log("DERIV WRONG");
        LogInfo.logs("DERIV %s", firstDeriv);

        // TODO no executor stats being written/loaded at present.
        //firstDeriv.getExecutorStats().logStats(""+j);

        List<Pair<String, Double>> topFeatures;
        List<Pair<String, Double>> botFeatures;
        if (row.size() >= 2) {
          Integer derivIndex = rowDerivIndices.get(1).get(firstDeriv);
          Derivation secondDeriv = (derivIndex == null)
                                   ? null
                                   : row.get(1).getPredDerivations().get(derivIndex);
          topFeatures = getTopFeatures(
              topN,
              params.get(0),
              params.get(1),
              firstDeriv,
              secondDeriv,
              false);
          botFeatures = getTopFeatures(
              topN,
              params.get(0),
              params.get(1),
              firstDeriv,
              secondDeriv,
              true);
        } else {
          topFeatures = getTopFeatures(
              topN,
              params.get(0),
              null,
              firstDeriv,
              null,
              false);
          botFeatures = getTopFeatures(
              topN,
              params.get(0),
              null,
              firstDeriv,
              null,
              true);
        }

        String[] positions = new String[row.size()];
        String[][] chart = new String[topFeatures.size() + botFeatures.size()][row.size()];
        String[][] totals = new String[4][row.size()];

        for (int k = 0; k < row.size(); k++) {
          Integer derivIndex = rowDerivIndices.get(k).get(firstDeriv);

          // Positions
          positions[k] = String.format("%12s", (derivIndex == null) ? "~" : ("" + derivIndex));

          // Features
          // Walk down topFeatures and up botFeatures at the same time.
          int n = Math.max(topFeatures.size(), botFeatures.size());
          for (int f = 0; f < n; f++) {
            if (f < topFeatures.size()) {
              String featureName = topFeatures.get(f).getFirst();
              double val = params.get(k).getWeight(featureName);
              chart[f][k] = String.format("%12.4f", val);
            }
            if (f < botFeatures.size()) {
              String featureName = botFeatures.get(f).getFirst();
              double val = params.get(k).getWeight(featureName);
              chart[chart.length - 1 - f][k] = String.format("%12.4f", val);
            }
          }

          // Totals
          if (derivIndex == null) {
            totals[0][k] = totals[1][k] = totals[2][k] = totals[3][k] = String.format("%12s", "~");
          } else {
            Derivation deriv = row.get(k).getPredDerivations().get(derivIndex);
            totals[0][k] = String.format("%12.4f", deriv.getScore());
            totals[1][k] = String.format("%12.4f", deriv.getProb());
            totals[2][k] = String.format("%12.4f", deriv.getCompatibility());
            totals[3][k] = String.format("%12d", deriv.getMaxBeamPosition());
          }
        }
        LogInfo.log("");

        LogInfo.logs("%-40s\t%12s%s", "POS", "", Joiner.on(' ').join(positions));
        LogInfo.log("");

        for (int f = 0; f < chart.length; f++) {
          List<Pair<String, Double>> tops = (f < topFeatures.size()) ? topFeatures : botFeatures;
          int topsIndex = (f < topFeatures.size()) ? f : chart.length - 1 - f;
          if (f == topFeatures.size())
            LogInfo.log("...");
          LogInfo.logs(
              "%-40s\t%12.4f%s",
              "FEAT " + tops.get(topsIndex).getFirst(),
              tops.get(topsIndex).getSecond(),
              Joiner.on(' ').join(chart[f]));
        }
        LogInfo.log("");
        LogInfo.logs("%-40s\t%12s%s", "SCORE", "", Joiner.on(' ').join(totals[0]));
        LogInfo.logs("%-40s\t%12s%s", "PROB", "", Joiner.on(' ').join(totals[1]));
        LogInfo.logs("%-40s\t%12s%s", "COMPAT", "", Joiner.on(' ').join(totals[2]));
        LogInfo.logs("%-40s\t%12s%s", "MAXBEAMPOS", "", Joiner.on(' ').join(totals[3]));
        LogInfo.end_track();
      }
      LogInfo.end_track();
      i++;
    }

    popLog();
    return true;
  }

  public void writeAll() {
    boolean done = false;
    for (int iter = 0; !done; iter++)
      for (String group : new String[]{"train", "dev"})
        if (done = !write(iter, group))
          break;
  }

  private List<Map<Derivation, Integer>> getRowDerivIndices(List<Example> row) {
    List<Map<Derivation, Integer>> res = new ArrayList<Map<Derivation, Integer>>(row.size());
    for (Example ex : row) {
      Map<Derivation, Integer> m = new HashMap<Derivation, Integer>();
      int i = 0;
      for (Derivation d : ex.getPredDerivations()) {
        if (!m.containsKey(d))
          m.put(d, i);
        i++;
      }
      res.add(m);
    }
    return res;
  }

  private List<Pair<String, Double>> getTopFeatures(int topN,
                                                    Params firstParams,
                                                    Params secondParams,
                                                    Derivation firstDeriv,
                                                    Derivation secondDeriv,
                                                    boolean reverse) {
    Map<String, Double> sortBy;
    Map<String, Double> firstFeats = new HashMap<String, Double>();
    firstDeriv.incrementAllFeatureVector(1.0d, firstFeats);
    double factor = reverse ? -1.0d : 1.0d;
    if (secondDeriv == null) {
      sortBy = Utils.scale(
          factor,
          Utils.elementwiseProduct(
              firstFeats,
              firstParams.getWeights()));
    } else {
      Map<String, Double> secondFeats = new HashMap<String, Double>();
      secondDeriv.incrementAllFeatureVector(1.0d, secondFeats);
      sortBy = Utils.linearComb(
          factor, -factor,
          Utils.elementwiseProduct(
              firstFeats,
              firstParams.getWeights()),
          Utils.elementwiseProduct(
              secondFeats,
              secondParams.getWeights()));
    }
    List<Pair<String, Double>> top = MapUtils.getTopN(sortBy, topN);
    List<Pair<String, Double>> topFeats = Lists.newArrayListWithExpectedSize(top.size());
    for (Pair<String, Double> pair : top) {
      topFeats.add(
          Pair.newPair(
              pair.getFirst(),
              firstFeats.get(pair.getFirst())));
    }
    return topFeats;
  }

  private List<Params> getParamsPerExec(int iter) {
    List<Params> params = new ArrayList<Params>();
    for (String execPath : execPaths) {
      Params p = new Params();
      p.read(execPath + "/params." + iter);
      params.add(p);
    }
    return params;
  }
}
