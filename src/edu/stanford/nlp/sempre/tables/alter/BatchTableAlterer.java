package edu.stanford.nlp.sempre.tables.alter;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.*;
import edu.stanford.nlp.sempre.tables.serialize.SerializedDataset;
import edu.stanford.nlp.sempre.tables.serialize.SerializedDumper;
import edu.stanford.nlp.sempre.tables.test.CustomExample;
import fig.basic.*;
import fig.exec.Execution;

/**
 * For each example,
 * - Generate altered tables
 * - Execute the formulas on altered tables.
 * - Select the most informative subset of tables to be sent to MTurk.
 * - Dump formulas consistent with the Turked results.
 *
 * @author ppasupat
 */
public class BatchTableAlterer implements Runnable {
  public static class Options {
    @Option(gloss = "verbosity")
    public int verbose = 2;
    @Option(gloss = "number of tables to generate")
    public int numAlteredTables = 30;
    @Option(gloss = "do not execute formulas for speed (will also skip steps that require denotations)")
    public boolean skipFormulaExecution = false;
    // Serialize denotations
    @Option(gloss = "dump all denotations")
    public boolean dumpAllDenotations = false;
    @Option(gloss = "dump annotated denotations")
    public boolean dumpAnnotatedDenotations = false;
    @Option(gloss = "load denotations from this directory")
    public String loadDenotationsFromDir = null;
    // Equivalent classes
    @Option(gloss = "dump representative formula of each equivalent class")
    public boolean dumpRepresentativeFormulas = false;
    // Persistence
    @Option(gloss = "if the altered tables are already saved to files, skip the example")
    public boolean skipExistingSaveDirs = false;
    @Option(gloss = "whether to overwrite the saved altered tables")
    public boolean overwriteExistingSaveDirs = false;
    // Choosing subset of altered tables
    @Option(gloss = "which subset chooser to use")
    public SubsetChooserSpec subsetChooser = SubsetChooserSpec.ENTROPY;
    @Option(gloss = "number of tables to retain, not counting the original table (0 = retain all)")
    public int numRetainedTables = 0;
    @Option(gloss = "also try subsets of size 1, 2, ..., (numRetainedTables - 1)")
    public boolean alsoTrySmallerSubsets = false;
    // Checking with annotated formulas
    @Option(gloss = "check with annotated formulas")
    public String annotatedFormulasPath = null;
    @Option(gloss = "Ignore agreed errors in Turk data")
    public boolean ignoreTurkedAgreedErrors = true;
    @Option(gloss = "dump all formulas in the same equivalent class as the annotation")
    public boolean dumpAllMatchingAnnotated = false;
    // Checking with Turked data
    @Option(gloss = "Turked data path")
    public String turkedDataPath = null;
  }
  public static Options opts = new Options();

  public static enum SubsetChooserSpec { NONE, ENTROPY, CACHED, PURE }

  public static void main(String[] args) {
    Execution.run(args, "BatchTableAltererMain", new BatchTableAlterer(), Master.getOptionsParser());
  }

  private Builder builder;
  private Map<String, CustomExample> idToAnnotated = new HashMap<>();
  private TableAltererCache tableAltererCache = null;
  private SerializedDumper representativeDumper = null;
  private PrintWriter retainedTablesOut = null;
  private SubsetChooser subsetChooser = null;
  private AggregatedTurkData turkedData = null;
  private PrintWriter turkInfoWriter = null;
  private SerializedDumper turkMatchDumper = null;

  @Override
  public void run() {
    if (opts.skipFormulaExecution && opts.numRetainedTables > 0)
      LogInfo.fails("Cannot simultaneously skip formula execution and choose tables to retain.");

    builder = new Builder();
    builder.build();

    // Read dataset
    Dataset dataset;
    if (Dataset.opts.inPaths.isEmpty() || Dataset.opts.inPaths.get(0).getSecond().endsWith(".gz")) {
      LogInfo.logs("Loading SERIALIZED dataset");
      dataset = new SerializedDataset();
    } else {
      LogInfo.logs("Loading USUAL dataset");
      dataset = new Dataset();
    }
    dataset.read();
    List<Example> examples = dataset.examples("train");
    if (examples == null) {
      // Representative?
      LogInfo.logs("Reading representatives");
      examples = dataset.examples("representative");
    }

    // Read annotation file (optional)
    if (opts.annotatedFormulasPath != null && !opts.annotatedFormulasPath.isEmpty()) {
      // Prevent verbose output
      if (CustomExample.opts.verbose > 1)
        CustomExample.opts.verbose = 1;
      List<CustomExample> annotated = CustomExample.getDataset(opts.annotatedFormulasPath);
      for (CustomExample ex : annotated) {
        if (ex == null || ex.targetFormula == null) continue;
        // Check annotation
        Value rawValue = builder.executor.execute(ex.targetFormula, ex.context).value;
        if (rawValue instanceof ListValue)
          rawValue = ((TableKnowledgeGraph) ex.context.graph).getListValueWithOriginalStrings((ListValue) rawValue);
        if (builder.valueEvaluator.getCompatibility(ex.targetValue, rawValue) == 1)
          idToAnnotated.put(ex.id, ex);
        else
          LogInfo.warnings("Wrong annotation [%s] expected %s; got %s", ex.id, ex.targetValue, rawValue);
      }
    }

    // Alterer cache
    tableAltererCache = new TableAltererCache();

    // Dump equivalent classes (optional)
    if (opts.dumpRepresentativeFormulas) {
      representativeDumper = new SerializedDumper("representative", examples.size());
    }

    // Read Turked data (optional)
    if (opts.turkedDataPath != null && !opts.turkedDataPath.isEmpty()) {
      turkedData = new AggregatedTurkData(opts.turkedDataPath);
      turkMatchDumper = new SerializedDumper("turk", examples.size());
      turkInfoWriter = IOUtils.openOutHard(Execution.getFile("turk-info.tsv"));
      TurkEquivalentClassInfo.dumpHeader(turkInfoWriter);
    }

    // Subset choosing
    switch (opts.subsetChooser) {
      case CACHED:
        subsetChooser = new CachedSubsetChooser();
        break;
      case ENTROPY:
        if (opts.numRetainedTables > 0)
          subsetChooser = new EntropySubsetChooser(opts.numAlteredTables,
              opts.numRetainedTables, opts.alsoTrySmallerSubsets);
        break;
      case PURE:
        if (opts.numRetainedTables > 0)
          subsetChooser = new PureSubsetChooser(opts.numAlteredTables,
              opts.numRetainedTables, opts.alsoTrySmallerSubsets);
        break;
      default:    // Do nothing
    }
    if (opts.subsetChooser != null)
      retainedTablesOut = IOUtils.openOutAppendEasy(Execution.getFile("retained-tables.tsv"));

    // Go through the dataset
    Execution.putOutput("group", "train");
    int index = -1;
    for (Example ex : examples) {
      Execution.putOutput("example", ++index);
      if (!CustomExample.checkFilterExamples(index) ||
          (opts.skipExistingSaveDirs && tableAltererCache.existsSaveDir(ex.id))) {
        LogInfo.logs("SKIPPING %s", ex.id);
        continue;
      }
      List<TableKnowledgeGraph> graphs = process(ex);
      if (!tableAltererCache.existsSaveDir(ex.id) || opts.overwriteExistingSaveDirs)
        tableAltererCache.dump(graphs, ex.id);
      ex.predDerivations.clear();     // Save memory
    }

    if (representativeDumper != null) representativeDumper.closeFile();
    if (retainedTablesOut != null) retainedTablesOut.close();
    if (turkInfoWriter != null) turkInfoWriter.close();
    if (turkMatchDumper != null) turkMatchDumper.closeFile();
  }

  private List<TableKnowledgeGraph> process(Example ex) {
    LogInfo.begin_track("Processing %s", ex.id);
    ex.log();
    if (ex.predDerivations == null)
      ex.predDerivations = Collections.emptyList();
    LogInfo.logs("Read %d derivations", ex.predDerivations.size());

    TableAlterer alterer = new TableAlterer(ex);
    // altered tables (alteredGraphs[0] is always the original table)
    List<TableKnowledgeGraph> alteredGraphs = new ArrayList<>();
    boolean loadedDenotationData = false;
    DenotationData denotationData = null;
    if (opts.loadDenotationsFromDir != null && !opts.loadDenotationsFromDir.isEmpty()) {
      File file = new File(opts.loadDenotationsFromDir, ex.id + ".gz");
      BufferedReader reader = IOUtils.openInEasy(file.toString());
      if (reader != null) {
        try {
          if (opts.verbose >= 1)
            LogInfo.logs("Reading from " + file);
          denotationData = DenotationData.load(reader);
          loadedDenotationData = true;
        } catch (Exception e) {
          LogInfo.warnings("File " + file + " contains error: " + e);
          e.printStackTrace();
          throw new RuntimeException(e);
        }
      }
    }
    if (denotationData == null)
      denotationData = new DenotationData(opts.numAlteredTables, ex.predDerivations.size());

    LogInfo.begin_track("Generating %d tables", opts.numAlteredTables);
    for (int tableIndex = 0; tableIndex <= opts.numAlteredTables; tableIndex++) {
      // Use the original table for table #0; an altered table otherwise
      TableKnowledgeGraph graph;
      if (tableIndex == 0) {
        graph = alterer.oldGraph;
      } else {
        graph = tableAltererCache.load(ex.id, tableIndex);
        if (graph == null)      // Nothing in the cache ...
          graph = alterer.constructAlteredGraph(tableIndex);
        if (graph == null)      // Something is wrong ...
          throw new RuntimeException("Cannot generate graph " + ex.id + " " + tableIndex);
      }
      alteredGraphs.add(graph);
      if (!opts.skipFormulaExecution) {
        ContextValue context = new ContextValue(graph);
        // Execute all formulas on the new graph
        List<Value> denotationsForTable = new ArrayList<>();
        for (int k = 0; k < ex.predDerivations.size(); k++) {
          Value value;
          if (loadedDenotationData) {
            value = denotationData.getDenotation(k, tableIndex);
          } else {
            Derivation deriv = ex.predDerivations.get(k);
            value = builder.executor.execute(deriv.formula, context).value;
            value = ValueCanonicalizer.canonicalize(value);
            denotationData.addDenotation(k, tableIndex, value);
          }
          denotationsForTable.add(value);
        }
        // Annotated formula
        Value annotatedValue = null;
        Example annotatedEx = idToAnnotated.get(ex.id);
        if (annotatedEx != null && annotatedEx.targetFormula != null) {
          annotatedValue = builder.executor.execute(annotatedEx.targetFormula, context).value;
          annotatedValue = ValueCanonicalizer.canonicalize(annotatedValue);
        }
        denotationData.addAnnotatedDenotation(tableIndex, annotatedValue);
        // Log
        if (opts.verbose >= 3) {
          LogInfo.begin_track("Table %d", tableIndex);
          graph.log();
          logGroups(DenotationData.groupByDenotation(denotationsForTable), annotatedValue, "ANNOTATED");
          LogInfo.end_track();
        }
      }
    }
    LogInfo.end_track();

    if (!opts.skipFormulaExecution) {

      if (opts.dumpAllDenotations) {
        File dir = new File(Execution.getFile("denotations"));
        if (!dir.isDirectory()) dir.mkdir();
        PrintWriter writer = IOUtils.openOutHard(Execution.getFile("denotations/" + ex.id + ".gz"));
        denotationData.dump(writer);
        writer.close();
      }

      if (opts.dumpAnnotatedDenotations && denotationData.isAnnotated()) {
        File dir = new File(Execution.getFile("annotated-denotations"));
        if (!dir.isDirectory()) dir.mkdir();
        PrintWriter writer = IOUtils.openOutHard(Execution.getFile("annotated-denotations/" + ex.id + ".gz"));
        denotationData.dumpAnnotated(writer);
        writer.close();
      }

      denotationData.computeGroups(ex.predDerivations);

      if (opts.dumpRepresentativeFormulas) {
        List<Derivation> representatives = new ArrayList<>();
        for (int index : denotationData.getRepresentativeIndices())
          representatives.add(ex.predDerivations.get(index));
        LogInfo.logs("Dumping %d representatives", representatives.size());
        representativeDumper.dumpExample(ex, representatives);
      }

      // Log the summary
      if (opts.verbose >= 3) {
        LogInfo.begin_track("Summary across %d tables", alteredGraphs.size());
        //logGroups(denotationData.groups, denotationData.getAnnotatedDenotations(), "OVERALL-ANNOTATED");
        if (opts.dumpAllMatchingAnnotated) {
          LogInfo.begin_track("All formulas matching annotated formula on all tables");
          for (int k = 0; k < ex.predDerivations.size(); k++) {
            if (denotationData.getDenotations(k).equals(denotationData.getAnnotatedDenotations()))
              LogInfo.logs("%s", ex.predDerivations.get(k));
          }
          LogInfo.end_track();
        }
        LogInfo.end_track();
      }

      // Choosing the most informative subset of tables
      // This will try all combinations. The complexity is (numAltered)^(numRetained)
      if (subsetChooser != null && opts.turkedDataPath == null) {
        Subset chosen = subsetChooser.chooseSubset(ex.id, denotationData);
        if (chosen != null) {
          LogInfo.logs("RETAINED TABLES: %s", chosen.indices);
          testSubset(denotationData, chosen);
        } else {
          LogInfo.logs("RETAINED TABLES: null");
          chosen = new Subset(ex.id);
        }
        retainedTablesOut.println(chosen);
        retainedTablesOut.flush();
      }

      // Check with Turked data
      if (turkedData != null) {
        Map<Integer, Value> turked = turkedData.get(ex.id);
        if (turked != null && !turked.isEmpty()) {
          LogInfo.logs("TURKED DATA: %s", turked.keySet());
          testWithTurkedData(ex, denotationData, turked, turkedData.getAllTurkedTables(ex.id));
        } else {
          LogInfo.logs("TURKED DATA: null");
          testWithTurkedData(ex, denotationData, new HashMap<>(), new ArrayList<>());
        }
      }
    }

    LogInfo.end_track();
    return alteredGraphs;
  }

  private <T> void logGroups(Map<T, List<Integer>> groups, T annotated, String prefix) {
    // Sort by count
    List<Map.Entry<T, List<Integer>>> entries = new ArrayList<>(groups.entrySet());
    entries.sort((o1, o2) -> o2.getValue().size() - o1.getValue().size());
    // Log the counts
    LogInfo.begin_track("Denotation counts");
    int annotatedCount = 0, totalCount = 0;
    for (Map.Entry<T, List<Integer>> entry : entries) {
      int size = entry.getValue().size();
      boolean matchAnnotated = entry.getKey().equals(annotated);
      LogInfo.logs("%3s %5d : %s", matchAnnotated ? "[O]" : "", size, entry.getKey());
      totalCount += size;
      if (matchAnnotated) annotatedCount = size;
    }
    if (annotated != null) {
      LogInfo.logs("%s = %s", prefix, annotated);
      LogInfo.logs("%s COUNT: %d / %d (%.3f%%)", prefix,
          annotatedCount, totalCount, annotatedCount * 100.0 / totalCount);
    } else {
      LogInfo.logs("Example is NOT ANNOTATED");
    }
    LogInfo.end_track();
  }

  // ============================================================
  // Test subset
  // ============================================================

  public void testSubset(DenotationData denotationData, Subset subset) {
    LogInfo.begin_track("testSubset : %s %s", subset.id, subset.indices);
    // denotations[i][j] for i in representativeDerivs and j in graphIndices
    Map<List<Value>, Integer> counts = new HashMap<>();
    for (int i : denotationData.getRepresentativeIndices()) {
      List<Value> denotationsForDeriv = new ArrayList<>();
      for (int j : subset.indices)
        denotationsForDeriv.add(denotationData.getDenotation(i, j));
      MapUtils.incr(counts, denotationsForDeriv);
    }
    LogInfo.logs("subset test: %s | score = %8.3f from tables %s", subset.id, subset.score, subset.indices);
    {
      List<Integer> values = new ArrayList<>(counts.values());
      Collections.sort(values);
      Collections.reverse(values);
      LogInfo.logs("   %s", values);
    }
    // Check annotated formulas
    if (denotationData.isAnnotated()) {
      List<Value> denotationsForDeriv = new ArrayList<>();
      for (int j : subset.indices)
        denotationsForDeriv.add(denotationData.getAnnotatedDenotation(j));
      Integer count = counts.get(denotationsForDeriv);
      if (count == null || count == 0) {
        LogInfo.logs("subset annotation: %s | 0 ANNOTATED DENOTATIONS NOT FOUND!", subset.id);
      } else if (count == 1) {
        LogInfo.logs("subset annotation: %s | 1 ANNOTATED DENOTATIONS IS IN ITS OWN CLASS.", subset.id);
      } else {
        LogInfo.logs("subset annotation: %s | %d ANNOTATED DENOTATIONS MIX WITH OTHER THINGS!", subset.id, count);
      }
    } else {
      LogInfo.logs("subset annotation: %s | X NOT ANNOTATED!", subset.id);
    }
    LogInfo.end_track();
  }

  // ============================================================
  // Test with Turked data
  // ============================================================
  /*
   * Output to 2 dump files
   * - turk-info: TSV file
   * - turk-match.gz: LispTrees file where each tree is a serialized example with matching formulas
   */

  public void testWithTurkedData(Example ex, DenotationData denotationData,
      Map<Integer, Value> turked, List<Integer> allTurkedTables) {
    LogInfo.begin_track("testWithTurkedData: %s", ex.id);
    TurkEquivalentClassInfo info = new TurkEquivalentClassInfo();
    info.id = ex.id;
    info.numDerivs = ex.predDerivations.size();
    info.numClasses = denotationData.numClasses();
    info.allTurkedTables = allTurkedTables;
    LogInfo.logs("ALL TURKED TABLES: %s", allTurkedTables);
    for (Map.Entry<Integer, Value> entry : turked.entrySet())
      LogInfo.logs("%d : %s", entry.getKey(), entry.getValue());
    // sort by altered table index
    info.agreedTurkedTables = new ArrayList<>(turked.keySet());
    Collections.sort(info.agreedTurkedTables);
    List<Value> turkedValues = new ArrayList<>();
    for (int j : info.agreedTurkedTables)
      turkedValues.add(turked.get(j));
    // denotations[i][j] for i in representativeDerivs and j in Turked keys
    // Group equivalent classes based on turked data
    Map<List<Value>, List<Integer>> equivClassGroups = new HashMap<>();
    for (int i : denotationData.getRepresentativeIndices()) {
      List<Value> denotationsForDeriv = new ArrayList<>();
      for (int j : info.agreedTurkedTables)
        denotationsForDeriv.add(denotationData.getDenotation(i, j));
      MapUtils.addToList(equivClassGroups, denotationsForDeriv, i);
    }
    {
      List<Integer> values = new ArrayList<>();
      for (List<Integer> equivClassGroupIndices : equivClassGroups.values())
        values.add(equivClassGroupIndices.size());
      Collections.sort(values);
      Collections.reverse(values);
      LogInfo.logs("   %s", values);
    }
    // Find out if even the answer for table 0 (original table) matches!
    info.origTableTarget = ex.targetValue;
    if (turked.containsKey(0)) {
      info.origTableTurkedTarget = turked.get(0);
      info.origTableFlag = isCompatible(info.origTableTarget, info.origTableTurkedTarget) ? "ok" : "mismatched";
    } else {
      info.origTableTurkedTarget = null;
      info.origTableFlag = "no turk";
    }
    LogInfo.logs("original table: dataset = %s", info.origTableTarget);
    LogInfo.logs("original table: turked = %s", info.origTableTurkedTarget);
    LogInfo.logs("original table: flag = %s", info.origTableFlag);
    // Find out how many equivalent classes match the annotation
    List<Integer> matchedDerivIndices = new ArrayList<>();
    List<Derivation> matchedDerivs = new ArrayList<>();
    for (Map.Entry<List<Value>, List<Integer>> entry : equivClassGroups.entrySet()) {
      boolean match = true;
      List<Value> equivClassDenotations = entry.getKey();
      List<Integer> equivClassGroupIndices = entry.getValue();
      for (int jj = 0; jj < turkedValues.size(); jj++) {
        Value equivClassDenotation = equivClassDenotations.get(jj);
        Value turkedDenotation = turkedValues.get(jj);
        if (opts.ignoreTurkedAgreedErrors && turkedDenotation instanceof ErrorValue)
          continue;
        if (!isCompatible(turkedDenotation, equivClassDenotation)) {
          match = false;
          break;
        }
      }
      if (match) {
        LogInfo.logs("Matched %d classes: %s", equivClassGroupIndices.size(), equivClassDenotations);
        info.numClassesMatched += equivClassGroupIndices.size();
        for (int equivClassGroupIndex : equivClassGroupIndices) {
          for (int index : denotationData.getEquivClass(equivClassGroupIndex)) {
            matchedDerivIndices.add(index);
            Derivation matchedDeriv = ex.predDerivations.get(index);
            matchedDerivs.add(matchedDeriv);
          }
        }
      }
    }
    info.numDerivsMatched = matchedDerivs.size();
    LogInfo.logs("turk matching equivalent classes: %d", info.numClassesMatched);
    LogInfo.logs("turk matching formulas: %d", info.numDerivsMatched);
    // If there are multiple equivalent classes, find out which other tables we can turk from
    if (subsetChooser != null) {
      if (info.numClassesMatched > 1) {
        DenotationData filtered = new DenotationData(opts.numAlteredTables, matchedDerivs.size());
        for (int i = 0; i < matchedDerivs.size(); i++) {
          for (int j = 0; j <= opts.numAlteredTables; j++)
            filtered.addDenotation(i, j, denotationData.getDenotation(matchedDerivIndices.get(i), j));
        }
        filtered.computeGroups(matchedDerivs);
        Subset chosen = subsetChooser.chooseSubset(ex.id, filtered, turked.keySet());
        if (chosen != null) {
          LogInfo.logs("RETAINED TABLES: %s", chosen.indices);
          retainedTablesOut.println(chosen);
          retainedTablesOut.flush();
        }
      } else {
        LogInfo.logs("Not choosing subset since turk matching equivalent classes = %d", info.numClassesMatched);
      }
    }
    // Dump stuff
    info.dump(turkInfoWriter);
    turkMatchDumper.dumpExample(ex, matchedDerivs);
    LogInfo.end_track();
  }

  private boolean isCompatible(Value target, Value pred) {
    if (target instanceof ErrorValue) {
      return pred instanceof ErrorValue || (pred instanceof ListValue && ((ListValue) pred).values.isEmpty());
    } else if (pred instanceof ErrorValue) {
      return false;
    }
    if (!(target instanceof ListValue))
      target = new ListValue(Collections.singletonList(target));
    if (!(pred instanceof ListValue))
      pred = new ListValue(Collections.singletonList(pred));
    return builder.valueEvaluator.getCompatibility(target, pred) == 1;
  }

}
