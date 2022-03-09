package edu.stanford.nlp.sempre.tables.alter;

import java.util.*;

public class Subset {
  public final String id;
  public final List<Integer> indices;
  public final double score;

  public Subset(String id, List<Integer> indices, double score) {
    this.id = id;
    this.indices = indices;
    this.score = score;
  }

  // Subset {0, 1, 2, ..., k}
  public Subset(String id, int k, double score) {
    this.id = id;
    this.indices = new ArrayList<>(k + 1);
    for (int i = 0; i <= k; i++)
      this.indices.add(i);
    this.score = score;
  }

  // NULL subset with very negative score
  public Subset(String id) {
    this.id = id;
    this.indices = new ArrayList<>();
    this.score = Double.NEGATIVE_INFINITY;
  }

  // Format: ID <tab> score <tab> space-separated tables
  public static Subset fromString(String line) {
    String[] tokens = line.trim().split("\t");
    if (tokens.length != 3)
      throw new RuntimeException("Expected 3 fields; got " + tokens.length);
    String[] indicesString = tokens[2].split(" ");
    List<Integer> indices = new ArrayList<>(indicesString.length);
    for (String x : indicesString)
      indices.add(Integer.parseInt(x));
    return new Subset(tokens[0], indices, Double.parseDouble(tokens[1]));
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder().append(id)
        .append("\t").append(score).append("\t");
    for (int graphIndex : indices) {
      sb.append(graphIndex).append(" ");
    }
    return sb.toString().trim();
  }

  public static boolean areDisjoint(Collection<Integer> x1, Collection<Integer> x2) {
    for (int x : x1)
      if (x2.contains(x)) return false;
    return true;
  }

  /**
   * Iterate all subsets of size k of {1,...,n} in lexicographic order.
   */
  public static class SubsetSizeKIterator implements Iterator<List<Integer>> {

    List<Integer> next = new ArrayList<>(), last = new ArrayList<>();

    public SubsetSizeKIterator(int numAlteredTables, int numRetainedTables) {
      for (int i = 1; i <= numRetainedTables; i++) {
        next.add(i);
        last.add(numAlteredTables - numRetainedTables + i);
      }
    }

    @Override
    public boolean hasNext() {
      return next != null;
    }

    @Override
    public List<Integer> next() {
      List<Integer> newNext = new ArrayList<>(), current = next;
      int changeIndex, changeValue;
      for (changeIndex = next.size() - 1; changeIndex >= 0; changeIndex--)
        if (next.get(changeIndex) != last.get(changeIndex)) break;
      if (changeIndex < 0) {
        next = null;
      } else {
        changeValue = next.get(changeIndex) + 1;
        for (int i = 0; i < changeIndex; i++)
          newNext.add(next.get(i));
        for (int i = 0; newNext.size() < next.size(); i++)
          newNext.add(changeValue + i);
        next = newNext;
      }
      return current;
    }

  }

  /**
   * Iterate all subsets of size AT MOST k of {1,...,n}.
   * Smaller subsets are generated first.
   */
  public static class SubsetSizeAtMostKIterator implements Iterator<List<Integer>> {

    int numAlteredTables, numRetainedTables, currentK;
    SubsetSizeKIterator sizeKIterator;

    public SubsetSizeAtMostKIterator(int numAlteredTables, int numRetainedTables) {
      this.numAlteredTables = numAlteredTables;
      this.numRetainedTables = numRetainedTables;
      this.currentK = 1;
      this.sizeKIterator = new SubsetSizeKIterator(numAlteredTables, currentK);
    }

    @Override
    public boolean hasNext() {
      return currentK < numRetainedTables || sizeKIterator.hasNext();
    }

    @Override
    public List<Integer> next() {
      if (!sizeKIterator.hasNext()) {
        currentK++;
        sizeKIterator = new SubsetSizeKIterator(numAlteredTables, currentK);
      }
      return sizeKIterator.next();
    }

  }

}