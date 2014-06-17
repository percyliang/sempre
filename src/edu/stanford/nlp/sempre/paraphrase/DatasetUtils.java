package edu.stanford.nlp.sempre.paraphrase;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import fig.basic.Pair;
import fig.prob.SampleUtils;

public class DatasetUtils {

  public static <V> Pair<List<V>,List<V>> splitTrainFromDev(List<V> originalExamples,double trainFrac, double devFrac, Random rand) {
    int split1 = (int) (trainFrac * originalExamples.size());
    int split2 = (int) ((1 - devFrac) * originalExamples.size());
    int[] perm = SampleUtils.samplePermutation(rand, originalExamples.size());

    List<V> trainExamples = new ArrayList<V>();
    List<V> devExamples = new ArrayList<V>();

    for (int i = 0; i < split1; i++)
      trainExamples.add(originalExamples.get(perm[i]));
    for (int i = split2; i < originalExamples.size(); i++)
      devExamples.add(originalExamples.get(perm[i]));
    
    return Pair.newPair(trainExamples, devExamples);

  }

}
