package edu.stanford.nlp.sempre.tables.alignment;

import java.util.*;

class BitextDataGroup {
  public final int count;
  public final List<String> words;
  public final List<BitextDatum> groupData = new ArrayList<>();

  public BitextDataGroup(int count, List<String> words) {
    this.count = count;
    this.words = words;
  }

  public void add(BitextDatum datum) {
    groupData.add(datum);
  }
}
