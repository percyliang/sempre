package edu.stanford.nlp.sempre.tables.lambdadcs;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.lambdadcs.LambdaDCSException.Type;
import fig.basic.*;

/**
 * A unary with finite number of elements. Represented as a set of values.
 *
 * @author ppasupat
 */
public class ExplicitUnaryDenotation extends UnaryDenotation {

  protected final List<Value> values;

  public ExplicitUnaryDenotation() {
    values = Collections.emptyList();
  }

  public ExplicitUnaryDenotation(Value value) {
    values = Collections.singletonList(value);
  }

  public ExplicitUnaryDenotation(Collection<Value> values) {
    this.values = new ArrayList<>(values);
  }

  @Override
  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("unary");
    for (Value value : values)
      tree.addChild(value.toLispTree());
    return tree;
  }

  protected ListValue cachedValue;

  @Override
  public ListValue toValue() {
    if (cachedValue != null) return cachedValue;
    ListValue result = new ListValue(values);
    if (LambdaDCSExecutor.opts.sortResults)
      result = result.getSorted();
    cachedValue = result;
    return result;
  }

  @Override
  public String toString() {
    return toLispTree().toString();
  }

  @Override
  public boolean contains(Object o) {
    return values.contains(o);
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return values.containsAll(c);
  }

  @Override
  public Iterator<Value> iterator() {
    return values.iterator();
  }

  @Override
  public Object[] toArray() {
    return values.toArray();
  }

  @Override public <T> T[] toArray(T[] a) {
    return values.toArray(a);
  }

  @Override
  public int size() {
    return values.size();
  }

  @Override
  public UnaryDenotation merge(UnaryDenotation that, MergeFormula.Mode mode) {
    if (that.size() == Integer.MAX_VALUE) return that.merge(this, mode);
    Set<Value> merged = new HashSet<>(values);
    switch (mode) {
      case and: merged.retainAll(that); break;
      case or: merged.addAll(that); break;
      default: throw new LambdaDCSException(Type.invalidFormula, "Unknown merge mode: %s", mode);
    }
    return new ExplicitUnaryDenotation(merged);
  }

  @Override
  public UnaryDenotation aggregate(AggregateFormula.Mode mode) {
    if (mode == AggregateFormula.Mode.count) {
      // Count the set size, not the list size
      return new ExplicitUnaryDenotation(new NumberValue(new HashSet<>(values).size()));
    }
    return new ExplicitUnaryDenotation(DenotationUtils.aggregate(this, mode));
  }

  @Override
  public UnaryDenotation filter(UnaryDenotation upperBound) {
    List<Value> filtered = new ArrayList<>();
    for (Value value : values) {
      if (upperBound.contains(value))
        filtered.add(value);
    }
    return new ExplicitUnaryDenotation(filtered);
  }

}
