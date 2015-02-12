package edu.stanford.nlp.sempre;

import java.util.*;
import fig.basic.*;

/**
 * Produces predicates (like LexiconFn) but do it from the logical forms
 * in the context (inspects the ContextValue of the example).
 *
 * Takes depth, restrictType, and forbiddenTypes arguments allowing you
 * to specify the depth/size and type of (formula) subtrees that you want to
 * extract from the context.
 *
 * ONLY USE WITH TYPES!!
 *
 * E.g.,
 *
 * (rule $X (context) (ContextFn (depth 0) (type fb:type.any))
 * would extract any unary/entity.
 *
 * (rule $X (context) (ContextFn (depth 1) (type (-> fb:type.any
 * fb:type.any)) (forbidden (-> fb:type.any fb:type.something)) would
 * extract all binaries except those with arg1 of type fb:type.something
 *
 * @author William Hamilton
 */
// TODO(Will): Reintegrate useful functionality from old implementation.
public class ContextFn extends SemanticFn {
  // the depth/size of subtrees to extract
  private int depth;
  // the type that you want to extract
  private SemType restrictType = SemType.topType;

  // set of types to not extract (overrides restrictType).
  // For example, if restrict type is very general (e.g., (-> type.any type.any))
  // and you don't want some specific subtype  (e.g., (-> type.something type.any))
  // then you would say specify (forbidden (-> type.something type.any))
  // and all subtypes of (-> type.any type.any) would be permissible
  // except the forbidden one(s).
  private Set<SemType> forbiddenTypes = new HashSet<SemType>();

  public void init(LispTree tree) {
    super.init(tree);
    for (int i = 1; i < tree.children.size(); i++) {
      LispTree arg = tree.child(i);
      if ("type".equals(arg.child(0).value)) {
        restrictType = SemType.fromLispTree(arg.child(1));
      } else if ("depth".equals(arg.child(0).value)) {
        depth = Integer.parseInt(arg.child(1).value);
      } else if ("forbidden".equals(arg.child(0).value)) {
        forbiddenTypes.add(SemType.fromLispTree(arg.child(1)));
      } else {
        throw new RuntimeException("Unknown argument: " + arg);
      }
    }
  }

  public DerivationStream call(final Example ex, final Callable c) {
    return new MultipleDerivationStream() {
      int index = 0;
      List<Formula> formulas;

      public Derivation createDerivation() {
        if (ex.context == null) return null;

        if (formulas == null) {
          formulas = new ArrayList<Formula>();
          for (int i = ex.context.exchanges.size() - 1; i >= 0; i--) {
            ContextValue.Exchange e = ex.context.exchanges.get(i);
            extractFormulas(e.formula.toLispTree());
          }
        }
        if (index >= formulas.size()) return null;
        Formula formula = formulas.get(index++);
        for (SemType forbiddenType : forbiddenTypes) {
          if (TypeInference.inferType(formula).meet(forbiddenType).isValid())
            return null;
        }
        return new Derivation.Builder()
                .withCallable(c)
                .formula(formula)
                .type(TypeInference.inferType(formula))
                .createDerivation();
      }

      private void addFormula(Formula formula) {
        if (formulas.contains(formula))
          return;
        formulas.add(formula);
      }

      // Extract from the logical form.
      private void extractFormulas(LispTree formula) {
        if (correctDepth(formula, 0) && typeCheck(formula)) {
          addFormula(Formulas.fromLispTree(formula));
        }
        if (formula.isLeaf())
          return;
        for (LispTree child : formula.children)
          extractFormulas(child);
      }

      private boolean correctDepth(LispTree formula, int currentLevel) {
        if (formula.isLeaf()) {
          return currentLevel == depth;
        } else {
          boolean isCorrect = true;
          for (LispTree child : formula.children)
            isCorrect = isCorrect && correctDepth(child, currentLevel + 1);
          return isCorrect;
        }
      }

      private boolean typeCheck(LispTree treeFormula) {
        Formula formula = Formulas.fromLispTree(treeFormula);
        SemType type = TypeInference.inferType(formula);
        type = restrictType.meet(type);
        return type.isValid();
      }

    };
  }
}
