package edu.stanford.nlp.sempre.fbalignment.lexicons;

import edu.stanford.nlp.sempre.StringCache;
import edu.stanford.nlp.sempre.StringCacheUtils;
import edu.stanford.nlp.sempre.Formula;
import edu.stanford.nlp.sempre.fbalignment.index.FbEntitySearcher;
import edu.stanford.nlp.sempre.fbalignment.index.FbIndexField;
import edu.stanford.nlp.sempre.fbalignment.lexicons.LexicalEntry.EntityLexicalEntry;
import edu.stanford.nlp.sempre.fbalignment.utils.FileUtils;
import edu.stanford.nlp.sempre.StringCache;
import edu.stanford.nlp.sempre.FreebaseSearch;
import edu.stanford.nlp.sempre.ValueFormula;
import edu.stanford.nlp.sempre.NameValue;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.ArrayUtils;
import edu.stanford.nlp.util.StringUtils;
import fig.basic.Option;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;

import java.io.IOException;
import java.util.*;

public class EntityLexicon {
  public enum SearchStrategy { exact, inexact, fbsearch };

  public static class Options {
    @Option(gloss = "Number of results return by the lexicon")
    public int maxEntries = 1000;
    @Option(gloss = "Number of documents queried from Lucene")
    public int numOfDocs = 10000;
    @Option(gloss = "Path to the exact match lucene index directory")
    public String exactMatchIndex;
    @Option(gloss = "Path to the inexact match lucene index directory")
    public String inexactMatchIndex = "lib/lucene/4.4/inexact";
    @Option(gloss = "Cache path to the types path")
    public String entityTypesPath = "localhost:4000:/u/nlp/data/semparse/scr/freebase/freebase-rdf-2013-06-09-00-00.canonicalized.en-types";
    @Option(gloss = "Cache path to the mid-to-id path")
    public String mid2idPath = "localhost:4000:/u/nlp/data/semparse/scr/freebase/freebase-rdf-2013-06-09-00-00.canonical-id-map";
  }

  public static Options opts = new Options();

  FbEntitySearcher exactSearcher;  // Lucene
  FbEntitySearcher inexactSearcher;  // Lucene

  FreebaseSearch freebaseSearch;  // Google's API
  StringCache mid2idCache;  // Google's API spits back mids, which we need to convert to ids
  StringCache entityTypesCache;  // Given those ids, we retrieve the set of types

  public List<EntityLexicalEntry> lookupEntries(String query, SearchStrategy strategy) throws ParseException, IOException {
    switch (strategy) {
      case exact:
        if (exactSearcher == null) exactSearcher = new FbEntitySearcher(opts.exactMatchIndex, opts.numOfDocs, "exact");
        return lookupEntries(exactSearcher, query);
      case inexact:
        if (inexactSearcher == null) inexactSearcher = new FbEntitySearcher(opts.inexactMatchIndex, opts.numOfDocs, "inexact");
        return lookupEntries(inexactSearcher, query);
      case fbsearch:
        if (freebaseSearch == null) freebaseSearch = new FreebaseSearch();
        if (mid2idCache == null) mid2idCache = StringCacheUtils.create(opts.mid2idPath);
        if (entityTypesCache == null) entityTypesCache = StringCacheUtils.create(opts.entityTypesPath);
        return lookupFreebaseSearchEntities(query);
      default:
        throw new RuntimeException("Unknown entity search strategy: " + strategy);
    }
  }

  public List<EntityLexicalEntry> lookupFreebaseSearchEntities(String query) {
    FreebaseSearch.ServerResponse response = freebaseSearch.lookup(query);
    List<EntityLexicalEntry> entities = new ArrayList<EntityLexicalEntry>();
    for (FreebaseSearch.Entry e : response.entries) {
      // Note: e.id might not be the same one we're using (e.g., fb:en.john_f_kennedy_airport versus fb:en.john_f_kennedy_international_airport),
      // so get the one from our canonical mid2idCache
      String id = mid2idCache.get(e.mid);
      if (id == null) continue;  // Skip if no ID (probably not worth referencing)
      int distance = editDistance(query.toLowerCase(), e.name.toLowerCase());  // Is this actually useful?
      Counter<String> tokenEditDistanceFeatures = TokenLevelMatchFeatures.extractFeatures(query, e.name);
      Set<String> types = new HashSet<String>();
      String typesStr = entityTypesCache.get(id);
      if (typesStr != null) {
        for (String type : typesStr.split(","))
          types.add(type);
      }
      entities.add(new EntityLexicalEntry(query, query, Collections.singleton(e.name), new ValueFormula<NameValue>(new NameValue(id, e.name)), EntrySource.FBSEARCH, e.score, distance, types, tokenEditDistanceFeatures));
    }
    return entities;
  }

  public List<EntityLexicalEntry> lookupEntries(FbEntitySearcher searcher, String textDesc) throws ParseException, IOException {

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


