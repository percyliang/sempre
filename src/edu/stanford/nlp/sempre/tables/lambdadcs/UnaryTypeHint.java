package edu.stanford.nlp.sempre.tables.lambdadcs;

import edu.stanford.nlp.sempre.*;

/**
 * Impose that the result is a unary and the set of values should be a subset of |upperBound|.
 *
 * @author ppasupat
 */
public class UnaryTypeHint extends TypeHint {

  public final UnaryDenotation upperBound;

  // Should only be called within this package
  protected UnaryTypeHint(UnaryDenotation u, VariableMap map) {
    upperBound = (u == null) ? InfiniteUnaryDenotation.STAR_UNARY : u;
    variableMap = map;
  }

  @Override
  public String toString() {
    return "UnaryTypeHint [" + upperBound + "] " + variableMap;
  }

  /**
   * Keep only the values that appear in this upperBound.
   * If a value occurs multiple times, keep the multiplicity.
   */
  public UnaryDenotation applyBound(UnaryDenotation denotation) {
    return denotation.filter(upperBound);
  }

  // ============================================================
  // Derive a new type hint
  // ============================================================

  public UnaryTypeHint withVar(String name, Value value) {
    return new UnaryTypeHint(upperBound, variableMap.plus(name, value));
  }

  public BinaryTypeHint asFirstOfBinary() {
    return newRestrictedBinary(upperBound, null);
  }

  public BinaryTypeHint asFirstOfBinaryWithSecond(UnaryDenotation second) {
    return newRestrictedBinary(upperBound, second);
  }

  public BinaryTypeHint asSecondOfBinary() {
    return newRestrictedBinary(null, upperBound);
  }

  public BinaryTypeHint asSecondOfBinaryWithFirst(UnaryDenotation first) {
    return newRestrictedBinary(first, upperBound);
  }

  public UnaryTypeHint restrict(UnaryDenotation newBound) {
    return newRestrictedUnary(upperBound.merge(newBound, MergeFormula.Mode.and));
  }

}
