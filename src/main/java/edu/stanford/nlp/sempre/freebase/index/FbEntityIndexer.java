package edu.stanford.nlp.sempre.freebase.index;

import edu.stanford.nlp.io.IOUtils;
import fig.basic.LogInfo;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.lucene.util.Version;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

public class FbEntityIndexer {

  private final IndexWriter indexer;
  private String nameFile;

  public FbEntityIndexer(String namefile, String outputDir, String indexingStrategy) throws IOException {

    if (!indexingStrategy.equals("exact") && !indexingStrategy.equals("inexact"))
      throw new RuntimeException("Bad indexing strategy: " + indexingStrategy);

    IndexWriterConfig config =  new IndexWriterConfig(Version.LUCENE_44 , indexingStrategy.equals("exact") ? new KeywordAnalyzer() : new StandardAnalyzer(Version.LUCENE_44));
    config.setOpenMode(OpenMode.CREATE);
    config.setRAMBufferSizeMB(256.0);
    indexer = new IndexWriter(new SimpleFSDirectory(new File(outputDir)), config);

    this.nameFile = namefile;
  }

  /**
   * Index the datadump file
   *
   * @throws IOException
   */
  public void index() throws IOException {

    LogInfo.begin_track("Indexing");
    BufferedReader reader = IOUtils.getBufferedFileReader(nameFile);
    String line;
    int indexed = 0;
    while ((line = reader.readLine()) != null) {

      String[] tokens = line.split("\t");

      String mid = tokens[0];
      String id = tokens[1];
      if (id.startsWith("fb:user.") || id.startsWith("fb:base."))
        continue;
      String popularity = tokens[2];
      String text = tokens[3].toLowerCase();

      // add to index
      Document doc = new Document();
      doc.add(new StringField(FbIndexField.MID.fieldName(), mid, Field.Store.YES));
      doc.add(new StringField(FbIndexField.ID.fieldName(), id, Field.Store.YES));
      doc.add(new StoredField(FbIndexField.POPULARITY.fieldName(), popularity));
      doc.add(new TextField(FbIndexField.TEXT.fieldName(), text, Field.Store.YES));
      if (tokens.length > 4) {
        doc.add(new StoredField(FbIndexField.TYPES.fieldName(), tokens[4]));
      }
      indexer.addDocument(doc);
      indexed++;

      if (indexed % 1000000 == 0) {
        LogInfo.log("Number of lines: " + indexed);
      }
    }
    reader.close();
    LogInfo.log("Indexed lines: " + indexed);

    indexer.close();
    LogInfo.log("Done");
    LogInfo.end_track("Indexing");
  }

  public static void main(String[] args) throws IOException {
    FbEntityIndexer fbni = new FbEntityIndexer(args[0], args[1], args[2]);
    fbni.index();
  }
}
