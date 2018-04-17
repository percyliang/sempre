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

/**
 * Compute various statistics about the dataset.
 *
 * - Table size (rows, columns, unique cells)
 * - Answer type
 * - Whether the answer is in the table
 *
 * Also aggregate column strings and cell word shapes.
 *
 * @author ppasupat
 */
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
    PrintWriter out = IOUtils.openOutHard(Execution.getFile("table-stats.tsv"));
    TableStatsComputerProcessor processor = new TableStatsComputerProcessor(out);
    CustomExample.getDataset(Dataset.opts.inPaths, processor);
    processor.analyzeTables();
    out.close();
  }

  static class TableStatsComputerProcessor implements ExampleProcessor {
    PrintWriter out;
    Evaluation evaluation = new Evaluation();
    Map<TableKnowledgeGraph, Integer> tableCounts = new HashMap<>();
    Map<String, Integer> columnStrings = new HashMap<>(), cellStrings = new HashMap<>();
    Builder builder;

    public TableStatsComputerProcessor(PrintWriter out) {
      builder = new Builder();
      builder.build();
      this.out = out;
      out.println(String.join("\t", new String[] {
          "id", "context", "rows", "columns", "uniqueCells", "targetType", "inTable",
      }));
    }

    @Override
    public void run(CustomExample ex) {
      List<String> outputFields = new ArrayList<>();
      outputFields.add(ex.id);
      TableKnowledgeGraph graph = (TableKnowledgeGraph) ex.context.graph;
      MapUtils.incr(tableCounts, graph);
      outputFields.add(graph.toLispTree().child(2).value);
      outputFields.add("" + graph.numRows());
      outputFields.add("" + graph.numColumns());
      outputFields.add("" + graph.numUniqueCells());
      // Answer type. For convenience, just use the first answer from the list
      Value value = ((ListValue) ex.targetValue).values.get(0);
      evaluation.add("value-number", value instanceof NumberValue);
      evaluation.add("value-date", value instanceof DateValue);
      evaluation.add("value-text", value instanceof DescriptionValue);
      evaluation.add("value-partial-number",
          value instanceof DescriptionValue && ((DescriptionValue) value).value.matches(".*[0-9].*"));
      // Check if the value is in the table
      boolean inTable = false;
      if (value instanceof DescriptionValue) {
        outputFields.add("text");
        Collection<Formula> formulas = graph.getFuzzyMatchedFormulas(((DescriptionValue) value).value, FuzzyMatchFnMode.ENTITY);
        inTable = !formulas.isEmpty();
        evaluation.add("value-text-in-table", inTable);
      } else if (value instanceof NumberValue) {
        outputFields.add("number");
        // (and (@type @cell) (@p.num ___))
        Formula formula = new MergeFormula(Mode.and,
            new JoinFormula(Formula.fromString(CanonicalNames.TYPE), Formula.fromString(TableTypeSystem.CELL_GENERIC_TYPE)),
            new JoinFormula(Formula.fromString(TableTypeSystem.CELL_NUMBER_VALUE.id), new ValueFormula<Value>(value)));
        Value result = builder.executor.execute(formula, ex.context).value;
        inTable = result instanceof ListValue && !((ListValue) result).values.isEmpty();
        evaluation.add("value-number-in-table", inTable);
      } else if (value instanceof DateValue) {
        outputFields.add("date");
        // (and (@type @cell) (@p.num ___))
        Formula formula = new MergeFormula(Mode.and,
            new JoinFormula(Formula.fromString(CanonicalNames.TYPE), Formula.fromString(TableTypeSystem.CELL_GENERIC_TYPE)),
            new JoinFormula(Formula.fromString(TableTypeSystem.CELL_DATE_VALUE.id), new ValueFormula<Value>(value)));
        Value result = builder.executor.execute(formula, ex.context).value;
        inTable = result instanceof ListValue && !((ListValue) result).values.isEmpty();
        evaluation.add("value-number-in-table", inTable);
      } else {
        outputFields.add("unknown");
      }
      evaluation.add("value-any-in-table", inTable);
      outputFields.add("" + inTable);
      out.println(String.join("\t", outputFields));
    }

    public void analyzeTables() {
      for (Map.Entry<TableKnowledgeGraph, Integer> entry : tableCounts.entrySet()) {
        TableKnowledgeGraph table = entry.getKey();
        evaluation.add("count", entry.getValue());
        table.populateStats(evaluation);
        for (String columnString : table.getAllColumnStrings())
          addIfOK(columnString, columnStrings);
        for (String cellString : table.getAllCellStrings())
          addIfOK(cellString, cellStrings);
      }
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

    void addIfOK(String x, Map<String, Integer> collection) {
      x = StringNormalizationUtils.characterNormalize(x).toLowerCase();
      if (x.length() <= TableStatsComputer.opts.statsMaxStringLength)
        MapUtils.incr(collection, x);
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
