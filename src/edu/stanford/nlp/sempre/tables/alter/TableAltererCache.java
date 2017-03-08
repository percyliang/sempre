package edu.stanford.nlp.sempre.tables.alter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.*;

import edu.stanford.nlp.sempre.tables.StringNormalizationUtils;
import edu.stanford.nlp.sempre.tables.TableKnowledgeGraph;
import edu.stanford.nlp.sempre.tables.serialize.TableWriter;
import fig.basic.IOUtils;
import fig.basic.LogInfo;
import fig.basic.Option;

public class TableAltererCache {
  public static class Options {
    @Option(gloss = "central path for saving altered tables")
    public String baseAlteredTablesDir = null;
    @Option(gloss = "path for altered table concat file (to reduce file server load)")
    public String alteredTablesConcatCache = null;
  }
  public static Options opts = new Options();

  private BufferedReader concatCache = null;

  public TableAltererCache() {
    if (opts.alteredTablesConcatCache != null) {
      if (opts.baseAlteredTablesDir != null)
        throw new RuntimeException("Cannot specify both baseAlteredTablesDir and alteredTablesConcatCache");
      concatCache = IOUtils.openInHard(opts.alteredTablesConcatCache);
    }
  }

  public boolean existsSaveDir(String id) {
    return opts.alteredTablesConcatCache != null
        || (opts.baseAlteredTablesDir != null && new File(opts.baseAlteredTablesDir, id).isDirectory());
  }

  public TableKnowledgeGraph load(String id, int alteredTableIndex) {
    return load(id, "" + alteredTableIndex);
  }

  // Load table from |baseAlteredTablesDir|/nt-??/??.tsv
  // or the next chunk of |alteredTablesConcatCache|
  public TableKnowledgeGraph load(String id, String alteredTableIndex) {
    if (opts.baseAlteredTablesDir == null) {
      if (concatCache == null) return null;
      try {
        String line = concatCache.readLine();
        String[] metadata = line.split("\t");
        if (metadata.length != 3 || !id.equals(metadata[0]) || !alteredTableIndex.equals(metadata[1]))
          throw new RuntimeException("Incorrect metadata. Expected " + id + " " + alteredTableIndex + " ___; found " + line);
        int numLines = Integer.parseInt(metadata[2]);
        if (BatchTableAlterer.opts.verbose >= 1)
          LogInfo.logs("Reading %d lines from %s", numLines, opts.alteredTablesConcatCache);
        List<String[]> data = new ArrayList<>();
        for (int i = 0; i < numLines; i++) {
          line = concatCache.readLine();
          String[] fields = line.split("\t", -1);     // Include trailing spaces
          for (int j = 0; j < fields.length; j++)
            fields[j] = StringNormalizationUtils.unescapeTSV(fields[j]);
          data.add(fields);
        }
        return new TableKnowledgeGraph(id + "/" + alteredTableIndex + ".tsv", data);
      } catch (IOException e) {
        e.printStackTrace();
        throw new RuntimeException("Error reading " + opts.alteredTablesConcatCache);
      }
    } else {
      File tablePath = new File(new File(opts.baseAlteredTablesDir, id), alteredTableIndex + ".tsv");
      if (!tablePath.exists()) return null;
      if (BatchTableAlterer.opts.verbose >= 1)
        LogInfo.logs("Reading from %s", tablePath.getPath());
      try {
        return TableKnowledgeGraph.fromRootedFilename(tablePath.getPath());
      } catch (Exception e) {
        LogInfo.warnings("Error reading %s: %s", tablePath.getPath(), e);
        return null;
      }
    }
  }

  public void dump(TableKnowledgeGraph graph, String id, int alteredTableIndex) {
    dump(graph, id, "" + alteredTableIndex);
  }

  // Dump table to |baseAlteredTablesDir|/nt-??/??.tsv
  public void dump(TableKnowledgeGraph graph, String id, String alteredTableIndex) {
    if (opts.baseAlteredTablesDir == null)
      throw new RuntimeException("cannot dump if baseAlteredTablesDir = null");
    File outDir = new File(opts.baseAlteredTablesDir, id);
    outDir.mkdirs();
    new TableWriter(graph).writeTSV(new File(outDir, alteredTableIndex + ".tsv").getPath());
  }

  // Dump tables to |baseAlteredTablesDir|/nt-??/??.tsv
  public void dump(List<TableKnowledgeGraph> graphs, String id) {
    for (int i = 0; i < graphs.size(); i++) {
      dump(graphs.get(i), id, i);
    }
  }


}
