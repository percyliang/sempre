package edu.stanford.nlp.sempre.freebase;

import org.tartarus.snowball.ext.PorterStemmer;

/**
 * Wrapper for the default stemmer used by this project.
 */
public class Stemmer {
  public String stem(String input) {
    PorterStemmer state = new PorterStemmer();
    state.setCurrent(input);
    state.stem();
    return state.getCurrent();
  }
}
