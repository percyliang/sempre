package edu.stanford.nlp.sempre.tables.lambdadcs;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.lambdadcs.LambdaDCSException.Type;
import fig.basic.*;

/**
 * A unary with infinite number of elements such as (>= 4) and * [= anything]
 *
 * @author ppasupat
 */
public abstract class InfiniteUnaryDenotation extends UnaryDenotation {

  @Override
  public ListValue toListValue(KnowledgeGraph graph) {
    throw new LambdaDCSException(Type.infiniteList, "Cannot convert to ListValue: %s", toLispTree());
  }

  // Default implementation: calls |contains| on all elements of |c|
  @Override
  public boolean containsAll(Collection<?> c) {
    for (Object o : c) {
      if (!contains(o)) return false;
    }
    return true;
  }

  @Override
  public Iterator<Value> iterator() {
    throw new LambdaDCSException(Type.infiniteList, "Cannot iterate over an infinite unary");
  }

  @Override
  public int size() {
    return Integer.MAX_VALUE;
  }

  @Override
  public UnaryDenotation uniqued() {
    return this;    // Already uniqued
  }

  @Override
  public UnaryDenotation filter(UnaryDenotation that) {
    return merge(that, MergeFormula.Mode.and);
  }

  // Create an InfiniteUnaryDenotation based on the specification
  public static InfiniteUnaryDenotation create(String binary, UnaryDenotation second) {
    try {
      if (ComparisonUnaryDenotation.COMPARATORS.contains(binary)) {
        return new ComparisonUnaryDenotation(binary, DenotationUtils.getSingleValue(second));
      }
    } catch (Exception e) { }
    throw new LambdaDCSException(Type.invalidFormula,
        "Cannot create an InfiniteUnaryDenotation: binary = %s, second = %s", binary, second);
  }

  // ============================================================
  // Everything (*)
  // ============================================================

  static class EverythingUnaryDenotation extends InfiniteUnaryDenotation {

    @Override
    public LispTree toLispTree() {
      LispTree tree = LispTree.proto.newList();
      tree.addChild("unary");
      tree.addChild("*");
      return tree;
    }

    @Override
    public boolean contains(Object o) {
      return true;
    }

    @Override
    public UnaryDenotation merge(UnaryDenotation that, MergeFormula.Mode mode) {
      switch (mode) {
      case and: return that.uniqued();
      case or:  return this;
      default:  throw new LambdaDCSException(Type.invalidFormula, "Unknown merge mode: %s", mode);
      }
    }

    @Override
    public UnaryDenotation aggregate(AggregateFormula.Mode mode) {
      throw new LambdaDCSException(Type.infiniteList, "Cannot use aggregate mode %s on *", mode);
    }

  }
  public static final InfiniteUnaryDenotation STAR_UNARY = new EverythingUnaryDenotation();

  // ============================================================
  // Comparison
  // ============================================================

  public static class ComparisonUnaryDenotation extends InfiniteUnaryDenotation {

    public static final List<String> COMPARATORS = Arrays.asList("!=", "<", ">", "<=", ">=");
    public String comparator;
    public Value value;
    private final DenotationUtils.TypeProcessor valueProcessor;

    public ComparisonUnaryDenotation(String comparator, Value value) {
      this.comparator = comparator;
      this.value = value;
      this.valueProcessor = comparator.equals("!=") ? null : DenotationUtils.getTypeProcessor(value);
    }

    @Override
    public LispTree toLispTree() {
      LispTree tree = LispTree.proto.newList();
      tree.addChild(comparator);
      tree.addChild(value.toLispTree());
      return tree;
    }

    @Override
    public UnaryDenotation merge(UnaryDenotation that, MergeFormula.Mode mode) {
      if (that.size() != Integer.MAX_VALUE) {
        if (mode == MergeFormula.Mode.and) {
          Set<Value> filtered = new HashSet<>();
          for (Value value : that)
            if (contains(value))
              filtered.add(value);
          return new ExplicitUnaryDenotation(filtered);
        }
      } else if (that instanceof EverythingUnaryDenotation) {
        return that.merge(this, mode);
      } else if ("!=".equals(comparator)) {
        if (mode == MergeFormula.Mode.and && !that.contains(value))
          return this;
        if (mode == MergeFormula.Mode.or)
          return that.contains(value) ? STAR_UNARY : this;
      } else {
        // TODO(ice): Handle some more cases
      }
      throw new LambdaDCSException(Type.infiniteList, "Cannot use merge mode %s on %s and %s", mode, this, that);
    }

    @Override
    public UnaryDenotation aggregate(AggregateFormula.Mode mode) {
      // Handle some cases
      if (">=".equals(comparator) && mode == AggregateFormula.Mode.min)
        return new ExplicitUnaryDenotation(value);
      if ("<=".equals(comparator) && mode == AggregateFormula.Mode.max)
        return new ExplicitUnaryDenotation(value);
      throw new LambdaDCSException(Type.infiniteList, "Cannot use aggregate mode %s on %s", mode, this);
    }

    @Override
    public boolean contains(Object o) {
      if (!(o instanceof Value)) return false;
      Value that = ((Value) o);
      if (comparator.equals("!=")) return !that.equals(value);
      if (!valueProcessor.isCompatible(that))
        throw new LambdaDCSException(Type.typeMismatch, "Cannot compare %s with %s", value, that);
      int comparison = valueProcessor.compareValues(that, value);
      switch (comparator) {
      case "<":  return comparison < 0;
      case ">":  return comparison > 0;
      case "<=": return comparison <= 0;
      case ">=": return comparison >= 0;
      default: throw new LambdaDCSException(Type.invalidFormula, "Unknown comparator: %s", comparator);
      }
    }

  }


}
