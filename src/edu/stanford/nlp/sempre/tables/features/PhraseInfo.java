package edu.stanford.nlp.sempre.tables.features;

import java.util.*;
import java.util.concurrent.ExecutionException;

import com.google.common.cache.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.FuzzyMatchFn.FuzzyMatchFnMode;
import edu.stanford.nlp.sempre.tables.TableKnowledgeGraph;
import fig.basic.*;

/**
 * Represents a phrase in the utterance.
 *
 * Also contains additional information such as POS and NER tags.
 *
 * @author ppasupat
 */
public class PhraseInfo {
  public static class Options {
    @Option(gloss = "Maximum number of tokens in a phrase")
    public int maxPhraseLength = 3;
    @Option(gloss = "Fuzzy match predicates")
    public boolean computeFuzzyMatchPredicates = false;
  }
  public static Options opts = new Options();

  public final int start, end, endOffset;
  public final String text;
  public final String lemmaText;
  public final List<String> tokens;
  public final List<String> lemmaTokens;
  public final List<String> posTags;
  public final List<String> nerTags;
  public final String normalizedNerSpan;
  public final String canonicalPosSeq;
  public final List<String> fuzzyMatchedPredicates;

  public PhraseInfo(Example ex, int start, int end) {
    this.start = start;
    this.end = end;
    LanguageInfo languageInfo = ex.languageInfo;
    this.endOffset = end - languageInfo.numTokens();
    tokens = languageInfo.tokens.subList(start, end);
    lemmaTokens = languageInfo.tokens.subList(start, end);
    posTags = languageInfo.posTags.subList(start, end);
    nerTags = languageInfo.nerTags.subList(start, end);
    if (nerTags.get(0) == null)
      normalizedNerSpan = null;
    else
      normalizedNerSpan = languageInfo.getNormalizedNerSpan(nerTags.get(0), start, end);
    text = languageInfo.phrase(start, end).toLowerCase();
    lemmaText = languageInfo.lemmaPhrase(start, end).toLowerCase();
    canonicalPosSeq = languageInfo.canonicalPosSeq(start, end);
    fuzzyMatchedPredicates = opts.computeFuzzyMatchPredicates ? getFuzzyMatchedPredicates(ex.context) : null;
  }

  private List<String> getFuzzyMatchedPredicates(ContextValue context) {
    if (context == null || context.graph == null || !(context.graph instanceof TableKnowledgeGraph))
      return null;
    TableKnowledgeGraph graph = (TableKnowledgeGraph) context.graph;
    List<String> matchedPredicates = new ArrayList<>();
    // Assume everything is ValueFormula with NameValue inside
    List<Formula> formulas = new ArrayList<>();
    formulas.addAll(graph.getFuzzyMatchedFormulas(text, FuzzyMatchFnMode.ENTITY));
    formulas.addAll(graph.getFuzzyMatchedFormulas(text, FuzzyMatchFnMode.BINARY));
    for (Formula formula : formulas) {
      if (formula instanceof ValueFormula) {
        Value value = ((ValueFormula<?>) formula).value;
        if (value instanceof NameValue) {
          matchedPredicates.add(((NameValue) value).id);
        }
      }
    }
    return matchedPredicates;
  }

  @Override
  public String toString() {
    return "\"" + text + "\"";
  }

  // Caching
  private static final LoadingCache<Example, List<PhraseInfo>> cache = CacheBuilder.newBuilder()
      .maximumSize(20)
      .build(
          new CacheLoader<Example, List<PhraseInfo>>() {
            @Override
            public List<PhraseInfo> load(Example ex) throws Exception {
              List<PhraseInfo> phraseInfos = new ArrayList<>();
              List<String> tokens = ex.languageInfo.tokens;
              for (int s = 1; s <= opts.maxPhraseLength; s++) {
                for (int i = 0; i <= tokens.size() - s; i++) {
                  phraseInfos.add(new PhraseInfo(ex, i, i + s));
                }
              }
              return phraseInfos;
            }
          });

  public static List<PhraseInfo> getPhraseInfos(Example ex) {
    try {
      return cache.get(ex);
    } catch (ExecutionException e) {
      throw new RuntimeException(e.getCause());
    }
  }

}
