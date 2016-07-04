package edu.stanford.nlp.sempre.tables.serialize;

import java.io.*;

import au.com.bytecode.opencsv.CSVWriter;
import edu.stanford.nlp.sempre.tables.*;
import fig.basic.LogInfo;

/**
 * Write a table in either CSV or TSV format.
 * All IOExceptions are thrown as RuntimeException.
 *
 * For CSV, this class is just a wrapper for OpenCSV.
 * Escape sequences for CSV:
 * - \\         => \
 * - \" or ""   => "
 * Each cell can be quoted inside "...". Embed newlines must be quoted.
 *
 * For TSV, each line must represent one table row (no embed newlines).
 * Escape sequences for TSV (custom):
 * - \n   => [newline]
 * - \\   => \
 * - \p   => |
 *
 * @author ppasupat
 */
public class TableWriter {

  public final TableKnowledgeGraph graph;

  public TableWriter(TableKnowledgeGraph graph) {
    this.graph = graph;
  }

  /**
   * If out is null, log using LogInfo. Otherwise, print line to out.
   */
  private void write(PrintWriter out, String stuff) {
    if (out == null)
      LogInfo.logs("%s", stuff);
    else
      out.println(stuff);
  }

  // ============================================================
  // CSV
  // ============================================================

  public void writeCSV() {
    writeCSVActual(null);
  }

  public void writeCSV(PrintWriter out) {
    writeCSVActual(out);
  }

  public void writeCSV(String filename) {
    try (PrintWriter out = new PrintWriter(filename)) {
      writeCSVActual(out);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void writeCSVActual(PrintWriter out) {
    try (CSVWriter writer = new CSVWriter(out)) {
      String[] record = new String[graph.numColumns()];
      // Print header
      for (int j = 0; j < record.length; j++)
        record[j] = graph.getColumn(j).originalString;
      writer.writeNext(record);
      // Print other rows
      for (int i = 0; i < graph.numRows(); i++) {
        for (int j = 0; j < record.length; j++)
          record[j] = graph.getCell(i, j).properties.originalString;
        writer.writeNext(record);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // ============================================================
  // TSV
  // ============================================================

  public void writeTSV() {
    writeTSVActual(null);
  }

  public void writeTSV(PrintWriter out) {
    writeTSVActual(out);
  }

  public void writeTSV(String filename) {
    try (PrintWriter out = new PrintWriter(filename)) {
      writeTSVActual(out);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void writeTSVActual(PrintWriter out) {
    String[] record = new String[graph.numColumns()];
    // Print header
    for (int j = 0; j < record.length; j++)
      record[j] = StringNormalizationUtils.escapeTSV(graph.getColumn(j).originalString);
    write(out, String.join("\t", record));
    // Print other rows
    for (int i = 0; i < graph.numRows(); i++) {
      for (int j = 0; j < record.length; j++)
        record[j] = StringNormalizationUtils.escapeTSV(graph.getCell(i, j).properties.originalString);
      write(out, String.join("\t", record));
    }
  }

  // ============================================================
  // Human Readable Format
  // ============================================================

  public void log() {
    writeHumanReadableActual(null);
  }

  public void writeHumanReadable() {
    writeHumanReadableActual(null);
  }

  public void writeHumanReadable(PrintWriter out) {
    writeHumanReadableActual(out);
  }

  public void writeHumanReadable(String filename) {
    try (PrintWriter out = new PrintWriter(filename)) {
      writeHumanReadableActual(out);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void writeHumanReadableActual(PrintWriter out) {
    int LIMIT = 20, numColumns = graph.numColumns();
    // Measure widths
    int[] widths = new int[numColumns];
    for (int i = 0; i < numColumns; i++)
      widths[i] = Math.max(widths[i], graph.getColumn(i).originalString.length());
    for (TableRow row : graph.rows)
      for (int j = 0; j < numColumns; j++)
        widths[j] = Math.max(widths[j], row.children.get(j).properties.originalString.length());
    for (int i = 0; i < numColumns; i++)
      widths[i] = Math.max(1, Math.min(widths[i], LIMIT));
    // Print!
    {
      StringBuilder sb = new StringBuilder("|");
      for (int i = 0; i < numColumns; i++)
        sb.append(String.format(" %-" + widths[i] + "s", cutoff(graph.columns.get(i).originalString, LIMIT))).append(" |");
      write(out, sb.toString());
    }
    for (TableRow row : graph.rows) {
      StringBuilder sb = new StringBuilder("|");
      for (int i = 0; i < numColumns; i++)
        sb.append(String.format(" %-" + widths[i] + "s", cutoff(row.children.get(i).properties.originalString, LIMIT))).append(" |");
      write(out, sb.toString());
    }
  }

  private String cutoff(String x, int limit) {
    x = x.replace('\n', ' ');
    if (x.length() < limit) return x;
    return x.substring(0, limit - 3) + "...";
  }
}
