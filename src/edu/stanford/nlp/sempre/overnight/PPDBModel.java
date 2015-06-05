package edu.stanford.nlp.sempre.overnight;

import fig.basic.IOUtils;
import fig.basic.LogInfo;
import fig.basic.MapUtils;
import fig.basic.Option;
import edu.stanford.nlp.sempre.*;

import java.util.HashMap;
import java.util.Map;

/**
 * PPDBModel extracts and scores paraphrasing featues from derivations.
 * This model is intended to be used with FloatingParser
 *
 * @author Yushi Wang
 */

public final class PPDBModel {
  public static class Options {
    @Option(gloss = "Path to file with alignment table")
    public String ppdbModelPath = "regex/regex-ppdb.txt";

    @Option(gloss = "Using ppdb format")
    public boolean ppdb = true;
  }

  public static Options opts = new Options();

  public static PPDBModel model;

  Map<String, Map<String, Double>> table;

  // We should only have one paraphrase model
  public static PPDBModel getSingleton() {
    if (model == null) {
      model = new PPDBModel();
    }
    return model;
  }

  private PPDBModel() {
    table = loadPPDBModel(opts.ppdbModelPath);
  }

  /**
   * Loading ppdb model from file
   */
  private Map<String, Map<String, Double>> loadPPDBModel(String path) {
    LogInfo.begin_track("Loading ppdb model");
    Map<String, Map<String, Double>> res = new HashMap<>();
    for (String line: IOUtils.readLinesHard(path)) {
      if (opts.ppdb) {
        String[] tokens = line.split("\\|\\|\\|");
        String first = tokens[1].trim();
        String second = tokens[2].trim();
        String stemmedFirst = LanguageInfo.LanguageUtils.stem(first);
        String stemmedSecond = LanguageInfo.LanguageUtils.stem(second);

        putParaphraseEntry(res, first, second);
        if ((!stemmedFirst.equals(first) || !stemmedSecond.equals(second)) &&
                !stemmedFirst.equals(stemmedSecond))
          putParaphraseEntry(res, stemmedFirst, stemmedSecond);
      } else {
        String[] tokens = line.split("\t");
        MapUtils.putIfAbsent(res, tokens[0], new HashMap<>());
        for (String token : tokens)
          LogInfo.logs("%s", token);
        res.get(tokens[0]).put(tokens[1], 1.0);
      }
    }
    LogInfo.logs("ParaphraseUtils.loadPhraseTable: number of entries=%s", res.size());
    LogInfo.end_track();
    return res;
  }

  private void putParaphraseEntry(Map<String, Map<String, Double>> res, String first, String second) {
    MapUtils.putIfAbsent(res, first, new HashMap<>());
    res.get(first).put(second, 1.0);
  }

  public boolean containsKey(String key) {
    return table.containsKey(key);
  }

  public Double get(String key, String token) {
    if (!table.containsKey(key) || !table.get(key).containsKey(token)) return 0.0;
    return table.get(key).get(token);
  }
}
