package edu.stanford.nlp.sempre.tables.lambdadcs;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import fig.basic.*;

/**
 * A binary with finite number of possible mapping. Represented as a set of pairs of values.
 *
 * @author ppasupat
 */
public class ExplicitBinaryDenotation extends BinaryDenotation {

  protected List<Pair<Value, Value>> pairs;
  protected Set<Pair<Value, Value>> pairsSet;

  public ExplicitBinaryDenotation() {
    pairs = Collections.emptyList();
    pairsSet = Collections.emptySet();
  }

  public ExplicitBinaryDenotation(Value v1, Value v2) {
    this(new Pair<>(v1, v2));
  }

  public ExplicitBinaryDenotation(Pair<Value, Value> pair) {
    pairs = Collections.singletonList(pair);
    pairsSet = Collections.singleton(pair);
  }

  public ExplicitBinaryDenotation(Collection<Pair<Value, Value>> pairs) {
    this.pairs = new ArrayList<>(pairs);
    this.pairsSet = new HashSet<>(pairs);
  }

  @Override
  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("binary");
    for (Pair<Value, Value> pair : pairs)
      tree.addChild(LispTree.proto.newList(pair.getFirst().toLispTree(), pair.getSecond().toLispTree()));
    return tree;
  }

  @Override
  public TableValue toTableValue(KnowledgeGraph graph) {
    List<String> header = Arrays.asList("key", "value");
    List<List<Value>> rows = new ArrayList<>();
    for (Pair<Value, Value> pair : pairsSet) {
      rows.add(Arrays.asList(pair.getFirst(), pair.getSecond()));
    }
    return new TableValue(header, rows);
  }

  @Override
  public UnaryDenotation joinFirst(UnaryDenotation firsts, KnowledgeGraph graph) {
    List<Value> seconds = new ArrayList<>();
    for (Pair<Value, Value> pair : pairs) {
      if (firsts.contains(pair.getFirst())) seconds.add(pair.getSecond());
    }
    return new ExplicitUnaryDenotation(seconds);
  }

  @Override
  public UnaryDenotation joinSecond(UnaryDenotation seconds, KnowledgeGraph graph) {
    List<Value> firsts = new ArrayList<>();
    for (Pair<Value, Value> pair : pairs) {
      if (seconds.contains(pair.getSecond())) firsts.add(pair.getFirst());
    }
    return new ExplicitUnaryDenotation(firsts);
  }

  @Override
  public BinaryDenotation reverse() {
    List<Pair<Value, Value>> reversedPairs = new ArrayList<>();
    for (Pair<Value, Value> pair : pairs) {
      reversedPairs.add(new Pair<>(pair.getSecond(), pair.getFirst()));
    }
    return new ExplicitBinaryDenotation(reversedPairs);
  }

  @Override
  public ExplicitBinaryDenotation explicitlyFilterFirst(UnaryDenotation firsts, KnowledgeGraph graph) {
    List<Pair<Value, Value>> filtered = new ArrayList<>();
    for (Pair<Value, Value> pair : pairs) {
      if (firsts.contains(pair.getFirst())) filtered.add(pair);
    }
    return new ExplicitBinaryDenotation(filtered);
  }

  @Override
  public ExplicitBinaryDenotation explicitlyFilterSecond(UnaryDenotation seconds, KnowledgeGraph graph) {
    List<Pair<Value, Value>> filtered = new ArrayList<>();
    for (Pair<Value, Value> pair : pairs) {
      if (seconds.contains(pair.getSecond())) filtered.add(pair);
    }
    return new ExplicitBinaryDenotation(filtered);
  }

}
