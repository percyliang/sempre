package edu.stanford.nlp.sempre.tables.test;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.FuzzyMatchFn.FuzzyMatchFnMode;
import edu.stanford.nlp.sempre.MergeFormula.Mode;
import edu.stanford.nlp.sempre.tables.StringNormalizationUtils;
import edu.stanford.nlp.sempre.tables.TableKnowledgeGraph;
import edu.stanford.nlp.sempre.tables.TableTypeSystem;
import edu.stanford.nlp.sempre.tables.test.CustomExample.ExampleProcessor;
import fig.basic.*;
import fig.exec.*;

public class TableStatsComputer implements Runnable {
  public static class Options {
    @Option(gloss = "Maximum string length to consider")
    public int statsMaxStringLength = 70;
  }
  public static Options opts = new Options();

  public static void main(String[] args) {
    Execution.run(args, "TableStatsComputerMain", new TableStatsComputer(), Master.getOptionsParser());
  }

  @Override
  public void run() {
    TableStatsComputerProcessor processor = new TableStatsComputerProcessor();
    CustomExample.getDataset(Dataset.opts.inPaths, processor);
    processor.analyzeTables();
    processor.summarize();
  }

  static class TableStatsComputerProcessor implements ExampleProcessor {
    Evaluation evaluation = new Evaluation();
    Map<TableKnowledgeGraph, Integer> tables = new HashMap<>();
    Map<String, Integer> columnStrings = new HashMap<>(), cellStrings = new HashMap<>();
    Builder builder;

    public TableStatsComputerProcessor() {
      builder = new Builder();
      builder.build();
    }

    @Override
    public void run(CustomExample ex) {
      TableKnowledgeGraph graph = (TableKnowledgeGraph) ex.context.graph;
      MapUtils.incr(tables, graph);
      // Answer type
      // For convenience, just use the first answer from the list
      Value value = ((ListValue) ex.targetValue).values.get(0);
      evaluation.add("value-number", value instanceof NumberValue);
      evaluation.add("value-date", value instanceof DateValue);
      evaluation.add("value-text", value instanceof DescriptionValue);
      evaluation.add("value-partial-number",
          value instanceof DescriptionValue && ((DescriptionValue) value).value.matches(".*[0-9].*"));
      checkInTable(value, ex.context);
    }

    private void checkInTable(Value value, ContextValue context) {
      boolean inTable = false;
      if (value instanceof DescriptionValue) {
        Collection<Formula> formulas = context.graph.getFuzzyMatchedFormulas(((DescriptionValue) value).value, FuzzyMatchFnMode.ENTITY);
        inTable = !formulas.isEmpty();
        evaluation.add("value-text-in-table", inTable);
        LogInfo.logs("value: text in table");
      } else if (value instanceof NumberValue) {
        // (and (@type @cell) (@p.num ___))
        Formula formula = new MergeFormula(Mode.and,
            new JoinFormula(Formula.fromString(CanonicalNames.TYPE), Formula.fromString(TableTypeSystem.CELL_GENERIC_TYPE)),
            new JoinFormula(Formula.fromString(TableTypeSystem.CELL_NUMBER_VALUE.id), new ValueFormula<Value>(value)));
        Value result = builder.executor.execute(formula, context).value;
        inTable = result instanceof ListValue && !((ListValue) result).values.isEmpty();
        evaluation.add("value-number-in-table", inTable);
        LogInfo.logs("value: number in table");
      } else if (value instanceof DateValue) {
        // (and (@type @cell) (@p.num ___))
        Formula formula = new MergeFormula(Mode.and,
            new JoinFormula(Formula.fromString(CanonicalNames.TYPE), Formula.fromString(TableTypeSystem.CELL_GENERIC_TYPE)),
            new JoinFormula(Formula.fromString(TableTypeSystem.CELL_DATE_VALUE.id), new ValueFormula<Value>(value)));
        Value result = builder.executor.execute(formula, context).value;
        inTable = result instanceof ListValue && !((ListValue) result).values.isEmpty();
        evaluation.add("value-number-in-table", inTable);
        LogInfo.logs("value: date in table");
      }
      evaluation.add("value-any-in-table", inTable);
    }

    public void analyzeTables() {
      for (Map.Entry<TableKnowledgeGraph, Integer> entry : tables.entrySet()) {
        TableKnowledgeGraph table = entry.getKey();
        evaluation.add("count", entry.getValue());
        table.populateStats(evaluation);
        for (String columnString : table.getAllColumnStrings())
          addIfOK(columnString, columnStrings);
        for (String cellString : table.getAllCellStrings())
          addIfOK(cellString, cellStrings);
      }
    }

    void addIfOK(String x, Map<String, Integer> collection) {
      x = StringNormalizationUtils.characterNormalize(x).toLowerCase();
      if (x.length() <= TableStatsComputer.opts.statsMaxStringLength)
        MapUtils.incr(collection, x);
    }

    public void summarize() {
      for (Map.Entry<String, Integer> entry : columnStrings.entrySet()) {
        evaluation.add("column-strings", entry.getKey(), entry.getValue());
      }
      for (Map.Entry<String, Integer> entry : cellStrings.entrySet()) {
        evaluation.add("cell-strings", entry.getKey(), entry.getValue());
      }
      evaluation.logStats("tables");
      dumpCollection(columnStrings, "columns");
      dumpCollection(cellStrings, "cells");
    }

    void dumpCollection(Map<String, Integer> collection, String filename) {
      List<Map.Entry<String, Integer>> entries = new ArrayList<>(collection.entrySet());
      Collections.sort(entries, new ValueComparator<String, Integer>(true));
      String path = Execution.getFile(filename);
      LogInfo.begin_track("Writing to %s (%d entries)", path, entries.size());
      PrintWriter out = IOUtils.openOutHard(path);
      for (Map.Entry<String, Integer> entry : entries) {
        out.printf("%6d : %s\n", entry.getValue(), entry.getKey());
      }
      out.close();
      LogInfo.end_track();
    }

  }
}
