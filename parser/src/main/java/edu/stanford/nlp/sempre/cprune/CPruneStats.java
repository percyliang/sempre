package edu.stanford.nlp.sempre.cprune;

/**
 * Stores various statistic.
 */
public class CPruneStats {
  public String iter;
  public int totalExplore = 0;
  public int successfulExplore = 0;
  public int totalExploit = 0;
  public int successfulExploit = 0;

  public void reset(String iter) {
    this.iter = iter;
    this.totalExplore = 0;
    this.successfulExplore = 0;
    this.totalExploit = 0;
    this.successfulExploit = 0;
  }
}
