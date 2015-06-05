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

  protected List<Value> values;
  protected Set<Value> valuesSet;

  public ExplicitUnaryDenotation() {
    values = Collections.emptyList();
    valuesSet = Collections.emptySet();
  }

  public ExplicitUnaryDenotation(Value value) {
    values = Collections.singletonList(value);
    valuesSet = Collections.singleton(value);
  }

  public ExplicitUnaryDenotation(Collection<Value> values) {
    this.values = new ArrayList<>(values);
    this.valuesSet = new HashSet<>(values);
  }

  @Override
  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("unary");
    for (Value value : values)
      tree.addChild(value.toLispTree());
    return tree;
  }

  @Override
  public ListValue toListValue(KnowledgeGraph graph) {
    return new ListValue(new ArrayList<>(valuesSet));
  }

  @Override
  public String toString() {
    return toLispTree().toString();
  }

  @Override
  public boolean contains(Object o) {
    return valuesSet.contains(o);
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return valuesSet.containsAll(c);
  }

  @Override
  public Iterator<Value> iterator() {
    return values.iterator();
  }

  @Override
  public int size() {
    return values.size();
  }

  @Override
  public UnaryDenotation uniqued() {
    return new ExplicitUnaryDenotation(valuesSet);
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
      return new ExplicitUnaryDenotation(new NumberValue(valuesSet.size()));
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
