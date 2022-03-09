package edu.stanford.nlp.sempre.tables.lambdadcs;

import edu.stanford.nlp.sempre.*;

/**
 * Impose that the result is a unary and the set of values should be a subset of |upperBound|.
 *
 * @author ppasupat
 */
public class UnarylikeTypeHint extends TypeHint {

  public final UnaryDenotation upperBound;
  public final UnaryDenotation domainUpperBound;

  // Should only be called within this package
  protected UnarylikeTypeHint(UnaryDenotation u, UnaryDenotation domain, VariableMap map) {
    upperBound = (u == null) ? InfiniteUnaryDenotation.STAR_UNARY : u;
    domainUpperBound = (domain == null) ? InfiniteUnaryDenotation.STAR_UNARY : domain;
    variableMap = map;
  }

  @Override
  public String toString() {
    return "UnaryTypeHint [" + domainUpperBound + " => " + upperBound + "] " + variableMap;
  }

  /**
   * Keep only the values that appear in this upperBound.
   * If a value occurs multiple times, keep the multiplicity.
   */
  public Unarylike applyBound(Unarylike denotation) {
    return denotation.filter(upperBound, domainUpperBound);
  }

  // ============================================================
  // Derive a new type hint
  // ============================================================

  public UnarylikeTypeHint withVar(String name, Value value) {
    return new UnarylikeTypeHint(upperBound, domainUpperBound, variableMap.plus(name, value));
  }

  public UnarylikeTypeHint withFreeVar(String name) {
    return new UnarylikeTypeHint(upperBound, domainUpperBound, variableMap.plusFreeVar(name));
  }

  public BinaryTypeHint asFirstOfBinary() {
    return new BinaryTypeHint(upperBound, null, variableMap);
  }

  public BinaryTypeHint asFirstOfBinaryWithSecond(UnaryDenotation second) {
    return new BinaryTypeHint(upperBound, second, variableMap);
  }

  public BinaryTypeHint asSecondOfBinary() {
    return new BinaryTypeHint(null, upperBound, variableMap);
  }

  public BinaryTypeHint asSecondOfBinaryWithFirst(UnaryDenotation first) {
    return new BinaryTypeHint(first, upperBound, variableMap);
  }

  public BinaryTypeHint asFirstAndSecondOfBinary() {
    return new BinaryTypeHint(upperBound, upperBound, variableMap);
  }

  public UnarylikeTypeHint restrict(Unarylike child1d) {
    return restrictedUnary(DenotationUtils.merge(upperBound, child1d, MergeFormula.Mode.and).range());
  }

}
