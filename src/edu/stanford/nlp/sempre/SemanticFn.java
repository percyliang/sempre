package edu.stanford.nlp.sempre;

import fig.basic.LispTree;
import fig.basic.Option;
import fig.basic.Utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A semantic function takes a sequence of child derivations and produces a set
 * of parent derivations.  This is a pretty general concept, which can be used to:
 * - Generating candidates (lexicon)
 * - Do simple combination
 * - Filtering of derivations
 *
 * To override implement this function, you just need to fill out the call() function.
 *
 * @author Percy Liang
 */
public abstract class SemanticFn {
  public static class Options {
    @Option(gloss = "Whether or not to add to Derivation.localChoices during " +
        "function application (for debugging only).")
    public boolean trackLocalChoices = false;
  }

  public static final Options opts = new Options();

  // Used to define this SemanticFn.
  private LispTree tree;

  // Initialize the semantic function with any arguments (optional).
  // Override this function and call super.init(tree);
  public void init(LispTree tree) {
    this.tree = tree;
  }

  public interface Callable {
    String getCat();
    int getStart();
    int getEnd();
    Rule getRule();
    List<Derivation> getChildren();
    Derivation child(int i);
    String childStringValue(int i);
  }

  public static class CallInfo implements Callable {
    final String cat;
    final int start;
    final int end;
    final Rule rule;
    final List<Derivation> children;
    public CallInfo(String cat, int start, int end, Rule rule, List<Derivation> children) {
      this.cat = cat;
      this.start = start;
      this.end = end;
      this.rule = rule;
      this.children = children;
    }
    public String getCat() { return cat; }
    public int getStart() { return start; }
    public int getEnd() { return end; }
    public Rule getRule() { return rule; }
    public List<Derivation> getChildren() { return children; }
    public Derivation child(int i) { return children.get(i); }
    public String childStringValue(int i) {
      return Formulas.getString(children.get(i).formula);
    }

    public static final CallInfo NULL_INFO =
      new CallInfo("", -1, -1, Rule.nullRule, new ArrayList<Derivation>());
  }

  // Main entry point: return a stream of Derivations (possibly none).
  // The computation of the Derivations should be done lazily.
  public abstract DerivationStream call(Example ex, Callable c);

  public LispTree toLispTree() { return tree; }
  @Override public String toString() { return tree.toString(); }

  // default does nothing
  public void addFeedback(Example ex) { return; }

  // default does nothing
  public void sortOnFeedback(Params params) { return; }

  /*
   * Filter on type data to save time.
   * Return a collection of DerivationGroup. The rule will be applied on each DerivationGroup.
   *
   * See an example in tables.grow.ApplyFn
   */
  public boolean supportFilteringOnTypeData() { return false; }
  public Collection<ChildDerivationsGroup> getFilteredDerivations(
      List<Derivation> derivations1, List<Derivation> derivations2) {
    throw new UnsupportedOperationException();
  }

}
