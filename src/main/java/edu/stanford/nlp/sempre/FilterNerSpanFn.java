package edu.stanford.nlp.sempre;

import fig.basic.LispTree;

import java.util.ArrayList;
import java.util.List;

/**
 * Given a phrase at a particular position, keep it if its NER tags all match
 * and are from a select set.
 *
 * @author Andrew Chou
 */
public class FilterNerSpanFn extends SemanticFn {
  // Accepted NER tags (PERSON, LOCATION, ORGANIZATION, etc)
  List<String> acceptableNerTags = new ArrayList<>();

  public void init(LispTree tree) {
    super.init(tree);
    for (int j = 1; j < tree.children.size(); j++)
      acceptableNerTags.add(tree.child(j).value);
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
    String nerTag = ex.languageInfo.nerTags.get(c.getStart());

    // Check that it's an acceptable tag
    if (!acceptableNerTags.contains(nerTag))
      return false;

    // Check to make sure that all the tags are the same
    for (int j = c.getStart() + 1; j < c.getEnd(); j++)
      if (!nerTag.equals(ex.languageInfo.nerTags.get(j)))
        return false;

    // Make sure that the whole NE is matched
    if (c.getStart() > 0 && nerTag.equals(ex.languageInfo.nerTags.get(c.getStart() - 1)))
      return false;

    if (c.getEnd() < ex.languageInfo.nerTags.size() &&
            nerTag.equals(ex.languageInfo.nerTags.get(c.getEnd())))
      return false;
    assert (c.getChildren().size() == 1) : c.getChildren();
    return true;
  }
}
