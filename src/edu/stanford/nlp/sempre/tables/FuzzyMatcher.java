package edu.stanford.nlp.sempre.tables;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import fig.basic.MapUtils;
import fig.basic.Option;

/**
 * Perform fuzzy matching on the table knowledge graph.
 *
 * @author ppasupat
 */
public class FuzzyMatcher {
  public static class Options {
    // This would prevent "canada ?" from fuzzy matching: we already fuzzy match "canada"
    @Option(gloss = "Ignore query strings where a boundary word is a punctuation (prevent overgeneration)")
    public boolean ignorePunctuationBoundedQueries = true;
    @Option(gloss = "Maximum edit distance ratio")
    public double fuzzyMatchMaxEditDistanceRatio = 0;
  }
  public static Options opts = new Options();

  public final TableKnowledgeGraph graph;

  public FuzzyMatcher(TableKnowledgeGraph graph) {
    this.graph = graph;
    precomputeForFuzzyMatching();
  }

  private static Collection<String> getAllCollapsedForms(String original) {
    Set<String> collapsedForms = new HashSet<>();
    collapsedForms.add(StringNormalizationUtils.collapseNormalize(original));
    String normalized = StringNormalizationUtils.aggressiveNormalize(original);
    collapsedForms.add(StringNormalizationUtils.collapseNormalize(normalized));
    collapsedForms.remove("");
    return collapsedForms;
  }

  private static String getCanonicalCollapsedForm(String original) {
    return StringNormalizationUtils.collapseNormalize(original);
  }

  private static int editDistance(String a, String b) {
    // TODO: Make this less naive
    int m = a.length() + 1, n = b.length() + 1;
    int[] dists = new int[m], newDists = new int[m];
    for (int i = 0; i < m; i++) dists[i] = i;
    for (int j = 1; j < n; j++) {
      newDists[0] = j;
      for (int i = 1; i < m; i++) {
        newDists[i] = Math.min(Math.min(
            dists[i] + 1,   // Insert
            newDists[i-1] + 1),   // Delete
            dists[i-1] + (a.charAt(i-1) == b.charAt(j-1) ? 0 : 1));   // Replace
      }
      int[] swap = dists; dists = newDists; newDists = swap;
    }
    return dists[m-1];
  }

  private static double editDistanceRatio(String a, String b) {
    if (a.isEmpty() && b.isEmpty()) return 0.0;
    return editDistance(a, b) * 2.0 / (a.length() + b.length());
  }

  // Map normalized strings to Values
  // ENTITIY --> ValueFormula fb:cell.___ or other primitive format
  //   UNARY --> JoinFormula (type fb:column.___)
  //  BINARY --> ValueFormula fb:row.row.___
  Set<Formula> allEntityFormulas, allUnaryFormulas, allBinaryFormulas;
  Map<String, Set<Formula>> phraseToEntityFormulas, phraseToUnaryFormulas, phraseToBinaryFormulas;

  protected void precomputeForFuzzyMatching() {
    allEntityFormulas = new HashSet<>();
    allUnaryFormulas = new HashSet<>();
    allBinaryFormulas = new HashSet<>();
    phraseToEntityFormulas = new HashMap<>();
    phraseToUnaryFormulas = new HashMap<>();
    phraseToBinaryFormulas = new HashMap<>();
    for (TableColumn column : graph.columns) {
      // unary and binary
      Formula unary = new JoinFormula(
          new ValueFormula<>(KnowledgeGraph.getReversedPredicate(column.propertyNameValue)),
          new JoinFormula(new ValueFormula<>(new NameValue(CanonicalNames.TYPE)),
                          new ValueFormula<>(new NameValue(TableTypeSystem.ROW_TYPE)))
      );
      Formula binary = new ValueFormula<>(column.propertyNameValue);
      allUnaryFormulas.add(unary);
      allBinaryFormulas.add(binary);
      for (String s : getAllCollapsedForms(column.originalString)) {
        MapUtils.addToSet(phraseToUnaryFormulas, s, unary);
        MapUtils.addToSet(phraseToBinaryFormulas, s, binary);
      }
      // entity
      for (TableCell cell : column.children) {
        Formula entity = new ValueFormula<>(cell.properties.entityNameValue);
        allEntityFormulas.add(entity);
        for (String s : getAllCollapsedForms(cell.properties.originalString))
          MapUtils.addToSet(phraseToEntityFormulas, s, entity);
      }
    }
  }

  boolean checkPunctuationBoundaries(String term) {
    String[] tokens = term.trim().split("\\s+");
    if (tokens.length == 0) return false;
    if (StringNormalizationUtils.collapseNormalize(tokens[0]).isEmpty()) return false;
    if (tokens.length == 1) return true;
    if (StringNormalizationUtils.collapseNormalize(tokens[tokens.length - 1]).isEmpty()) return false;
    return true;
  }

  public Collection<Formula> getFuzzyMatchedFormulas(String term, FuzzyMatchFn.FuzzyMatchFnMode mode) {
    if (opts.ignorePunctuationBoundedQueries && !checkPunctuationBoundaries(term)) return Collections.emptySet();
    String normalized = getCanonicalCollapsedForm(term);
    Set<Formula> answer;
    Map<String, Set<Formula>> target;
    switch (mode) {
      case ENTITY: target = phraseToEntityFormulas; break;
      case UNARY:  target = phraseToUnaryFormulas;  break;
      case BINARY: target = phraseToBinaryFormulas; break;
      default: throw new RuntimeException("Unknown FuzzyMatchMode " + mode);
    }
    if (opts.fuzzyMatchMaxEditDistanceRatio == 0) {
      answer = target.get(normalized);
    } else {
      answer = new HashSet<>();
      for (String key : target.keySet()) {
        if (editDistanceRatio(key, normalized) < opts.fuzzyMatchMaxEditDistanceRatio) {
          answer.addAll(target.get(key));
        }
      }
    }
    return answer == null ? Collections.emptySet() : answer;
  }

  public Collection<Formula> getAllFormulas(FuzzyMatchFn.FuzzyMatchFnMode mode) {
    switch (mode) {
      case ENTITY: return allEntityFormulas;
      case UNARY:  return allUnaryFormulas;
      case BINARY: return allBinaryFormulas;
      default: throw new RuntimeException("Unknown FuzzyMatchMode " + mode);
    }
  }

  // ============================================================
  // Test
  // ============================================================

  public static void main(String[] args) {
    System.out.println(editDistance("unionist", "unionists"));
  }
}
