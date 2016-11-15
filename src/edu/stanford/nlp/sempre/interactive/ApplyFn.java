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
  
  // utilities for grammar induction
  public static final Formula combineFormula =  Formulas.fromLispTree(LispTree.proto.parseFromString("(lambda a1 (lambda a2 (:s (var a1) (var a2))))"));
  public static Rule combineRule() {
    return new Rule("$Action", Lists.newArrayList("$Action", "$Action"), new ApplyFn(combineFormula));
  }
  public static Derivation combine(Derivation d1, Derivation d2) {
    Formula f = Formulas.lambdaApply((LambdaFormula)combineFormula, d1.getFormula());
    f = Formulas.lambdaApply((LambdaFormula)f, d2.getFormula());
    List<Derivation> children = Lists.newArrayList(d1, d2);
    Derivation res = new Derivation.Builder()
        .withCallable(new SemanticFn.CallInfo("$Actions", -1, -1, combineRule(), ImmutableList.copyOf(children)))
        .formula(f)
        .createDerivation();
    return res;
  }
}
