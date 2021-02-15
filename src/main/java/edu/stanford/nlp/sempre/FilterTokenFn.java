package edu.stanford.nlp.sempre;

import fig.basic.LispTree;

import java.util.ArrayList;
import java.util.List;

/**
 * Given a token at a particular position, keep it is from a select set.
 *
 * @author ppasupat
 */
public class FilterTokenFn extends SemanticFn {
  List<String> acceptableTokens = new ArrayList<>();
  String mode;

  public void init(LispTree tree) {
    super.init(tree);
    mode = tree.child(1).value;
    if (!mode.equals("token") && !mode.equals("lemma"))
      throw new RuntimeException("Illegal description for FilterTokenFn: " + mode);
    for (int j = 2; j < tree.children.size(); j++) {
      acceptableTokens.add(tree.child(j).value);
    }
  }

  public DerivationStream call(final Example ex, final Callable c) {
    return new SingleDerivationStream() {
      @Override
      public Derivation createDerivation() {
        if (!isValid(ex, c))
          return null;
        else {
          return new Derivation.Builder()
                  .withCallable(c)
                  .withFormulaFrom(c.child(0))
                  .createDerivation();
        }
      }
    };
  }

  private boolean isValid(Example ex, Callable c) {
    if (c.getEnd() - c.getStart() != 1) return false;
    String token;
    if ("token".equals(mode))
      token = ex.token(c.getStart());
    else
      token = ex.lemmaToken(c.getStart());
    return acceptableTokens.contains(token);
  }
}
