package edu.stanford.nlp.sempre.tables.match;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.FuzzyMatchFn.FuzzyMatchFnMode;
import edu.stanford.nlp.sempre.tables.*;
import fig.basic.*;

/**
 * Perform fuzzy matching on the table knowledge graph.
 *
 * @author ppasupat
 */
public abstract class FuzzyMatcher {
  public static class Options {
    // This would prevent "canada ?" from fuzzy matching: we already fuzzy match "canada"
    @Option(gloss = "Ignore query strings where a boundary word is a punctuation (prevent overgeneration)")
    public boolean ignorePunctuationBoundedQueries = true;
    @Option(gloss = "Default fuzzy matcher to use")
    public String fuzzyMatcher = "tables.match.EditDistanceFuzzyMatcher";
  }
  public static Options opts = new Options();

  /**
   * Get a fuzzy matcher of the default class.
   */
  public static FuzzyMatcher getFuzzyMatcher(TableKnowledgeGraph graph) {
    return getFuzzyMatcher(opts.fuzzyMatcher, graph);
  }

  public static FuzzyMatcher getFuzzyMatcher(String className, TableKnowledgeGraph graph) {
    try {
      Class<?> classObject = Class.forName(SempreUtils.resolveClassName(className));
      return (FuzzyMatcher) classObject.getConstructor(TableKnowledgeGraph.class).newInstance(graph);
    } catch (Exception e) {
      e.printStackTrace();
      e.getCause().printStackTrace();
      throw new RuntimeException(e);
    }
  }

  // ============================================================
  // Precomputation
  // ============================================================

  public final TableKnowledgeGraph graph;

  public FuzzyMatcher(TableKnowledgeGraph graph) {
    this.graph = graph;
  }

  // ============================================================
  // Fuzzy Matching Main Interface
  // ============================================================

  /**
   * Check if the first or the last token is a punctuation (no alphanumeric character).
   */
  public boolean checkPunctuationBoundaries(String term) {
    String[] tokens = term.trim().split("\\s+");
    if (tokens.length == 0) return false;
    if (StringNormalizationUtils.collapseNormalize(tokens[0]).isEmpty()) return false;
    if (tokens.length == 1) return true;
    if (StringNormalizationUtils.collapseNormalize(tokens[tokens.length - 1]).isEmpty()) return false;
    return true;
  }

  /**
   * If needed, compute the fuzzy matched predicates for all substrings of sentence and cache the result.
   * Then, return all formulas of the specified mode that match the phrase formed by sentence[startIndex:endIndex].
   */
  public Collection<Formula> getFuzzyMatchedFormulas(
      List<String> sentence, int startIndex, int endIndex, FuzzyMatchFnMode mode) {
    FuzzyMatchCache cache = cacheSentence(sentence, mode);
    Collection<Formula> formulas = cache.get(startIndex, endIndex);
    return formulas == null ? Collections.emptySet() : formulas;
  }

  abstract protected FuzzyMatchCache cacheSentence(List<String> sentence, FuzzyMatchFnMode mode);

  /**
   * Return all formulas of the specified mode that match the phrase.
   * Do not use any cached results.
   */
  public Collection<Formula> getFuzzyMatchedFormulas(String term, FuzzyMatchFnMode mode) {
    if (opts.ignorePunctuationBoundedQueries && !checkPunctuationBoundaries(term))
      return Collections.emptySet();
    Collection<Formula> formulas = getFuzzyMatchedFormulasInternal(term, mode);
    return formulas == null ? Collections.emptySet() : formulas;
  }

  abstract protected Collection<Formula> getFuzzyMatchedFormulasInternal(String term, FuzzyMatchFnMode mode);

  /** Return all formulas of the specified mode. */
  public Collection<Formula> getAllFormulas(FuzzyMatchFnMode mode) {
    Collection<Formula> formulas = getAllFormulasInternal(mode);
    return formulas == null ? Collections.emptySet() : formulas;
  }

  abstract protected Collection<Formula> getAllFormulasInternal(FuzzyMatchFnMode mode);

  // ============================================================
  // Helper Functions: Construct Formulas
  // ============================================================
  /*
   * ENTITIY --> fb:cell.___
   *   UNARY --> (!fb.row.row.___ (fb:type.object.type fb:type.row))
   *  BINARY --> fb:row.row.___
   */

  static Formula getEntityFormula(NameValue nameValue) {
    return new ValueFormula<>(nameValue);
  }

  static Formula getEntityFormula(TableCell cell) {
    return new ValueFormula<>(cell.properties.nameValue);
  }

  static Formula getEntityFormula(TableCellProperties properties) {
    return new ValueFormula<>(properties.nameValue);
  }

  static Formula getUnaryFormula(TableColumn column) {
    return new JoinFormula(
        new ValueFormula<>(CanonicalNames.reverseProperty(column.relationNameValue)),
        new JoinFormula(new ValueFormula<>(new NameValue(CanonicalNames.TYPE)),
            new ValueFormula<>(new NameValue(TableTypeSystem.ROW_TYPE))));
  }

  static Formula getBinaryFormula(TableColumn column) {
    return new ValueFormula<>(column.relationNameValue);
  }

  static Formula getConsecutiveBinaryFormula(TableColumn column) {
    return new ValueFormula<>(column.relationConsecutiveNameValue);
  }

  static List<Formula> getNormalizedBinaryFormulas(TableColumn column) {
    List<Formula> formulas = new ArrayList<>();
    for (Value normalization : column.getAllNormalization()) {
      formulas.add(new LambdaFormula("x", new JoinFormula(
          new ValueFormula<>(column.relationNameValue), new JoinFormula(
              new ValueFormula<>(normalization), new VariableFormula("x")))));
    }
    return formulas;
  }
}
