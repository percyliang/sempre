package edu.stanford.nlp.sempre;

import fig.basic.LispTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Given a phrase at a particular position, keep it if its NER tags all match
 * and are from a select set.
 *
 * @author Andrew Chou
 */
public class FilterNerSpanFn extends SemanticFn {
  // Accepted NER tags (PERSON, LOCATION, ORGANIZATION, etc)
  List<String> acceptableNerTags = new ArrayList<String>();

  public void init(LispTree tree) {
    super.init(tree);
    for (int j = 1; j < tree.children.size(); j++)
      acceptableNerTags.add(tree.child(j).value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FilterNerSpanFn that = (FilterNerSpanFn) o;
    if (!acceptableNerTags.equals(that.acceptableNerTags)) return false;
    return true;
  }

  public List<Derivation> call(Example ex, Callable c) {
    String nerTag = ex.languageInfo.nerTags.get(c.getStart());

    // Check that it's an acceptable tag
    if (!acceptableNerTags.contains(nerTag))
      return Collections.emptyList();

    // Check to make sure that all the tags are the same
    for (int j = c.getStart() + 1; j < c.getEnd(); j++)
      if (!nerTag.equals(ex.languageInfo.nerTags.get(j)))
        return Collections.emptyList();

    // Make sure that the whole NE is matched
    if (c.getStart() > 0 && nerTag.equals(ex.languageInfo.nerTags.get(c.getStart() - 1)))
      return Collections.emptyList();

    if (c.getEnd() < ex.languageInfo.nerTags.size() &&
        nerTag.equals(ex.languageInfo.nerTags.get(c.getEnd())))
      return Collections.emptyList();

    assert (c.getChildren().size() == 1) : c.getChildren();
    return Collections.singletonList(
        new Derivation.Builder()
            .withCallable(c)
            .withFormulaFrom(c.child(0))
            .createDerivation());
  }
}
