package edu.stanford.nlp.sempre;

import fig.basic.LispTree;

import java.util.ArrayList;
import java.util.List;

/**
 * Given a token at a particular position, keep it if its POS tag is from a
 * select set.
 *
 * @author Andrew Chou
 */
public class FilterPosTagFn extends SemanticFn {
  // Accepted POS tags (e.g., NNP, NNS, etc.)
  List<String> posTags = new ArrayList<>();
  String mode;
  boolean reverse;

  public void init(LispTree tree) {
    super.init(tree);
    mode = tree.child(1).value;
    if (!mode.equals("span") && !mode.equals("token"))
      throw new RuntimeException("Illegal description for whether to filter by token or span: " + tree.child(1).value);

    for (int j = 2; j < tree.children.size(); j++) {
      // Optionally, we can use a reverse filter (only reject certain tags)
      if (j == 2 && tree.child(2).value.equals("reverse")) {
        reverse = true;
        continue;
      }
      posTags.add(tree.child(j).value);
    }
  }

  public DerivationStream call(final Example ex, final Callable c) {
    return new SingleDerivationStream() {
      @Override
      public Derivation createDerivation() {
        if (mode.equals("span"))
          return callSpan(ex, c);
        else
          return callToken(ex, c);
      }
    };
  }

  private Derivation callToken(Example ex, Callable c) {
    // Only apply to single tokens
    String posTag = ex.posTag(c.getStart());
    if (c.getEnd() - c.getStart() != 1 ||
            (!posTags.contains(posTag) ^ reverse))
      return null;
    else {
      return new Derivation.Builder()
              .withCallable(c)
              .withFormulaFrom(c.child(0))
              .createDerivation();
    }
  }

  private Derivation callSpan(Example ex, Callable c) {
    if (isValidSpan(ex, c)) {
      return new Derivation.Builder()
              .withCallable(c)
              .withFormulaFrom(c.child(0))
              .createDerivation();
    } else {
      return null;
    }
  }

  private boolean isValidSpan(Example ex, Callable c) {
    if (reverse) {
      for (int j = c.getStart(); j < c.getEnd(); j++) {
        if (posTags.contains(ex.posTag(j)))
          return false;
      }
      return true;
    }
    String posTag = ex.posTag(c.getStart());
    // Check that it's an acceptable tag
    if (!posTags.contains(posTag))
      return false;
    // Check to make sure that all the tags are the same
    for (int j = c.getStart() + 1; j < c.getEnd(); j++) {
      if (!posTag.equals(ex.posTag(j)))
        return false;
    }
    // Make sure that the whole POS sequence is matched
    if (c.getStart() > 0 && posTag.equals(ex.posTag(c.getStart() - 1)))
      return false;
    if (c.getEnd() < ex.numTokens() && posTag.equals(ex.posTag(c.getEnd())))
      return false;
    assert (c.getChildren().size() == 1) : c.getChildren();
    return true;
  }
}
