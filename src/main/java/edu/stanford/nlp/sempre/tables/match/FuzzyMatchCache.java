package edu.stanford.nlp.sempre.tables.match;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import fig.basic.Pair;

public class FuzzyMatchCache {

  Map<Pair<Integer, Integer>, Collection<Formula>> entries = new HashMap<>();

  public void put(int startIndex, int endIndex, Collection<Formula> formulas) {
    entries.put(new Pair<>(startIndex, endIndex), formulas);
  }

  public void add(int startIndex, int endIndex, Formula formula) {
    Collection<Formula> current = entries.get(new Pair<>(startIndex, endIndex));
    if (current == null) entries.put(new Pair<>(startIndex, endIndex), current = new HashSet<>());
    current.add(formula);
  }

  public void addAll(int startIndex, int endIndex, Collection<Formula> formulas) {
    Collection<Formula> current = entries.get(new Pair<>(startIndex, endIndex));
    if (current == null) entries.put(new Pair<>(startIndex, endIndex), current = new HashSet<>());
    current.addAll(formulas);
  }

  public void clear(int startIndex, int endIndex) {
    entries.remove(new Pair<>(startIndex, endIndex));
  }

  public void removeAll(int startIndex, int endIndex, Collection<Formula> formulas) {
    Collection<Formula> current = entries.get(new Pair<>(startIndex, endIndex));
    if (current == null) return;
    current.removeAll(formulas);
    if (current.isEmpty()) entries.remove(new Pair<>(startIndex, endIndex));
  }

  public Collection<Formula> get(int startIndex, int endIndex) {
    Collection<Formula> answer = entries.get(new Pair<>(startIndex, endIndex));
    return answer == null ? Collections.emptySet() : answer;
  }

}
