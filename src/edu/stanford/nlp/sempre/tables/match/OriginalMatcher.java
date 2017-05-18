package edu.stanford.nlp.sempre.tables.match;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.FuzzyMatchFn.FuzzyMatchFnMode;
import edu.stanford.nlp.sempre.tables.StringNormalizationUtils;
import edu.stanford.nlp.sempre.tables.TableCell;
import edu.stanford.nlp.sempre.tables.TableColumn;
import edu.stanford.nlp.sempre.tables.TableKnowledgeGraph;
import edu.stanford.nlp.sempre.tables.TableTypeSystem;
import fig.basic.MapUtils;
import fig.basic.Option;
import fig.basic.Pair;

/**
 * Original matcher used in ACL 2015. Only does exact matches.
 *
 * @author ppasupat
 */
public class OriginalMatcher extends FuzzyMatcher {
  public static class Options {
    @Option(gloss = "Do not fuzzy match if the query matches more than this number of formulas (prevent overgeneration)")
    public int maxMatchedCandidates = Integer.MAX_VALUE;
  }
  public static Options opts = new Options();

  public OriginalMatcher(TableKnowledgeGraph graph) {
    super(graph);
    precomputeForMatching();
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

  // Map normalized strings to Values
  // ENTITIY --> ValueFormula fb:cell.___ or other primitive format
  //   UNARY --> JoinFormula (type fb:column.___)
  //  BINARY --> ValueFormula fb:row.row.___
  Set<Formula> allEntityFormulas, allUnaryFormulas, allBinaryFormulas;
  Map<String, Set<Formula>> phraseToEntityFormulas, phraseToUnaryFormulas, phraseToBinaryFormulas;

  protected void precomputeForMatching() {
    allEntityFormulas = new HashSet<>();
    allUnaryFormulas = new HashSet<>();
    allBinaryFormulas = new HashSet<>();
    phraseToEntityFormulas = new HashMap<>();
    phraseToUnaryFormulas = new HashMap<>();
    phraseToBinaryFormulas = new HashMap<>();
    for (TableColumn column : graph.columns) {
      // unary and binary
      Formula unary = new JoinFormula(
          new ValueFormula<>(CanonicalNames.reverseProperty(column.relationNameValue)),
          new JoinFormula(new ValueFormula<>(new NameValue(CanonicalNames.TYPE)),
                          new ValueFormula<>(new NameValue(TableTypeSystem.ROW_TYPE)))
      );
      Formula binary = new ValueFormula<>(column.relationNameValue);
      allUnaryFormulas.add(unary);
      allBinaryFormulas.add(binary);
      for (String s : getAllCollapsedForms(column.originalString)) {
        MapUtils.addToSet(phraseToUnaryFormulas, s, unary);
        MapUtils.addToSet(phraseToBinaryFormulas, s, binary);
      }
      // entity
      for (TableCell cell : column.children) {
        Formula entity = new ValueFormula<>(cell.properties.nameValue);
        allEntityFormulas.add(entity);
        for (String s : getAllCollapsedForms(cell.properties.originalString))
          MapUtils.addToSet(phraseToEntityFormulas, s, entity);
      }
    }
  }
  
  // ============================================================
  // Internal methods
  // ============================================================
  
  Map<Pair<String, FuzzyMatchFnMode>, FuzzyMatchCache> cacheMap = new HashMap<>();

  @Override
  protected FuzzyMatchCache cacheSentence(List<String> sentence, FuzzyMatchFnMode mode) {
    String joined = String.join(" ", sentence);
    FuzzyMatchCache cache = cacheMap.get(new Pair<>(joined, mode));
    if (cache != null) return cache;
    // Compute a new FuzzyMatchCache
    cache = new FuzzyMatchCache();
    for (int i = 0; i < sentence.size(); i++) {
      for (int j = i + 1; j < sentence.size(); j++) {
        String term = String.join(" ", sentence.subList(i, j));
        cache.addAll(i, j, getFuzzyMatchedFormulasInternal(term, mode));
      }
    }
    cacheMap.put(new Pair<>(joined, mode), cache);
    return cache;
  }

  @Override
  protected Collection<Formula> getFuzzyMatchedFormulasInternal(String term,
      FuzzyMatchFnMode mode) {
    String normalized = getCanonicalCollapsedForm(term);
    Set<Formula> answer;
    switch (mode) {
      case ENTITY: answer = phraseToEntityFormulas.get(normalized); break;
      case UNARY:  answer = phraseToUnaryFormulas.get(normalized);  break;
      case BINARY: answer = phraseToBinaryFormulas.get(normalized); break;
      default: throw new RuntimeException("Unknown FuzzyMatchMode " + mode);
    }
    return (answer == null || answer.size() > opts.maxMatchedCandidates) ? Collections.emptySet() : answer;
  }

  @Override
  protected Collection<Formula> getAllFormulasInternal(FuzzyMatchFnMode mode) {
    switch (mode) {
      case ENTITY: return allEntityFormulas;
      case UNARY:  return allUnaryFormulas;
      case BINARY: return allBinaryFormulas;
      default: throw new RuntimeException("Unknown FuzzyMatchMode " + mode);
    }
  }

}
