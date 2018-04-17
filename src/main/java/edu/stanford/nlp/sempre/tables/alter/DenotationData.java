package edu.stanford.nlp.sempre.tables.alter;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.Value;
import edu.stanford.nlp.sempre.Values;
import edu.stanford.nlp.sempre.tables.InfiniteListValue;
import fig.basic.MapUtils;

public class DenotationData {

  public final int numAlteredTables, numDerivs;
  private final List<Value> uniqueDenotations = new ArrayList<>();
  private final Map<Value, Integer> uniqueDenotationsToId = new HashMap<>();
  // derivation -> denotations of that derivation on altered tables
  private final List<List<Integer>> denotations;
  // denotations of the annotated formula on altered tables
  private List<Integer> annotatedDenotations;
  // denotations on altered tables -> indices of formulas that give those denotations
  private Map<List<Integer>, List<Integer>> groups;
  // representative derivation of each group
  private List<Integer> representativeDerivs;

  public DenotationData(int numAlteredTables, int numDerivs) {
    this.numAlteredTables = numAlteredTables;
    this.numDerivs = numDerivs;
    denotations = new ArrayList<>(numDerivs);
    for (int k = 0; k < numDerivs; k++) {
      List<Integer> denotationsForDeriv = new ArrayList<>(numAlteredTables + 1);
      for (int j = 0; j < numAlteredTables + 1; j++)
        denotationsForDeriv.add(null);
      denotations.add(denotationsForDeriv);
    }
  }

  private int lookup(Value value) {
    Integer id = uniqueDenotationsToId.get(value);
    if (id == null) {
      id = uniqueDenotations.size();
      uniqueDenotations.add(value);
      uniqueDenotationsToId.put(value, id);
    }
    return id;
  }

  public void addDenotation(int derivIndex, int tableIndex, Value value) {
    denotations.get(derivIndex).set(tableIndex, lookup(value));
  }

  public Value getDenotation(int derivIndex, int tableIndex) {
    return uniqueDenotations.get(denotations.get(derivIndex).get(tableIndex));
  }

  public List<Value> getDenotations(int derivIndex) {
    List<Value> answer = new ArrayList<>(numAlteredTables);
    for (int x : denotations.get(derivIndex))
      answer.add(uniqueDenotations.get(x));
    return answer;
  }

  public int[][] toArray(List<Integer> derivs) {
    int[][] answer = new int[derivs.size()][numAlteredTables + 1];
    for (int i = 0; i < derivs.size(); i++) {
      List<Integer> derivDenotationIndices = denotations.get(derivs.get(i));
      for (int j = 0; j <= numAlteredTables; j++)
        answer[i][j] = derivDenotationIndices.get(j);
    }
    return answer;
  }

  public void addAnnotatedDenotation(int tableIndex, Value value) {
    if (annotatedDenotations == null) {
      annotatedDenotations = new ArrayList<>(numAlteredTables + 1);
      for (int j = 0; j < numAlteredTables + 1; j++)
        annotatedDenotations.add(null);
    }
    annotatedDenotations.set(tableIndex, lookup(value));
  }

  public Value getAnnotatedDenotation(int tableIndex) {
    return uniqueDenotations.get(annotatedDenotations.get(tableIndex));
  }

  public List<Value> getAnnotatedDenotations() {
    List<Value> answer = new ArrayList<>(numAlteredTables);
    for (int x : annotatedDenotations)
      answer.add(uniqueDenotations.get(x));
    return answer;
  }

  public boolean isAnnotated() {
    return annotatedDenotations != null;
  }

  public void computeGroups(List<Derivation> derivs) {
    groups = groupByDenotation(denotations);
    // Get the representative derivation of each group
    // Choose the smallest formula
    representativeDerivs = new ArrayList<>();
    for (List<Integer> equivClass : groups.values()) {
      int bestIndex = 0, bestScore = Integer.MIN_VALUE;
      for (int index : equivClass) {
        Derivation deriv = derivs.get(index);
        int score = -index;
        try {
          if (deriv.canonicalUtterance.startsWith("$ROOT:"))
            score = 100 - Integer.parseInt(deriv.canonicalUtterance.substring("$ROOT:".length()));
        } catch (NumberFormatException e) { }
        if (score > bestScore) {
          bestScore = score;
          bestIndex = index;
        }
      }
      representativeDerivs.add(bestIndex);
    }
  }

  /**
   * Helper method:
   * Group formulas that execute to the same denotation (or tuple of denotations).
   *
   * Return a map from denotations to lists of formula indices.
   */
  public static <T> Map<T, List<Integer>> groupByDenotation(List<T> denotations) {
    Map<T, List<Integer>> groups = new HashMap<>();
    for (int i = 0; i < denotations.size(); i++)
      MapUtils.addToList(groups, denotations.get(i), i);
    return groups;
  }

  public int numClasses() {
    if (groups == null)
      throw new RuntimeException("Must call computeGroups(derivs) first");
    return groups.size();
  }

  public List<Integer> getRepresentativeIndices() {
    if (groups == null)
      throw new RuntimeException("Must call computeGroups(derivs) first");
    return representativeDerivs;
  }

  public List<Integer> getEquivClass(int representativeIndex) {
    if (groups == null)
      throw new RuntimeException("Must call computeGroups(derivs) first");
    return groups.get(denotations.get(representativeIndex));
  }

  // ============================================================
  // Serialization
  // ============================================================

  public void dump(PrintWriter out) {
    // # derivations, # altered tables, # unique denotations
    out.println("" + numDerivs + " " + numAlteredTables + " " + uniqueDenotations.size());
    for (Value denotation : uniqueDenotations)
      out.println(denotation);
    for (List<Integer> derivDenotationIndices : denotations) {
      StringBuilder sb = new StringBuilder();
      for (Integer derivDenotationIndex : derivDenotationIndices)
        sb.append(derivDenotationIndex == null ? -1 : derivDenotationIndex).append(" ");
      out.println(sb.toString().trim());
    }
  }

  public void dumpAnnotated(PrintWriter out) {
    for (Integer annotatedDenotationIndex : annotatedDenotations)
      out.println(annotatedDenotationIndex == null ? null : uniqueDenotations.get(annotatedDenotationIndex));
  }

  public static DenotationData load(BufferedReader in) {
    try {
      String line = in.readLine();
      String[] tokens = line.split(" ");
      if (tokens.length != 3)
        throw new RuntimeException("Expected 3 tokens; got " + tokens.length);
      int numDerivs = Integer.parseInt(tokens[0]),
          numAlteredTables = Integer.parseInt(tokens[1]),
          numUniqueDenotations = Integer.parseInt(tokens[2]);
      DenotationData denotationData = new DenotationData(numAlteredTables, numDerivs);
      for (int i = 0; i < numUniqueDenotations; i++) {
        line = in.readLine();
        Value value;
        if ("ERROR".equals(line)) {
          value = ValueCanonicalizer.ERROR;
        } else if ("null".equals(line)) {
          value = null;
        } else {
          try {
            value = Values.fromString(line);
          } catch (Exception e) {
            // Probably InfiniteValue
            value = new InfiniteListValue(line);
          }
        }
        denotationData.uniqueDenotations.add(value);
        denotationData.uniqueDenotationsToId.put(value, i);
      }
      for (int i = 0; i < numDerivs; i++) {
        tokens = in.readLine().split(" ");
        if (tokens.length != numAlteredTables + 1)
          throw new RuntimeException("Expected " + (numAlteredTables + 1) + " tokens; got " + tokens.length);
        for (int j = 0; j <= numAlteredTables; j++) {
          denotationData.denotations.get(i).set(j, Integer.parseInt(tokens[j]));
        }
      }
      return denotationData;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

}
