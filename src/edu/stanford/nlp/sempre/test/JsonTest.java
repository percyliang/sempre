package edu.stanford.nlp.sempre.test;

import edu.stanford.nlp.sempre.*;
import fig.basic.LogInfo;
import org.testng.annotations.Test;

import java.util.ArrayList;

/**
 * Test JSON serialization and deserialization.
 */
public class JsonTest {
  public static String S(Object o) {
    return Json.writeValueAsStringHard(o);
  }

  public static String S(Object o, Class<?> view) {
    return Json.writeValueAsStringHard(o, view);
  }

  public static <T> T D(String s, Class<T> klass) {
    return Json.readValueHard(s, klass);
  }

  public static <T> T D(String s, Class<T> klass, Class<?> view) {
    return Json.readValueHard(s, klass, view);
  }

  public static boolean exampleEquals(Example a, Example b) {
    if (!a.id.equals(b.id)) return false;
    if (!a.utterance.equals(b.utterance)) return false;
    if (a.derivConstraint != b.derivConstraint &&
        !a.derivConstraint.getFormulaPattern().equals(b.derivConstraint.getFormulaPattern()))
      return false;
    if (a.targetFormula != b.targetFormula &&
        !a.targetFormula.toString().equals(b.targetFormula.toString()))
      return false;
    if (a.targetValue != b.targetValue &&
        !a.targetValue.equals(b.targetValue))
      return false;
    if (a.getPredDerivations() != b.getPredDerivations() &&
        !a.getPredDerivations().equals(b.getPredDerivations()))
      return false;
    if (a.languageInfo != b.languageInfo &&
        !a.languageInfo.equals(b.languageInfo))
      return false;
    return true;
  }

  @Test
  public void testDerivation() {
    Derivation.Builder b = new Derivation.Builder()
      .cat("_cat")
      .start(0).end(9)
      .rule(Rule.nullRule)
      .children(new ArrayList<Derivation>())
      .withStringFormulaFrom("_stringFormula");
    Derivation d = b.createDerivation();
    assert S(d).equals(S(b.createDerivation()));

    String there = S(d);
    Derivation back = D(S(d), Derivation.class);
    String thereAgain = S(back);

    assert there.equals(thereAgain);
    assert d.equals(back);
  }

  @Test
  public void testExample() {
    Example.Builder b = new Example.Builder();
    Example ex = b
      .setId("id")
      .setUtterance("A is for Alice")
      .setTargetValue(new StringValue("B is for Bob"))
      .createExample();
    LogInfo.logs(S(ex));
    assert exampleEquals(ex, D(S(ex), Example.class));

    ex.preprocess();
    LogInfo.logs(S(ex));
    assert ex.languageInfo != null;
    assert !ex.languageInfo.tokens.isEmpty();
    assert exampleEquals(ex, D(S(ex), Example.class));

    ex = TestUtils.makeSimpleExample("1 2 3");

    boolean tmpUseAnnotators = LanguageInfo.opts.useAnnotators;
    LanguageInfo.opts.useAnnotators = false;

    ex.preprocess();
    LogInfo.logs(S(ex, Example.JsonViews.WithDerivations.class));
    ParserTest.makeSimpleBeamParser().parse(
        new Params(),
        ex);
    String there = S(ex, Example.JsonViews.WithDerivations.class);
    Example back = D(
        there, Example.class, Example.JsonViews.WithDerivations.class);
    String thereAgain = S(back, Example.JsonViews.WithDerivations.class);
    LogInfo.logs(there);
    LogInfo.logs(thereAgain);

    assert ex.getPredDerivations() != null;
    assert !ex.getPredDerivations().isEmpty();
    assert there.equals(thereAgain);
    assert exampleEquals(ex, back);

    LanguageInfo.opts.useAnnotators = tmpUseAnnotators;
  }
}
