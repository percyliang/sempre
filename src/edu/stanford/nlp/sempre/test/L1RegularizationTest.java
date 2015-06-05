package edu.stanford.nlp.sempre.test;

import java.util.*;

import org.testng.annotations.Test;

import static org.testng.AssertJUnit.*;
import edu.stanford.nlp.sempre.Params;

/**
 * Test lazy L1 regularization.
 *
 * @author ppasupat
 */
public class L1RegularizationTest {

  private static final double EPSILON = 1e-3;

  class Options {
    public double initStepSize = 1.0;
    public String l1Reg = "none";
    public double l1RegCoeff = 0;
    public Options initStepSize(double x) { initStepSize = x; return this; }
    public Options l1Reg(String x) { l1Reg = x; return this; }
    public Options l1RegCoeff(double x) { l1RegCoeff = x; return this; }
  }

  private Options originalOptions = null;

  private void saveOptions() {
    originalOptions = new Options()
      .initStepSize(Params.opts.initStepSize)
      .l1Reg(Params.opts.l1Reg)
      .l1RegCoeff(Params.opts.l1RegCoeff);
  }

  private void loadOptions(Options options) {
    Params.opts.initStepSize = options.initStepSize;
    Params.opts.l1Reg = options.l1Reg;
    Params.opts.l1RegCoeff = options.l1RegCoeff;
  }

  private Map<String, Double> constructGradient(double a, double b, double c, double d) {
    Map<String, Double> gradient = new HashMap<>();
    if (a != 0) gradient.put("a", a);
    if (b != 0) gradient.put("b", b);
    if (c != 0) gradient.put("c", c);
    if (d != 0) gradient.put("d", d);
    return gradient;
  }

  @Test
  public void zeroLazyL1Test() {
    saveOptions();
    {
      loadOptions(new Options().l1Reg("none").l1RegCoeff(0));
      Params params = new Params();
      assertEquals(0.0, params.getWeight("a"), EPSILON);
      assertEquals(0.0, params.getWeight("b"), EPSILON);
      params.update(constructGradient(1.0, 0, 0, 0));
      assertEquals(1.0 / Math.sqrt(1), params.getWeight("a"), EPSILON);
      assertEquals(0.0, params.getWeight("b"), EPSILON);
      params.update(constructGradient(1.0, 0, 0, 0));
      assertEquals(1.0 / Math.sqrt(1) + 1.0 / Math.sqrt(2), params.getWeight("a"), EPSILON);
      assertEquals(0.0, params.getWeight("b"), EPSILON);
      params.update(constructGradient(0.0, -2.0, 0, 0));
      assertEquals(1.0 / Math.sqrt(1) + 1.0 / Math.sqrt(2), params.getWeight("a"), EPSILON);
      assertEquals(-2.0 / Math.sqrt(4), params.getWeight("b"), EPSILON);
      params.update(constructGradient(1.0, 2.0, 0, 0));
      assertEquals(1.0 / Math.sqrt(1) + 1.0 / Math.sqrt(2) + 1.0 / Math.sqrt(3), params.getWeight("a"), EPSILON);
      assertEquals(-2.0 / Math.sqrt(4) + 2.0 / Math.sqrt(8), params.getWeight("b"), EPSILON);
    }
    {
      loadOptions(new Options().l1Reg("nonlazy").l1RegCoeff(0));
      Params params = new Params();
      assertEquals(0.0, params.getWeight("a"), EPSILON);
      assertEquals(0.0, params.getWeight("b"), EPSILON);
      params.update(constructGradient(1.0, 0, 0, 0));
      // NONLAZY will give a different result as the denominator of the AdaGrad update is incremented by 1
      assertEquals(1.0 / Math.sqrt(2), params.getWeight("a"), EPSILON);
      assertEquals(0.0, params.getWeight("b"), EPSILON);
      params.update(constructGradient(1.0, 0, 0, 0));
      assertEquals(1.0 / Math.sqrt(2) + 1.0 / Math.sqrt(3), params.getWeight("a"), EPSILON);
      assertEquals(0.0, params.getWeight("b"), EPSILON);
      params.update(constructGradient(0.0, -2.0, 0, 0));
      assertEquals(1.0 / Math.sqrt(2) + 1.0 / Math.sqrt(3), params.getWeight("a"), EPSILON);
      assertEquals(-2.0 / Math.sqrt(5), params.getWeight("b"), EPSILON);
      params.update(constructGradient(1.0, 2.0, 0, 0));
      assertEquals(1.0 / Math.sqrt(2) + 1.0 / Math.sqrt(3) + 1.0 / Math.sqrt(4), params.getWeight("a"), EPSILON);
      assertEquals(-2.0 / Math.sqrt(5) + 2.0 / Math.sqrt(9), params.getWeight("b"), EPSILON);
    }
    {
      loadOptions(new Options().l1Reg("lazy").l1RegCoeff(0));
      Params params = new Params();
      assertEquals(0.0, params.getWeight("a"), EPSILON);
      assertEquals(0.0, params.getWeight("b"), EPSILON);
      params.update(constructGradient(1.0, 0, 0, 0));
      // LAZY will give the same result as NONLAZY
      assertEquals(1.0 / Math.sqrt(2), params.getWeight("a"), EPSILON);
      assertEquals(0.0, params.getWeight("b"), EPSILON);
      params.update(constructGradient(1.0, 0, 0, 0));
      assertEquals(1.0 / Math.sqrt(2) + 1.0 / Math.sqrt(3), params.getWeight("a"), EPSILON);
      assertEquals(0.0, params.getWeight("b"), EPSILON);
      params.update(constructGradient(0.0, -2.0, 0, 0));
      assertEquals(1.0 / Math.sqrt(2) + 1.0 / Math.sqrt(3), params.getWeight("a"), EPSILON);
      assertEquals(-2.0 / Math.sqrt(5), params.getWeight("b"), EPSILON);
      params.update(constructGradient(1.0, 2.0, 0, 0));
      assertEquals(1.0 / Math.sqrt(2) + 1.0 / Math.sqrt(3) + 1.0 / Math.sqrt(4), params.getWeight("a"), EPSILON);
      assertEquals(-2.0 / Math.sqrt(5) + 2.0 / Math.sqrt(9), params.getWeight("b"), EPSILON);
    }
    loadOptions(originalOptions);
  }

  @Test
  public void nonZeroLazyL1Test() {
    saveOptions();
    {
      loadOptions(new Options().l1Reg("nonlazy").l1RegCoeff(1.0));
      Params params = new Params();
      assertEquals(0.0, params.getWeight("a"), EPSILON);
      assertEquals(0.0, params.getWeight("b"), EPSILON);
      params.update(constructGradient(2.0, 0, -3.14, 0));
      assertEquals(1.0 / Math.sqrt(5), params.getWeight("a"), EPSILON);
      assertEquals(0.0, params.getWeight("b"), EPSILON);
      params.update(constructGradient(1.0, 0, 0, 0));
      assertEquals(1.0 / Math.sqrt(5), params.getWeight("a"), EPSILON);
      assertEquals(0.0, params.getWeight("b"), EPSILON);
      params.update(constructGradient(0.0, -2.0, 0, 0));
      assertEquals(1.0 / Math.sqrt(5) - 1.0 / Math.sqrt(6), params.getWeight("a"), EPSILON);
      assertEquals(-1.0 / Math.sqrt(5), params.getWeight("b"), EPSILON);
      params.update(constructGradient(1.0, 2.0, 0, 0));
      assertEquals(1.0 / Math.sqrt(5) - 1.0 / Math.sqrt(6), params.getWeight("a"), EPSILON);
      assertEquals(0.0, params.getWeight("b"), EPSILON);
      params.update(constructGradient(0.0, 3.0, 0, 0));
      assertEquals(0.0, params.getWeight("a"), EPSILON);
      assertEquals(2.0 / Math.sqrt(18), params.getWeight("b"), EPSILON);
      params.update(constructGradient(0.0, 0.0, 0, 0));
      assertEquals(0.0, params.getWeight("a"), EPSILON);
      assertEquals(1.0 / Math.sqrt(18), params.getWeight("b"), EPSILON);
      params.update(constructGradient(-5.0, 0.0, 1.0, 0));
      assertEquals(-4.0 / Math.sqrt(32), params.getWeight("a"), EPSILON);
      assertEquals(0.0, params.getWeight("b"), EPSILON);
      params.update(constructGradient(0.0, 0.0, -1.0, 0));
      assertEquals(-3.0 / Math.sqrt(32), params.getWeight("a"), EPSILON);
      assertEquals(0.0, params.getWeight("b"), EPSILON);
      assertEquals(0.0, params.getWeight("c"), EPSILON);
    }
    // LAZY: Randomly access the features in between.
    Random r = new Random(42);
    for (double t = 1; t > 0; t -= 0.02) {
      loadOptions(new Options().l1Reg("lazy").l1RegCoeff(1.0));
      Params params = new Params();
      if (r.nextDouble() < t) assertEquals(0.0, params.getWeight("a"), EPSILON);
      if (r.nextDouble() < t) assertEquals(0.0, params.getWeight("b"), EPSILON);
      params.update(constructGradient(2.0, 0, -3.14, 0));
      if (r.nextDouble() < t) assertEquals(1.0 / Math.sqrt(5), params.getWeight("a"), EPSILON);
      if (r.nextDouble() < t) assertEquals(0.0, params.getWeight("b"), EPSILON);
      params.update(constructGradient(1.0, 0, 0, 0));
      if (r.nextDouble() < t) assertEquals(1.0 / Math.sqrt(5), params.getWeight("a"), EPSILON);
      if (r.nextDouble() < t) assertEquals(0.0, params.getWeight("b"), EPSILON);
      params.update(constructGradient(0.0, -2.0, 0, 0));
      if (r.nextDouble() < t) assertEquals(1.0 / Math.sqrt(5) - 1.0 / Math.sqrt(6), params.getWeight("a"), EPSILON);
      if (r.nextDouble() < t) assertEquals(-1.0 / Math.sqrt(5), params.getWeight("b"), EPSILON);
      params.update(constructGradient(1.0, 2.0, 0, 0));
      if (r.nextDouble() < t) assertEquals(1.0 / Math.sqrt(5) - 1.0 / Math.sqrt(6), params.getWeight("a"), EPSILON);
      if (r.nextDouble() < t) assertEquals(0.0, params.getWeight("b"), EPSILON);
      params.update(constructGradient(0.0, 3.0, 0, 0));
      if (r.nextDouble() < t) assertEquals(0.0, params.getWeight("a"), EPSILON);
      if (r.nextDouble() < t) assertEquals(2.0 / Math.sqrt(18), params.getWeight("b"), EPSILON);
      params.update(constructGradient(0.0, 0.0, 0, 0));
      if (r.nextDouble() < t) assertEquals(0.0, params.getWeight("a"), EPSILON);
      if (r.nextDouble() < t) assertEquals(1.0 / Math.sqrt(18), params.getWeight("b"), EPSILON);
      params.update(constructGradient(-5.0, 0.0, 1.0, 0));
      if (r.nextDouble() < t) assertEquals(-4.0 / Math.sqrt(32), params.getWeight("a"), EPSILON);
      if (r.nextDouble() < t) assertEquals(0.0, params.getWeight("b"), EPSILON);
      params.update(constructGradient(0.0, 0.0, -1.0, 0));
      if (r.nextDouble() < t) assertEquals(-3.0 / Math.sqrt(32), params.getWeight("a"), EPSILON);
      if (r.nextDouble() < t) assertEquals(0.0, params.getWeight("b"), EPSILON);
      if (r.nextDouble() < t) assertEquals(0.0, params.getWeight("c"), EPSILON);
    }
    loadOptions(originalOptions);
  }
}
