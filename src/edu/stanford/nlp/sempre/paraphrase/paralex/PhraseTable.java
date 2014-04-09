package edu.stanford.nlp.sempre.paraphrase.paralex;

import java.util.HashMap;
import java.util.Map;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.sempre.paraphrase.Aligner.AlignmentStats;
import fig.basic.LogInfo;
import fig.basic.MapUtils;
import fig.basic.Option;

public class PhraseTable {
  
  public static class Options {
    @Option(gloss="Path to file with phrase table") public String phraseTablePath="lib/paralex/phrase-table.counts.txt";
    @Option(gloss="Minimum phrase table count") public int phraseTableThresh=5;
  }
  public static Options opts = new Options();
  
  public static PhraseTable phraseTable;
  
  Map<String,Map<String,AlignmentStats>> table;

  public static PhraseTable getSingleton() { 
    if(phraseTable==null) {
      phraseTable = new PhraseTable();
    }
    return phraseTable;     
  }
  
  private PhraseTable() {
    table = loadPhraseTable(opts.phraseTablePath, opts.phraseTableThresh);
  }
  
  /**
   * Loading phrase table from file
   * @return
   */
  public Map<String, Map<String,AlignmentStats>> loadPhraseTable(String path, int threshold) {
    LogInfo.begin_track("Loading phrase table");
    Map<String,Map<String,AlignmentStats>> res = new HashMap<String,Map<String,AlignmentStats>>();
    for(String line: IOUtils.readLines(path)) {
      String[] tokens = line.split("\t");
      double count = Double.parseDouble(tokens[2]);
      MapUtils.putIfAbsent(res, tokens[0], new HashMap<String,AlignmentStats>());
      if(count>=threshold) {
        AlignmentStats aStats = new AlignmentStats(count, Double.parseDouble(tokens[3]), Double.parseDouble(tokens[4]));
        res.get(tokens[0]).put(tokens[1], aStats);
      }
    }
    LogInfo.logs("ParaphraseUtils.loadPhraseTable: number of entries=%s",res.size());
    LogInfo.end_track();
    return res;
  }

  public boolean containsKey(String lhsPhrase) {
    return table.containsKey(lhsPhrase);
  }

  public Map<String, AlignmentStats> get(String lhsPhrase) {
    return table.get(lhsPhrase);
  }
}
