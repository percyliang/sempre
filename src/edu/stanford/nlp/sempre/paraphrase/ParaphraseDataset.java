package edu.stanford.nlp.sempre.paraphrase;

import java.util.ArrayList;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Lists;

import edu.stanford.nlp.sempre.Json;
import fig.basic.IOUtils;
import fig.basic.LogInfo;
import fig.basic.MapUtils;
import fig.basic.Option;
import fig.basic.Pair;
import fig.exec.Execution;

/**
 * Holds both paraphrasing examples and parsing examples
 * TODO - now there is duplicate code for paraphrasing and parsing examples
 * @author jonathanberant
 *
 */
public class ParaphraseDataset {

  public static class Options {
    @Option(gloss = "Paths to read paraphrase example files (format: <group>:<file>)")
    public ArrayList<Pair<String, String>> paraphraseInPaths = new ArrayList<>();
    @Option(gloss = "Paths to read parsing example files (format: <group>:<file>)")
    public ArrayList<Pair<String, String>> parsingInPaths = new ArrayList<>();


    @Option(gloss = "Maximum number of paraphrase examples to read")
    public ArrayList<Pair<String, Integer>> maxParaphraseExamples = new ArrayList<>();
    @Option(gloss = "Maximum number of parsing examples to read")
    public ArrayList<Pair<String, Integer>> maxParsingExamples = new ArrayList<>();

    // Training file gets split into:
    // |  trainFrac  -->  |           | <-- devFrac |
    @Option(gloss = "Fraction of trainExamples (from the beginning) to keep for training")
    public double trainFrac = 1;
    @Option(gloss = "Fraction of trainExamples (from the end) to keep for development")
    public double devFrac = 0;
    @Option(gloss = "Used to randomly divide training examples")
    public Random splitRandom = new Random(1);
  }
  public static Options opts = new Options();

  // Group id -> examples in that group
  private LinkedHashMap<String, List<ParaphraseExample>> paraphraseExamples = new LinkedHashMap<>();
  private LinkedHashMap<String, List<ParsingExample>> parsingExamples = new LinkedHashMap<>();

  public Set<String> parsingGroups() { return parsingExamples.keySet(); }
  public List<ParaphraseExample> paraphraseExamples(String group) { return paraphraseExamples.get(group); }
  public List<ParsingExample> parsingExamples(String group) { return parsingExamples.get(group); }

  /** For JSON. */
  static class ParaphrasingGroupInfo {
    @JsonProperty final String group;
    @JsonProperty final List<ParaphraseExample> examples;
    String path;  // Optional, used if this was read from a path.
    @JsonCreator
    public ParaphrasingGroupInfo(@JsonProperty("group") String group,
        @JsonProperty("examples") List<ParaphraseExample> examples) {
      this.group = group;
      this.examples = examples;
    }
  }

  static class ParsingGroupInfo {
    @JsonProperty final String group;
    @JsonProperty final List<ParsingExample> examples;
    String path;  // Optional, used if this was read from a path.
    @JsonCreator
    public ParsingGroupInfo(@JsonProperty("group") String group,
        @JsonProperty("examples") List<ParsingExample> examples) {
      this.group = group;
      this.examples = examples;
    }
  }

  /** For JSON. */
  @JsonProperty("paraphrasing_groups")
  public List<ParaphrasingGroupInfo> getAllParaphrasingGroupInfos() {
    List<ParaphrasingGroupInfo> all = Lists.newArrayList();
    for (Map.Entry<String, List<ParaphraseExample>> entry : paraphraseExamples.entrySet())
      all.add(new ParaphrasingGroupInfo(entry.getKey(), entry.getValue()));
    return all;
  }

  /** For JSON. */
  @JsonProperty("parsing_groups")
  public List<ParsingGroupInfo> getAllParsingGroupInfos() {
    List<ParsingGroupInfo> all = Lists.newArrayList();
    for (Map.Entry<String, List<ParsingExample>> entry : parsingExamples.entrySet())
      all.add(new ParsingGroupInfo(entry.getKey(), entry.getValue()));
    return all;
  }

  /** For JSON. */
  @JsonCreator
  public static ParaphraseDataset fromGroupInfos(@JsonProperty("paraphrasing_groups") List<ParaphrasingGroupInfo> paraphrasingGroups,
      @JsonProperty("parsing_groups") List<ParsingGroupInfo> parsingGroups) {
    ParaphraseDataset d = new ParaphraseDataset();
    d.readFromParaphrasingGroupInfos(paraphrasingGroups);
    d.readFromParsingGroupInfos(parsingGroups);
    return d;
  }

  public void read() {
    readFromParaphrasingPathPairs(opts.paraphraseInPaths);
    readFromParsingPathPairs(opts.parsingInPaths);
    collectStats();
  }

  public void readFromParaphrasingPathPairs(List<Pair<String, String>> pathPairs) {

    List<ParaphrasingGroupInfo> groups = Lists.newArrayListWithCapacity(pathPairs.size());
    for (Pair<String, String> pathPair : pathPairs) {
      String group = pathPair.getFirst();
      String path = pathPair.getSecond();
      List<ParaphraseExample> examples = Json.readValueHard(
          IOUtils.openInHard(path),
          new TypeReference<List<ParaphraseExample>>() {});
      ParaphrasingGroupInfo gi = new ParaphrasingGroupInfo(group, examples);
      gi.path = path;
      groups.add(gi);
    }
    readFromParaphrasingGroupInfos(groups);
  }

  public void readFromParsingPathPairs(List<Pair<String, String>> pathPairs) {

    List<ParsingGroupInfo> groups = Lists.newArrayListWithCapacity(pathPairs.size());
    for (Pair<String, String> pathPair : pathPairs) {
      String group = pathPair.getFirst();
      String path = pathPair.getSecond();
      List<ParsingExample> examples = Json.readValueHard(
          IOUtils.openInHard(path),
          new TypeReference<List<ParsingExample>>() {});
      ParsingGroupInfo gi = new ParsingGroupInfo(group, examples);
      gi.path = path;
      groups.add(gi);
    }
    readFromParsingGroupInfos(groups);
  }

  private void readFromParaphrasingGroupInfos(List<ParaphrasingGroupInfo> paraphrasingGroupInfos) {
    LogInfo.begin_track_printAll("Dataset.read paraphrasing");

    for (ParaphrasingGroupInfo paraphrasingGroupInfo : paraphrasingGroupInfos) {
      int maxExamples = getMaxExamplesForParaphrasingGroup(paraphrasingGroupInfo.group);
      List<ParaphraseExample> examples = paraphraseExamples.get(paraphrasingGroupInfo.group);
      if (examples == null)
        paraphraseExamples.put(paraphrasingGroupInfo.group, examples = new ArrayList<>());
      readParaphrasingHelper(paraphrasingGroupInfo.examples, maxExamples, examples, paraphrasingGroupInfo.path);
    }
    Pair<List<ParaphraseExample>,List<ParaphraseExample>> splitExamples = 
        DatasetUtils.splitTrainFromDev(MapUtils.get(paraphraseExamples, "train", new ArrayList<ParaphraseExample>()),
            opts.trainFrac,opts.devFrac, 
            opts.splitRandom);
    paraphraseExamples.put("train", splitExamples.getFirst());
    paraphraseExamples.put("dev", splitExamples.getSecond());
    LogInfo.end_track();
  }

  private void readFromParsingGroupInfos(List<ParsingGroupInfo> parsingGroupInfo) {
    LogInfo.begin_track_printAll("Dataset.read parsing");

    for (ParsingGroupInfo groupInfo : parsingGroupInfo) {
      int maxExamples = getMaxExamplesForParsingGroup(groupInfo.group);
      List<ParsingExample> examples = parsingExamples.get(groupInfo.group);
      if (examples == null)
        parsingExamples.put(groupInfo.group, examples = new ArrayList<>());
      readParsingHelper(groupInfo.examples, maxExamples, examples, groupInfo.path);
    }

    Pair<List<ParsingExample>,List<ParsingExample>> splitExamples = 
        DatasetUtils.splitTrainFromDev(MapUtils.get(parsingExamples, "train", new ArrayList<ParsingExample>()), 
            opts.trainFrac,
            opts.devFrac,
            opts.splitRandom);
    parsingExamples.put("train", splitExamples.getFirst());
    parsingExamples.put("dev", splitExamples.getSecond());    LogInfo.end_track();
  }

  private void readParaphrasingHelper(List<ParaphraseExample> incoming,
      int maxExamples,
      List<ParaphraseExample> examples,
      String path) {
    if (examples.size() >= maxExamples)
      return;

    int i = 0;
    for (ParaphraseExample ex : incoming) {
      ex.id=""+ (i++);
      if (examples.size() >= maxExamples) break;
      LogInfo.logs("Example %s: source=%s target=%s => %s",
          ex.id, ex.source, ex.target, ex.goldValue);
      examples.add(ex);
    }
  }  

  private void readParsingHelper(List<ParsingExample> incoming,
      int maxExamples,
      List<ParsingExample> examples,
      String path) {
    if (examples.size() >= maxExamples)
      return;

    int i = 0;
    for (ParsingExample ex : incoming) {
      if (examples.size() >= maxExamples) break;

      String id = ""+i;
      ex = new ParsingExample.Builder().withExample(ex).setId(id).createExample();

      i++;
      ex.preprocess();
      LogInfo.logs("Example %s (%d): %s => %s",
          ex.id, examples.size(), ex.getTokens(), ex.targetValue);
      examples.add(ex);
    }
  }  

  private static int getMaxExamplesForParaphrasingGroup(String group) {
    int maxExamples = Integer.MAX_VALUE;
    for (Pair<String, Integer> maxPair : opts.maxParaphraseExamples)
      if (maxPair.getFirst().equals(group))
        maxExamples = maxPair.getSecond();
    return maxExamples;
  }

  private static int getMaxExamplesForParsingGroup(String group) {
    int maxExamples = Integer.MAX_VALUE;
    for (Pair<String, Integer> maxPair : opts.maxParsingExamples)
      if (maxPair.getFirst().equals(group))
        maxExamples = maxPair.getSecond();
    return maxExamples;
  }
  
  private void collectStats() {
    LogInfo.begin_track_printAll("Dataset stats");
    for (Map.Entry<String, List<ParsingExample>> e : parsingExamples.entrySet())
      Execution.putLogRec("numExamples." + e.getKey(), e.getValue().size());
    LogInfo.end_track();
  }
}
