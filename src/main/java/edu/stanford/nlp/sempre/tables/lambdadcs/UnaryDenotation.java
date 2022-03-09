package edu.stanford.nlp.sempre.tables.lambdadcs;

import java.util.*;

import edu.stanford.nlp.sempre.*;

/**
 * Unary denotation: a list of values.
 *
 * @author ppasupat
 */
public abstract class UnaryDenotation implements Unarylike, Collection<Value> {

  @Override
  public String toString() {
    return toLispTree().toString();
  }

  @Override
  public String getDomainVar() {
    return null;
  }

  public static final UnaryDenotation EMPTY = new ExplicitUnaryDenotation();
  public static final UnaryDenotation NULL = new ExplicitUnaryDenotation((Value) null);
  public static final UnaryDenotation ZERO = new ExplicitUnaryDenotation(new NumberValue(0));
  public static final UnaryDenotation ONE = new ExplicitUnaryDenotation(new NumberValue(1));

  @Override
  public UnaryDenotation get(Value key) {
    // Any assignment yields the same answer.
    return this;
  }

  @Override
  public UnaryDenotation inverseGet(Value value) {
    return NULL;
  }

  @Override
  public UnaryDenotation domain() {
    return NULL;
  }

  @Override
  public UnaryDenotation range() {
    return this;
  }

  public abstract UnaryDenotation merge(UnaryDenotation that, MergeFormula.Mode mode);

  @Override
  public abstract UnaryDenotation aggregate(AggregateFormula.Mode mode);

  @Override
  public UnaryDenotation filter(UnaryDenotation upperBound, UnaryDenotation domainUpperBound) {
    // domainUpperBound information is not used
    return filter(upperBound);
  }

  public abstract UnaryDenotation filter(UnaryDenotation upperBound);

  // ============================================================
  // Collection interface
  // ============================================================

  // Don't support mutation
  @Override public boolean add(Value e) { throw new UnsupportedOperationException("unsupported"); }
  @Override public boolean addAll(Collection<? extends Value> c) { throw new UnsupportedOperationException("unsupported"); }
  @Override public void clear() { throw new UnsupportedOperationException("unsupported"); }
  @Override public boolean remove(Object o) { throw new UnsupportedOperationException("unsupported"); }
  @Override public boolean removeAll(Collection<?> c) { throw new UnsupportedOperationException("unsupported"); }
  @Override public boolean retainAll(Collection<?> c) { throw new UnsupportedOperationException("unsupported"); }

  @Override public abstract boolean contains(Object o);
  @Override public abstract boolean containsAll(Collection<?> c);
  @Override public abstract Iterator<Value> iterator();
  @Override public abstract Object[] toArray();
  @Override public abstract <T> T[] toArray(T[] a);
  @Override public abstract int size();
  @Override public boolean isEmpty() { return size() == 0; }


}
