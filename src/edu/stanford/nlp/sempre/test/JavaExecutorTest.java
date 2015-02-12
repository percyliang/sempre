package edu.stanford.nlp.sempre.test;

import edu.stanford.nlp.sempre.*;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;

/**
 * Test JavaExecutor.
 * @author Percy Liang
 */
public class JavaExecutorTest {
  JavaExecutor executor = new JavaExecutor();

  private static Formula F(String s) { return Formula.fromString(s); }

  private static Value V(double x) { return new NumberValue(x); }
  private static Value V(String x) { return Values.fromString(x); }

  @Test public void numbers() {
    assertEquals(V(8), executor.execute(F("(call + (number 3) (number 5))"), null).value);
    assertEquals(V(6), executor.execute(F("(call + (call - (number 10) (number 9)) (number 5))"), null).value);
    assertEquals(V(1), executor.execute(F("(call java.lang.Math.cos (number 0))"), null).value);

    assertEquals(V(1), executor.execute(F("((lambda x (call java.lang.Math.cos (var x))) (number 0))"), null).value);  // Make sure beta reduction is called
  }

  @Test public void conditionals() {
    assertEquals(V("(string no)"), executor.execute(F("(call if (boolean false) (string yes) (string no))"), null).value);
    assertEquals(V("(string yes)"), executor.execute(F("(call if (call < (number 3) (number 4)) (string yes) (string no))"), null).value);
  }

  @Test public void strings() {
    assertEquals(V(5), executor.execute(F("(call .length (string hello))"), null).value);
    assertEquals(V("(string abcdef)"), executor.execute(F("(call .concat (string abc) (string def))"), null).value);
  }

  @Test public void higherOrder() {
    assertEquals(V("(list (number 10) (number 40))"), executor.execute(F("(call map (list (number 1) (number 4)) (lambda x (call * (number 10) (var x))))"), null).value);
    assertEquals(V("(list (number 4))"), executor.execute(F("(call select (list (number 1) (number 4)) (lambda x (call == (number 0) (call % (var x) (number 2)))))"), null).value);
    // assertEquals(V("(list (number 5))"), executor.execute(F("(call reduce (list (number 1) (number 4)) +)")).value);  // Not implemented yet
  }
}
