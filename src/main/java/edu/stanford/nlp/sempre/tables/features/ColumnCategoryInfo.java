package edu.stanford.nlp.sempre.tables.features;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.sempre.Example;
import edu.stanford.nlp.sempre.tables.TableKnowledgeGraph;
import fig.basic.*;

public class ColumnCategoryInfo {
  public static class Options {
    @Option(gloss = "Read category information from this file")
    public String tableCategoryInfo = null;
  }
  public static Options opts = new Options();

  // ============================================================
  // Singleton access
  // ============================================================

  private static ColumnCategoryInfo singleton;

  public static ColumnCategoryInfo getSingleton() {
    if (opts.tableCategoryInfo == null)
      return null;
    else if (singleton == null)
      singleton = new ColumnCategoryInfo();
    return singleton;
  }

  // ============================================================
  // Read data from file
  // ============================================================

  // tableId -> columnIndex -> list of (category, weight)
  protected static Map<String, List<List<Pair<String, Double>>>> allCategoryInfo = null;

  private ColumnCategoryInfo() {
    LogInfo.begin_track("Loading category information from %s", opts.tableCategoryInfo);
    allCategoryInfo = new HashMap<>();
    try {
      BufferedReader reader = IOUtils.openIn(opts.tableCategoryInfo);
      String line;
      while ((line = reader.readLine()) != null) {
        String[] tokens = line.split("\t");
        String tableId = tokens[0];
        List<List<Pair<String, Double>>> categoryInfoForTable = allCategoryInfo.get(tableId);
        if (categoryInfoForTable == null)
          allCategoryInfo.put(tableId, categoryInfoForTable = new ArrayList<>());
        int columnIndex = Integer.parseInt(tokens[1]);
        // Assume that the columns are ordered
        assert categoryInfoForTable.size() == columnIndex;
        // Read the category-weight pairs
        List<Pair<String, Double>> categories = new ArrayList<>();
        for (int i = 2; i < tokens.length; i++) {
          String[] pair = tokens[i].split(":");
          categories.add(new Pair<>(pair[0], Double.parseDouble(pair[1])));
        }
        categoryInfoForTable.add(categories);
      }
      reader.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    LogInfo.end_track();
  }

  // ============================================================
  // Getters
  // ============================================================

  public List<Pair<String, Double>> get(String tableId, int columnIndex) {
    return allCategoryInfo.get(tableId).get(columnIndex);
  }

  public List<Pair<String, Double>> get(Example ex, String columnId) {
    TableKnowledgeGraph graph = (TableKnowledgeGraph) ex.context.graph;
    String tableId = graph.filename;
    int columnIndex = graph.getColumnIndex(columnId);
    if (columnIndex == -1) return null;
    return allCategoryInfo.get(tableId).get(columnIndex);
  }

}
