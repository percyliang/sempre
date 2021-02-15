package edu.stanford.nlp.sempre.tables.alter;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.*;
import edu.stanford.nlp.sempre.tables.lambdadcs.DenotationUtils;
import fig.basic.*;

/**
 * Alter the given table.
 *
 * Given a table, the corresponding Example, and a seed (alteredTableIndex), return an altered table.
 *
 * @author ppasupat
 */
public class TableAlterer {
  public static class Options {
    @Option(gloss = "verbosity")
    public int verbose = 0;
    @Option(gloss = "parameter for the geometric distribution used to cut the number of rows")
    public double altererGeomDistParam = 0.75;
    @Option
    public int maxNumRows = 50;
  }
  public static Options opts = new Options();

  public final Example ex;
  public final TableKnowledgeGraph oldGraph;

  public TableAlterer(Example ex) {
    this.ex = ex;
    this.oldGraph = (TableKnowledgeGraph) ex.context.graph;
  }

  /**
   * For each column, perform random draws with replacement until all rows are filled.
   *
   * Exceptions:
   * - If the column has distinct cells, then just permute.
   * - If the column is sorted, keep it sorted.
   */
  public TableKnowledgeGraph constructAlteredGraph(int alteredTableIndex) {
    Random altererRandom = new Random();
    int numRows = Math.min(oldGraph.numRows(), opts.maxNumRows);
    numRows -= getGeometricRandom(numRows / 2, altererRandom);
    List<List<TableCellProperties>> cellsByColumn = new ArrayList<>();
    // Fuzzy Matching
    Set<Value> fuzzyMatchedValues = new HashSet<>();
    for (int i = 0; i < ex.numTokens(); i++) {
      for (int j = i + 1; j < ex.numTokens(); j++) {
        for (Formula formula : oldGraph.getFuzzyMatchedFormulas(
            ex.getTokens(), i, j, FuzzyMatchFn.FuzzyMatchFnMode.ENTITY)) {
          if (formula instanceof ValueFormula) {
            fuzzyMatchedValues.add(((ValueFormula<?>) formula).value);
          }
        }
      }
    }
    if (opts.verbose >= 2) {
      LogInfo.logs("Fuzzy matched: %s", fuzzyMatchedValues);
    }
    // Go over each column
    for (int j = 0; j < oldGraph.numColumns(); j++) {
      altererRandom = new Random();
      TableColumn oldColumnCells = oldGraph.getColumn(j);
      List<TableCellProperties> oldColumn = new ArrayList<>(), newColumn = new ArrayList<>();
      for (TableCell cell : oldColumnCells.children)
        oldColumn.add(cell.properties);
      // Keep the entries that are fuzzy matched
      Set<TableCellProperties> fuzzyMatchedValuesInColumn = new HashSet<>();
      for (TableCellProperties properties : oldColumn) {
        if (fuzzyMatchedValues.contains(properties.nameValue))
          fuzzyMatchedValuesInColumn.add(properties);
      }
      newColumn.addAll(fuzzyMatchedValuesInColumn);
      while (newColumn.size() > numRows)
        newColumn.remove(newColumn.size() - 1);
      // Sample the cells
      boolean isAllDistinct = isAllDistinct(oldColumn);
      if (isAllDistinct) {
        // Go from top to bottom, ignoring the ones already added
        List<TableCellProperties> nonFuzzyMatched = new ArrayList<>(oldColumn);
        for (TableCellProperties properties : newColumn)
          nonFuzzyMatched.remove(properties);
        for (int i = 0; newColumn.size() < numRows; i++)
          newColumn.add(nonFuzzyMatched.get(i));
      } else {
        // Sample with replacement
        while (newColumn.size() < numRows)
          newColumn.add(oldColumn.get(altererRandom.nextInt(numRows)));
      }
      Collections.shuffle(newColumn, altererRandom);
      // Sort?
      String sorted = "";
      for (Pair<String, Comparator<TableCellProperties>> pair : COMPS) {
        if (isSorted(oldColumn, pair.getSecond())) {
          sorted = pair.getFirst();
          newColumn.sort(pair.getSecond());
          break;
        }
      }
      // Done!
      cellsByColumn.add(newColumn);
      if (opts.verbose >= 2) {
        LogInfo.logs("Column %3s%4s %s", isAllDistinct ? "[!]" : "", sorted, oldColumnCells.relationNameValue);
      }
    }
    if (opts.verbose >= 1)
      LogInfo.logs("numRows = %d | final size = %d columns x %d rows",
          numRows, cellsByColumn.size(), cellsByColumn.get(0).size());
    return new TableKnowledgeGraph(null, oldGraph.columns, cellsByColumn, true);
  }

  // ============================================================
  // Helper Functions
  // ============================================================

  int getGeometricRandom(int limit, Random random) {
    int geometricRandom = 0;
    while (geometricRandom < limit && random.nextDouble() < opts.altererGeomDistParam)
      geometricRandom++;
    return geometricRandom;
  }

  private boolean isAllDistinct(List<TableCellProperties> properties) {
    Set<String> ids = new HashSet<>();
    for (TableCellProperties x : properties) {
      if (ids.contains(x.id)) return false;
      ids.add(x.id);
    }
    return true;
  }

  private static final Comparator<TableCellProperties> NUMBER_COMP = new Comparator<TableCellProperties>() {
    @Override public int compare(TableCellProperties o1, TableCellProperties o2) {
      Collection<Value> v1 = o1.metadata.get(TableTypeSystem.CELL_NUMBER_VALUE),
          v2 = o2.metadata.get(TableTypeSystem.CELL_NUMBER_VALUE);
      try {
        return DenotationUtils.NumberProcessor.singleton.compareValues(v1.iterator().next(), v2.iterator().next());
      } catch (Exception e) {
        throw new ClassCastException();
      }
    }
  };
  private static final Comparator<TableCellProperties> NUMBER_COMP_REV = Collections.reverseOrder(NUMBER_COMP);

  private static final Comparator<TableCellProperties> DATE_COMP = new Comparator<TableCellProperties>() {
    @Override public int compare(TableCellProperties o1, TableCellProperties o2) {
      Collection<Value> v1 = o1.metadata.get(TableTypeSystem.CELL_DATE_VALUE),
          v2 = o2.metadata.get(TableTypeSystem.CELL_DATE_VALUE);
      try {
        return DenotationUtils.DateProcessor.singleton.compareValues(v1.iterator().next(), v2.iterator().next());
      } catch (Exception e) {
        throw new ClassCastException();
      }
    }
  };
  private static final Comparator<TableCellProperties> DATE_COMP_REV = Collections.reverseOrder(DATE_COMP);

  private static final List<Pair<String, Comparator<TableCellProperties>>> COMPS = Arrays.asList(
      new Pair<>("[N+]", NUMBER_COMP),
      new Pair<>("[N-]", NUMBER_COMP_REV),
      new Pair<>("[D+]", DATE_COMP),
      new Pair<>("[D-]", DATE_COMP_REV)
      );

  private boolean isSorted(List<TableCellProperties> properties, Comparator<TableCellProperties> comparator) {
    try {
      for (int i = 0; i < properties.size() - 1; i++) {
        if (comparator.compare(properties.get(i), properties.get(i+1)) > 0)
          return false;
      }
      return true;
    } catch (ClassCastException e) {
      return false;
    }
  }

}
