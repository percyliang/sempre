package edu.stanford.nlp.sempre.tables.lambdadcs;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.AggregateFormula.Mode;
import fig.basic.LispTree;
import fig.basic.MapUtils;
import fig.basic.Pair;

public class ExplicitPairList implements PairList {

  // Following LambdaDCS convention, pair (v, k) means k maps to v.
  protected final List<Pair<Value, Value>> pairs;
  protected final Map<Value, UnaryDenotation> mapping;
  protected final Map<Value, UnaryDenotation> reverseMapping;

  // ============================================================
  // Constructors
  // ============================================================

  public ExplicitPairList() {
    pairs = Collections.emptyList();
    mapping = Collections.emptyMap();
    reverseMapping = Collections.emptyMap();
  }

  public ExplicitPairList(Value key, Value value) {
    pairs = Collections.singletonList(new Pair<>(value, key));
    mapping = Collections.singletonMap(key, new ExplicitUnaryDenotation(value));
    reverseMapping = Collections.singletonMap(value, new ExplicitUnaryDenotation(key));
  }

  public ExplicitPairList(Pair<Value, Value> pair) {
    pairs = Collections.singletonList(pair);
    mapping = Collections.singletonMap(pair.getSecond(), new ExplicitUnaryDenotation(pair.getFirst()));
    reverseMapping = Collections.singletonMap(pair.getFirst(), new ExplicitUnaryDenotation(pair.getSecond()));
  }

  public ExplicitPairList(List<Pair<Value, Value>> pairs) {
    this.pairs = pairs;
    Map<Value, List<Value>> mappingBuilder = new HashMap<>(), reverseMappingBuilder = new HashMap<>();
    for (Pair<Value, Value> pair : pairs) {
      MapUtils.addToList(mappingBuilder, pair.getSecond(), pair.getFirst());
      MapUtils.addToList(reverseMappingBuilder, pair.getFirst(), pair.getSecond());
    }
    mapping = new HashMap<>();
    for (Map.Entry<Value, List<Value>> entry : mappingBuilder.entrySet())
      mapping.put(entry.getKey(), new ExplicitUnaryDenotation(entry.getValue()));
    reverseMapping = new HashMap<>();
    for (Map.Entry<Value, List<Value>> entry : reverseMappingBuilder.entrySet())
      reverseMapping.put(entry.getKey(), new ExplicitUnaryDenotation(entry.getValue()));
  }

  public <T extends Collection<Value>> ExplicitPairList(Map<Value, T> keyToValues) {
    pairs = new ArrayList<>();
    Map<Value, List<Value>> reverseMappingBuilder = new HashMap<>();
    for (Map.Entry<Value, T> entry : keyToValues.entrySet()) {
      for (Value value : entry.getValue()) {
        pairs.add(new Pair<>(value, entry.getKey()));
        MapUtils.addToList(reverseMappingBuilder, value, entry.getKey());
      }
    }
    mapping = new HashMap<>();
    for (Map.Entry<Value, T> entry : keyToValues.entrySet())
      mapping.put(entry.getKey(), new ExplicitUnaryDenotation(entry.getValue()));
    reverseMapping = new HashMap<>();
    for (Map.Entry<Value, List<Value>> entry : reverseMappingBuilder.entrySet())
      reverseMapping.put(entry.getKey(), new ExplicitUnaryDenotation(entry.getValue()));
  }

  // ============================================================
  // Representation
  // ============================================================

  @Override
  public String toString() {
    return toLispTree().toString();
  }

  protected static final LispTree NULL_LEAF = LispTree.proto.newLeaf(null);

  @Override
  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    for (Pair<Value, Value> pair : pairs) {
      Value first = pair.getFirst(), second = pair.getSecond();
      tree.addChild(LispTree.proto.newList(
          first == null ? NULL_LEAF : first.toLispTree(), second == null ? NULL_LEAF : second.toLispTree()));
    }
    return tree;
  }

  @Override
  public PairListValue toValue() {
    PairListValue result = new PairListValue(pairs);
    if (LambdaDCSExecutor.opts.sortResults)
      result = result.getSorted();
    return result;
  }

  // ============================================================
  // Getter
  // ============================================================

  @Override
  public UnaryDenotation domain() {
    return new ExplicitUnaryDenotation(mapping.keySet());
  }

  @Override
  public UnaryDenotation range() {
    return new ExplicitUnaryDenotation(reverseMapping.keySet());
  }

  @Override
  public UnaryDenotation get(Value key) {
    UnaryDenotation values = mapping.get(key);
    if (values == null) values = mapping.get(null);
    return values == null ? UnaryDenotation.EMPTY : values;
  }

  @Override
  public UnaryDenotation inverseGet(Value value) {
    UnaryDenotation keys = reverseMapping.get(value);
    return keys == null ? UnaryDenotation.EMPTY : keys;
  }

  // ============================================================
  // Operations
  // ============================================================


  @Override
  public PairList aggregate(Mode mode) {
    Map<Value, UnaryDenotation> aggregated = new HashMap<>();
    for (Map.Entry<Value, UnaryDenotation> entry : mapping.entrySet()) {
      aggregated.put(entry.getKey(), entry.getValue().aggregate(mode));
    }
    if (mode == Mode.count && !aggregated.containsKey(null))
      aggregated.put(null, UnaryDenotation.ZERO);
    return new ExplicitPairList(aggregated);
  }

  @Override
  public PairList filter(UnaryDenotation upperBound, UnaryDenotation domainUpperBound) {
    return explicitlyFilter(upperBound, domainUpperBound);
  }

  @Override
  public ExplicitPairList reverse() {
    List<Pair<Value, Value>> reversed = new ArrayList<>();
    for (Pair<Value, Value> pair : pairs) {
      reversed.add(new Pair<>(pair.getSecond(), pair.getFirst()));
    }
    return new ExplicitPairList(reversed);
  }

  @Override
  public UnaryDenotation joinOnKey(UnaryDenotation keys) {
    List<Value> values = new ArrayList<>();
    for (Map.Entry<Value, UnaryDenotation> entry : mapping.entrySet()) {
      if (keys.contains(entry.getKey())) values.addAll(entry.getValue());
    }
    return new ExplicitUnaryDenotation(values);
  }

  @Override
  public UnaryDenotation joinOnValue(UnaryDenotation values) {
    List<Value> keys = new ArrayList<>();
    for (Map.Entry<Value, UnaryDenotation> entry : reverseMapping.entrySet()) {
      if (values.contains(entry.getKey())) keys.addAll(entry.getValue());
    }
    return new ExplicitUnaryDenotation(keys);
  }

  @Override
  public ExplicitPairList explicitlyFilterOnKey(UnaryDenotation keys) {
    List<Pair<Value, Value>> filtered = new ArrayList<>();
    for (Pair<Value, Value> pair : pairs) {
      if (keys.contains(pair.getSecond())) filtered.add(pair);
    }
    return new ExplicitPairList(filtered);
  }

  @Override
  public ExplicitPairList explicitlyFilterOnValue(UnaryDenotation values) {
    List<Pair<Value, Value>> filtered = new ArrayList<>();
    for (Pair<Value, Value> pair : pairs) {
      if (values.contains(pair.getFirst())) filtered.add(pair);
    }
    return new ExplicitPairList(filtered);
  }

  public ExplicitPairList explicitlyFilter(UnaryDenotation values, UnaryDenotation keys) {
    List<Pair<Value, Value>> filtered = new ArrayList<>();
    for (Pair<Value, Value> pair : pairs) {
      if (values.contains(pair.getFirst()) && keys.contains(pair.getSecond())) filtered.add(pair);
    }
    return new ExplicitPairList(filtered);
  }

}
