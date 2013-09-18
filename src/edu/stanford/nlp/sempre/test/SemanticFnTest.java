package edu.stanford.nlp.sempre.test;

import edu.stanford.nlp.sempre.*;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;

import static org.testng.AssertJUnit.assertEquals;

/**
 * Test Formulas.
 * @author Percy Liang
 */
public class SemanticFnTest {
  private static Formula F(String s) { return Formula.fromString(s); }

  void check(Formula target, List<Derivation> derivations) {
    if (derivations.size() != 1) throw new RuntimeException("Expected 1 derivation, got " + derivations);
    assertEquals(target, derivations.get(0).formula);
  }

  void check(Formula target, String utterance, SemanticFn fn) {
    Example ex = TestUtils.makeSimpleExample(utterance);
    List<Derivation> empty = Collections.emptyList();
    check(target, fn.call(ex, new SemanticFn.CallInfo(null, 0, ex.numTokens(), Rule.nullRule, empty)));
  }

  @Test public void constantFn() {
    check(F("(number 3)"), "whatever", new ConstantFn(F("(number 3)")));
  }

  @Test public void numberFn() {
    check(F("(number 35000)"), "thirty-five thousand", new NumberFn());
  }

  @Test public void dateFn() {
    check(F("(date 2013 8 7)"), "August 7, 2013", new DateFn());
    check(F("(date 1982 -1 -1)"), "1982", new DateFn());
    check(F("(date -1 6 4)"), "june 4", new DateFn());
  }
}
