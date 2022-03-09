package edu.stanford.nlp.sempre.tables.lambdadcs;

import java.time.YearMonth;
import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.DenotationTypeInference;
import edu.stanford.nlp.sempre.tables.InfiniteListValue;
import edu.stanford.nlp.sempre.tables.lambdadcs.LambdaDCSException.Type;
import fig.basic.*;

/**
 * A unary with infinite number of elements such as (>= 4) and * [= anything]
 *
 * @author ppasupat
 */
public abstract class InfiniteUnaryDenotation extends UnaryDenotation {
  public static class Options {
    @Option(gloss = "(!= x) only contains things with the same type as x")
    public boolean neqMustTypeCheck = true;
  }
  public static Options opts = new Options();

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
  public Object[] toArray() {
    throw new LambdaDCSException(Type.infiniteList, "Cannot convert an infinite unary to array");
  }

  @Override public <T> T[] toArray(T[] a) {
    throw new LambdaDCSException(Type.infiniteList, "Cannot convert an infinite unary to array");
  }

  @Override
  public int size() {
    return Integer.MAX_VALUE;
  }

  @Override
  public UnaryDenotation aggregate(AggregateFormula.Mode mode) {
    throw new LambdaDCSException(Type.infiniteList, "Cannot use aggregate mode %s on %s", mode, this);
  }

  @Override
  public UnaryDenotation filter(UnaryDenotation that) {
    return merge(that, MergeFormula.Mode.and);
  }

  // Create an InfiniteUnaryDenotation based on the specification
  public static InfiniteUnaryDenotation create(String binary, UnaryDenotation second) {
    try {
      if (ComparisonUnaryDenotation.COMPARATORS.contains(binary)) {
        if (second instanceof EverythingUnaryDenotation) {
          return (EverythingUnaryDenotation) second;
        } else if (second instanceof GenericDateUnaryDenotation) {
          if ("<".equals(binary) || ">=".equals(binary))
            return new ComparisonUnaryDenotation(binary, ((GenericDateUnaryDenotation) second).getMin());
          if (">".equals(binary) || "<=".equals(binary))
            return new ComparisonUnaryDenotation(binary, ((GenericDateUnaryDenotation) second).getMax());
        }
        return new ComparisonUnaryDenotation(binary, DenotationUtils.getSingleValue(second));
      }
    } catch (LambdaDCSException e) {
      throw e;
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
    public Value toValue() {
      return new InfiniteListValue(Arrays.asList("*"));
    }

    @Override
    public boolean contains(Object o) {
      return true;
    }

    @Override
    public UnaryDenotation merge(UnaryDenotation that, MergeFormula.Mode mode) {
      switch (mode) {
        case and: return that;
        case or:  return this;
        default:  throw new LambdaDCSException(Type.invalidFormula, "Unknown merge mode: %s", mode);
      }
    }

  }
  public static final InfiniteUnaryDenotation STAR_UNARY = new EverythingUnaryDenotation();

  // ============================================================
  // Comparison
  // ============================================================

  public static class ComparisonUnaryDenotation extends InfiniteUnaryDenotation {

    public static final List<String> COMPARATORS = Arrays.asList("!=", "<", ">", "<=", ">=");
    public final String comparator;
    public final Value value;
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
    public Value toValue() {
      return new InfiniteListValue(Arrays.asList(comparator, value));
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
      } else if (that instanceof EverythingUnaryDenotation || that instanceof RangeUnaryDenotation) {
        return that.merge(this, mode);
      } else if (mode == MergeFormula.Mode.and && that instanceof InfiniteUnaryDenotation) {
        UnaryDenotation answer = RangeEnds.andMerge(this, (InfiniteUnaryDenotation) that);
        if (answer != null) return answer;
      }
      throw new LambdaDCSException(Type.infiniteList, "Cannot use merge mode %s on %s and %s", mode, this, that);
    }

    @Override
    public boolean contains(Object o) {
      if (!(o instanceof Value)) return false;
      Value that = ((Value) o);
      if (comparator.equals("!=")) {
        if (InfiniteUnaryDenotation.opts.neqMustTypeCheck) {
          return !that.equals(value) && DenotationTypeInference.typeCheck(that, value);
        } else {
          return !that.equals(value);
        }
      }
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

  // ============================================================
  // Range
  // ============================================================

  static class RangeEnds {
    public final String leftComparator, rightComparator;
    public final Value leftValue, rightValue;

    public RangeEnds(String leftComparator, Value leftValue, String rightComparator, Value rightValue) {
      this.leftComparator = leftComparator;
      this.leftValue = leftValue;
      this.rightComparator = rightComparator;
      this.rightValue = rightValue;
    }

    public static RangeEnds getRangeEnds(InfiniteUnaryDenotation x) {
      if (x instanceof RangeUnaryDenotation) {
        return ((RangeUnaryDenotation) x).rangeEnds;
      } else if (x instanceof ComparisonUnaryDenotation) {
        ComparisonUnaryDenotation comparison = (ComparisonUnaryDenotation) x;
        String leftComparator = ">", rightComparator = "<";
        Value leftValue = null, rightValue = null;
        switch (comparison.comparator) {
          case "<=": rightComparator = "<=";
          case "<": rightValue = comparison.value; break;
          case ">=": leftComparator = ">=";
          case ">": leftValue = comparison.value; break;
          default: return null;
        }
        return new RangeEnds(leftComparator, leftValue, rightComparator, rightValue);
      }
      return null;
    }

    // Helper function for performing AND on ComparisonUnaryDenotation or RangeUnaryDenotation
    public static UnaryDenotation andMerge(InfiniteUnaryDenotation xDeno, InfiniteUnaryDenotation yDeno) {
      RangeEnds x = getRangeEnds(xDeno), y = getRangeEnds(yDeno);
      if (x == null || y == null) return null;
      String leftComparator, rightComparator;
      Value leftValue, rightValue;
      int comparison;
      // Left
      comparison = (x.leftValue == null) ? -1 : (y.leftValue == null) ? +1 :
        DenotationUtils.getTypeProcessor(x.leftValue, y.leftValue).compareValues(x.leftValue, y.leftValue);
      if (comparison > 0) {
        leftValue = x.leftValue; leftComparator = x.leftComparator;
      } else if (comparison < 0) {
        leftValue = y.leftValue; leftComparator = y.leftComparator;
      } else {
        leftValue = x.leftValue; leftComparator = (">".equals(x.leftComparator) || ">".equals(y.leftComparator))? ">" : ">=";
      }
      // Right
      comparison = (x.rightValue == null) ? 1 : (y.rightValue == null) ? -1 :
        DenotationUtils.getTypeProcessor(x.rightValue, y.rightValue).compareValues(x.rightValue, y.rightValue);
      if (comparison < 0) {
        rightValue = x.rightValue; rightComparator = x.rightComparator;
      } else if (comparison > 0) {
        rightValue = y.rightValue; rightComparator = y.rightComparator;
      } else {
        rightValue = x.rightValue; rightComparator = ("<".equals(x.rightComparator) || "<".equals(y.rightComparator))? "<" : "<=";
      }
      // Return answer
      if (leftValue == null) {
        if (rightValue == null) return null;
        return new ComparisonUnaryDenotation(rightComparator, rightValue);
      } else if (rightValue == null) {
        return new ComparisonUnaryDenotation(leftComparator, leftValue);
      } else {
        comparison = DenotationUtils.getTypeProcessor(leftValue, rightValue).compareValues(leftValue, rightValue);
        if (comparison < 0)
          return new RangeUnaryDenotation(leftComparator, leftValue, rightComparator, rightValue);
        if (comparison == 0 && ">=".equals(leftComparator) && "<=".equals(rightComparator))
          return new ExplicitUnaryDenotation(leftValue);
        else
          return null;
      }
    }
  }

  public static class RangeUnaryDenotation extends InfiniteUnaryDenotation {

    public final RangeEnds rangeEnds;
    private final DenotationUtils.TypeProcessor valueProcessor;

    public RangeUnaryDenotation(String leftComparator, Value leftValue, String rightComparator, Value rightValue) {
      this.rangeEnds = new RangeEnds(leftComparator, leftValue, rightComparator, rightValue);
      this.valueProcessor = DenotationUtils.getTypeProcessor(leftValue, rightValue);
    }

    @Override
    public LispTree toLispTree() {
      LispTree tree = LispTree.proto.newList();
      tree.addChild("and");
      tree.addChild(LispTree.proto.newList(rangeEnds.leftComparator, rangeEnds.leftValue.toLispTree()));
      tree.addChild(LispTree.proto.newList(rangeEnds.rightComparator, rangeEnds.rightValue.toLispTree()));
      return tree;
    }

    @Override
    public Value toValue() {
      return new InfiniteListValue(Arrays.asList(rangeEnds.leftComparator, rangeEnds.leftValue,
          rangeEnds.rightComparator, rangeEnds.rightValue));
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
      } else if (mode == MergeFormula.Mode.and && that instanceof InfiniteUnaryDenotation) {
        UnaryDenotation answer = RangeEnds.andMerge(this, (InfiniteUnaryDenotation) that);
        if (answer != null) return answer;
      }
      throw new LambdaDCSException(Type.infiniteList, "Cannot use merge mode %s on %s and %s", mode, this, that);
    }

    @Override
    public boolean contains(Object o) {
      if (!(o instanceof Value)) return false;
      Value that = ((Value) o);
      if (!valueProcessor.isCompatible(that))
        throw new LambdaDCSException(Type.typeMismatch, "Cannot compare %s and %s with %s", rangeEnds.leftValue, rangeEnds.rightValue, that);
      int comparison = valueProcessor.compareValues(that, rangeEnds.leftValue);
      switch (rangeEnds.leftComparator) {
        case ">":  if (comparison <= 0) return false; break;
        case ">=": if (comparison < 0) return false; break;
        default: throw new LambdaDCSException(Type.invalidFormula, "Unknown leftComparator: %s", rangeEnds.leftComparator);
      }
      comparison = valueProcessor.compareValues(that, rangeEnds.rightValue);
      switch (rangeEnds.rightComparator) {
        case "<":  if (comparison >= 0) return false; break;
        case "<=": if (comparison > 0) return false; break;
        default: throw new LambdaDCSException(Type.invalidFormula, "Unknown rightComparator: %s", rangeEnds.rightComparator);
      }
      return true;
    }

  }

  // ============================================================
  // Generic Date (e.g., (date -1 5 -1) in the formula also matches (date -1 5 12) in knowledge graph)
  // ============================================================

  public static class GenericDateUnaryDenotation extends InfiniteUnaryDenotation {
    DateValue date;

    public GenericDateUnaryDenotation(DateValue date) {
      if (date.year == -1 && date.month == -1 && date.day == -1)
        throw new LambdaDCSException(Type.invalidFormula, "Date cannot be (date -1 -1 -1)");
      this.date = date;
    }

    @Override
    public LispTree toLispTree() {
      return date.toLispTree();
    }

    @Override
    public ListValue toValue() {
      return new ListValue(Collections.singletonList(date));
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
      } 
      throw new LambdaDCSException(Type.infiniteList, "Cannot use merge mode %s on %s and %s", mode, this, that);
    }
    
    public DateValue getMin() {
      if (date.day != -1)
        return date;
      if (date.month != -1)
        return new DateValue(date.year, date.month, 1);
      if (date.year != -1)
        return new DateValue(date.year, 1, 1);
      throw new LambdaDCSException(Type.unknown, "Invalid date: (-1 -1 -1).");
    }
    
    public DateValue getMax() {
      if (date.day != -1)
        return date;
      if (date.month != -1)
        return new DateValue(date.year, date.month,
            YearMonth.of(date.year == -1 ? 2000 : date.year, date.month).lengthOfMonth());
      if (date.year != -1)
        return new DateValue(date.year, 12, 31);
      throw new LambdaDCSException(Type.unknown, "Invalid date: (-1 -1 -1).");
    }

    @Override
    public boolean contains(Object o) {
      if (!(o instanceof DateValue)) return false;
      DateValue that = (DateValue) o;
      return (date.year == -1 || date.year == that.year) &&
          (date.month == -1 || date.month == that.month) &&
          (date.day == -1 || date.day == that.day);
    }

    /**
     * If the provided value is a full date like (date 2015 10 21), return an ExplicitUnaryDenotation object.
     * If instead it has a placeholder like (date -1 10 21), return a GenericDateUnaryDenotation object.
     */
    public static UnaryDenotation get(DateValue value) {
      if (value.year != -1 && value.month != -1 && value.day != -1)
        return new ExplicitUnaryDenotation(value);
      return new GenericDateUnaryDenotation(value);
    }

  }

  // ============================================================
  // Test
  // ============================================================

  public static void main(String[] args) {
    //UnaryDenotation x = new RangeUnaryDenotation(">", new NumberValue(2), "<=", new NumberValue(4));
    UnaryDenotation x = new ComparisonUnaryDenotation("<=", new NumberValue(10));
    LogInfo.logs("%s", x);
    //UnaryDenotation y = new RangeUnaryDenotation(">=", new NumberValue(3), "<=", new NumberValue(5));
    UnaryDenotation y = new ComparisonUnaryDenotation("<", new NumberValue(4));
    LogInfo.logs("%s", y);
    UnaryDenotation z = x.merge(y, MergeFormula.Mode.and);
    LogInfo.logs("%s", z);
  }

}
