package edu.stanford.nlp.sempre;

import fig.basic.LispTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Given a token at a particular position, keep it if its POS tag is from a
 * select set.
 *
 * @author Andrew Chou
 */
public class FilterPosTagFn extends SemanticFn {
  // Accepted POS tags (e.g., NNP, NNS, etc.)
  List<String> posTags = new ArrayList<String>();
  String mode;

  public void init(LispTree tree) {
    super.init(tree);
    mode = tree.child(1).value;
    if (!mode.equals("span") && !mode.equals("token"))
      throw new RuntimeException("Illegal description for whether to filter by token or span: " + tree.child(1).value);

    for (int j = 2; j < tree.children.size(); j++)
      posTags.add(tree.child(j).value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FilterPosTagFn that = (FilterPosTagFn) o;
    if (!mode.equals(that.mode)) return false;
    if (!posTags.equals(that.posTags)) return false;
    return true;
  }

  public List<Derivation> call(Example ex, Callable c) {
    if (mode.equals("span"))
      return callSpan(ex, c);
    return callToken(ex, c);
  }

  private List<Derivation> callToken(Example ex, Callable c) {
    // Only apply to single tokens
    if (c.getEnd() - c.getStart() != 1)
      return Collections.emptyList();

    String posTag = ex.posTag(c.getStart());
    if (posTags.contains(posTag)) {
      return Collections.singletonList(
          new Derivation.Builder()
              .withCallable(c)
              .withFormulaFrom(c.child(0))
              .createDerivation());
    }
    return Collections.emptyList();
  }

  private List<Derivation> callSpan(Example ex, Callable c) {
    String posTag = ex.posTag(c.getStart());
    // Check that it's an acceptable tag
    if (!posTags.contains(posTag)) return Collections.emptyList();
    // Check to make sure that all the tags are the same
    for (int j = c.getStart() + 1; j < c.getEnd(); j++) {
      if (!posTag.equals(ex.posTag(j)))
        return Collections.emptyList();
    }
    // Make sure that the whole POS sequence is matched
    if (c.getStart() > 0 && posTag.equals(ex.posTag(c.getStart() - 1)))
      return Collections.emptyList();
    if (c.getEnd() < ex.numTokens() && posTag.equals(ex.posTag(c.getEnd())))
      return Collections.emptyList();

    assert (c.getChildren().size() == 1) : c.getChildren();
    return Collections.singletonList(
        new Derivation.Builder()
            .withCallable(c)
            .withFormulaFrom(c.child(0))
            .createDerivation());
  }
}
