package edu.stanford.nlp.sempre.tables.grow;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.DenotationTypeInference;
import edu.stanford.nlp.sempre.tables.ScopedFormula;
import edu.stanford.nlp.sempre.tables.ScopedValue;
import fig.basic.LispTree;
import fig.basic.MapUtils;
import fig.basic.Option;

/**
 * Apply the function on the children from left to right.
 *
 * Example:
 *   (lambda x (lambda y ((var x) (count (var y)))))
 *   on x = population, y = (type cake)
 *   ==> (population (count (type cake)))
 *
 * If some of the arguments are ScopeFormulas, they must have either
 * the same denotation (when exactScopeHead is false) or the same
 * head formula (when exactScopeHead is true). The function is applied
 * on the relation part of the ScopeFormula.
 *
 * In addition to the function, a type can be specified.
 * A parser can use this type information to save running time
 * (e.g., by not subtracting things that are not numbers or dates)
 *
 * @author ppasupat
 */
public class ApplyFn extends SemanticFn {
  public static class Options {
    @Option(gloss = "verbosity") public int verbose = 0;
  }
  public static Options opts = new Options();

  public static boolean exactScopeHead = true;

  Formula formula;
  // Type information
  boolean hasTypeInfo = false, sameType = false;
  SemType arg1Type = null, arg2Type = null;

  public void init(LispTree tree) {
    super.init(tree);
    formula = Formulas.fromLispTree(tree.child(1));
    for (int i = 2; i < tree.children.size(); i++) {
      hasTypeInfo = true;
      LispTree typeInfo = tree.child(i);
      if (typeInfo.isLeaf() && "same-type".equals(typeInfo.value)) {
        sameType = true;
      } else if (!typeInfo.isLeaf() && typeInfo.children.size() == 2 && "arg1-type".equals(typeInfo.child(0).value)) {
        arg1Type = SemType.fromLispTree(typeInfo.child(1));
      } else if (!typeInfo.isLeaf() && typeInfo.children.size() == 2 && "arg2-type".equals(typeInfo.child(0).value)) {
        arg2Type = SemType.fromLispTree(typeInfo.child(1));
      } else {
        throw new RuntimeException("Cannot parse type information: " + typeInfo);
      }
    }
  }

  public Formula getFormula() {
    return formula;
  }

  public boolean hasTypeInfo() { return hasTypeInfo; }
  public boolean sameType() { return sameType; }
  public SemType getArg1Type() { return arg1Type == null ? SemType.anyType : arg1Type; }
  public SemType getArg2Type() { return arg2Type == null ? SemType.anyType : arg2Type; }

  @Override
  public DerivationStream call(Example ex, Callable c) {
    return new SingleDerivationStream() {
      @Override
      public Derivation createDerivation() {
        Formula result = formula, head = null;
        Value headValue = null;
        // Check the scopes
        for (Derivation child : c.getChildren()) {
          if (!(child.formula instanceof ScopedFormula)) continue;
          ScopedFormula scoped = (ScopedFormula) child.formula;
          ScopedValue scopedValue = (ScopedValue) child.value;
          if (head == null) {
            head = scoped.head;
            if (scopedValue != null)
              headValue = scopedValue.head;
          } else {
            if ((exactScopeHead && !head.equals(scoped.head)) ||
                (!exactScopeHead && !headValue.equals(scopedValue.head)))
              return null;
          }
        }
        // Apply the function on the arguments
        for (Derivation child : c.getChildren()) {
          if (!(result instanceof LambdaFormula))
            throw new RuntimeException("Too many arguments: " + c.getChildren() + " for " + formula);
          Formula argument = child.formula;
          if (argument instanceof ScopedFormula)
            argument = ((ScopedFormula) argument).relation;
          result = Formulas.lambdaApply((LambdaFormula) result, argument);
        }
        // SUPER HACK: Resolve ((reverse (lambda x (r1 (r2 (var x))))) ...) => ((reverse r2) ((reverse r1) ...))
        if (result instanceof JoinFormula)
          result = hackJoin(result);
        else if (result instanceof LambdaFormula && ((LambdaFormula) result).body instanceof JoinFormula) {
          LambdaFormula lambda = (LambdaFormula) result;
          if (lambda.body instanceof JoinFormula)
            result = new LambdaFormula(lambda.var, hackJoin(lambda.body));
        }
        // END SUPER HACK
        return new Derivation.Builder().withCallable(c)
            .formula(head == null ? result : new ScopedFormula(head, result))
            .type(TypeInference.inferType(result)).createDerivation();
      }
    };
  }

  private Formula hackJoin(Formula formula) {
    if (formula instanceof JoinFormula) {
      JoinFormula join = (JoinFormula) formula;
      if (join.relation instanceof ReverseFormula) {
        ReverseFormula reverse = (ReverseFormula) join.relation;
        if (reverse.child instanceof LambdaFormula) {
          LambdaFormula lambda = (LambdaFormula) reverse.child;
          if (lambda.body instanceof JoinFormula) {
            JoinFormula join2 = (JoinFormula) lambda.body;
            if (join2.child instanceof JoinFormula) {
              JoinFormula join3 = (JoinFormula) join2.child;
              // YAY!
              return new JoinFormula(new ReverseFormula(join3.relation),
                  new JoinFormula(new ReverseFormula(join2.relation), join.child));
            }
          }
        }
      }
    }
    // AWW!
    return formula;
  }

  @Override
  public boolean supportFilteringOnTypeData() { return true; }

  @Override
  public Collection<ChildDerivationsGroup> getFilteredDerivations(List<Derivation> derivations1, List<Derivation> derivations2) {
    if (!hasTypeInfo)
      return Collections.singleton(new ChildDerivationsGroup(derivations1, derivations2));
    // TODO: Currently this works only for lists of values, not mappings
    Map<String, List<Derivation>> grouped1 = groupByType(derivations1, getArg1Type()),
        grouped2 = (derivations2 == null) ? null : groupByType(derivations2, getArg2Type());
    List<ChildDerivationsGroup> groups = new ArrayList<>();
    if (sameType) {       // Matching pairs
      for (String valueType : grouped1.keySet()) {
        if (!grouped2.containsKey(valueType)) continue;
        groups.add(new ChildDerivationsGroup(grouped1.get(valueType), grouped2.get(valueType)));
      }
    } else if (derivations2 != null) {     // All pairs
      for (List<Derivation> filtered1 : grouped1.values()) {
        for (List<Derivation> filtered2 : grouped2.values()) {
          groups.add(new ChildDerivationsGroup(filtered1, filtered2));
        }
      }
    } else {
      for (List<Derivation> filtered : grouped1.values()) {
        groups.add(new ChildDerivationsGroup(filtered));
      }
    }
    return groups;
  }

  private Map<String, List<Derivation>> groupByType(List<Derivation> derivations, SemType parentType) {
    Map<String, List<Derivation>> typeToDerivs = new HashMap<>();
    for (Derivation deriv : derivations) {
      String valueType = DenotationTypeInference.getValueType(deriv.value);
      MapUtils.addToList(typeToDerivs, valueType, deriv);
    }
    for (Iterator<String> itr = typeToDerivs.keySet().iterator(); itr.hasNext();) {
      String valueType = itr.next();
      if (!parentType.meet(SemType.newAtomicSemType(valueType)).isValid())
        itr.remove();
    }
    return typeToDerivs;
  }
}
