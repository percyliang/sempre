package edu.stanford.nlp.sempre;

/**
 * Parser that can mutate after each training iteration.
 *
 * @author ppasupat
 */
public interface MutatingParser {

  // This will be called after each training iteration.
  public void mutate(int iter, int numIters, String group);
}
