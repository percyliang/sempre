package edu.stanford.nlp.sempre.tables;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.TableKnowledgeGraph.TableCell;
import edu.stanford.nlp.sempre.tables.TableKnowledgeGraph.TableColumn;
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
    @Option(gloss = "Allow token subsequence match (can overgenerate a lot)")
    public boolean allowTokenSubsequenceMatch = false;
    @Option(gloss = "Do not fuzzy match if the query matches more than this number of formulas (prevent overgeneration)")
    public int maxFuzzyMatchCandidates = Integer.MAX_VALUE;
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
    if (opts.allowTokenSubsequenceMatch) {
      String[] tokens = normalized.trim().split("\\s+");
      for (int i = 0; i < tokens.length; i++) {
        StringBuilder sb = new StringBuilder();
        for (int j = i; j < tokens.length; j++) {
          sb.append(tokens[j]);
          collapsedForms.add(StringNormalizationUtils.collapseNormalize(sb.toString()));
        }
      }
    }
    collapsedForms.remove("");
    return collapsedForms;
  }

  private static String getCanonicalCollapsedForm(String original) {
    return StringNormalizationUtils.collapseNormalize(original);
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
    switch (mode) {
      case ENTITY: answer = phraseToEntityFormulas.get(normalized); break;
      case UNARY:  answer = phraseToUnaryFormulas.get(normalized);  break;
      case BINARY: answer = phraseToBinaryFormulas.get(normalized); break;
      default: throw new RuntimeException("Unknown FuzzyMatchMode " + mode);
    }
    return (answer == null || answer.size() > opts.maxFuzzyMatchCandidates) ? Collections.emptySet() : answer;
  }

  public Collection<Formula> getAllFormulas(FuzzyMatchFn.FuzzyMatchFnMode mode) {
    switch (mode) {
      case ENTITY: return allEntityFormulas;
      case UNARY:  return allUnaryFormulas;
      case BINARY: return allBinaryFormulas;
      default: throw new RuntimeException("Unknown FuzzyMatchMode " + mode);
    }
  }

}
