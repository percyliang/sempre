package edu.stanford.nlp.sempre.tables.serialize;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.sempre.*;
import fig.basic.*;

public class SerializedDataset extends Dataset {
  public static class Options {
    @Option(gloss = "Base directory for dumped datasets")
    public String dumpDir = null;
  }
  public static Options opts = new Options();

  private final Map<String, String> availableGroups = new LinkedHashMap<>();

  public void read() {
    LogInfo.begin_track_printAll("Dataset.read");
    if (Dataset.opts.trainFrac != 1)
      LogInfo.warnings("Dataset.opts.trainfrac is ignored!");
    List<Pair<String, String>> pathPairs = new ArrayList<>();
    if (opts.dumpDir != null) {
      for (String group : new String[] {"train", "dev", "test"}) {
        String filename = opts.dumpDir + "/dumped-" + group + ".gz";
        File file = new File(filename);
        if (file.exists()) {
          pathPairs.add(new Pair<>(group, filename));
        }
      }
    } else {
      pathPairs.addAll(Dataset.opts.inPaths);
    }
    for (Pair<String, String> pathPair : pathPairs) {
      File file = new File(pathPair.getSecond());
      if (!file.exists() || file.isDirectory()) {
        throw new RuntimeException("Error reading dataset: " + file + " does not exist.");
      }
      availableGroups.put(pathPair.getFirst(), pathPair.getSecond());
      LogInfo.logs("%s = %s", pathPair.getFirst(), pathPair.getSecond());
    }
    LogInfo.end_track();
  }

  private static int getMaxExamplesForGroup(String group) {
    int maxExamples = Integer.MAX_VALUE;
    for (Pair<String, Integer> maxPair : Dataset.opts.maxExamples)
      if (maxPair.getFirst().equals(group))
        maxExamples = maxPair.getSecond();
    return maxExamples;
  }

  public Set<String> groups() { return availableGroups.keySet(); }

  public List<Example> examples(String group) {
    return new LoadedExampleList(availableGroups.get(group), getMaxExamplesForGroup(group));
  }

}
