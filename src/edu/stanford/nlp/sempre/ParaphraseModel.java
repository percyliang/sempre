package edu.stanford.nlp.sempre;

import java.util.HashMap;
import java.util.Map;

import fig.basic.IOUtils;
import fig.basic.Option;
import fig.basic.MapUtils;
import fig.basic.LogInfo;

/**
 * ParaphraseModel extracts and scores paraphrasing featues from derivations.
 * This model is intended to be used with FloatingParser
 *
 * @author Yushi Wang
 */

public final class ParaphraseModel {
  public static class Options {
    @Option(gloss = "Path to file with alignment table")
    public String paraphraseModelPath = "regex/regex-alignments.txt";
  }

  public static Options opts = new Options();

  public static ParaphraseModel model;

  Map<String, Map<String, Double>> table;

  // We should only have one paraphrase model
  public static ParaphraseModel getSingleton() {
    if (model == null) {
      model = new ParaphraseModel();
    }
    return model;
  }

  private ParaphraseModel() {
    table = loadParaphraseModel(opts.paraphraseModelPath);
  }

  /**
   * Loading paraphrase model from file
   */
  private Map<String, Map<String, Double>> loadParaphraseModel(String path) {
    LogInfo.begin_track("Loading paraphrase model");
    Map<String, Map<String, Double>> res = new HashMap<String, Map<String, Double>>();
    for (String line: IOUtils.readLinesHard(path)) {
      String[] tokens = line.split("\t");
      // double count = Double.parseDouble(tokens[2]);
      MapUtils.putIfAbsent(res, tokens[0], new HashMap<String, Double>());
      for (String token : tokens)
        LogInfo.logs("%s", token);
      res.get(tokens[0]).put(tokens[1], 1.0);
      /* Alignment stats don't matter right now
      if(count>=threshold) {
        AlignmentStats aStats = new AlignmentStats(count, Double.parseDouble(tokens[3]), Double.parseDouble(tokens[4]));
        res.get(tokens[0]).put(tokens[1], aStats);
      }
      */
    }
    LogInfo.logs("ParaphraseUtils.loadPhraseTable: number of entries=%s", res.size());
    LogInfo.end_track();
    return res;
  }

  public boolean containsKey(String key) {
    return table.containsKey(key);
  }

  public Double get(String key, String token) {
    if (!table.containsKey(key) || !table.get(key).containsKey(token)) return 0.0;
    return table.get(key).get(token);
  }
}
