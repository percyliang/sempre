package edu.stanford.nlp.sempre.roboy.index;

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

import java.io.InputStream;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.io.FileReader;
import java.lang.reflect.Type;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.reflect.TypeToken;

public class DbEntityIndexer {

  private final IndexWriter indexer;
  private String nameFile;

  public Properties prop = new Properties();
  public InputStream input;
  public Gson gson = new Gson();
  public static Map<String, String[]> index = new HashMap();

  public DbEntityIndexer(String namefile, String outputDir, String indexingStrategy) throws IOException {

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

    input = new FileInputStream("config.properties");
    prop.load(this.input);
    JsonReader json_reader = new JsonReader(new FileReader(prop.getProperty("DATABASE")));
    Type type = new TypeToken<Map<String, String[]>>(){}.getType();
    index = gson.fromJson(json_reader, type);

    String[] items = index.get("ignore");

    while ((line = reader.readLine()) != null) {

      String[] tokens = line.split("\t");

      String mid = tokens[0];
      String id = tokens[1];
      //if (id.startsWith("fb:user.") || id.startsWith("fb:base."))

      if (Arrays.stream(items).parallel().anyMatch(id::contains))
        continue;
      String popularity = tokens[2];
      String text = tokens[3].toLowerCase();

      // add to index
      Document doc = new Document();
      doc.add(new StringField(DbIndexField.MID.fieldName(), mid, Field.Store.YES));
      doc.add(new StringField(DbIndexField.ID.fieldName(), id, Field.Store.YES));
      doc.add(new StoredField(DbIndexField.POPULARITY.fieldName(), popularity));
      doc.add(new TextField(DbIndexField.TEXT.fieldName(), text, Field.Store.YES));
      if (tokens.length > 4) {
        doc.add(new StoredField(DbIndexField.TYPES.fieldName(), tokens[4]));
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
    DbEntityIndexer fbni = new DbEntityIndexer(args[0], args[1], args[2]);
    fbni.index();
  }
}
