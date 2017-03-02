package edu.stanford.nlp.sempre.interactive;

import java.util.*;

import org.testng.collections.Lists;

import com.google.common.collect.ImmutableList;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.SemanticFn.Callable;
import edu.stanford.nlp.sempre.Derivation;
import fig.basic.LispTree;
import fig.basic.MapUtils;
import fig.basic.Option;

/**
 * Take any number of arguments and apply them to the lambda expression given in this SemanticFn
 *
 * @author sidaw
 */
public class ApplyFn extends SemanticFn {
  public static class Options {
    @Option(gloss = "verbosity") public int verbose = 0;
  }
  public static Options opts = new Options();

  Formula formula;

  public void init(LispTree tree) {
    super.init(tree);
    formula = Formulas.fromLispTree(tree.child(1));
  }

  public Formula getFormula() {
    return formula;
  }

  public ApplyFn() {}
  
  public ApplyFn(Formula f) {
    formula = f;
  }

  public DerivationStream call(final Example ex, final Callable c) {
    return new SingleDerivationStream() {
      @Override
      public Derivation createDerivation() {
        List<Derivation> args = c.getChildren();
        Formula f = Formulas.fromLispTree(formula.toLispTree());
        for (Derivation arg : args) {
          if (!(f instanceof LambdaFormula))
            throw new RuntimeException("Expected LambdaFormula, but got " + f + "; initial: " + formula);
          f = Formulas.lambdaApply((LambdaFormula)f, arg.getFormula());
        }
        Derivation res = new Derivation.Builder()
                .withCallable(c)
                .formula(f)
                .createDerivation();
        return res;
      }
    };
  }
}
