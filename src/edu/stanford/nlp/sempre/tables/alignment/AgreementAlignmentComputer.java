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

class AgreementAlignmentComputer implements AlignmentComputer {

  private final BitextData data;

  public AgreementAlignmentComputer(BitextData data) {
    this.data = data;
  }

  @Override
  public DoubleMap align() {
    // The order is (word, pred) for both DoubleMaps
    Set<String> allWords, allPreds;
    allWords = data.allWords();
    allPreds = data.allPreds();
    if (AlignerMain.opts.nullWordHandling == NullWordHandling.trained) {
      allWords.add(null);
      allPreds.add(null);
    }

    // Initialize uniformly
    DoubleMap predGivenWord, wordGivenPred;
    LogInfo.begin_track("Initialize");
    predGivenWord = new DoubleMap.ConstantDoubleMap(1.0 / allPreds.size());
    wordGivenPred = new DoubleMap.ConstantDoubleMap(1.0 / allWords.size());
    LogInfo.end_track();

    // EM
    for (int iter = 0; iter < AlignerMain.opts.maxIters; iter++) {
      LogInfo.begin_track("EM Iteration %d", iter);
      // (word, pred) => soft count
      DoubleMap softCounts = new DoubleMap();
      Map<String, Double> wordToMarginalized = new HashMap<>(), predToMarginalized = new HashMap<>();
      for (BitextDataGroup group : data.dataGroups()) {
        for (BitextDatum datum : group.groupData) {
          double weight = 1.0 / group.count;
          List<String> words = datum.words, preds = datum.preds;
          if (AlignerMain.opts.nullWordHandling == NullWordHandling.trained) {
            // Add a null word in front
            words = new ArrayList<>(words);
            words.add(0, null);
            preds = new ArrayList<>(preds);
            preds.add(0, null);
          }
          double[][] probsWordToPred = new double[words.size()][preds.size()],
              probsPredToWord = new double[words.size()][preds.size()];
          // word to pred
          for (int j = 0; j < preds.size(); j++) {
            String pred = preds.get(j);
            double normalizer = 1e-10;
            for (int i = 0; i < words.size(); i++) {
              String word = words.get(i);
              normalizer += (probsWordToPred[i][j] = predGivenWord.get(word, pred));
            }
            if (AlignerMain.opts.nullWordHandling == NullWordHandling.fixed) {
              normalizer += AlignerMain.opts.nullWordProb;
            } else if (AlignerMain.opts.nullWordHandling == NullWordHandling.varied) {
              normalizer += 1.0 / (words.size() + 1);
            }
            for (int i = 0; i < words.size(); i++) {
              probsWordToPred[i][j] *= (weight / normalizer);
            }
          }
          // pred to word
          for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            double normalizer = 1e-10;
            for (int j = 0; j < preds.size(); j++) {
              String pred = preds.get(j);
              normalizer += (probsPredToWord[i][j] = wordGivenPred.get(word, pred));
            }
            if (AlignerMain.opts.nullWordHandling == NullWordHandling.fixed) {
              normalizer += AlignerMain.opts.nullWordProb;
            } else if (AlignerMain.opts.nullWordHandling == NullWordHandling.varied) {
              normalizer += 1.0 / (preds.size() + 1);
            }
            for (int j = 0; j < preds.size(); j++) {
              probsPredToWord[i][j] *= (weight / normalizer);
            }
          }
          // soft count
          for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            for (int j = 0; j < preds.size(); j++) {
              String pred = preds.get(j);
              double softCount = probsWordToPred[i][j] * probsPredToWord[i][j];
              softCounts.incr(word, pred, softCount);
              MapUtils.incr(wordToMarginalized, word, softCount);
              MapUtils.incr(predToMarginalized, pred, softCount);
            }
          }
        }
      }
      predGivenWord = new DoubleMap();
      wordGivenPred = new DoubleMap();
      for (Map.Entry<Pair<String, String>, Double> entry : softCounts.entrySet()) {
        if (entry.getValue() <= 0) continue;
        double prob = entry.getValue() / wordToMarginalized.get(entry.getKey().getFirst());
        if (prob > AlignerMain.epsilon)
          predGivenWord.put(entry.getKey(), prob);
        prob = entry.getValue() / predToMarginalized.get(entry.getKey().getSecond());
        if (prob > AlignerMain.epsilon)
          wordGivenPred.put(entry.getKey(), prob);
      }
      LogInfo.end_track();
    }
    return DoubleMap.product(predGivenWord, wordGivenPred);
  }

}
