package edu.stanford.nlp.sempre.tables;

import java.io.*;
import java.util.*;

import au.com.bytecode.opencsv.CSVReader;
import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.lambdadcs.LambdaDCSException;
import edu.stanford.nlp.sempre.tables.lambdadcs.LambdaDCSException.Type;
import fig.basic.*;

/**
 * A knowledge graph constructed from a table.
 *
 * - Each row becomes an entity
 * - Each cell becomes an entity
 * - Each column becomes a property between a row and a cell
 *     e.g., (row5 nationality canada)
 * - Rows have several special properties (next, index)
 *
 * === Special Row Properties ===
 * - name = fb:row.row.next  | type = (-> fb:type.row fb:type.row)
 * - name = fb:row.row.index | type = (-> fb:type.int fb:type.row)
 *
 * === Special Cell Properties ===
 * - name = fb:cell.cell.number | type = (-> fb:type.number fb:type.cell)
 *
 * @author ppasupat
 */
public class TableKnowledgeGraph extends KnowledgeGraph {
  public static class Options {
    @Option(gloss = "Verbosity") public int verbose = 0;
    @Option(gloss = "Base directory for CSV files") public String baseCSVDir = null;
    @Option(gloss = "Normalize cell content before assigning id")
    public boolean normalizeBeforeCreatingId = true;
    @Option(gloss = "Forbid row.row.next on multiple rows")
    public boolean forbidNextOnManyRows = true;
  }
  public static Options opts = new Options();

  // ============================================================
  // Fields
  // ============================================================

  public List<TableRow> rows;
  public List<TableColumn> columns;
  String filename;

  Map<String, TableRow> rowNameToTableRow;
  Map<String, TableColumn> columnNameToTableColumn;
  Map<String, TableColumn> propertyIdToTableColumn;
  Map<String, TableCellProperties> cellIdToTableCellProperties;
  FuzzyMatcher fuzzyMatcher;

  // ============================================================
  // Constructor
  // ============================================================

  /**
   * Constructor (not visible to public)
   */
  TableKnowledgeGraph(String filename) {
    // Cells in the same column with the same string content gets the same id.
    Map<Pair<TableColumn, String>, String> columnAndOriginalStringToCellId = new HashMap<>();

    // Read the CSV file
    this.filename = filename;
    filename = new File(opts.baseCSVDir, filename).getPath();
    try (CSVReader csv = new CSVReader(new FileReader(filename))) {
      for (String[] record : csv) {
        if (columns == null) {
          // Initialize
          rows = new ArrayList<>();
          columns = new ArrayList<>();
          rowNameToTableRow = new HashMap<>();
          columnNameToTableColumn = new HashMap<>();
          propertyIdToTableColumn = new HashMap<>();
          cellIdToTableCellProperties = new HashMap<>();
          // Read the header row
          for (String entry : record) {
            entry = StringNormalizationUtils.unescape(entry);
            String normalizedEntry = (opts.normalizeBeforeCreatingId ?
                StringNormalizationUtils.characterNormalize(entry).toLowerCase() : entry);
            String columnName = TableTypeSystem.getUnusedName(
                TableTypeSystem.canonicalizeName(normalizedEntry), columnNameToTableColumn.keySet());
            TableColumn column = new TableColumn(entry, columnName, columns.size());
            columns.add(column);
            columnNameToTableColumn.put(columnName, column);
            propertyIdToTableColumn.put(column.propertyNameValue.id, column);
          }
        } else {
          // Read the content row
          if (record.length != columns.size()) {
            LogInfo.warnings("Table has %d columns but row has %d cells: %s | %s", columns.size(),
                record.length, columns, Fmt.D(record));
          }
          TableRow currentRow = new TableRow(rows.size());
          rowNameToTableRow.put(currentRow.entityNameValue.id, currentRow);
          rows.add(currentRow);
          for (int i = 0; i < columns.size(); i++) {
            TableColumn column = columns.get(i);
            String entry = (i < record.length) ? record[i] : "";
            entry = StringNormalizationUtils.unescape(entry);
            String normalizedEntry = (opts.normalizeBeforeCreatingId ?
                StringNormalizationUtils.characterNormalize(entry).toLowerCase() : entry);
            Pair<TableColumn, String> columnAndOriginalString = new Pair<>(column, normalizedEntry);
            String id = columnAndOriginalStringToCellId.get(columnAndOriginalString);
            if (id == null) {
              String canonicalName = TableTypeSystem.canonicalizeName(normalizedEntry);
              id = TableTypeSystem.getUnusedName(
                  TableTypeSystem.getCellName(canonicalName, column.fieldName),
                  cellIdToTableCellProperties.keySet());
              columnAndOriginalStringToCellId.put(columnAndOriginalString, id);
              cellIdToTableCellProperties.put(id, new TableCellProperties(id, entry));
            }
            TableCellProperties properties = cellIdToTableCellProperties.get(id);
            TableCell.createAndAddTo(properties, column, currentRow);
          }
        }
      }
      // Generate cell properties by analyzing cell content in each column
      for (TableColumn column : columns)
        StringNormalizationUtils.analyzeColumn(column);
      // Precompute normalized strings for fuzzy matching
      fuzzyMatcher = new FuzzyMatcher(this);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // Cache (don't create multiple graphs for the same CSV file)
  static final Map<String, TableKnowledgeGraph> filenameToGraph = new HashMap<>();

  public static synchronized TableKnowledgeGraph fromFilename(String filename) {
    // Get from cache if possible
    TableKnowledgeGraph graph = filenameToGraph.get(filename);
    if (graph == null) {
      if (opts.verbose >= 1)
        LogInfo.logs("create new TableKnowledgeGraph from filename = %s", filename);
      StopWatchSet.begin("TableKnowledgeGraph.new");
      graph = new TableKnowledgeGraph(filename);
      StopWatchSet.end();
      filenameToGraph.put(filename, graph);
    }
    return graph;
  }

  public static TableKnowledgeGraph fromLispTree(LispTree tree) {
    return fromFilename(tree.child(2).value);
  }

  // ============================================================
  // Convert to other formats
  // ============================================================

  @Override
  public LispTree toLispTree() {
    if (filename != null) {
      // short version: just print the filename
      LispTree tree = LispTree.proto.newList();
      tree.addChild("graph");
      tree.addChild("tables.TableKnowledgeGraph");
      tree.addChild(filename);
      return tree;
    }
    return toTableValue().toLispTree();
  }

  public TableValue toTableValue() {
    List<String> tableValueHeader = new ArrayList<>();
    List<List<Value>> tableValueRows = new ArrayList<>();
    for (TableColumn column : columns) {
      tableValueHeader.add(column.originalString);
    }
    for (TableRow row : rows) {
      List<Value> tableValueRow = new ArrayList<>();
      for (TableCell cell : row.children) {
        tableValueRow.add(cell.properties.entityNameValue);
      }
      tableValueRows.add(tableValueRow);
    }
    return new TableValue(tableValueHeader, tableValueRows);
  }

  // ============================================================
  // Fuzzy matching
  // ============================================================

  @Override
  public Collection<Formula> getFuzzyMatchedFormulas(String term, FuzzyMatchFn.FuzzyMatchFnMode mode) {
    return fuzzyMatcher.getFuzzyMatchedFormulas(term, mode);
  }

  @Override
  public Collection<Formula> getAllFormulas(FuzzyMatchFn.FuzzyMatchFnMode mode) {
    return fuzzyMatcher.getAllFormulas(mode);
  }

  // ============================================================
  // Query
  // ============================================================

  public static final NameValue TYPE = new NameValue(CanonicalNames.TYPE);
  public static final NameValue ROW_TYPE = new NameValue(TableTypeSystem.ROW_TYPE);
  public static final NameValue CELL_TYPE = new NameValue(TableTypeSystem.CELL_GENERIC_TYPE);

  /** Return all y such that x in firsts and (x,r,y) in graph */
  @Override
  public List<Value> joinFirst(Value r, Collection<Value> firsts) {
    return joinSecond(getReversedPredicate(r), firsts);
  }

  /** Return all x such that y in seconds and (x,r,y) in graph */
  @Override
  public List<Value> joinSecond(Value r, Collection<Value> seconds) {
    List<Value> answer = new ArrayList<>();
    for (Pair<Value, Value> pair : filterSecond(r, seconds))
      answer.add(pair.getFirst());
    return answer;
  }

  /** Return all (x,y) such that x in firsts and (x,r,y) in graph */
  @Override
  public List<Pair<Value, Value>> filterFirst(Value r, Collection<Value> firsts) {
    return getReversedPairs(filterSecond(getReversedPredicate(r), firsts));
  }

  /*
   * - one-one and many-one: Each X maps to 1 Y:
   *   X-Y = row-row, row-primitive, primitive-row, row-cell, cell-primitive
   * - one-many: Deduplicate first, then each X maps to possibly many Y's
   *   X-Y = cell-row, primitive-cell
   */
  /** Return all (x,y) such that y in seconds and (x,r,y) in graph */
  // TODO(ice): Check correctness
  @Override
  public List<Pair<Value, Value>> filterSecond(Value r, Collection<Value> seconds) {
    List<Pair<Value, Value>> answer = new ArrayList<>();
    Value reversed = isReversedRelation(r);
    if (reversed != null) {
      r = reversed;
      if (r.equals(TYPE)) {
        // (!fb:type.object.type fb:row.r5) --> fb:type.row
        // Not handled right now.
        throw new BadFormulaException("Unhandled! " + r);
      } else if (r.equals(TableTypeSystem.ROW_NEXT_VALUE)) {
        // (!fb:row.row.next fb:row.r5) --> fb:row.r6
        if (opts.forbidNextOnManyRows && seconds.size() != 1) {
          throw new LambdaDCSException(Type.nonSingletonList, "cannot call next on " + seconds.size() + " rows.");
        }
        if (seconds.size() == Integer.MAX_VALUE) {
          for (int i = 0; i < rows.size() - 1; i++) {
            if (!seconds.contains(rows.get(i).entityNameValue)) continue;
            answer.add(new Pair<>(rows.get(i + 1).entityNameValue, rows.get(i).entityNameValue));
          }
        } else {
          for (Value value : seconds) {
            if (!(value instanceof NameValue)) continue;
            TableRow row = rowNameToTableRow.get(((NameValue) value).id);
            if (row == null) continue;
            int i = row.index;
            if (i + 1 >= rows.size()) continue;
            answer.add(new Pair<>(rows.get(i + 1).entityNameValue, row.entityNameValue));
          }
        }
      } else if (r.equals(TableTypeSystem.ROW_INDEX_VALUE)) {
        // (!fb:row.row.index fb:row.r5) --> (number 5)
        if (seconds.size() == Integer.MAX_VALUE) {
          for (TableRow row : rows) {
            if (!seconds.contains(row.entityNameValue)) continue;
            answer.add(new Pair<>(row.indexValue, row.entityNameValue));
          }
        } else {
          for (Value value : seconds) {
            if (!(value instanceof NameValue)) continue;
            TableRow row = rowNameToTableRow.get(((NameValue) value).id);
            if (row == null) continue;
            answer.add(new Pair<>(row.indexValue, row.entityNameValue));
          }
        }
      } else if (TableTypeSystem.isCellProperty(r)) {
        // (!fb:cell.cell.number fb:cell_id.5) --> 5
        if (seconds.size() == Integer.MAX_VALUE) {
          for (TableColumn column : columns) {
            for (TableCell cell : column.children) {
              Value property = cell.properties.metadata.get(r);
              if (property == null || !seconds.contains(cell.properties.entityNameValue)) continue;
              answer.add(new Pair<>(property, cell.properties.entityNameValue));
            }
          }
        } else {
          for (Value value : seconds) {
            if (!(value instanceof NameValue)) continue;
            TableCellProperties properties = cellIdToTableCellProperties.get(((NameValue) value).id);
            if (properties == null) continue;
            Value property = properties.metadata.get(r);
            if (property == null) continue;
            answer.add(new Pair<>(property, properties.entityNameValue));
          }
        }
      } else {
        // (!fb:column.nationality fb:row.r5) --> fb:cell.canada
        if (seconds.size() == Integer.MAX_VALUE) {
          for (int i = 0; i < columns.size(); i++) {
            if (!r.equals(columns.get(i).propertyNameValue)) continue;
            for (TableRow row : rows) {
              if (!seconds.contains(row.entityNameValue)) continue;
              answer.add(new Pair<>(row.children.get(i).properties.entityNameValue, row.entityNameValue));
            }
          }
        } else {
          for (int i = 0; i < columns.size(); i++) {
            if (!r.equals(columns.get(i).propertyNameValue)) continue;
            for (Value value : seconds) {
              if (!(value instanceof NameValue)) continue;
              TableRow row = rowNameToTableRow.get(((NameValue) value).id);
              if (row == null) continue;
              answer.add(new Pair<>(row.children.get(i).properties.entityNameValue, row.entityNameValue));
            }
          }
        }
      }
    } else {
      if (r.equals(TYPE)) {
        // (fb:type.object.type fb:type.row) --> {fb:row.r1, fb:row.r2, ...}
        // Right now handles fb:type.row, fb:type.cell, fb:column.___
        for (Value second : seconds) {
          if (second.equals(ROW_TYPE)) {
            for (TableRow row : rows)
              answer.add(new Pair<>(row.entityNameValue, second));
          } else if (second.equals(CELL_TYPE)) {
            for (TableRow row : rows)
              for (TableCell cell : row.children)
                answer.add(new Pair<>(cell.properties.entityNameValue, second));
          } else {
            for (TableColumn column : columns) {
              if (!second.equals(column.cellTypeValue)) continue;
              for (TableCell cell : column.children)
                answer.add(new Pair<>(cell.properties.entityNameValue, second));
            }
          }
        }
      } else if (r.equals(TableTypeSystem.ROW_NEXT_VALUE)) {
        // (fb:row.row.next fb:row.r5) --> fb:row.r4
        if (opts.forbidNextOnManyRows && seconds.size() != 1) {
          throw new LambdaDCSException(Type.nonSingletonList, "cannot call next on " + seconds.size() + " rows.");
        }
        if (seconds.size() == Integer.MAX_VALUE) {
          for (int i = 1; i < rows.size(); i++) {
            if (!seconds.contains(rows.get(i).entityNameValue)) continue;
            answer.add(new Pair<>(rows.get(i - 1).entityNameValue, rows.get(i).entityNameValue));
          }
        } else {
          for (Value value : seconds) {
            if (!(value instanceof NameValue)) continue;
            TableRow row = rowNameToTableRow.get(((NameValue) value).id);
            if (row == null) continue;
            int i = row.index;
            if (i - 1 < 0) continue;
            answer.add(new Pair<>(rows.get(i - 1).entityNameValue, row.entityNameValue));
          }
        }
      } else if (r.equals(TableTypeSystem.ROW_INDEX_VALUE)) {
        // (fb:row.row.index (number 5)) --> fb:row.r5
        if (seconds.size() == Integer.MAX_VALUE) {
          for (TableRow row : rows) {
            if (!seconds.contains(row.indexValue)) continue;
            answer.add(new Pair<>(row.entityNameValue, row.indexValue));
          }
        } else {
          for (Value value : seconds) {
            if (!(value instanceof NumberValue)) continue;
            int i = (int) ((NumberValue) value).value;
            if (i < 0 || i >= rows.size()) continue;
            TableRow row = rows.get(i);
            answer.add(new Pair<>(row.entityNameValue, row.indexValue));
          }
        }
      } else if (TableTypeSystem.isCellProperty(r)) {
        // (fb:cell.cell.number (number 5)) --> {fb:cell_id.5 fb:cell_population.5, ...}
        // Possibly with repeated id (if there are multiple cells with that id)
        for (TableColumn column : columns) {
          for (TableCell cell : column.children) {
            Value property = cell.properties.metadata.get(r);
            if (property == null || !seconds.contains(property)) continue;
            answer.add(new Pair<>(cell.properties.entityNameValue, property));
          }
        }
      } else {
        // (fb:column.nationality fb:cell.canada) --> fb:row.r5
        for (int i = 0; i < columns.size(); i++) {
          if (!r.equals(columns.get(i).propertyNameValue)) continue;
          for (TableRow row : rows) {
            if (!seconds.contains(row.children.get(i).properties.entityNameValue)) continue;
            answer.add(new Pair<>(row.entityNameValue, row.children.get(i).properties.entityNameValue));
          }
        }
      }
    }
    return answer;
  }

  // ============================================================
  // Methods specific to TableKnowledgeGraph
  // ============================================================

  public void populateStats(Evaluation evaluation) {
    evaluation.add("rows", rows.size());
    evaluation.add("columns", columns.size());
    evaluation.add("cells", rows.size() * columns.size());
  }

  public int numRows() { return rows.size(); }
  public int numColumns() { return columns.size(); }

  public List<String> getAllColumnStrings() {
    List<String> columnStrings = new ArrayList<>();
    for (TableColumn column : columns) {
      columnStrings.add(column.originalString);
    }
    return columnStrings;
  }

  public List<String> getAllCellStrings() {
    List<String> cellStrings = new ArrayList<>();
    for (TableColumn column : columns) {
      for (TableCell cell : column.children) {
        cellStrings.add(cell.properties.originalString);
      }
    }
    return cellStrings;
  }

  public String getOriginalString(Value value) {
    return (value instanceof NameValue) ? getOriginalString(((NameValue) value).id) : null;
  }

  public String getOriginalString(String nameValueId) {
    if (nameValueId.startsWith("!")) nameValueId = nameValueId.substring(1);
    if (cellIdToTableCellProperties.containsKey(nameValueId))
      return cellIdToTableCellProperties.get(nameValueId).originalString;
    if (propertyIdToTableColumn.containsKey(nameValueId))
      return propertyIdToTableColumn.get(nameValueId).originalString;
    if (nameValueId.startsWith(TableTypeSystem.CELL_SPECIFIC_TYPE_PREFIX)) {
      String property = nameValueId.replace(TableTypeSystem.CELL_SPECIFIC_TYPE_PREFIX, TableTypeSystem.ROW_PROPERTY_NAME_PREFIX);
      if (propertyIdToTableColumn.containsKey(property))
        return propertyIdToTableColumn.get(property).originalString;
    }
    return null;
  }

  public List<Integer> getRowIndices(String nameValueId) {
    String property = TableTypeSystem.getPropertyOfEntity(nameValueId);
    if (property == null) return null;
    TableColumn column = propertyIdToTableColumn.get(property);
    if (column == null) return null;
    List<Integer> answer = new ArrayList<>();
    for (int i = 0; i < column.children.size(); i++) {
      if (column.children.get(i).properties.id.equals(nameValueId))
        answer.add(i);
    }
    return answer;
  }

  // ============================================================
  // Test
  // ============================================================

  public static void main(String[] args) {
    //opts.baseCSVDir = "tables/toy-examples/random/";
    //String filename = "nikos_machlas.csv";
    opts.normalizeBeforeCreatingId = true;
    opts.baseCSVDir = "lib/data/tables/";
    String filename = "csv/204-csv/255.csv";
    TableKnowledgeGraph graph = (TableKnowledgeGraph) KnowledgeGraph.fromLispTree(
        LispTree.proto.parseFromString("(graph tables.TableKnowledgeGraph " + filename + ")"));
    //LogInfo.logs("%s", graph.toLispTree().toStringWrap());
    //LogInfo.logs("%s", graph.toTableValue().toLispTree().toStringWrap(100));
    for (TableColumn column : graph.columns) {
      LogInfo.begin_track("%s", column.fieldName);
      for (TableCell cell : column.children) {
        LogInfo.logs("%s %s", cell.properties.entityNameValue, cell.properties.metadata);
      }
      LogInfo.end_track();
    }
  }

}
