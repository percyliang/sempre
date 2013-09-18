package edu.stanford.nlp.sempre;

import fig.basic.LispTree;
import fig.basic.LogInfo;
import fig.basic.Option;

import java.util.Collections;
import java.util.List;

/**
 * Given a particular position i, return the ith element on the RHS of the
 * derivation's rule.
 *
 * @author Andrew Chou
 */
public class SelectFn extends SemanticFn {
  public static class Options {
    @Option(gloss = "Verbose") public int verbose = 0;
  }

  public static Options opts = new Options();

  // Which child derivation to select and return.
  int position = -1;

  public SelectFn() { }

  public SelectFn(int position) {
    init(LispTree.proto.newList("SelectFn", position + ""));
  }

  public void init(LispTree tree) {
    super.init(tree);
    this.position = Integer.valueOf(tree.child(1).value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SelectFn selectFn = (SelectFn) o;
    if (position != selectFn.position) return false;
    return true;
  }

  public List<Derivation> call(Example ex, Callable c) {
    FeatureVector features = new FeatureVector();

    // TODO: move into FeatureExtractor
    if (FeatureExtractor.containsDomain("skipPos")) {
      for (int i = 0; i < c.getChildren().size(); ++i) {
        if (i != this.position) {
          Derivation child = c.child(i);
          for (int index = child.start; index < child.end; ++index) {
            List<String> posTags = ex.languageInfo.posTags;
            features.add("skipPos", posTags.get(index));
            if (opts.verbose > 0) {
              LogInfo.logs(
                  "SelectFn: adding pos-skipping feature, pos: %s, word: %s",
                  posTags.get(index), ex.languageInfo.tokens.get(index));
            }
          }
        }
      }
    }
    Derivation deriv = new Derivation.Builder()
        .withCallable(c)
        .withFormulaFrom(c.child(this.position))
        .localFeatureVector(features)
        .createDerivation();
    return Collections.singletonList(deriv);
  }
}
