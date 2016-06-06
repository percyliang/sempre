package edu.stanford.nlp.sempre.tables.grow;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.ScopedFormula;
import fig.basic.*;

/**
 * Formula s [finite set] ==> ScopedFormula(s, identity function)
 *
 * @author ppasupat
 */
public class BeginGrowFn extends SemanticFn {
  public static class Options {
    @Option(gloss = "verbosity") public int verbose = 0;
  }
  public static Options opts = new Options();

  public void init(LispTree tree) {
    super.init(tree);
  }

  public static final Formula IDENTITY = new LambdaFormula("x", new VariableFormula("x"));

  @Override
  public DerivationStream call(Example ex, Callable c) {
    return new SingleDerivationStream() {
      @Override
      public Derivation createDerivation() {
        if (c.getChildren().size() != 1)
          throw new RuntimeException("Wrong number of argument: expected 1; got " + c.getChildren().size());
        ScopedFormula scoped = new ScopedFormula(c.child(0).formula, IDENTITY);
        return new Derivation.Builder().withCallable(c)
            .formula(scoped).type(TypeInference.inferType(scoped.relation)).createDerivation();
      }
    };
  }
}
