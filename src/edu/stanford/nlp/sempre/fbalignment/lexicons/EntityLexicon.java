package edu.stanford.nlp.sempre.fbalignment.lexicons;

import edu.stanford.nlp.sempre.Formula;
import edu.stanford.nlp.sempre.fbalignment.index.FbEntitySearcher;
import edu.stanford.nlp.sempre.fbalignment.index.FbIndexField;
import edu.stanford.nlp.sempre.fbalignment.lexicons.LexicalEntry.EntityLexicalEntry;
import edu.stanford.nlp.sempre.fbalignment.utils.FileUtils;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.ArrayUtils;
import edu.stanford.nlp.util.StringUtils;
import fig.basic.Option;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;

import java.io.IOException;
import java.util.*;

public class EntityLexicon {

  public static class Options {
    @Option(gloss = "Number of results return by the lexicon")
    public int maxEntries = 1000;
    @Option(gloss = "Number of documents queried from Lucene")
    public int numOfDocs = 10000;
    @Option(gloss = "Path to the exact match lucene index directory")
    public String exactMatchIndex;
    @Option(gloss = "Path to the inexact match lucene index directory")
    public String inexactMatchIndex = "lib/lucene/4.4/inexact";
  }

  public static Options opts = new Options();

  public static final int MAX_EDIT_DISTANCE = 5;
  FbEntitySearcher searcher;
  //private int numOfResults = 20;

  public EntityLexicon(String luceneSearchStrategy) throws IOException {
    if (luceneSearchStrategy.equals("exact"))
      searcher = new FbEntitySearcher(opts.exactMatchIndex, opts.numOfDocs, luceneSearchStrategy);
    else
      searcher = new FbEntitySearcher(opts.inexactMatchIndex, opts.numOfDocs, luceneSearchStrategy);
  }

  public List<EntityLexicalEntry> lookupEntries(String textDesc) throws ParseException, IOException {

    List<EntityLexicalEntry> res = new LinkedList<EntityLexicalEntry>();
    textDesc = textDesc.replaceAll("\\?", "\\\\?").toLowerCase();
    List<Document> docs = searcher.searchDocs(textDesc);
    for (Document doc : docs) {

      Formula formula = Formula.fromString(doc.get(FbIndexField.ID.fieldName()));
      String[] fbDescriptions = new String[]{doc.get(FbIndexField.TEXT.fieldName())};
      String typesDesc = doc.get(FbIndexField.TYPES.fieldName());

      Set<String> types = new HashSet<String>();
      if (typesDesc != null) {
        String[] tokens = typesDesc.split(",");
        for (String token : tokens)
          types.add(token);
      }

      double popularity = Double.parseDouble(doc.get(FbIndexField.POPULARITY.fieldName()));
      int distance = editDistance(textDesc.toLowerCase(), fbDescriptions[0].toLowerCase());
      Counter<String> tokenEditDistanceFeatures = TokenLevelMatchFeatures.extractFeatures(textDesc, fbDescriptions[0]);

      if ((popularity > 0 || distance == 0) && TokenLevelMatchFeatures.diffSetSize(textDesc, fbDescriptions[0]) < 4) {

        res.add(new EntityLexicalEntry(textDesc, textDesc, ArrayUtils.asSet(fbDescriptions), formula, EntrySource.LUCENE, popularity, distance, types, tokenEditDistanceFeatures));
      }
    }
    Collections.sort(res, new LexicalEntryComparator());
    return res.subList(0, Math.min(res.size(), opts.maxEntries));
  }

  private int editDistance(String query, String name) {

    String[] queryTokens = FileUtils.omitPunct(query).split("\\s+");
    String[] nameTokens = FileUtils.omitPunct(name).split("\\s+");

    StringBuilder querySb = new StringBuilder();
    for (int i = 0; i < queryTokens.length; ++i)
      querySb.append(queryTokens[i] + " ");

    StringBuilder nameSb = new StringBuilder();
    for (int i = 0; i < nameTokens.length; ++i)
      nameSb.append(nameTokens[i] + " ");

    return StringUtils.editDistance(querySb.toString().trim(), nameSb.toString().trim());
  }
  
  public static class LexicalEntryComparator implements Comparator<LexicalEntry> {

    @Override
    public int compare(LexicalEntry arg0, LexicalEntry arg1) {

      if (arg0.popularity > arg1.popularity)
        return -1;
      if (arg0.popularity < arg1.popularity)
        return 1;
      if (arg0.distance < arg1.distance)
        return -1;
      if (arg0.distance > arg1.distance)
        return 1;
      return 0;


    }


  }

}


