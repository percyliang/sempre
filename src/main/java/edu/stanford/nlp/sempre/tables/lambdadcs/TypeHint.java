package edu.stanford.nlp.sempre.tables.lambdadcs;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.lambdadcs.LambdaDCSException.Type;
import fig.basic.Pair;

/**
 * Impose some constraints on the possible denotation of a Formula.
 *
 * TypeHint is immutable, but one can create a new TypeHint object using the information
 * from the original TypeHint.
 *
 * @author ppasupat
 */
public abstract class TypeHint {

  /**
   * Immutable map from variable name to its value.
   * At most one variable can be a free variable.
   * The scope (domain) of the free variable can also be specified as a formula.
   */
  protected static class VariableMap {

    protected final Map<String, Value> mapping;
    public final String freeVar;

    public VariableMap() {
      mapping = new HashMap<>();
      freeVar = null;
    }

    private VariableMap(Map<String, Value> mapping, String freeVar) {
      this.mapping = new HashMap<>(mapping);
      this.freeVar = freeVar;
    }

    public VariableMap plus(String name, Value value) {
      VariableMap answer;
      if (name.equals(freeVar)) {
        answer = new VariableMap(mapping, null);
      } else {
        answer = new VariableMap(mapping, freeVar);
      }
      answer.mapping.put(name, value);
      return answer;
    }

    public VariableMap plusFreeVar(String name) {
      if (freeVar != null)
        throw new LambdaDCSException(Type.invalidFormula,
            "TypeHint already has a free variable %s", freeVar);
      if (mapping.containsKey(name))
        throw new LambdaDCSException(Type.invalidFormula,
            "Variable %s is already bound to %s", name, mapping.get(name));
      VariableMap answer = new VariableMap(mapping, name);
      return answer;
    }

    public Value get(String name) {
      Value value = mapping.get(name);
      if (value == null && !name.equals(freeVar))
        throw new LambdaDCSException(Type.invalidFormula, "Unbound variable: " + name);
      return value;
    }

    public Pair<String, Value> getIfSingleVar() {
      if (mapping.size() != 1) return null;
      Map.Entry<String, Value> entry = mapping.entrySet().iterator().next();
      return new Pair<>(entry.getKey(), entry.getValue());
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      if (freeVar != null) {
        builder.append("(").append(freeVar).append(")");
      }
      for (Map.Entry<String, Value> entry : mapping.entrySet()) {
        builder.append(", ").append(entry.getKey()).append(": ").append(entry.getValue());
      }
      return "{" + builder.append("}").toString();
    }
  }

  public VariableMap variableMap;

  public Value get(String name) {
    return variableMap.get(name);
  }

  public Pair<String, Value> getIfSingleVar() {
    return variableMap.getIfSingleVar();
  }

  public String getFreeVar() {
    return variableMap.freeVar;
  }

  // Unrestricted type hints

  public static final UnarylikeTypeHint UNRESTRICTED_UNARY = new UnarylikeTypeHint(null, null, new VariableMap());
  public static final BinaryTypeHint UNRESTRICTED_BINARY = new BinaryTypeHint(null, null, new VariableMap());

  public UnarylikeTypeHint unrestrictedUnary() {
    return new UnarylikeTypeHint(null, null, variableMap);
  }

  public BinaryTypeHint unrestrictedBinary() {
    return new BinaryTypeHint(null, null, variableMap);
  }

  // Restricted type hints

  public static UnarylikeTypeHint newRestrictedUnary(UnaryDenotation upperBound) {
    return new UnarylikeTypeHint(upperBound, null, new VariableMap());
  }

  public static UnarylikeTypeHint newRestrictedUnary(UnaryDenotation upperBound, UnaryDenotation domainUpperBound) {
    return new UnarylikeTypeHint(upperBound, domainUpperBound, new VariableMap());
  }

  public static BinaryTypeHint newRestrictedBinary(UnaryDenotation first, UnaryDenotation second) {
    return new BinaryTypeHint(first, second, new VariableMap());
  }

  public UnarylikeTypeHint restrictedUnary(UnaryDenotation upperBound) {
    return new UnarylikeTypeHint(upperBound, null, variableMap);
  }

  public UnarylikeTypeHint restrictedUnary(UnaryDenotation upperBound, UnaryDenotation domainUpperBound) {
    return new UnarylikeTypeHint(upperBound, domainUpperBound, variableMap);
  }

  public BinaryTypeHint restrictedBinary(UnaryDenotation first, UnaryDenotation second) {
    return new BinaryTypeHint(first, second, variableMap);
  }

}
