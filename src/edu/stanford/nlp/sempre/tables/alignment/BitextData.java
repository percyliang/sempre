package edu.stanford.nlp.sempre.tables.alignment;

import java.util.*;

public class BitextData {

  private final Map<String, BitextDataGroup> idToDataGroup;
  private final Set<String> allReadWords, allReadPreds;

  public BitextData() {
    idToDataGroup = new HashMap<>();
    allReadWords = new HashSet<>();
    allReadPreds = new HashSet<>();
  }

  public Collection<BitextDataGroup> dataGroups() {
    return idToDataGroup.values();
  }

  public Set<String> allWords() {
    return new HashSet<>(allReadWords);
  }

  public Set<String> allPreds() {
    return new HashSet<>(allReadPreds);
  }

  public Set<String> allSources(boolean swap) {
    return new HashSet<>(swap ? allReadPreds : allReadWords);
  }

  public Set<String> allTargets(boolean swap) {
    return new HashSet<>(swap ? allReadWords : allReadPreds);
  }

  public void add(String id, int count, List<String> words, List<String> preds) {
    BitextDataGroup group = idToDataGroup.get(id);
    if (group == null) {
      idToDataGroup.put(id, group = new BitextDataGroup(count, words));
      allReadWords.addAll(words);
    }
    allReadPreds.addAll(preds);
    group.add(new BitextDatum(words, preds));
  }
}
