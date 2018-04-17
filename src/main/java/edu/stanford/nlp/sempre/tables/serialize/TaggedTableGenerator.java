package edu.stanford.nlp.sempre.tables.serialize;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.*;
import fig.basic.IOUtils;
import fig.basic.LogInfo;
import fig.exec.Execution;

/**
 * Generate TSV files containing CoreNLP tags of the tables.
 *
 * Mandatory fields:
 * - row:         row index (-1 is the header row)
 * - col:         column index
 * - id:          unique ID of the cell.
 *   - Each header cell gets a unique ID even when the contents are identical
 *   - Non-header cells get the same ID <=> they have exactly the same content
 * - content:     the cell text (images and hidden spans are removed)
 * - tokens:      the cell text, tokenized
 * - lemmaTokens: the cell text, tokenized and lemmatized
 * - posTags:     the part of speech tag of each token
 * - nerTags:     the name entity tag of each token
 * - nerValues:   if the NER tag is numerical or temporal, the value of that
 *                NER span will be listed here
 *
 * The following fields are optional:
 * - number:      interpretation as a number
 *   - For multiple numbers, the first number is extracted
 * - date:        interpretation as a date
 * - num2:        the second number in the cell (useful for scores like `1-2`)
 * - list:        interpretation as a list of items
 * - listId:      unique ID of list items
 *
 * @author ppasupat
 */
public class TaggedTableGenerator extends TSVGenerator implements Runnable {

  public static void main(String[] args) {
    Execution.run(args, "TaggedTableGeneratorMain", new TaggedTableGenerator(),
        Master.getOptionsParser());
  }

  public static final Pattern FILENAME_PATTERN = Pattern.compile("^.*/(\\d+)-csv/(\\d+).csv$");
  private LanguageAnalyzer analyzer;

  @Override
  public void run() {
    // Get the list of all tables
    analyzer = LanguageAnalyzer.getSingleton();
    Path baseDir = Paths.get(TableKnowledgeGraph.opts.baseCSVDir);
    try {
      Files.walkFileTree(baseDir, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Matcher matcher = FILENAME_PATTERN.matcher(file.toString());
          if (matcher.matches()) {
            LogInfo.begin_track("Processing %s", file);
            int batchIndex = Integer.parseInt(matcher.group(1)),
                dataIndex = Integer.parseInt(matcher.group(2));
            TableKnowledgeGraph table = TableKnowledgeGraph.fromFilename(baseDir.relativize(file).toString());
            String outDir = Execution.getFile("tagged/" + batchIndex + "-tagged/"),
                outFilename = new File(outDir, dataIndex + ".tagged").getPath();
            new File(outDir).mkdirs();
            out = IOUtils.openOutHard(outFilename);
            dumpTable(table);
            out.close();
            LogInfo.end_track();
          }
          return super.visitFile(file, attrs);
        }
      });
    } catch (IOException e) {
      e.printStackTrace();
      LogInfo.fails("%s", e);
    }
  }

  private static final String[] FIELDS = new String[] {
    "row", "col", "id", "content",
    "tokens", "lemmaTokens", "posTags", "nerTags", "nerValues",
    "number", "date", "num2", "list", "listId",
  };

  @Override
  protected void dump(String... stuff) {
    assert stuff.length == FIELDS.length;
    super.dump(stuff);
  }

  private void dumpTable(TableKnowledgeGraph table) {
    dump(FIELDS);
    // header row
    for (int j = 0; j < table.columns.size(); j++) {
      dumpColumnHeader(j, table.columns.get(j));
    }
    // other rows
    for (int i = 0; i < table.rows.size(); i++) {
      for (int j = 0; j < table.columns.size(); j++) {
        dumpCell(i, j, table.rows.get(i).children.get(j));
      }
    }
  }

  private void dumpColumnHeader(int j, TableColumn column) {
    String[] fields = new String[FIELDS.length];
    fields[0] = "-1";
    fields[1] = "" + j;
    fields[2] = serialize(column.relationNameValue.id);
    fields[3] = serialize(column.originalString);
    LanguageInfo info = analyzer.analyze(column.originalString);
    fields[4] = serialize(info.tokens);
    fields[5] = serialize(info.lemmaTokens);
    fields[6] = serialize(info.posTags);
    fields[7] = serialize(info.nerTags);
    fields[8] = serialize(info.nerValues);
    fields[9] = fields[10] = fields[11] = fields[12] = fields[13] = "";
    dump(fields);
  }

  private void dumpCell(int i, int j, TableCell cell) {
    String[] fields = new String[FIELDS.length];
    fields[0] = "" + i;
    fields[1] = "" + j;
    fields[2] = serialize(cell.properties.nameValue.id);
    fields[3] = serialize(cell.properties.originalString);
    LanguageInfo info = analyzer.analyze(cell.properties.originalString);
    fields[4] = serialize(info.tokens);
    fields[5] = serialize(info.lemmaTokens);
    fields[6] = serialize(info.posTags);
    fields[7] = serialize(info.nerTags);
    fields[8] = serialize(info.nerValues);
    fields[9] = serialize(new ListValue(new ArrayList<>(cell.properties.metadata.get(TableTypeSystem.CELL_NUMBER_VALUE))));
    fields[10] = serialize(new ListValue(new ArrayList<>(cell.properties.metadata.get(TableTypeSystem.CELL_DATE_VALUE))));
    fields[11] = serialize(new ListValue(new ArrayList<>(cell.properties.metadata.get(TableTypeSystem.CELL_NUM2_VALUE))));
    ListValue parts = new ListValue(new ArrayList<>(cell.properties.metadata.get(TableTypeSystem.CELL_PART_VALUE)));
    fields[12] = serialize(parts);
    fields[13] = serializeId(parts);
    dump(fields);
  }

}
