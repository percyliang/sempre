package edu.stanford.nlp.sempre.tables.lambdadcs;

import edu.stanford.nlp.sempre.*;

/**
 * Impose that the result is a binary and:
 * - the set of first pair entries should be a subset of |upperBoundFirst|
 * - the set of second pair entries should be a subset of |upperBoundSecond|
 *
 * @author ppasupat
 */
public class BinaryTypeHint extends TypeHint {

  public final UnaryDenotation firstUpperBound, secondUpperBound;

  // Should only be called within this package
  protected BinaryTypeHint(UnaryDenotation first, UnaryDenotation second, VariableMap map) {
    firstUpperBound = (first == null) ? InfiniteUnaryDenotation.STAR_UNARY : first;
    secondUpperBound = (second == null) ? InfiniteUnaryDenotation.STAR_UNARY : second;
    variableMap = map;
  }

  @Override
  public String toString() {
    return "BinaryTypeHint [" + firstUpperBound + "|" + secondUpperBound + "] " + variableMap;
  }

  // ============================================================
  // Derive a new type hint
  // ============================================================

  public BinaryTypeHint withVar(String name, Value value) {
    return new BinaryTypeHint(firstUpperBound, secondUpperBound, variableMap.plus(name, value));
  }

  public BinaryTypeHint reverse() {
    return newRestrictedBinary(secondUpperBound, firstUpperBound);
  }

  public UnaryTypeHint first() {
    return newRestrictedUnary(firstUpperBound);
  }

  public UnaryTypeHint second() {
    return newRestrictedUnary(secondUpperBound);
  }
}
