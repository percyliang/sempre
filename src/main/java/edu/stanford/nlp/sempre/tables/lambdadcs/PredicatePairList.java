package edu.stanford.nlp.sempre.tables.lambdadcs;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.AggregateFormula.Mode;
import edu.stanford.nlp.sempre.tables.lambdadcs.LambdaDCSException.Type;
import fig.basic.LispTree;
import fig.basic.Pair;

/**
 * Implicitly represent a pair list using a single predicate:
 * - NORMAL: a relation (fb:people.person.parent) or its reverse
 * - COMPARISON: != < > <= >=
 * - COLON: (: u) is an empty unary if u is empty; STAR otherwise.
 * - EQUAL: identity map
 * @author ppasupat
 *
 */
public class PredicatePairList implements PairList {

  enum PredicateType { NORMAL, COMPARISON, COLON, EQUAL }
  protected final PredicateType type;

  // predicate fb:people.person.birthdate maps key (date 1961 8 4) to value fb:en.barack_obama
  // predicate > maps key 3 to value 7
  // predicate : maps any non-empty key to everything (*)
  // predicate = maps any key to itself
  protected final Value predicate;
  protected final String predicateId;
  protected final KnowledgeGraph graph;

  // ============================================================
  // Constructors
  // ============================================================

  public PredicatePairList(Value predicate, KnowledgeGraph graph) {
    this.predicate = predicate;
    this.predicateId = (predicate instanceof NameValue) ? ((NameValue) predicate).id : null;
    this.graph = graph;
    if ("=".equals(predicateId)) {
      type = PredicateType.EQUAL;
    } else if (CanonicalNames.COLON.equals(predicateId)) {
      type = PredicateType.COLON;
    } else if (CanonicalNames.COMPARATORS.contains(predicateId)) {
      type = PredicateType.COMPARISON;
    } else {
      type = PredicateType.NORMAL;
      assert graph != null;
    }
  }

  public static final PredicatePairList IDENTITY = new PredicatePairList(new NameValue("="), null);

  // ============================================================
  // Representation
  // ============================================================

  @Override
  public String toString() {
    return toLispTree().toString();
  }

  @Override
  public LispTree toLispTree() {
    return predicate.toLispTree();
  }

  @Override
  public PairListValue toValue() {
    return explicitlyFilterOnKey(InfiniteUnaryDenotation.STAR_UNARY).toValue();
  }

  // ============================================================
  // Getter
  // ============================================================

  // If needed, the explicit list of pairs are computed and cached
  protected ExplicitPairList explicitPairListCache;

  protected ExplicitPairList getExplicit() {
    if (explicitPairListCache == null)
      explicitPairListCache = explicitlyFilterOnKey(InfiniteUnaryDenotation.STAR_UNARY);
    return explicitPairListCache;
  }

  @Override
  public UnaryDenotation domain() {
    switch (type) {
      case EQUAL: case COLON: case COMPARISON:
        return InfiniteUnaryDenotation.STAR_UNARY;
      default:
        return getExplicit().domain();
    }
  }

  @Override
  public UnaryDenotation range() {
    switch (type) {
      case EQUAL: case COLON: case COMPARISON:
        return InfiniteUnaryDenotation.STAR_UNARY;
      default:
        return getExplicit().range();
    }
  }

  @Override
  public UnaryDenotation get(Value key) {
    switch (type) {
      case EQUAL:
        return new ExplicitUnaryDenotation(key);
      case COLON:
        return InfiniteUnaryDenotation.STAR_UNARY;
      case COMPARISON:
        return new InfiniteUnaryDenotation.ComparisonUnaryDenotation(predicateId, key);
      default:
        return getExplicit().get(key);
    }
  }

  @Override
  public UnaryDenotation inverseGet(Value value) {
    switch (type) {
      case EQUAL:
        return new ExplicitUnaryDenotation(value);
      case COLON:
        throw new LambdaDCSException(Type.invalidFormula, "Cannot perform inverseGet on COLON");
      case COMPARISON:
        return new InfiniteUnaryDenotation.ComparisonUnaryDenotation(
            CanonicalNames.COMPARATOR_REVERSE.get(predicateId), value);
      default:
        return getExplicit().inverseGet(value);
    }
  }

  // ============================================================
  // Operations
  // ============================================================

  @Override
  public PairList aggregate(Mode mode) {
    switch (type) {
      case EQUAL:
        return this;
      case COLON: case COMPARISON:
        throw new LambdaDCSException(Type.infiniteList, "Cannot call aggregate on %s", this);
      default:
        return getExplicit().aggregate(mode);
    }
  }

  @Override
  public PairList filter(UnaryDenotation upperBound, UnaryDenotation domainUpperBound) {
    return explicitlyFilter(upperBound, domainUpperBound);
  }

  @Override
  public PairList reverse() {
    switch (type) {
      case EQUAL:
        return this;
      case COLON:
        throw new LambdaDCSException(Type.invalidFormula, "Cannot perform reverse on COLON");
      default:
        return new PredicatePairList(CanonicalNames.reverseProperty(predicate), graph);
    }
  }

  @Override
  public UnaryDenotation joinOnKey(UnaryDenotation keys) {
    switch (type) {
      case EQUAL:
        return keys;
      case COLON:
        return keys.isEmpty() ? UnaryDenotation.EMPTY : InfiniteUnaryDenotation.STAR_UNARY;
      case COMPARISON:
        return InfiniteUnaryDenotation.create(predicateId, keys);
      default:
        return new ExplicitUnaryDenotation(graph.joinSecond(predicate, keys));
    }
  }

  @Override
  public UnaryDenotation joinOnValue(UnaryDenotation values) {
    switch (type) {
      case EQUAL:
        return values;
      case COLON:
        throw new LambdaDCSException(Type.invalidFormula, "Cannot perform reverse on COLON");
      case COMPARISON:
        return InfiniteUnaryDenotation.create(CanonicalNames.COMPARATOR_REVERSE.get(predicateId), values);
      default:
        return new ExplicitUnaryDenotation(graph.joinFirst(predicate, values));
    }
  }

  @Override
  public ExplicitPairList explicitlyFilterOnKey(UnaryDenotation keys) {
    switch (type) {
      case EQUAL:
        if (keys.size() == Integer.MAX_VALUE)
          throw new LambdaDCSException(Type.infiniteList, "Cannot call explicitlyFilter* on %s", this);
        List<Pair<Value, Value>> pairs = new ArrayList<>();
        for (Value x : keys) pairs.add(new Pair<>(x, x));
        return new ExplicitPairList(pairs);
      case COLON: case COMPARISON:
        throw new LambdaDCSException(Type.infiniteList, "Cannot call explicitlyFilter* on %s", this);
      default:
        return new ExplicitPairList(graph.filterSecond(predicate, keys));
    }
  }

  @Override
  public ExplicitPairList explicitlyFilterOnValue(UnaryDenotation values) {
    switch (type) {
      case EQUAL:
        if (values.size() == Integer.MAX_VALUE)
          throw new LambdaDCSException(Type.infiniteList, "Cannot call explicitlyFilter* on %s", this);
        List<Pair<Value, Value>> pairs = new ArrayList<>();
        for (Value x : values) pairs.add(new Pair<>(x, x));
        return new ExplicitPairList(pairs);
      case COLON: case COMPARISON:
        throw new LambdaDCSException(Type.infiniteList, "Cannot call explicitlyFilter* on %s", this);
      default:
        return new ExplicitPairList(graph.filterFirst(predicate, values));
    }
  }

  public ExplicitPairList explicitlyFilter(UnaryDenotation values, UnaryDenotation keys) {
    List<Pair<Value, Value>> pairs = new ArrayList<>();
    switch (type) {
      case EQUAL:
        UnaryDenotation domain = values.merge(keys, MergeFormula.Mode.and);
        if (domain.size() == Integer.MAX_VALUE)
          throw new LambdaDCSException(Type.infiniteList, "Cannot call explicitlyFilter* on %s", this);
        for (Value x : domain) pairs.add(new Pair<>(x, x));
        return new ExplicitPairList(pairs);
      case COLON: case COMPARISON:
        throw new LambdaDCSException(Type.infiniteList, "Cannot call explicitlyFilter* on %s", this);
      default:
        try {
          for (Pair<Value, Value> pair : graph.filterSecond(predicate, keys))
            if (values.contains(pair.getFirst())) pairs.add(pair);
          return new ExplicitPairList(pairs);
        } catch (LambdaDCSException e) {
          try {
            for (Pair<Value, Value> pair : graph.filterFirst(predicate, values))
              if (keys.contains(pair.getSecond())) pairs.add(pair);
            return new ExplicitPairList(pairs);
          } catch (LambdaDCSException e2) {
            throw new LambdaDCSException(Type.infiniteList, "Cannot call explicitlyFilter* on %s", this);
          }
        }
    }
  }
}
