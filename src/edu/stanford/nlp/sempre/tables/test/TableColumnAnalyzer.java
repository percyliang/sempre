package edu.stanford.nlp.sempre.tables.test;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.*;
import fig.basic.*;
import fig.exec.Execution;

/**
 * Analyze table columns and print out any hard-to-process column.
 *
 * @author ppasupat
 */
public class TableColumnAnalyzer implements Runnable {
  public static class Options {
    @Option(gloss = "Maximum number of tables to process (for debugging)")
    public int maxNumTables = Integer.MAX_VALUE;
    @Option(gloss = "Load Wikipedia article titles from this file")
    public String wikiTitles = null;
  }
  public static Options opts = new Options();

  public static void main(String[] args) {
    Execution.run(args, "TableColumnAnalyzerMain", new TableColumnAnalyzer(), Master.getOptionsParser());
  }

  PrintWriter out;
  PrintWriter outCompact;

  @Override
  public void run() {
    out = IOUtils.openOutHard(Execution.getFile("column-stats.tsv"));
    outCompact = IOUtils.openOutHard(Execution.getFile("column-compact.tsv"));
    Map<String, List<String>> tableIdToExIds = getTableIds();
    int tablesProcessed = 0;
    for (Map.Entry<String, List<String>> entry : tableIdToExIds.entrySet()) {
      Execution.putOutput("example", tablesProcessed);
      String tableId = entry.getKey(),
          tableIdAbbrev = tableId.replaceAll("csv/(\\d+)-csv/(\\d+)\\.csv", "$1-$2");
      LogInfo.begin_track("Processing %s ...", tableId);
      TableKnowledgeGraph graph = TableKnowledgeGraph.fromFilename(tableId);
      out.printf("%s\tIDS\t%s\n", tableIdAbbrev, String.join(" ", entry.getValue()));
      out.printf("%s\tCOLUMNS\t%d\n", tableIdAbbrev, graph.numColumns());
      for (int i = 0; i < graph.numColumns(); i++) {
        analyzeColumn(graph, graph.columns.get(i), tableIdAbbrev + "\t" + i);
      }
      LogInfo.end_track();
      if (tablesProcessed++ >= opts.maxNumTables) break;
    }
    out.close();
    outCompact.close();
  }

  protected Map<String, List<String>> getTableIds() {
    Map<String, List<String>> tableIdToExIds = new LinkedHashMap<>();
    LogInfo.begin_track_printAll("Collect table IDs");
    for (Pair<String, String> pathPair : Dataset.opts.inPaths) {
      String group = pathPair.getFirst();
      String path = pathPair.getSecond();
      Execution.putOutput("group", group);
      LogInfo.begin_track("Reading %s", path);
      Iterator<LispTree> trees = LispTree.proto.parseFromFile(path);
      while (trees.hasNext()) {
        LispTree tree = trees.next();
        if ("metadata".equals(tree.child(0).value)) continue;
        String exId = null, tableId = null;
        for (int i = 1; i < tree.children.size(); i++) {
          LispTree arg = tree.child(i);
          String label = arg.child(0).value;
          if ("id".equals(label)) {
            exId = arg.child(1).value;
          } else if ("context".equals(label)) {
            tableId = arg.child(1).child(2).value;
          }
        }
        if (exId != null && tableId != null) {
          List<String> exIdsForTable = tableIdToExIds.get(tableId);
          if (exIdsForTable == null)
            tableIdToExIds.put(tableId, exIdsForTable = new ArrayList<>());
          exIdsForTable.add(exId);
        }
      }
      LogInfo.end_track();
    }
    LogInfo.end_track();
    LogInfo.logs("Got %d IDs", tableIdToExIds.size());
    return tableIdToExIds;
  }

  protected void analyzeColumn(TableKnowledgeGraph graph, TableColumn column, String printPrefix) {
    List<String> escapedCells = new ArrayList<>();
    // Print the header
    String h = column.originalString, escapedH = StringNormalizationUtils.escapeTSV(h);
    out.printf("%s\t0\t%s\n", printPrefix, escapedH);
    escapedCells.add(escapedH);
    // Print the cells
    Map<String, Integer> typeCounts = new HashMap<>();
    for (int j = 0; j < column.children.size(); j++) {
      TableCell cell = column.children.get(j);
      String c = cell.properties.originalString, escapedC = StringNormalizationUtils.escapeTSV(c);
      escapedCells.add(escapedC);
      // Infer the type
      List<String> types = analyzeCell(c);
      for (String type : types)
        MapUtils.incr(typeCounts, type);
      out.printf("%s\t%d\t%s\t%s\n", printPrefix, j + 1, String.join("|", types), escapedC);
    }
    // Analyze the common types
    List<String> commonTypes = new ArrayList<>();
    for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
      if (entry.getValue() == column.children.size()) {
        commonTypes.add(entry.getKey());
      } else if (entry.getValue() == column.children.size() - 1) {
        commonTypes.add("ALMOST-" + entry.getKey());
      }
    }
    outCompact.printf("%s\t%s\t%s\n", String.join("|", commonTypes), printPrefix, String.join("\t", escapedCells));
  }

  // ============================================================
  // Cell analysis
  // ============================================================

  public static final Pattern ORDINAL = Pattern.compile("^(\\d+)(st|nd|rd|th)$");

  protected List<String> analyzeCell(String c) {
    List<String> types = new ArrayList<>();
    LanguageInfo languageInfo = LanguageAnalyzer.getSingleton().analyze(c);
    {
      // Integer
      NumberValue n = StringNormalizationUtils.parseNumberStrict(c);
      if (n != null) {
        // Number
        types.add("num");
        // Integer
        double value = n.value;
        if (Math.abs(value - Math.round(value)) < 1e-9) {
          types.add("int");
          if (c.matches("^[12]\\d\\d\\d$")) {
            // Year?
            types.add("year");
          }
        }
      }
    }
    {
      // Ordinal
      Matcher m = ORDINAL.matcher(c);
      if (m.matches()) {
        types.add("ordinal");
      }
    }
    {
      // Integer-Integer
      String[] splitted = StringNormalizationUtils.STRICT_DASH.split(c);
      if (splitted.length == 2 && splitted[0].matches("^[0-9]+$") && splitted[1].matches("^[0-9]+$")) {
        types.add("2ints");
      }
    }
    {
      // Date
      DateValue date = StringNormalizationUtils.parseDateWithLanguageAnalyzer(languageInfo);
      if (date != null) {
        types.add("date");
        // Also more detailed date type
        types.add("date-"
            + (date.year != -1  ? "Y" : "")
            + (date.month != -1 ? "M" : "")
            + (date.day != -1   ? "D" : ""));
      }
    }
    {
      // Quoted text
      if (c.matches("^[“”\"].*[“”\"]$")) {
        types.add("quoted");
      }
    }
    if (opts.wikiTitles != null) {
      // Wikipedia titles
      WikipediaTitleLibrary library = WikipediaTitleLibrary.getSingleton();
      if (library.contains(c)) {
        types.add("wiki");
      }
    }
    {
      // POS and NER
      types.add("POS=" + String.join("-", languageInfo.posTags));
      types.add("NER=" + String.join("-", languageInfo.nerTags));
    }
    return types;
  }

  // ============================================================
  // Helper class: Wikipedia titles
  // ============================================================

  public static class WikipediaTitleLibrary {

    private static WikipediaTitleLibrary _singleton = null;

    public static WikipediaTitleLibrary getSingleton() {
      if (_singleton == null)
        _singleton = new WikipediaTitleLibrary();
      return _singleton;
    }

    Set<String> titles = new HashSet<>();

    private WikipediaTitleLibrary() {
      assert opts.wikiTitles != null;
      LogInfo.begin_track("Reading Wikipedia article titles from %s ...", opts.wikiTitles);
      try {
        BufferedReader reader = IOUtils.openIn(opts.wikiTitles);
        String line;
        while ((line = reader.readLine()) != null) {
          titles.add(line);
          if (titles.size() <= 10) {
            LogInfo.logs("Example title: %s", line);
          }
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      LogInfo.logs("Read %d titles", titles.size());
      LogInfo.end_track();
    }

    public boolean contains(String c) {
      return titles.contains(c.toLowerCase().trim());
    }
  }


}
