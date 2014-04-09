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
    // Primitive predicate
    if (tree.isLeaf()) return new ValueFormula<NameValue>(new NameValue(tree.value, null));

    String func = tree.child(0).value;

    if (func != null) {
      if (func.equals("boolean") || func.equals("number") || func.equals("string") || func.equals("date"))
        return new ValueFormula<Value>(Values.fromLispTree(tree));
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

    { // Merge: (intersect (fb:type.object.type fb:people.person) (fb:people.person.children fb:en.barack_obama))
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
        return new SuperlativeFormula(
            mode,
            Integer.parseInt(tree.child(1).value),
            Integer.parseInt(tree.child(2).value),
            fromLispTree(tree.child(3)),
            fromLispTree(tree.child(4)));
      }
    }

    // Default is join: (fb:type.object.type fb:people.person)
    if (tree.children.size() != 2)
      throw new RuntimeException("Invalid number of arguments for join (want 2): " + tree);
    return new JoinFormula(fromLispTree(tree.child(0)), fromLispTree(tree.child(1)));
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
      MergeFormula merge = (MergeFormula)formula;
      return containsFreeVar(merge.child1, var) || containsFreeVar(merge.child2, var);
    }
    if (formula instanceof JoinFormula) {
      JoinFormula join = (JoinFormula)formula;
      return containsFreeVar(join.relation, var) || containsFreeVar(join.child, var);
    }
    if (formula instanceof LambdaFormula) {
      LambdaFormula lambda = (LambdaFormula)formula;
      if (lambda.var.equals(var.name)) return false;  // Blocked by bound variable
      return containsFreeVar(lambda.body, var);
    }
    if (formula instanceof MarkFormula) {
      MarkFormula mark = (MarkFormula)formula;
      // Note: marks are transparent, unlike lambdas
      return containsFreeVar(mark.body, var);
    }
    if (formula instanceof ReverseFormula) {
      return containsFreeVar(((ReverseFormula)formula).child, var);
    }
    if (formula instanceof AggregateFormula) {
      return containsFreeVar(((AggregateFormula)formula).child, var);
    }
    if (formula instanceof SuperlativeFormula) {
      SuperlativeFormula superlative = (SuperlativeFormula)formula;
      return containsFreeVar(superlative.head, var) || containsFreeVar(superlative.relation, var);
    }
    if (formula instanceof NotFormula) {
      NotFormula notForm = (NotFormula) formula;
      return containsFreeVar(notForm.child, var);
    }
    throw new RuntimeException("Unhandled: " + formula);
  }
    
  // TODO: use Formula.map
  public static Set<String> extractAtomicFreebaseElements(Formula formula) {
    Set<String> res = new HashSet<String>();
    LispTree formulaTree = formula.toLispTree();
    extractAtomicFreebaseElements(formulaTree, res);
    return res;
  }
  private static void extractAtomicFreebaseElements(LispTree formulaTree,
                                                    Set<String> res) {
    //base
    if (formulaTree.isLeaf()) {
      if (formulaTree.value.startsWith("fb:"))
        res.add(formulaTree.value);
      else if (formulaTree.value.startsWith("!fb:"))
        res.add(formulaTree.value.substring(1));
    }
    //recursion
    else {
      for (LispTree child : formulaTree.children) {
        extractAtomicFreebaseElements(child, res);
      }
    }
  }

  // TODO: remove
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
      Value value = ((ValueFormula)formula).value;
      if (value instanceof StringValue)
        return ((StringValue)value).value;
    }
    return null;
  }
  public static String getNameId(Formula formula) {
    if (formula instanceof ValueFormula) {
      Value value = ((ValueFormula)formula).value;
      if (value instanceof NameValue)
        return ((NameValue)value).id;
    }
    return null;
  }

  public static ValueFormula<NameValue> newNameFormula(String id) {
    return new ValueFormula<NameValue>(new NameValue(id));
  }
  
  /*
   * Extract all subformulas in a string format (to also have primitive values)
   */
  public static Set<String> extractSubparts(Formula f) {
    Set<String> res = new HashSet<String>();
    extractSubpartsRecursive(f,res);
    return res;
  }

  private static void extractSubpartsRecursive(Formula f, Set<String> res) {
    //base
    res.add(f.toString());
    //recurse
    if(f instanceof AggregateFormula) {
      AggregateFormula aggFormula = (AggregateFormula) f;
      extractSubpartsRecursive(aggFormula, res);
    }
    else if(f instanceof CallFormula) {
      CallFormula callFormula = (CallFormula) f;
      extractSubpartsRecursive(callFormula.func,res);
      for(Formula argFormula: callFormula.args)
        extractSubpartsRecursive(argFormula, res);     
    }
    else if(f instanceof JoinFormula) {
      JoinFormula joinFormula = (JoinFormula) f;
      extractSubpartsRecursive(joinFormula.relation,res);
      extractSubpartsRecursive(joinFormula.child,res);
    }
    else if(f instanceof LambdaFormula) {
      LambdaFormula lambdaFormula = (LambdaFormula) f;
      extractSubpartsRecursive(lambdaFormula.body, res);
    }
    else if(f instanceof MarkFormula) {
      MarkFormula markFormula = (MarkFormula) f;
      extractSubpartsRecursive(markFormula.body, res);
    }
    else if(f instanceof MergeFormula) {
      MergeFormula mergeFormula = (MergeFormula) f;
      extractSubpartsRecursive(mergeFormula.child1, res);
      extractSubpartsRecursive(mergeFormula.child2, res);
    }
    else if(f instanceof NotFormula) {
      NotFormula notFormula = (NotFormula) f;
      extractSubpartsRecursive(notFormula.child, res);
    }
    else if(f instanceof ReverseFormula) {
      ReverseFormula revFormula = (ReverseFormula) f;
      extractSubpartsRecursive(revFormula.child,res);
    }
    else if(f instanceof SuperlativeFormula) {
      SuperlativeFormula superlativeFormula = (SuperlativeFormula) f;
      extractSubpartsRecursive(superlativeFormula.head, res);
      extractSubpartsRecursive(superlativeFormula.relation, res);
    }
  }
}
