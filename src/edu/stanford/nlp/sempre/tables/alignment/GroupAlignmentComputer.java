package edu.stanford.nlp.sempre.tables.alignment;

import java.util.*;

import fig.basic.*;

public class GroupAlignmentComputer implements AlignmentComputer {

  private final BitextData data;

  public GroupAlignmentComputer(BitextData data) {
    this.data = data;
  }

  @Override
  public DoubleMap align() {
    // (word, pred) => p(word|pred)
    DoubleMap alignment;

    // Initialize uniformly
    LogInfo.begin_track("Initialize");
    alignment = new DoubleMap.ConstantDoubleMap(1.0 / data.allWords().size());
    LogInfo.end_track();

    // EM
    for (int iter = 0; iter <= AlignerMain.opts.maxIters; iter++) {
      LogInfo.begin_track("EM Iteration %d", iter);
      // count(word|pred)
      DoubleMap softCounts = new DoubleMap();
      // count(*|pred)
      Map<String, Double> predToMarginalized = new HashMap<>();
      // Go through each group:
      for (BitextDataGroup group : data.dataGroups()) {
        // Compute the posterior
        List<String> words = group.words;
        List<double[][]> posteriors = new ArrayList<>();
        List<double[]> posteriorsSumOverAi = new ArrayList<>();
        double[] totalPosteriors;
        {
          double[] logTotalPosteriors = new double[group.groupData.size()];
          for (int r = 0; r < group.groupData.size(); r++) {
            List<String> preds = group.groupData.get(r).preds;
            // TODO(ice): Make the one below vary with formula size
            double rGivenF = 1.0 / group.groupData.size();
            logTotalPosteriors[r] += Math.log(rGivenF);
            // posteriorsForFr[i][j] = p(a_i = j) p(e_i | f_j)
            double[][] posteriorsForFr = new double[words.size()][preds.size()];
            double[] posteriorsSumOverAiForFr = new double[words.size()];
            posteriors.add(posteriorsForFr);
            posteriorsSumOverAi.add(posteriorsSumOverAiForFr);
            for (int i = 0; i < words.size(); i++) {
              posteriorsSumOverAiForFr[i] = IBMAligner.opts.nullWordProb;
              for (int j = 0; j < preds.size(); j++) {
                posteriorsForFr[i][j] = alignment.get(words.get(i), preds.get(j)) / preds.size();
                posteriorsSumOverAiForFr[i] += posteriorsForFr[i][j];
              }
              logTotalPosteriors[r] += Math.log(posteriorsSumOverAiForFr[i]);
            }
          }
          NumUtils.expNormalize(logTotalPosteriors);
          totalPosteriors = logTotalPosteriors;
        }
        // Aggregate the soft counts
        for (int r = 0; r < group.groupData.size(); r++) {
          List<String> preds = group.groupData.get(r).preds;
          for (int i = 0; i < words.size(); i++) {
            for (int j = 0; j < preds.size(); j++) {
              double softCount = totalPosteriors[r] / posteriorsSumOverAi.get(r)[i] * posteriors.get(r)[i][j];
              softCounts.incr(words.get(i), preds.get(j), softCount);
              MapUtils.incr(predToMarginalized, preds.get(j), softCount);
            }
          }
        }
        if (iter == AlignerMain.opts.maxIters) {
          int rBest = 0;
          double rBestScore = 0;
          for (int r = 0; r < group.groupData.size(); r++) {
            if (totalPosteriors[r] > rBestScore) {
              rBest = r;
              rBestScore = totalPosteriors[r];
            }
          }
          LogInfo.logs("%s", group.words);
          LogInfo.logs(">> %s", group.groupData.get(rBest).preds);
        }
      }
      if (iter < AlignerMain.opts.maxIters) {
        // Update parameters
        alignment = new DoubleMap();
        for (Map.Entry<Pair<String, String>, Double> entry : softCounts.entrySet()) {
          if (entry.getValue() <= 0) continue;
          double prob = entry.getValue() / predToMarginalized.get(entry.getKey().getSecond());
          if (prob > AlignerMain.epsilon)
            alignment.put(entry.getKey(), prob);
        }
      }
      LogInfo.end_track();
    }

    return alignment;
  }
}
