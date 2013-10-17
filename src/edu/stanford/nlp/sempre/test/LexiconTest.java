package edu.stanford.nlp.sempre.test;

import edu.stanford.nlp.sempre.fbalignment.lexicons.BinaryLexicon;
import edu.stanford.nlp.sempre.fbalignment.lexicons.EntrySource;
import edu.stanford.nlp.sempre.fbalignment.lexicons.Lexicon;
import edu.stanford.nlp.sempre.fbalignment.lexicons.LexicalEntry.BinaryLexicalEntry;
import edu.stanford.nlp.sempre.fbalignment.lexicons.LexicalEntry.UnaryLexicalEntry;
import edu.stanford.nlp.sempre.fbalignment.lexicons.UnaryLexicon;
import fig.basic.LogInfo;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;

import static org.testng.AssertJUnit.assertEquals;

public class LexiconTest {

  @Test(groups = "emnlp2013")
  public void unary() throws IOException {
    UnaryLexicon.opts.unaryLexiconFilePath =
      "lib/fb_data/" + Lexicon.opts.fbData + "/unaryInfoStringAndAlignment.txt";
    UnaryLexicon unary = new UnaryLexicon();
    boolean existsAlignment = false, existsStringMatch=false;
    double popularity=0.0;
    double intersection=0.0;
    
    List<UnaryLexicalEntry> entries = unary.lookupEntries("continent");
    LogInfo.logs("Num of entries: %s",entries.size());
    for(UnaryLexicalEntry entry: entries) {
      if(entry.formula.toString().equals("(fb:type.object.type fb:location.continent)")) {
        if(entry.source==EntrySource.ALIGNMENT) {
          existsAlignment=true;
          intersection = entry.alignmentScores.get(UnaryLexicon.INTERSECTION);
        }
        else if(entry.source==EntrySource.STRING_MATCH) {
          existsStringMatch=true;
          popularity = entry.popularity;
        }
      }
    }
    assertEquals(true,existsAlignment);
    assertEquals(true,existsStringMatch);
    assertEquals(7.0, popularity, 0.0001);
    assertEquals(5.0, intersection, 0.0001);
    
    existsAlignment = false;
    existsStringMatch=false;
    popularity=0.0;
    intersection=0.0;
    entries = unary.lookupEntries("lawyer");
    for(UnaryLexicalEntry entry: entries) {
      if(entry.formula.toString().equals("(fb:people.person.profession fb:en.attorney)")) {
        if(entry.source==EntrySource.ALIGNMENT) {
          existsAlignment=true;
          intersection = entry.alignmentScores.get(UnaryLexicon.INTERSECTION);
        }
        else if(entry.source==EntrySource.STRING_MATCH) {
          existsStringMatch=true;
          popularity = entry.popularity;
        }       
      }
    }
    assertEquals(true,existsAlignment);
    assertEquals(true,existsStringMatch);
    assertEquals(12282.0, popularity, 0.0001);  // Based on 93.exec (full Freebase)
    assertEquals(26.0, intersection, 0.0001);
  }
  
  @Test(groups = "emnlp2013")
  public void binary() throws IOException {
    BinaryLexicon.opts.binaryLexiconFilesPath.add(
        "lib/fb_data/" + Lexicon.opts.fbData + "/binaryInfoStringAndAlignment.txt");
    BinaryLexicon.opts.keyToSortBy = BinaryLexicon.INTERSECTION;

    BinaryLexicon lexicon = new BinaryLexicon();
    List<BinaryLexicalEntry> entries = lexicon.lookupEntries("bear in");
    BinaryLexicalEntry top = entries.get(0);
    assertEquals("people born here", top.fbDescriptions.iterator().next());
    assertEquals("!fb:location.location.people_born_here", top.formula.toString());
    assertEquals("ALIGNMENT", top.source.toString());
    assertEquals(759773.0,top.popularity,0.00001);  // Based on 93.exec (full Freebase)
    assertEquals("fb:people.person",top.expectedType1);
    assertEquals("fb:location.location",top.expectedType2);
    assertEquals(16184.0,top.alignmentScores.get(BinaryLexicon.FB_TYPED),0.0001);
    assertEquals(13856.0,top.alignmentScores.get(BinaryLexicon.INTERSECTION),0.0001);
    assertEquals(15765.0, top.alignmentScores.get(BinaryLexicon.NL_TYPED),0.0001);    
  }
}
