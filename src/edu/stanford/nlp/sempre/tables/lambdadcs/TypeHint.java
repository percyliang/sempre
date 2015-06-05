package edu.stanford.nlp.sempre.tables.lambdadcs;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.lambdadcs.LambdaDCSException.Type;

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
   */
  protected static class VariableMap {

    protected final Map<String, Value> mapping;

    public VariableMap() {
      mapping = new HashMap<>();
    }

    public VariableMap plus(String name, Value value) {
      VariableMap answer = new VariableMap();
      answer.mapping.putAll(mapping);
      answer.mapping.put(name, value);
      return answer;
    }

    public Value get(String name) {
      Value value = mapping.get(name);
      if (value == null) throw new LambdaDCSException(Type.invalidFormula, "Unbound variable: " + name);
      return value;
    }

    @Override
    public String toString() {
      if (mapping.isEmpty()) return "{}";
      StringBuilder builder = new StringBuilder();
      for (Map.Entry<String, Value> entry : mapping.entrySet()) {
        builder.append(", ").append(entry.getKey()).append(": ").append(entry.getValue());
      }
      return "{" + builder.append("}").toString().substring(2);
    }
  }

  public VariableMap variableMap;

  public Value get(String name) {
    return variableMap.get(name);
  }

  // Unrestricted type hints

  public static final UnaryTypeHint UNRESTRICTED_UNARY = new UnaryTypeHint(null, new VariableMap());
  public static final BinaryTypeHint UNRESTRICTED_BINARY = new BinaryTypeHint(null, null, new VariableMap());

  /** Create an unrestricted unary from the current variable map */
  public UnaryTypeHint newUnrestrictedUnary() {
    return new UnaryTypeHint(null, variableMap);
  }

  /** Create an unrestricted binary from the current variable map */
  public BinaryTypeHint newUnrestrictedBinary() {
    return new BinaryTypeHint(null, null, variableMap);
  }

  // Restricted type hints

  /** Create an restricted unary from the current variable map */
  public UnaryTypeHint newRestrictedUnary(UnaryDenotation upperBound) {
    return new UnaryTypeHint(upperBound, variableMap);
  }

  /** Create an restricted binary from the current variable map */
  public BinaryTypeHint newRestrictedBinary(UnaryDenotation firstUpperBound, UnaryDenotation secondUpperBound) {
    return new BinaryTypeHint(firstUpperBound, secondUpperBound, variableMap);
  }
}
