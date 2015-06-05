package edu.stanford.nlp.sempre.tables.alignment;

import java.util.List;

class BitextDatum {
  public final List<String> words, preds;

  public BitextDatum(List<String> words, List<String> preds) {
    this.words = words;
    this.preds = preds;
  }

  public List<String> getSource(boolean swap) {
    return swap ? preds : words;
  }

  public List<String> getTarget(boolean swap) {
    return swap ? words : preds;
  }

}
