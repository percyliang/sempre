package edu.stanford.nlp.sempre.tables.lambdadcs;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import fig.basic.*;

/**
 * An implicit binary defined by binary predicates (e.g., fb:film.film.directed_by)
 * Represented as a list of binary predicates.
 *
 * @author ppasupat
 */
public class PredicateBinaryDenotation extends BinaryDenotation {

  protected List<Value> values;
  protected Set<Value> valuesSet;

  public PredicateBinaryDenotation() {
    values = Collections.emptyList();
    valuesSet = Collections.emptySet();
  }

  public PredicateBinaryDenotation(Value value) {
    values = Collections.singletonList(value);
    valuesSet = Collections.singleton(value);
  }

  public PredicateBinaryDenotation(Collection<Value> values) {
    this.values = new ArrayList<>(values);
    this.valuesSet = new HashSet<>(values);
  }

  @Override
  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("binary");
    for (Value value : values)
      tree.addChild(value.toLispTree());
    return tree;
  }

  @Override
  public TableValue toTableValue(KnowledgeGraph graph) {
    return explicitlyFilterSecond(InfiniteUnaryDenotation.STAR_UNARY, graph).toTableValue(graph);
  }

  @Override
  public UnaryDenotation joinFirst(UnaryDenotation firsts, KnowledgeGraph graph) {
    List<Value> seconds = new ArrayList<>();
    for (Value predicate : values) {
      seconds.addAll(graph.joinFirst(predicate, firsts));
    }
    return new ExplicitUnaryDenotation(seconds);
  }

  @Override
  public UnaryDenotation joinSecond(UnaryDenotation seconds, KnowledgeGraph graph) {
    List<Value> firsts = new ArrayList<>();
    for (Value predicate : values) {
      firsts.addAll(graph.joinSecond(predicate, seconds));
    }
    return new ExplicitUnaryDenotation(firsts);
  }

  @Override
  public BinaryDenotation reverse() {
    List<Value> reversedValues = new ArrayList<>();
    for (Value value : values) {
      reversedValues.add(KnowledgeGraph.getReversedPredicate(value));
    }
    return new PredicateBinaryDenotation(reversedValues);
  }

  @Override
  public ExplicitBinaryDenotation explicitlyFilterFirst(UnaryDenotation firsts, KnowledgeGraph graph) {
    List<Pair<Value, Value>> filtered = new ArrayList<>();
    for (Value predicate : values) {
      filtered.addAll(graph.filterFirst(predicate, firsts));
    }
    return new ExplicitBinaryDenotation(filtered);
  }

  @Override
  public ExplicitBinaryDenotation explicitlyFilterSecond(UnaryDenotation seconds, KnowledgeGraph graph) {
    List<Pair<Value, Value>> filtered = new ArrayList<>();
    for (Value predicate : values) {
      filtered.addAll(graph.filterSecond(predicate, seconds));
    }
    return new ExplicitBinaryDenotation(filtered);
  }

}
