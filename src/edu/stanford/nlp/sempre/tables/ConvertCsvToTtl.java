package edu.stanford.nlp.sempre.tables;

import java.io.*;
import java.util.*;
import java.text.*;

import au.com.bytecode.opencsv.CSVReader;
import edu.stanford.nlp.sempre.*;
import fig.basic.*;
import fig.exec.*;
import static fig.basic.LogInfo.*;

/**
Converts a CSV file into a TTL file to be loaded into Virtuoso.
Also dumps a lexicon file that maps strings to Freebase constants.
This allows us to quickly deploy semantic parsers on random CSV data that's not
already in Freebase.

Domain (e.g., paleo): everything will live under fb:<domain>.*
The input is a set of tables.  Each table has:
- A set of column names (each column corresponds to a property)
- A set of row names (each row corresponds to an event).
- A name (this determines the type of the event).

Columns that contain strings are considered entities, and we define a new type
for that based on the column name.  Important: note that different tables
interact by virtue of having the same column name (think of joining two tables
by string matching their column names).  Of course, this is restrictive, but
it's good enough for now.

Example (domain: foo)

  table name: info
  columns: person, age, marital_status, social_security, place_of_birth
  ...

  table name: education
  columns: person, elementary_school, high_school, college
  ...

  Event types: fb:foo.info, fb:foo.education
  Properties: fb:foo.info.person, fb:foo.info.age, ...
  Entity types: fb:foo.person, ...
  Events: fb:foo.info0, fb:foo.info1, ...
  Entities: fb:foo.barack_obama, ...

Each column is a property that could take on several values.  A column could
have many different types (e.g., int, text, entity) depending on how the
values are parsed, but we can't do this with one pass over the data, so we're
punting on this for now.

@author Percy Liang
*/
public class ConvertCsvToTtl implements Runnable {
  @Option(required = true, gloss = "Domain (used to specify all the entities)") public String domain;
  @Option(required = true, gloss = "Input CSV <table name>:<table path> (assume each file has a header)") public List<String> tables;
  @Option(required = true, gloss = "Output schema ttl to this path") public String outSchemaPath;
  @Option(required = true, gloss = "Output ttl to this path") public String outTtlPath;
  @Option(required = true, gloss = "Output lexicon to this path") public String outLexiconPath;
  @Option(gloss = "Only use these columns") public List<String> keepProperties;
  @Option(gloss = "Maximum number of rows to read per file") public int maxRowsPerFile = Integer.MAX_VALUE;

  public static final String ttlPrefix = "@prefix fb: <http://rdf.freebase.com/ns/>.";

  // Names and types of entities
  Map<String, String> id2name = new HashMap<String, String>();
  Map<String, Set<String>> id2types = new HashMap<String, Set<String>>();

  // Used to parse values into dates
  List<SimpleDateFormat> dateFormats = Arrays.asList(
    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH),  // One used by Freebase
    new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH),
    new SimpleDateFormat("MM/dd/yyyy", Locale.ENGLISH));

  String prependDomain(String s) { return "fb:" + domain + "." + s; }
  String makeString(String s) {
    if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\""))
      s = s.substring(1, s.length() - 1);
    s = s.replaceAll("\"", "\\\\\"");  // Quote
    return "\"" + s + "\"@en";
  }
  String lexEntry(String s, String id, Set<String> types) {
    SemType type = types == null ? SemType.anyType : SemType.newUnionSemType(types);
    Map<String, Object> result = new HashMap<String, Object>();
    result.put("lexeme", s);  // Note: this is not actually a lexeme, but the full rawPhrase.
    result.put("formula", id);
    result.put("source", "STRING_MATCH");
    result.put("type", type);
    return Json.writeValueAsStringHard(result);
  }

  String canonicalize(String value) {
    if (Character.isDigit(value.charAt(value.length() - 1))) {
      // Try to convert to integer
      try {
        Integer.parseInt(value);
        return "\"" + value + "\"^^xsd:int";
      } catch (NumberFormatException e) {
      }

      // Try to convert to double
      try {
        Double.parseDouble(value);
        return "\"" + value + "\"^^xsd:double";
      } catch (NumberFormatException e) {
      }

      // Try to convert to date
      for (DateFormat format : dateFormats) {
        try {
          Date date = format.parse(value);
          return "\"" + dateFormats.get(0).format(date) + "\"^^xsd:datetime";
        } catch (ParseException e) {
        }
      }
    }

    // Try to interpret as entity if it is short enough
    if (value.split(" ").length <= 5) {
      String id = value;
      id = id.replaceAll("[^\\w]", "_");  // Replace abnormal characters with _
      id = id.replaceAll("_+", "_");  // Merge consecutive _'s
      id = id.replaceAll("_$", "");
      id = id.toLowerCase();
      if (id.length() == 0) id = "null";
      id = prependDomain(id);
      id2name.put(id, value);
      return id;
    }

    // Just interpret as string
    return makeString(value);
  }

  public static void writeTriple(PrintWriter out, String arg1, String property, String arg2) {
    out.println(arg1 + "\t" + property + "\t" + arg2 + ".");
  }

  static class Column {
    String description;
    String header;
    String property;
    int numInt, numDouble, numDate, numText, numEntity;

    // Return whether we have an entity.
    boolean add(String value) {
      if (value.endsWith("xsd:int"))
        numInt++;
      else if (value.endsWith("xsd:double"))
        numDouble++;
      else if (value.endsWith("xsd:datetime"))
        numDate++;
      else if (value.endsWith("@en"))
        numText++;
      else {
        numEntity++;
        return true;
      }
      return false;
    }

    String getEntityType() { return header; }

    String getType() {
      int[] counts = new int[] {numInt, numDouble, numDate, numText, numEntity};
      int max = ListUtils.max(counts);
      // if (numInt == max) return FreebaseInfo.INT;
      // if (numDouble == max) return FreebaseInfo.FLOAT;
      if (numInt == max) return CanonicalNames.NUMBER;
      if (numDouble == max) return CanonicalNames.NUMBER;
      if (numDate == max) return CanonicalNames.DATE;
      if (numText == max) return CanonicalNames.TEXT;
      return getEntityType();
    }

    @Override public String toString() {
      StringBuilder b = new StringBuilder();
      b.append(header);
      if (numInt > 0) b.append(", int=" + numInt);
      if (numDouble > 0) b.append(", double=" + numDouble);
      if (numDate > 0) b.append(", date=" + numDate);
      if (numText > 0) b.append(", text=" + numText);
      if (numEntity > 0) b.append(", entity=" + numEntity);
      return b.toString();
    }
  }

  public void run() {
    PrintWriter schemaOut = IOUtils.openOutHard(outSchemaPath);
    PrintWriter ttlOut = IOUtils.openOutHard(outTtlPath);
    PrintWriter lexiconOut = IOUtils.openOutHard(outLexiconPath);

    ttlOut.println(ttlPrefix);
    int total = 0;
    for (String pairStr : tables) {
      int num = 0;  // The row number (standards for an event / CSV)
      String[] pair = pairStr.split(":", 2);
      if (pair.length != 2) throw new RuntimeException("Expected <table name>:<file name> pair, but got: " + pair);
      String tableName = pair[0];
      String inPath = pair[1];
      String eventType = prependDomain(tableName);  // e.g., fb:paleo.taxon

      LogInfo.begin_track("Reading %s for events of type %s", inPath, eventType);
      Column[] columns = null;
      try (CSVReader csv = new CSVReader(new FileReader(inPath))) {
        for (String[] row : csv) {
          if (num >= maxRowsPerFile) break;

          // Initialize the columns
          if (columns == null) {
            columns = new Column[row.length];
            for (int i = 0; i < columns.length; i++) {
              Column c = columns[i] = new Column();
              row[i] = row[i].trim();
              c.description = row[i];
              c.header = canonicalize(row[i]);
              if (keepProperties != null && !keepProperties.contains(c.header)) {
                c.header = null;
                continue;
              }
              if (!c.header.startsWith("fb:" + domain))
                throw new RuntimeException("Invalid (internal problem): " + c.header);
              c.property = c.header.replace("fb:" + domain, "fb:" + domain + "." + tableName); // Property
            }
            continue;
          }

          // Read a row (corresponds to an event/CVT)
          String event = prependDomain(tableName + (num++));
          writeTriple(ttlOut, event, "fb:type.object.type", tableName);
          for (int i = 0; i < Math.min(row.length, columns.length); i++) {  // For each column...
            Column c = columns[i];
            if (c.header == null || row[i].equals("")) continue;
            row[i] = canonicalize(row[i]);
            writeTriple(ttlOut, event, c.property, row[i]);  // Write out the assertion

            if (c.add(row[i])) {  // Write out type for entities
              MapUtils.addToSet(id2types, row[i], c.getEntityType());
              writeTriple(ttlOut, row[i], "fb:type.object.type", c.getEntityType());
              writeTriple(ttlOut, row[i], "fb:type.object.type", CanonicalNames.ENTITY);
            }
          }
          if (num % 10000 == 0)
            logs("Read %d rows (events)", num);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      // Write out schema
      writeTriple(schemaOut, eventType, "fb:freebase.type_hints.mediator", "\"true\"^^xsd:boolean");  // event type is a CVT
      for (Column c : columns) {
        if (c.header == null) continue;
        writeTriple(schemaOut, c.property, "fb:type.object.type", "fb:type.property");
        writeTriple(schemaOut, c.property, "fb:type.property.schema", eventType);
        writeTriple(schemaOut, c.property, "fb:type.property.expected_type", c.getType());
        writeTriple(schemaOut, c.property, "fb:type.object.name", makeString(c.description));
        if (c.getType().equals(c.getEntityType())) {
          writeTriple(schemaOut, c.getEntityType(), "fb:type.object.name", makeString(c.description));
          writeTriple(schemaOut, c.getEntityType(), "fb:freebase.type_hints.included_types", CanonicalNames.ENTITY);
        }
        LogInfo.logs("%s", c);
      }
      LogInfo.end_track();
      total += num;
    }

    logs("%d events, %d entities (ones with names)", total, id2name.size());
    for (Map.Entry<String, String> e : id2name.entrySet()) {
      writeTriple(ttlOut, e.getKey(), "fb:type.object.name", makeString(e.getValue()));
      String k = e.getKey();
      String s = e.getValue();
      Set<String> types = id2types.get(k);
      lexiconOut.println(lexEntry(s, k, types));
    }

    schemaOut.close();
    ttlOut.close();
    lexiconOut.close();
  }

  public static void main(String[] args) {
    Execution.run(args, new ConvertCsvToTtl());
  }
}
