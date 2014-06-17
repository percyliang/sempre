package edu.stanford.nlp.sempre.fbalignment.lexicons;

import edu.stanford.nlp.sempre.StringCache;
import edu.stanford.nlp.sempre.StringCacheUtils;
import edu.stanford.nlp.sempre.fbalignment.lexicons.LexicalEntry.LexicalEntrySerializer;
import fig.basic.LispTree;
import fig.basic.LogInfo;
import fig.basic.Option;
import org.apache.lucene.queryparser.classic.ParseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Lexicon {
  public static class Options {
    @Option(gloss = "The path for the cache") 
    public String cachePath;
    @Option(gloss = "Search strategies for entities: exact, inexact, fbsearch")
    public EntityLexicon.SearchStrategy entitySearchStrategy = EntityLexicon.SearchStrategy.inexact;
  }
  public static Options opts = new Options();

  private static Lexicon lexicon;
  public static Lexicon getSingleton() throws IOException {
    if(lexicon==null)
      lexicon=new Lexicon();
    return lexicon;
  }

  public StringCache cache;

  private EntityLexicon entityLexicon;
  private UnaryLexicon unaryLexicon;
  protected BinaryLexicon binaryLexicon;

  private Lexicon() throws IOException {
    LogInfo.begin_track("Lexicon()");
    entityLexicon = new EntityLexicon();
    unaryLexicon = new UnaryLexicon();
    binaryLexicon = new BinaryLexicon();
    LogInfo.end_track();

    if (opts.cachePath != null)
      cache = StringCacheUtils.create(opts.cachePath);
  }

  public List<? extends LexicalEntry> lookupUnaryPredicates(String query) throws IOException {
    return unaryLexicon.lookupEntries(query);
  }

  public List<? extends LexicalEntry> lookupBinaryPredicates(String query) throws IOException  {
    return binaryLexicon.lookupEntries(query);
  }

  public List<? extends LexicalEntry> lookupEntities(String query, EntityLexicon.SearchStrategy strategy) throws IOException, ParseException {
    List<? extends LexicalEntry> entries = getCache("entity", query);
    if (entries == null)
      putCache("entity", query, entries = entityLexicon.lookupEntries(query, strategy));
    return entries;
  }

  private List<LexicalEntry> getCache(String mode, String query) {
    if (cache == null) return null;
    String key = mode + ":" + query;
    String response;
    synchronized (cache) {
      response = cache.get(key);  
    }
    if (response == null) return null;
    LispTree tree = LispTree.proto.parseFromString(response);
    List<LexicalEntry> entries = new ArrayList<LexicalEntry>();
    for (int i = 0; i < tree.children.size(); i++)
      entries.add(LexicalEntrySerializer.entryFromLispTree(tree.child(i)));
    return entries;
  }

  private void putCache(String mode, String query, List<? extends LexicalEntry> entries) {
    if (cache == null) return;
    String key = mode + ":" + query;
    LispTree result = LispTree.proto.newList();
    for (LexicalEntry entry : entries)
      result.addChild(LexicalEntrySerializer.entryToLispTree(entry));
    synchronized (cache) {
      cache.put(key, result.toString());  
    }    
  }
}
