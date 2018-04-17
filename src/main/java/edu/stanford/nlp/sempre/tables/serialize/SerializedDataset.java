package edu.stanford.nlp.sempre.tables.serialize;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.TableKnowledgeGraph;
import fig.basic.*;

/**
 * A Dataset object created from serialized examples created by SerializedDumper.
 *
 * @author ppasupat
 */
public class SerializedDataset extends Dataset {
  public static class Options {
    @Option(gloss = "Directory with dumped-*.gz files; to load only specific files, use Dataset.inPaths")
    public List<String> dumpDirs = new ArrayList<>();
  }
  public static Options opts = new Options();

  private final Map<String, List<String>> availableGroups = new LinkedHashMap<>();

  @Override
  public void read() {
    LogInfo.begin_track_printAll("Dataset.read");
    if (Dataset.opts.trainFrac != 1)
      LogInfo.warnings("Dataset.opts.trainFrac is ignored!");
    if (opts.dumpDirs != null && !opts.dumpDirs.isEmpty()) {
      readDirs(opts.dumpDirs);
    } else {
      read(Dataset.opts.inPaths);
    }
    LogInfo.end_track();
  }

  public void read(String group, String inPath) {
    MapUtils.addToList(availableGroups, group, inPath);
    checkFiles();
  }

  public void read(List<Pair<String, String>> inPaths) {
    for (Pair<String, String> pair : inPaths)
      MapUtils.addToList(availableGroups, pair.getFirst(), pair.getSecond());
    checkFiles();
  }

  public void readDir(String dumpDir) {
    readDirs(Collections.singleton(dumpDir));
  }

  public static final Pattern GZ_PATTERN = Pattern.compile("^dumped-([^-]+)(?:-(\\d+)(?:-(.*))?)?\\.gz$");

  public void readDirs(Collection<String> dumpDirs) {
    // Get filenames
    // File format is dumped-groupname[-offset][-examplename].gz
    Set<String> filenames = new HashSet<>(), groups = new HashSet<>();
    for (String dumpDir : dumpDirs) {
      String[] filenamesInDumpDir = new File(dumpDir).list();
      for (String filename : filenamesInDumpDir) {
        Matcher matcher = GZ_PATTERN.matcher(filename);
        if (matcher.matches()) {
          filenames.add(new File(dumpDir, filename).toString());
          groups.add(matcher.group(1));
        }
      }
    }
    LogInfo.logs("Available groups: %s", groups);
    for (String group : groups) {
      List<String> filenamesForGroup = new ArrayList<>();
      for (String filename : filenames) {
        File file = new File(filename);
        Matcher matcher = GZ_PATTERN.matcher(file.getName());
        if (matcher.matches() && group.equals(matcher.group(1)))
          filenamesForGroup.add(file.toString());
      }
      if (!filenamesForGroup.isEmpty()) {
        Collections.sort(filenamesForGroup);
        availableGroups.put(group, filenamesForGroup);
      }
    }
    checkFiles();
  }

  private void checkFiles() {
    // Check if all files exist
    for (List<String> filenamesForGroup : availableGroups.values()) {
      for (String filename : filenamesForGroup) {
        File file = new File(filename);
        if (!file.isFile())
          throw new RuntimeException("Error reading dataset: " + file + " is not a file.");
      }
    }
  }

  @Override
  public Set<String> groups() { return availableGroups.keySet(); }

  @Override
  public LazyLoadedExampleList examples(String group) {
    if (!availableGroups.containsKey(group)) return null;
    return new LazyLoadedExampleList(availableGroups.get(group),
        getMaxExamplesForGroup(group), group.startsWith("single_"));
  }

  // ============================================================
  // Test
  // ============================================================

  public static void main(String[] args) {
    TableKnowledgeGraph.opts.baseCSVDir = "lib/data/WikiTableQuestions/";
    SerializedDataset dataset = new SerializedDataset();
    dataset.readDir("out/sliced-dump-8-reps/representative-00-training-sliced-00000-00299/");
    LazyLoadedExampleList examples = dataset.examples("representative");
    for (int i : new int[]{
        25, 20, 20, 20, 21, 31, 29, 45, 35, 36, 37, 31, 99, 99, 100, 100, 99, 1, 3, 10, 23, 499
    }) {
      LogInfo.logs("=== %d ===", i);
      Example ex = examples.get(i);
      LogInfo.logs("%s %d", ex.id, ex.predDerivations.size());
      if (!ex.id.equals("nt-" + i))
        throw new RuntimeException(String.format("Wrong ID: %s != nt-%d", ex.id, i));
    }
  }

}