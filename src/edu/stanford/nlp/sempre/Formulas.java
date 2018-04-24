package edu.stanford.nlp.sempre;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import fig.basic.LispTree;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utilities for working with Formulas.
 *
 * @author Percy Liang
 */
public abstract class Formulas {
  public static Formula fromLispTree(LispTree tree) {
    // Try to interpret as ValueFormula
    if (tree.isLeaf())  // Leaves are name values
      return new ValueFormula<NameValue>(new NameValue(tree.value, null));
    Value value = Values.fromLispTreeOrNull(tree);  // General case
    if (value != null)
      return new ValueFormula<Value>(value);

    String func = tree.child(0).value;
    if (func != null) {
      if (func.equals("var"))
        return new VariableFormula(tree.child(1).value);
      if (func.equals("lambda"))
        return new LambdaFormula(tree.child(1).value, fromLispTree(tree.child(2)));
      if (func.equals("mark"))
        return new MarkFormula(tree.child(1).value, fromLispTree(tree.child(2)));
      if (func.equals("not"))
        return new NotFormula(fromLispTree(tree.child(1)));
      if (func.equals("reverse"))
        return new ReverseFormula(fromLispTree(tree.child(1)));
      if (func.equals("call")) {
        Formula callFunc = fromLispTree(tree.child(1));
        List<Formula> args = Lists.newArrayList();
        for (int i = 2; i < tree.children.size(); i++)
          args.add(fromLispTree(tree.child(i)));
        return new CallFormula(callFunc, args);
      }
    }

    { // Merge: (and (fb:type.object.type fb:people.person) (fb:people.person.children fb:en.barack_obama))
      MergeFormula.Mode mode = MergeFormula.parseMode(func);
      if (mode != null)
        return new MergeFormula(mode, fromLispTree(tree.child(1)), fromLispTree(tree.child(2)));
    }

    { // Aggregate: (count (fb:type.object.type fb:people.person))
      AggregateFormula.Mode mode = AggregateFormula.parseMode(func);
      if (mode != null)
        return new AggregateFormula(mode, fromLispTree(tree.child(1)));
    }

    { // Superlative: (argmax 1 1 (fb:type.object.type fb:people.person) (lambda x (!fb:people.person.height_meters (var x))))
      SuperlativeFormula.Mode mode = SuperlativeFormula.parseMode(func);
      if (mode != null) {
        Formula rank = parseIntToFormula(tree.child(1));
        Formula count = parseIntToFormula(tree.child(2));
        return new SuperlativeFormula(
            mode,
            rank,
            count,
            fromLispTree(tree.child(3)),
            fromLispTree(tree.child(4)));
      }
    }

    { // Arithmetic: (- (!fb:people.person.height_meters (var x)) (!fb:people.person.height_meters (var y)))
      ArithmeticFormula.Mode mode = ArithmeticFormula.parseMode(func);
      if (mode != null)
        return new ArithmeticFormula(mode, fromLispTree(tree.child(1)), fromLispTree(tree.child(2)));
    }
    
    { // ActionFormula
      ActionFormula.Mode mode = ActionFormula.parseMode(func);
      if (mode != null) {
        List<Formula> args = Lists.newArrayList();
        for (int i = 1; i < tree.children.size(); i++)
          args.add(fromLispTree(tree.child(i)));
        return new ActionFormula(mode, args);
      }
    }

    // Default is join: (fb:type.object.type fb:people.person)
    if (tree.children.size() != 2)
      throw new RuntimeException("Invalid number of arguments for join (want 2): " + tree);
    return new JoinFormula(fromLispTree(tree.child(0)), fromLispTree(tree.child(1)));
  }

  // Special case to enable "argmax 1 1" rather than "argmax (number 1) (number 1)"
  private static Formula parseIntToFormula(LispTree tree) {
    try {
      int i = Integer.parseInt(tree.value);
      double d = i;
      NumberValue value = new NumberValue(d);
      return new ValueFormula(value);
    } catch (NumberFormatException e) {
      Formula formula = fromLispTree(tree);
      if (!(formula instanceof PrimitiveFormula))
        throw new RuntimeException("Rank and count of argmax must be variables or numbers");
      return formula;
    }
  }

  // Replace occurrences of the variable reference |var| with |formula|.
  public static Formula substituteVar(Formula formula, final String var, final Formula replaceFormula) {
    return formula.map(
        new Function<Formula, Formula>() {
          public Formula apply(Formula formula) {
            if (formula instanceof VariableFormula) {  // Replace variable
              String name = ((VariableFormula) formula).name;
              return var.equals(name) ? replaceFormula : formula;
            } else if (formula instanceof LambdaFormula) {
              if (((LambdaFormula) formula).var.equals(var)) // |var| is bound, so don't substitute inside
                return formula;
            }
            return null;
          }
        });
  }

  // Replace top-level occurrences of |searchFormula| inside |formula| with |replaceFormula|.
  public static Formula substituteFormula(Formula formula, final Formula searchFormula, final Formula replaceFormula) {
    return formula.map(
        new Function<Formula, Formula>() {
          public Formula apply(Formula formula) {
            if (formula.equals(searchFormula)) return replaceFormula;
            return null;
          }
        });
  }

  // Beta-reduction.
  public static Formula lambdaApply(LambdaFormula func, Formula arg) {
    return substituteVar(func.body, func.var, arg);
  }

  // Apply all the nested LambdaFormula's.
  public static Formula betaReduction(Formula formula) {
    return formula.map(
        new Function<Formula, Formula>() {
          public Formula apply(Formula formula) {
            if (formula instanceof JoinFormula) {
              Formula relation = betaReduction(((JoinFormula) formula).relation);
              Formula child = ((JoinFormula) formula).child;
              if (relation instanceof LambdaFormula)
                return betaReduction(lambdaApply((LambdaFormula) relation, child));
            }
            return null;
          }
        });
  }

  // Return whether |formula| contains a free instance of |var|.
  public static boolean containsFreeVar(Formula formula, VariableFormula var) {
    if (formula instanceof PrimitiveFormula)
      return formula.equals(var);
    if (formula instanceof MergeFormula) {
      MergeFormula merge = (MergeFormula) formula;
      return containsFreeVar(merge.child1, var) || containsFreeVar(merge.child2, var);
    }
    if (formula instanceof JoinFormula) {
      JoinFormula join = (JoinFormula) formula;
      return containsFreeVar(join.relation, var) || containsFreeVar(join.child, var);
    }
    if (formula instanceof LambdaFormula) {
      LambdaFormula lambda = (LambdaFormula) formula;
      if (lambda.var.equals(var.name)) return false;  // Blocked by bound variable
      return containsFreeVar(lambda.body, var);
    }
    if (formula instanceof MarkFormula) {
      MarkFormula mark = (MarkFormula) formula;
      // Note: marks are transparent, unlike lambdas
      return containsFreeVar(mark.body, var);
    }
    if (formula instanceof ReverseFormula) {
      return containsFreeVar(((ReverseFormula) formula).child, var);
    }
    if (formula instanceof AggregateFormula) {
      return containsFreeVar(((AggregateFormula) formula).child, var);
    }
    if (formula instanceof ArithmeticFormula) {
      return containsFreeVar(((ArithmeticFormula) formula).child1, var) || containsFreeVar(((ArithmeticFormula) formula).child2, var);
    }
    if (formula instanceof SuperlativeFormula) {
      SuperlativeFormula superlative = (SuperlativeFormula) formula;
      return containsFreeVar(superlative.rank, var) || containsFreeVar(superlative.count, var) || containsFreeVar(superlative.head, var) || containsFreeVar(superlative.relation, var);
    }
    if (formula instanceof NotFormula) {
      NotFormula notForm = (NotFormula) formula;
      return containsFreeVar(notForm.child, var);
    }
    throw new RuntimeException("Unhandled: " + formula);
  }

  // TODO(joberant): use Formula.map, and use CanonicalNames.isReverseProperty, etc.
  public static Set<String> extractAtomicFreebaseElements(Formula formula) {
    Set<String> res = new HashSet<>();
    LispTree formulaTree = formula.toLispTree();
    extractAtomicFreebaseElements(formulaTree, res);
    return res;
  }
  private static void extractAtomicFreebaseElements(LispTree formulaTree,
                                                    Set<String> res) {
    if (formulaTree.isLeaf()) {  // base
      if (formulaTree.value.startsWith("fb:"))
        res.add(formulaTree.value);
      else if (formulaTree.value.startsWith("!fb:"))
        res.add(formulaTree.value.substring(1));
    } else {  // recursion
      for (LispTree child : formulaTree.children) {
        extractAtomicFreebaseElements(child, res);
      }
    }
  }

  // TODO(jonathan): move to feature extractor (this function doesn't seem fundamental)
  public static boolean isCountFormula(Formula formula) {
    if (formula instanceof AggregateFormula)
      return ((AggregateFormula) formula).mode == AggregateFormula.Mode.count;
    if (formula instanceof JoinFormula) {
      Formula relation = ((JoinFormula) formula).relation;
      if (relation instanceof LambdaFormula) {
        Formula l = ((LambdaFormula) relation).body;
        if (l instanceof AggregateFormula)
          return ((AggregateFormula) l).mode == AggregateFormula.Mode.count;
      }
    }
    return false;
  }

  public static String getString(Formula formula) {
    if (formula instanceof ValueFormula) {
      Value value = ((ValueFormula) formula).value;
      if (value instanceof StringValue)
        return ((StringValue) value).value;
      if (value instanceof NameValue)
        return ((NameValue) value).id;
      if (value instanceof NumberValue)
        return ((NumberValue) value).value + "";
    } else if (formula instanceof VariableFormula) {
      return ((VariableFormula) formula).name;
    }
    return null;
  }

  public static String getNameId(Formula formula) {
    if (formula instanceof ValueFormula) {
      Value value = ((ValueFormula) formula).value;
      if (value instanceof NameValue)
        return ((NameValue) value).id;
    }
    return null;
  }

  public static double getDouble(Formula formula) {
    if (formula instanceof ValueFormula) {
      Value value = ((ValueFormula) formula).value;
      if (value instanceof NumberValue)
        return ((NumberValue) value).value;
    }
    return Double.NaN;
  }

  public static int getInt(Formula formula) {
    return (int) getDouble(formula);
  }

  /**
   * If the formula represents a binary (e.g., fb:a.b.c or <=),
   *   return the ID of the binary as a string.
   * If the formula represents a reversed binary (e.g., !fb:a.b.c or (reverse fb:a.b.c)),
   *   return "!" + ID of the binary.
   * Otherwise, return null.
   */
  public static String getBinaryId(Formula formula) {
    if (formula instanceof ReverseFormula) {
      String childId = getBinaryId(((ReverseFormula) formula).child);
      if (childId == null) return null;
      return CanonicalNames.reverseProperty(childId);
    } else if (formula instanceof ValueFormula) {
      Value v = ((ValueFormula<?>) formula).value;
      if (v instanceof NameValue) {
        return ((NameValue) v).id;
      }
    }
    return null;
  }

  public static ValueFormula<NameValue> newNameFormula(String id) {
    return new ValueFormula<NameValue>(new NameValue(id));
  }

  /*
   * Extract all subformulas in a string format (to also have primitive values)
   * TODO(joberant): replace this with Formulas.map
   */
  public static Set<String> extractSubparts(Formula f) {
    Set<String> res = new HashSet<String>();
    extractSubpartsRecursive(f, res);
    return res;
  }

  private static void extractSubpartsRecursive(Formula f, Set<String> res) {
    // base
    res.add(f.toString());
    // recurse
    if (f instanceof AggregateFormula) {
      AggregateFormula aggFormula = (AggregateFormula) f;
      extractSubpartsRecursive(aggFormula.child, res);
    } else if (f instanceof CallFormula) {
      CallFormula callFormula = (CallFormula) f;
      extractSubpartsRecursive(callFormula.func, res);
      for (Formula argFormula : callFormula.args)
        extractSubpartsRecursive(argFormula, res);
    } else if (f instanceof JoinFormula) {
      JoinFormula joinFormula = (JoinFormula) f;
      extractSubpartsRecursive(joinFormula.relation, res);
      extractSubpartsRecursive(joinFormula.child, res);
    } else if (f instanceof LambdaFormula) {
      LambdaFormula lambdaFormula = (LambdaFormula) f;
      extractSubpartsRecursive(lambdaFormula.body, res);
    } else if (f instanceof MarkFormula) {
      MarkFormula markFormula = (MarkFormula) f;
      extractSubpartsRecursive(markFormula.body, res);
    } else if (f instanceof MergeFormula) {
      MergeFormula mergeFormula = (MergeFormula) f;
      extractSubpartsRecursive(mergeFormula.child1, res);
      extractSubpartsRecursive(mergeFormula.child2, res);
    } else if (f instanceof NotFormula) {
      NotFormula notFormula = (NotFormula) f;
      extractSubpartsRecursive(notFormula.child, res);
    } else if (f instanceof ReverseFormula) {
      ReverseFormula revFormula = (ReverseFormula) f;
      extractSubpartsRecursive(revFormula.child, res);
    } else if (f instanceof SuperlativeFormula) {
      SuperlativeFormula superlativeFormula = (SuperlativeFormula) f;
      extractSubpartsRecursive(superlativeFormula.rank, res);
      extractSubpartsRecursive(superlativeFormula.count, res);
      extractSubpartsRecursive(superlativeFormula.head, res);
      extractSubpartsRecursive(superlativeFormula.relation, res);
    }
  }

  // Takes in a |rawFormula| which represents a function x => y and returns a
  // function y => x.
  public static Formula reverseFormula(Formula rawFormula) {
    if (rawFormula instanceof ValueFormula) {
      @SuppressWarnings({ "unchecked" })
      ValueFormula<NameValue> vf = (ValueFormula<NameValue>) rawFormula;
      return reverseNameFormula(vf);
    } else if (rawFormula instanceof LambdaFormula) {
      // Convert (lambda x (relation1 (relation2 (var x)))) <=> (lambda x (!relation2 (!relation1 (var x))))
      // Note: currently only handles chains.  Make this more generic.
      LambdaFormula formula = (LambdaFormula) rawFormula;
      if (isChain(formula.body))
        return new LambdaFormula(formula.var, reverseChain(formula.body, new VariableFormula(formula.var)));
      else
        return new ReverseFormula(formula);
    } else {
      return new ReverseFormula(rawFormula);
      // throw new RuntimeException("Not handled: " + rawFormula);
    }
  }

  // Helper function for reverseFormula().
  // Check to see if formula has the form (a (b (c (var x))))
  private static boolean isChain(Formula source) {
    if (source instanceof JoinFormula) {
      JoinFormula join = (JoinFormula) source;
      return isChain(join.child);
    }
    return source instanceof VariableFormula;
  }
  // Reverse the chain
  private static Formula reverseChain(Formula source, Formula result) {
    if (source instanceof JoinFormula) {
      JoinFormula join = (JoinFormula) source;
      return reverseChain(join.child, new JoinFormula(reverseFormula(join.relation), result));
    } else if (source instanceof VariableFormula) {
      return result;
    } else {
      throw new RuntimeException("Not handled: " + source);
    }
  }

  // !fb:people.person.place_of_birth <=> fb:people.person.place_of_birth
  private static ValueFormula<NameValue> reverseNameFormula(ValueFormula<NameValue> formula) {
    String id = formula.value.id;
    return new ValueFormula<>(new NameValue(CanonicalNames.reverseProperty(id)));
  }

  // Try to simplify reverse subformulas within the specified formula
  public static Formula simplifyReverses(Formula formula) {
    return formula.map(new Function<Formula, Formula>() {
      public Formula apply(Formula formula) {
        if (formula instanceof ReverseFormula)
          return reverseFormula(((ReverseFormula) formula).child);
        return null;
      }
    });
  }


}
