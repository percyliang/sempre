package edu.stanford.nlp.sempre;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import fig.basic.LispTree;
import fig.basic.Option;
import fig.basic.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * A semantic function takes a sequence of child derivations and produces a set
 * of parent derivations.
 *
 * @author Percy Liang
 */
public abstract class SemanticFn {
  public static class Options {
    @Option(gloss = "Whether or not to add to Derivation.localChoices during " +
        "function application.")
    public boolean trackLocalChoices = false;
  }

  public static final Options opts = new Options();

  // Used to define this SemanticFn (right now, mostly for printing).
  private LispTree tree;

  // Initialize the semantic function with any arguments (optional).
  // Override this function and call super.init(tree);
  public void init(LispTree tree) {
    this.tree = tree;
  }

  public static interface Callable {
    public String getCat();
    public int getStart();
    public int getEnd();
    public Rule getRule();
    public List<Derivation> getChildren();
    public Derivation child(int i);
    public String childStringValue(int i);
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

  // Important: all subclasses are responsible for:
  //  - setting deriv.semTypes
  //  - setting deriv.auxSemTypes
  //  - calling deriv.setFormula()
  // from applying this semantic function to args.
  public abstract List<Derivation> call(Example ex, Callable c);

  public LispTree toLispTree() { return tree; }

  public static SemanticFn fromLispTree(LispTree tree) {
    String name = tree.child(0).value;
    SemanticFn fn = null;

    if (fn == null)
      fn = (SemanticFn) Utils.newInstanceHard(Grammar.opts.semanticFnPackage + "." + name);
    if (fn == null)
      throw new RuntimeException("Invalid SemanticFn name: " + name);

    fn.init(tree);
    return fn;
  }

  @JsonValue
  @Override
  public String toString() { return tree.toString(); }

  @JsonCreator
  public static SemanticFn fromString(String s) {
    return fromLispTree(LispTree.proto.parseFromString(s));
  }

  @Override abstract public boolean equals(Object o);
}
