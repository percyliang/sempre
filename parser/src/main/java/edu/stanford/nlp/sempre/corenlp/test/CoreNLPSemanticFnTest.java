package edu.stanford.nlp.sempre.corenlp.test;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.corenlp.CoreNLPAnalyzer;
import edu.stanford.nlp.sempre.test.TestUtils;
import fig.basic.LispTree;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;

import static org.testng.AssertJUnit.assertEquals;

/**
 * Test SemanticFns that depend on CoreNLP (e.g., NumberFn on "one thousand")
 * @author Percy Liang
 */
public class CoreNLPSemanticFnTest {
  private static Formula F(String s) { return Formula.fromString(s); }

  void check(Formula target, DerivationStream derivations) {
    if (!derivations.hasNext()) throw new RuntimeException("Expected 1 derivation, got " + derivations);
    assertEquals(target, derivations.next().formula);
  }

  void check(Formula target, String utterance, SemanticFn fn, List<Derivation> children) {
    Example ex = TestUtils.makeSimpleExample(utterance);
    check(target, fn.call(ex, new SemanticFn.CallInfo(null, 0, ex.numTokens(), Rule.nullRule, children)));
  }

  void check(Formula target, String utterance, SemanticFn fn) {
    List<Derivation> empty = Collections.emptyList();
    check(target, utterance, fn, empty);
  }

  void checkNumDerivations(DerivationStream derivations, int num) {
    assertEquals(num, derivations.estimatedSize());
  }

  Derivation D(Formula f) {
    return (new Derivation.Builder())
        .formula(f)
        .prob(1.0)
        .createDerivation();
  }

  LispTree T(String str) {
    return LispTree.proto.parseFromString(str);
  }

  // TODO(chaganty): Test bridge fn - requires freebase (?)
  // TODO(chaganty): Test context fn

  @Test public void dateFn() {
    LanguageAnalyzer.setSingleton(new CoreNLPAnalyzer());
    check(F("(date 2013 8 7)"), "August 7, 2013", new DateFn());
    check(F("(date 1982 -1 -1)"), "1982", new DateFn());
    check(F("(date -1 6 4)"), "june 4", new DateFn());
  }

  @Test public void filterNerTagFn() {
    LanguageAnalyzer.setSingleton(new CoreNLPAnalyzer());
    FilterNerSpanFn filter = new FilterNerSpanFn();
    filter.init(T("(FilterNerSpanFn token PERSON)"));
    Derivation child = new Derivation.Builder().createDerivation();
    Example ex = TestUtils.makeSimpleExample("where is Obama");
    assertEquals(filter.call(ex,
            new SemanticFn.CallInfo(null, 0, 1, Rule.nullRule, Collections.singletonList(child))).hasNext(),
        false);
    assertEquals(filter.call(ex,
            new SemanticFn.CallInfo(null, 1, 2, Rule.nullRule, Collections.singletonList(child))).hasNext(),
        false);
    assertEquals(filter.call(ex,
            new SemanticFn.CallInfo(null, 2, 3, Rule.nullRule, Collections.singletonList(child))).hasNext(),
        true);
  }

  // TODO(chaganty): Test fuzzy match fn
  // TODO(chaganty): Test identity fn
  // TODO(chaganty): Test join fn
  // TODO(chaganty): Test lexicon fn
  // TODO(chaganty): Test merge fn

  @Test public void numberFn() {
    LanguageAnalyzer.setSingleton(new CoreNLPAnalyzer());
    check(F("(number 35000)"), "thirty-five thousand", new NumberFn());
  }

  // TODO(chaganty): Test select fn
  // TODO(chaganty): Test simple lexicon fn

}
