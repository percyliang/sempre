package edu.stanford.nlp.sempre.test;

import edu.stanford.nlp.sempre.Stemmer;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;

public class StemmerTest {
  @Test public void simpleStem() {
    Stemmer stemmer = new Stemmer();
    assertEquals("box", stemmer.stem("boxes"));
    assertEquals("creat", stemmer.stem("created"));
    assertEquals("citi", stemmer.stem("cities"));
  }
}
