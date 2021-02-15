package edu.stanford.nlp.sempre.tables.alter;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.TableKnowledgeGraph;
import edu.stanford.nlp.sempre.tables.serialize.SerializedDataset;
import edu.stanford.nlp.sempre.tables.test.CustomExample;
import fig.basic.IOUtils;
import fig.basic.LogInfo;
import fig.basic.StrUtils;
import fig.exec.Execution;

/**
 * Execute the formulas on the altered tables.
 *
 * Summarized turk data can be provided in
 *     BatchTableAlterer.opts.turkedDataPath,
 * in which case only turked tables will be executed on.
 * A "check" file, indicating whether the denotation matches
 * the turked answers, will also be dumped.
 *
 * Annotated formulas can be provided in
 *     BatchTableAlterer.opts.annotatedFormulasPath,
 * in which case the annotated formula will be executed
 * (to a separate file).
 *
 * @author ppasupat
 */
public class AlteredTablesExecutor implements Runnable {

  public static void main(String[] args) {
    Execution.run(args, "AlteredTablesExecutorMain", new AlteredTablesExecutor(), Master.getOptionsParser());
  }

  private Builder builder;
  private TableAltererCache tableAltererCache = null;

  private boolean hasAnnotated = false;
  private Map<String, CustomExample> idToAnnotated = new HashMap<>();

  private boolean hasTurk = false;
  private AggregatedTurkData turkedData = null;

  @Override
  public void run() {
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

    // Alterer cache
    tableAltererCache = new TableAltererCache();

    // Read annotation file
    if (!StrUtils.isEmpty(BatchTableAlterer.opts.annotatedFormulasPath)) {
      hasAnnotated = true;
      // Prevent verbose output
      if (CustomExample.opts.verbose > 1)
        CustomExample.opts.verbose = 1;
      List<CustomExample> annotated = CustomExample.getDataset(BatchTableAlterer.opts.annotatedFormulasPath);
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

    // Read Turked data
    if (!StrUtils.isEmpty(BatchTableAlterer.opts.turkedDataPath)) {
      hasTurk = true;
      turkedData = new AggregatedTurkData(BatchTableAlterer.opts.turkedDataPath);
    }

    Execution.putOutput("group", "train");
    int index = -1;
    for (Example ex : examples) {
      Execution.putOutput("example", ++index);
      if (CustomExample.checkFilterExamples(index))
        process(ex);
      ex.predDerivations.clear();     // Save memory
    }
  }


  // For each example, execute on the turked tables, then dump to file
  private void process(Example ex) {
    LogInfo.begin_track("Processing %s", ex.id);
    ex.log();
    if (ex.predDerivations == null)
      ex.predDerivations = Collections.emptyList();
    LogInfo.logs("Read %d derivations", ex.predDerivations.size());
    DenotationData denotationData = new DenotationData(BatchTableAlterer.opts.numAlteredTables, ex.predDerivations.size());
    DenotationData checkData = new DenotationData(BatchTableAlterer.opts.numAlteredTables, ex.predDerivations.size());

    // Get the relevant indices
    List<Integer> tableIndices;
    if (hasTurk) {
      tableIndices = turkedData.getAllTurkedTables(ex.id);
      if (tableIndices == null)
        tableIndices = new ArrayList<>();
    } else {
      tableIndices = new ArrayList<>(BatchTableAlterer.opts.numAlteredTables + 1);
      for (int i = 0; i <= BatchTableAlterer.opts.numAlteredTables; i++)
        tableIndices.add(i);
    }

    for (int tableIndex : tableIndices) {
      LogInfo.begin_track("Executing on table %d", tableIndex);
      TableKnowledgeGraph graph = tableAltererCache.load(ex.id, tableIndex);
      Value target = hasTurk ? turkedData.get(ex.id, tableIndex) : null;
      ContextValue context = new ContextValue(graph);
      // Execute all formulas on the new graph
      for (int k = 0; k < ex.predDerivations.size(); k++) {
        Derivation deriv = ex.predDerivations.get(k);
        Value value = builder.executor.execute(deriv.formula, context).value;
        if (value instanceof ListValue)
          value = graph.getListValueWithOriginalStrings((ListValue) value);
        boolean correct = isCorrect(ex, target, value);
        value = ValueCanonicalizer.canonicalize(value);
        denotationData.addDenotation(k, tableIndex, value);
        if (hasTurk)
          checkData.addDenotation(k, tableIndex, getCheck(target, value, correct));
      }
      // Annotated formula
      if (hasAnnotated) {
        Value annotatedValue = null;
        Example annotatedEx = idToAnnotated.get(ex.id);
        boolean annotatedCorrect = false;
        if (annotatedEx != null && annotatedEx.targetFormula != null) {
          annotatedValue = builder.executor.execute(annotatedEx.targetFormula, context).value;
          if (annotatedValue instanceof ListValue)
            annotatedValue = graph.getListValueWithOriginalStrings((ListValue) annotatedValue);
          annotatedCorrect = isCorrect(ex, target, annotatedValue);
          annotatedValue = ValueCanonicalizer.canonicalize(annotatedValue);
        }
        denotationData.addAnnotatedDenotation(tableIndex, annotatedValue);
        if (hasTurk)
          checkData.addAnnotatedDenotation(tableIndex, getCheck(target, annotatedValue, annotatedCorrect));
      }
      LogInfo.end_track();
    }

    {
      File dir = new File(Execution.getFile("actual-denotations"));
      if (!dir.isDirectory()) dir.mkdir();
      PrintWriter writer = IOUtils.openOutHard(dir.toString() + "/" + ex.id + ".gz");
      denotationData.dump(writer);
      writer.close();
    }
    if (hasAnnotated) {
      File dir = new File(Execution.getFile("actual-annotated-denotations"));
      if (!dir.isDirectory()) dir.mkdir();
      PrintWriter writer = IOUtils.openOutHard(dir.toString() + "/" + ex.id + ".gz");
      denotationData.dumpAnnotated(writer);
      writer.close();
    }
    if (hasTurk) {
      File dir = new File(Execution.getFile("check-denotations"));
      if (!dir.isDirectory()) dir.mkdir();
      PrintWriter writer = IOUtils.openOutHard(dir.toString() + "/" + ex.id + ".gz");
      checkData.dump(writer);
      writer.close();
    }
    if (hasTurk && hasAnnotated) {
      File dir = new File(Execution.getFile("check-annotated-denotations"));
      if (!dir.isDirectory()) dir.mkdir();
      PrintWriter writer = IOUtils.openOutHard(dir.toString() + "/" + ex.id + ".gz");
      checkData.dumpAnnotated(writer);
      writer.close();
    }

    LogInfo.end_track();
  }

  // See if a value matches the targetValue
  boolean isCorrect(Example ex, Value target, Value pred) {
    if (target == null || target instanceof ErrorValue) return false;
    double result = 0;
    try {
      result = builder.valueEvaluator.getCompatibility(target, pred);
    } catch (Exception e) {
      LogInfo.logs("%s", e);
      e.printStackTrace();
    }
    return result == 1;
  }

  private static final Value TURK_A_MATCHED = new NameValue("Ao");
  private static final Value TURK_A_MISMATCHED = new NameValue("Ax");
  private static final Value TURK_B_MATCHED = new NameValue("Bo");
  private static final Value TURK_B_MISMATCHED = new NameValue("Bx");
  private static final Value TURK_X = new NameValue("X");

  private Value getCheck(Value target, Value pred, boolean correct) {
    if (target == null) return TURK_X;
    if (target instanceof ErrorValue) {
      return pred instanceof ErrorValue ? TURK_B_MATCHED : TURK_B_MISMATCHED;
    } else {
      return correct ? TURK_A_MATCHED : TURK_A_MISMATCHED;
    }
  }

}
