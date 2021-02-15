package edu.stanford.nlp.sempre.test;

import edu.stanford.nlp.sempre.*;
import fig.basic.LogInfo;
import org.testng.annotations.Test;

/**
 * Test JSON serialization and deserialization.
 */
public class JsonTest {
  public static String S(Object o) {
    return Json.writeValueAsStringHard(o);
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
    if (a.context != b.context &&
        !a.context.equals(b.context))
      return false;
    if (a.targetFormula != b.targetFormula &&
        !a.targetFormula.toString().equals(b.targetFormula.toString()))
      return false;
    if (a.targetValue != b.targetValue &&
        !a.targetValue.equals(b.targetValue))
      return false;
    return true;
  }

  public static Parser makeSimpleBeamParser() {
    return new BeamParser(ParserTest.ABCTest().getParserSpec());
  }

  @Test
  public void testExample() {
    Builder builder = new Builder();
    builder.build();

    Example.Builder b = new Example.Builder();
    Example ex = b
      .setId("id")
      .setUtterance("A is for Alice")
      .setTargetValue(new StringValue("B is for Bob"))
      .createExample();
    LogInfo.log(S(ex));
    assert exampleEquals(ex, D(S(ex), Example.class));

    ex.preprocess();
    LogInfo.log(S(ex));
    assert ex.languageInfo != null;
    assert !ex.languageInfo.tokens.isEmpty();
    assert exampleEquals(ex, D(S(ex), Example.class));

    ex = TestUtils.makeSimpleExample("1 2 3");
    ex.preprocess();
    makeSimpleBeamParser().parse(new Params(), ex, true);
    String there = S(ex);
    Example back = D(there, Example.class);
    String thereAgain = S(back);
    LogInfo.log(there);
    LogInfo.log(thereAgain);
    assert there.equals(thereAgain);
    assert exampleEquals(ex, back);
  }
}
