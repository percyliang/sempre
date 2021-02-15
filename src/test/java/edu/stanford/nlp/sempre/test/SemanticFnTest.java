package edu.stanford.nlp.sempre.test;

import edu.stanford.nlp.sempre.*;
import fig.basic.LispTree;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.testng.AssertJUnit.assertEquals;

/**
 * Test Formulas.
 * @author Percy Liang
 */
public class SemanticFnTest {
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

  @Test public void constantFn() {
    LanguageAnalyzer.setSingleton(new SimpleAnalyzer());
    check(F("(number 3)"), "whatever", new ConstantFn(F("(number 3)")));
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

  // TODO(chaganty): Test bridge fn - requires freebase

  @Test public void concatFn() {
    LanguageAnalyzer.setSingleton(new SimpleAnalyzer());
    check(F("(string \"a b\")"), "a b", new ConcatFn(" "),
        Arrays.asList(D(F("(string a)")), D(F("(string b)"))));
  }

  // TODO(chaganty): Test context fn

  @Test public void filterPosTagFn() {
    LanguageAnalyzer.setSingleton(new SimpleAnalyzer());
    FilterPosTagFn filter = new FilterPosTagFn();
    filter.init(T("(FilterPosTagFn token NNP)"));
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

  @Test public void filterSpanLengthFn() {
    LanguageAnalyzer.setSingleton(new SimpleAnalyzer());
    FilterSpanLengthFn filter = new FilterSpanLengthFn();
    filter.init(T("(FilterSpanLengthFn 2)"));
    Derivation child = new Derivation.Builder().createDerivation();
    Example ex = TestUtils.makeSimpleExample("This is a sentence with some words");
    assertEquals(
        filter.call(ex, new SemanticFn.CallInfo(null, 0, 1, Rule.nullRule, Collections.singletonList(child))).hasNext(),
        false);
    assertEquals(
        filter.call(ex, new SemanticFn.CallInfo(null, 0, 2, Rule.nullRule, Collections.singletonList(child))).hasNext(),
        true);
    assertEquals(
        filter.call(ex, new SemanticFn.CallInfo(null, 0, 2, Rule.nullRule, Collections.singletonList(child))).hasNext(),
        true);

    filter = new FilterSpanLengthFn();
    filter.init(T("(FilterSpanLengthFn 2 4)"));
    assertEquals(
        filter.call(ex, new SemanticFn.CallInfo(null, 0, 1, Rule.nullRule, Collections.singletonList(child))).hasNext(),
        false);
    assertEquals(
        filter.call(ex, new SemanticFn.CallInfo(null, 0, 2, Rule.nullRule, Collections.singletonList(child))).hasNext(),
        true);
    assertEquals(
        filter.call(ex, new SemanticFn.CallInfo(null, 0, 3, Rule.nullRule, Collections.singletonList(child))).hasNext(),
        true);
    assertEquals(
        filter.call(ex, new SemanticFn.CallInfo(null, 0, 4, Rule.nullRule, Collections.singletonList(child))).hasNext(),
        true);
    assertEquals(
        filter.call(ex, new SemanticFn.CallInfo(null, 0, 5, Rule.nullRule, Collections.singletonList(child))).hasNext(),
        false);
  }

  // TODO(chaganty): Test fuzzy match fn
  // TODO(chaganty): Test identity fn
  // TODO(chaganty): Test join fn
  // TODO(chaganty): Test lexicon fn
  // TODO(chaganty): Test merge fn
  // TODO(chaganty): Test select fn
  // TODO(chaganty): Test simple lexicon fn

}
