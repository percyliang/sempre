package edu.stanford.nlp.sempre.tables.alignment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.sempre.tables.alignment.AlignerMain.NullWordHandling;
import fig.basic.LogInfo;
import fig.basic.MapUtils;
import fig.basic.Pair;

class IBM1AlignmentComputer implements AlignmentComputer {

  private final BitextData data;
  private final boolean swap;

  public IBM1AlignmentComputer(BitextData data, boolean swap) {
    this.data = data;
    this.swap = swap;
  }

  @Override
  public DoubleMap align() {
    // (source, target) => P(target|source)
    DoubleMap alignment;
    Set<String> allSources = data.allSources(swap);
    Set<String> allTargets = data.allTargets(swap);
    if (AlignerMain.opts.nullWordHandling == NullWordHandling.trained)
      allSources.add(null);

    // Initialize uniformly
    LogInfo.begin_track("Initialize");
    alignment = new DoubleMap.ConstantDoubleMap(1.0 / allTargets.size());
    LogInfo.end_track();

    // EM
    for (int iter = 0; iter < AlignerMain.opts.maxIters; iter++) {
      LogInfo.begin_track("EM Iteration %d", iter);
      // (source, target) => count(source, target)
      DoubleMap softCounts = new DoubleMap();
      Map<String, Double> sourceToMarginalized = new HashMap<>();
      for (BitextDataGroup group : data.dataGroups()) {
        for (BitextDatum datum : group.groupData) {
          double weight = 1.0 / group.count;
          List<String> sources = datum.getSource(swap);
          if (AlignerMain.opts.nullWordHandling == NullWordHandling.trained) {
            // Add a null word in front
            sources = new ArrayList<String>(sources);
            sources.add(0, null);
          }
          double[] probs = new double[sources.size()];
          for (String target : datum.getTarget(swap)) {
            double normalizer = 1e-10;
            for (int i = 0; i < sources.size(); i++) {
              probs[i] = alignment.get(sources.get(i), target);
              normalizer += probs[i];
            }
            if (AlignerMain.opts.nullWordHandling == NullWordHandling.fixed) {
              normalizer += AlignerMain.opts.nullWordProb;
            } else if (AlignerMain.opts.nullWordHandling == NullWordHandling.varied) {
              normalizer += 1.0 / (sources.size() + 1);
            }
            for (int i = 0; i < sources.size(); i++) {
              double softCount = probs[i] * weight / normalizer;
              softCounts.incr(sources.get(i), target, softCount);
              MapUtils.incr(sourceToMarginalized, sources.get(i), softCount);
            }
          }
        }
      }
      alignment = new DoubleMap();
      for (Map.Entry<Pair<String, String>, Double> entry : softCounts.entrySet()) {
        if (entry.getValue() <= 0) continue;
        double prob = entry.getValue() / sourceToMarginalized.get(entry.getKey().getFirst());
        if (prob > AlignerMain.epsilon)
          alignment.put(entry.getKey(), prob);
      }
      LogInfo.end_track();
    }

    if (swap) alignment.reverseKeys();
    return alignment;
  }

}
