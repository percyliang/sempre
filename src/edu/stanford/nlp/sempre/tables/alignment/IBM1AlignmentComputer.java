package edu.stanford.nlp.sempre.tables.alignment;

import java.io.PrintWriter;
import java.util.*;

import edu.stanford.nlp.sempre.Formula;
import edu.stanford.nlp.sempre.Learner;
import edu.stanford.nlp.sempre.tables.alignment.AlignerMain.NullWordHandling;
import edu.stanford.nlp.sempre.tables.alignment.BitextData.BitextDataGroup;
import edu.stanford.nlp.sempre.tables.alignment.BitextData.BitextDatum;
import fig.basic.*;
import fig.exec.Execution;

/**
 * IBM Model 1.
 *
 * Each target word independently aligns to a source word (or NULL).
 *
 * @author ppasupat
 */
public abstract class IBM1AlignmentComputer implements AlignmentComputer {
  public static class Options {
    @Option(gloss = "When computing p(t|s), use the sum over all alignments instead of argmax")
    public boolean marginalizeAlignments = false;
  }
  public static Options opts = new Options();

  // genProbs[w_s, w_t]
  // = P_theta(w_t|w_s) = probability of aligning target word w_t to source word w_s
  protected DoubleMap genProbs;

  protected abstract Collection<String> getAllSources(BitextData bitextData);
  protected abstract Collection<String> getAllTargets(BitextData bitextData);
  protected abstract List<String> getSource(BitextDatum datum);
  protected abstract List<String> getTarget(BitextDatum datum);

  protected double getNullProb(String target, int sourcesSize) {
    switch (AlignerMain.opts.nullWordHandling) {
      case FIXED: return AlignerMain.opts.fixedNullWordProb;
      case UNIFORM: return 1.0 / sourcesSize;
      case TRAINED: return genProbs.get(null, target);
      default:
        throw new RuntimeException("Unknown nullWordHandling " + AlignerMain.opts.nullWordHandling);
    }
  }

  @Override
  public void align(BitextData bitextData) {
    LogInfo.begin_track("IBM1AlignmentComputer.align()");
    NullWordHandling nullWordHandling = AlignerMain.opts.nullWordHandling;

    // Initialize
    LogInfo.begin_track("Initialize ...");
    Set<String> allSources = new HashSet<>(getAllSources(bitextData));
    Set<String> allTargets = new HashSet<>(getAllTargets(bitextData));
    allSources.add(null);
    genProbs = new DoubleMap.ConstantDoubleMap(1.0 / allTargets.size());
    LogInfo.end_track();

    // Run EM
    for (int iter = 0; iter < Learner.opts.maxTrainIters; iter++) {
      LogInfo.begin_track("EM Iteration %d", iter);
      Execution.putOutput("iter", iter);
      // parameters --> alignments
      // alignmentSoftCounts[w_s, w_t]
      // = soft count of the number of times w_t aligns to w_s
      DoubleMap alignmentSoftCounts = new DoubleMap();
      // sourceToMarginalSoftCounts[w_s]
      // sum of alignmentSoftCounts[w_s, w_t] over all w_t
      Map<String, Double> sourceToMarginalSoftCounts = new HashMap<>();
      for (int groupIndex = 0; groupIndex < bitextData.bitextDataGroups.size(); groupIndex++) {
        BitextDataGroup group = bitextData.bitextDataGroups.get(groupIndex);
        LogInfo.logs("Example %s (%d): %s", group.id, groupIndex, group.tokens);
        Execution.putOutput("example", groupIndex);
        double weight = 1.0 / group.bitextDatums.size();
        for (BitextDatum datum : group.bitextDatums) {
          List<String> sources = new ArrayList<>(getSource(datum));
          // Add a null word in front
          sources.add(0, null);
          List<String> targets = getTarget(datum);
          double[] probs = new double[sources.size()];
          for (String target : targets) {
            double normalizer = 1e-10;
            for (int i = 1; i < sources.size(); i++) {
              probs[i] = genProbs.get(sources.get(i), target);
              normalizer += probs[i];
            }
            // Handle NULL source
            probs[0] = getNullProb(target, sources.size());
            normalizer += probs[0];
            for (int i = 0; i < sources.size(); i++) {
              double softCount = probs[i] * weight / normalizer;
              alignmentSoftCounts.incr(sources.get(i), target, softCount);
              MapUtils.incr(sourceToMarginalSoftCounts, sources.get(i), softCount);
            }
          }
        }
      }
      // alignments --> parameters
      genProbs = new DoubleMap();
      for (Map.Entry<Pair<String, String>, Double> entry : alignmentSoftCounts.entrySet()) {
        String source = entry.getKey().getFirst(), target = entry.getKey().getSecond();
        double alignmentSoftCount = entry.getValue();
        if (nullWordHandling != NullWordHandling.TRAINED && source == null) continue;
        if (alignmentSoftCount <= 0) continue;
        double prob = alignmentSoftCount / sourceToMarginalSoftCounts.get(source);
        if (prob > AlignerMain.epsilon)
          genProbs.put(source, target, prob);
      }
      LogInfo.end_track();
    }

    LogInfo.end_track();
  }

  @Override
  public void dump(PrintWriter out) {
    for (Map.Entry<Pair<String, String>, Double> entry : genProbs.entrySet()) {
      double value = entry.getValue();
      if (value < AlignerMain.epsilon)
        continue;
      out.printf("%s\t%s\t%.6f\n", entry.getKey().getFirst(), entry.getKey().getSecond(), value);
    }
  }

  @Override
  public List<Pair<Formula, Double>> score(BitextDataGroup group) {
    List<Pair<Formula, Double>> scores = new ArrayList<>();
    for (BitextDatum datum : group.bitextDatums) {
      scores.add(new Pair<>(datum.formula, score(datum)));
    }
    return scores;
  }

  @Override
  public double score(BitextDatum datum) {
    List<String> sources = new ArrayList<>(getSource(datum));
    // Add a null word in front
    sources.add(0, null);
    List<String> targets = getTarget(datum);
    if (AlignerMain.opts.verbose >= 2)
      LogInfo.begin_track("%s | %s", sources, targets);
    double score = 0;
    for (String target : targets) {
      double alignmentScore = getNullProb(target, sources.size());
      if (opts.marginalizeAlignments) {
        // Sum over all alignments
        for (int i = 1; i < sources.size(); i++) {
          String source = sources.get(i);
          alignmentScore += genProbs.get(source, target);
        }
        if (AlignerMain.opts.verbose >= 2)
          LogInfo.logs("[%10.3f] %20s", alignmentScore, target);
      } else {
        // Use only the best alignment
        String bestSource = null;
        for (int i = 1; i < sources.size(); i++) {
          String source = sources.get(i);
          double challenger = genProbs.get(source, target);
          if (challenger > alignmentScore) {
            alignmentScore = challenger;
            bestSource = source;
          }
        }
        if (AlignerMain.opts.verbose >= 2)
          LogInfo.logs("[%10.3f] %20s : %s", alignmentScore, target, bestSource);
      }
      score += Math.log(alignmentScore);
    }
    if (AlignerMain.opts.verbose >= 2)
      LogInfo.end_track();
    // Normalize by length
    score -= targets.size() * Math.log(sources.size());
    return score;
  }

}

/**
 * IBM Model 1 that models p(formula|utterance) [x ==> z]
 */
class IBM1XToZAlignmentComputer extends IBM1AlignmentComputer {

  @Override
  protected Collection<String> getAllSources(BitextData bitextData) {
    return bitextData.allTokens;
  }

  @Override
  protected Collection<String> getAllTargets(BitextData bitextData) {
    return bitextData.allPredicates;
  }

  @Override
  protected List<String> getSource(BitextDatum datum) {
    return datum.group.tokens;
  }

  @Override
  protected List<String> getTarget(BitextDatum datum) {
    return datum.predicates;
  }

}

/**
 * IBM Model 1 that models p(utterance|formula). [z ==> x]
 */
class IBM1ZToXAlignmentComputer extends IBM1AlignmentComputer {

  @Override
  protected Collection<String> getAllSources(BitextData bitextData) {
    return bitextData.allPredicates;
  }

  @Override
  protected Collection<String> getAllTargets(BitextData bitextData) {
    return bitextData.allTokens;
  }

  @Override
  protected List<String> getSource(BitextDatum datum) {
    return datum.predicates;
  }

  @Override
  protected List<String> getTarget(BitextDatum datum) {
    return datum.group.tokens;
  }
}
