package edu.stanford.nlp.sempre.fbalignment.lexicons;

import org.apache.lucene.queryparser.classic.ParseException;

import java.io.IOException;
import java.util.List;

public class Lexicon {

  private EntityLexicon exactMatchEntityLexicon;
  private EntityLexicon inexactMatchEntityLexicon;
  private UnaryLexicon unaryLexicon;
  protected BinaryLexicon binaryLexicon;

  public Lexicon(String entitySearchStrategy) throws IOException {

    if (entitySearchStrategy.equals("exact")) {
      exactMatchEntityLexicon = new EntityLexicon("exact");
    } else if (entitySearchStrategy.equals("inexact")) {
      inexactMatchEntityLexicon = new EntityLexicon("inexact");
    } else {
      exactMatchEntityLexicon = new EntityLexicon("exact");
      inexactMatchEntityLexicon = new EntityLexicon("inexact");
    }
    unaryLexicon = new UnaryLexicon();
    binaryLexicon = new BinaryLexicon();
  }

  public List<? extends LexicalEntry> lookupUnaryPredicates(String phrase) throws IOException {
    return unaryLexicon.lookupEntries(phrase);
  }

  public List<? extends LexicalEntry> lookupBinaryPredicates(String phrase) throws IOException  {
    return binaryLexicon.lookupEntries(phrase);
  }

  public List<? extends LexicalEntry> lookupExactMatchEntities(String phrase) throws IOException, ParseException {
    return exactMatchEntityLexicon.lookupEntries(phrase);
  }
  public List<? extends LexicalEntry> lookupInexactMatchEntities(String phrase) throws ParseException, IOException {
    return inexactMatchEntityLexicon.lookupEntries(phrase);
  }
}
