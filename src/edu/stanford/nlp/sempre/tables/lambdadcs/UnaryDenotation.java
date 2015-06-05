package edu.stanford.nlp.sempre.tables.lambdadcs;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import fig.basic.*;

/**
 * Unary denotation: a set of values.
 *
 * @author ppasupat
 */
public abstract class UnaryDenotation implements Collection<Value> {

  @Override
  public String toString() {
    return toLispTree().toString();
  }

  public abstract LispTree toLispTree();

  public abstract ListValue toListValue(KnowledgeGraph graph);

  /** return a unary denotation with all duplicates removed */
  public abstract UnaryDenotation uniqued();

  /** intersection or union */
  public abstract UnaryDenotation merge(UnaryDenotation that, MergeFormula.Mode mode);

  /** sum, average, min, max, count */
  public abstract UnaryDenotation aggregate(AggregateFormula.Mode mode);

  /** return a new UnaryDenotation where only the values found in |upperBound| are kept */
  public abstract UnaryDenotation filter(UnaryDenotation upperBound);

  // ============================================================
  // Collection interface
  // ============================================================

  @Override public boolean add(Value e) { throw new UnsupportedOperationException("unsupported"); }
  @Override public boolean addAll(Collection<? extends Value> c) { throw new UnsupportedOperationException("unsupported"); }
  @Override public void clear() { throw new UnsupportedOperationException("unsupported"); }
  @Override public boolean remove(Object o) { throw new UnsupportedOperationException("unsupported"); }
  @Override public boolean removeAll(Collection<?> c) { throw new UnsupportedOperationException("unsupported"); }
  @Override public boolean retainAll(Collection<?> c) { throw new UnsupportedOperationException("unsupported"); }
  @Override public Object[] toArray() { throw new UnsupportedOperationException("unsupported"); }
  @Override public <T> T[] toArray(T[] a) { throw new UnsupportedOperationException("unsupported"); }

  @Override public abstract boolean contains(Object o);
  @Override public abstract boolean containsAll(Collection<?> c);
  @Override public abstract Iterator<Value> iterator();
  @Override public abstract int size();
  @Override public boolean isEmpty() { return size() == 0; }


}
