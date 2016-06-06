package edu.stanford.nlp.sempre.tables.alter;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

import fig.basic.*;

public class CachedSubsetChooser implements SubsetChooser {
  public static class Options {
    @Option(gloss = "read the list of retained table from these files")
    public List<String> retainedTablesFilenames = new ArrayList<>();
  }
  public static Options opts = new Options();

  Map<String, Subset> cache = new HashMap<>();

  public CachedSubsetChooser() {
    for (String filename : opts.retainedTablesFilenames)
      load(filename);
  }

  private void load(String retainedTablesFilename) {
    try {
      BufferedReader reader = IOUtils.openInHard(retainedTablesFilename);
      String line;
      while ((line = reader.readLine()) != null) {
        Subset subset = Subset.fromString(line);
        if (subset.score > Double.NEGATIVE_INFINITY)
          cache.put(subset.id, subset);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Subset chooseSubset(String id, DenotationData denotationData) {
    return cache.get(id);
  }

  @Override
  public Subset chooseSubset(String id, DenotationData denotationData, Collection<Integer> forbiddenTables) {
    throw new RuntimeException("CachedSubsetChooser.chooseSubset cannot take forbiddenTables");
  }

}
