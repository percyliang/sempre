package edu.stanford.nlp.sempre.interactive;

import java.util.List;

import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.DerivationStream;
import edu.stanford.nlp.sempre.Example;
import edu.stanford.nlp.sempre.Formula;
import edu.stanford.nlp.sempre.Formulas;
import edu.stanford.nlp.sempre.LambdaFormula;
import edu.stanford.nlp.sempre.SemanticFn;
import edu.stanford.nlp.sempre.SingleDerivationStream;
import fig.basic.LispTree;
import fig.basic.Option;

/**
 * Take any number of arguments and apply them to the lambda expression given in
 * this SemanticFn TODO: type inference, some function applications
 * 
 * @author sidaw
 */
public class ApplyFn extends SemanticFn {
  public static class Options {
    @Option(gloss = "verbosity")
    public int verbose = 0;
  }

  public static Options opts = new Options();

  Formula formula;

  @Override
  public void init(LispTree tree) {
    super.init(tree);
    formula = Formulas.fromLispTree(tree.child(1));
  }

  public Formula getFormula() {
    return formula;
  }

  public ApplyFn() {
  }

  public ApplyFn(Formula f) {
    formula = f;
  }

  @Override
  public DerivationStream call(final Example ex, final Callable c) {
    return new SingleDerivationStream() {
      @Override
      public Derivation createDerivation() {
        List<Derivation> args = c.getChildren();
        Formula f = Formulas.fromLispTree(formula.toLispTree());
        for (Derivation arg : args) {
          if (!(f instanceof LambdaFormula))
            throw new RuntimeException("Expected LambdaFormula, but got " + f + "; initial: " + formula);
          f = Formulas.lambdaApply((LambdaFormula) f, arg.getFormula());
        }
        Derivation res = new Derivation.Builder().withCallable(c).formula(f).createDerivation();
        return res;
      }
    };
  }
}
