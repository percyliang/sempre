package edu.stanford.nlp.sempre.tables.lambdadcs;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.lambdadcs.LambdaDCSException.Type;
import fig.basic.*;

/**
 * Utilities for denotations.
 *
 * Handle aggregation, superlative, and arithmetic operations on different types of values.
 *
 * @author ppasupat
 */
public final class DenotationUtils {
  public static class Options {
    @Option(gloss = "Allow string comparison (lexicographic)")
    public boolean allowStringComparison = false;
  }
  public static Options opts = new Options();

  private DenotationUtils() { }

  // ============================================================
  // Type Enforcer
  // ============================================================

  /**
   * Try to convert to a number
   */
  public static int convertToInteger(Value value) {
    if (value instanceof NumberValue) {
      return (int) ((NumberValue) value).value;
    } else {
      throw new LambdaDCSException(Type.typeMismatch, "Cannot convert %s to number", value);
    }
  }

  /**
   * Try to convert to a number
   */
  public static double convertToNumber(Value value) {
    if (value instanceof NumberValue) {
      return ((NumberValue) value).value;
    } else {
      throw new LambdaDCSException(Type.typeMismatch, "Cannot convert %s to number", value);
    }
  }

  /**
   * Ensure that the unary only has a single positive integer and return that integer
   */
  public static int getSinglePositiveInteger(UnaryDenotation unary) {
    if (unary.size() != 1)
      throw new LambdaDCSException(Type.nonSingletonList, "getSinglePositiveInteger(): denotation %s has != 1 elements", unary);
    int amount = convertToInteger(unary.iterator().next());
    if (amount > 0) return amount;
    throw new LambdaDCSException(Type.typeMismatch, "getSinglePositiveInteger(): denotation %s is not a positive integer", unary);
  }

  /**
   * Ensure that the unary only has a single number and return that number
   */
  public static double getSingleNumber(UnaryDenotation unary) {
    if (unary.size() != 1)
      throw new LambdaDCSException(Type.nonSingletonList, "getSingleNumber(): denotation %s has != 1 elements", unary);
    return convertToNumber(unary.iterator().next());
  }

  /**
   * Ensure that the unary has only a single value and return that value
   */
  public static Value getSingleValue(UnaryDenotation unary) {
    if (unary.size() != 1)
      throw new LambdaDCSException(Type.nonSingletonList, "getSingleValue(): denotation %s has != 1 elements", unary);
    return unary.iterator().next();
  }

  // ============================================================
  // Operations on Denotations
  // ============================================================

  /**
   * Aggregate values of the same type.
   */
  public static Value aggregate(Collection<Value> values, AggregateFormula.Mode mode) {
    // Handle basic cases
    if (mode == AggregateFormula.Mode.count) {
      if (values.size() == Integer.MAX_VALUE)
        throw new LambdaDCSException(Type.infiniteList, "Cannot call %s on an infinite list.", mode);
      return new NumberValue(values.size());
    }
    if (values.isEmpty()) {
      if (LambdaDCSExecutor.opts.aggregatesFailOnEmptyLists)
        throw new LambdaDCSException(Type.emptyList, "Cannot call %s on an empty list.", mode);
      return new ListValue(Collections.emptyList());
    }
    // General cases
    TypeProcessor processor = getTypeProcessor(values);
    switch (mode) {
      case max: return processor.max(values);
      case min: return processor.min(values);
      case sum: return processor.sum(values);
      case avg: return processor.avg(values);
      default: throw new LambdaDCSException(Type.invalidFormula, "Unknown aggregate mode: %s", mode);
    }
  }

  /**
   * Compute the superlative.
   *
   * If opt.aggregateReturnAllTopTies is true, (argmin 1 1 ...) and (argmax 1 1 ...) will return
   * the list of all values that produce the min or max. Otherwise, only 1 arbitrary value will be returned.
   */
  public static UnaryDenotation superlative(int rank, int count, ExplicitBinaryDenotation table, SuperlativeFormula.Mode mode) {
    // Handle basic cases
    if (table.pairs.isEmpty()) {
      if (LambdaDCSExecutor.opts.superlativesFailOnEmptyLists)
        throw new LambdaDCSException(Type.emptyList, "Cannot call %s on an empty list.", mode);
      return new ExplicitUnaryDenotation();
    }
    // General cases
    List<Value> seconds = new ArrayList<>();
    for (Pair<Value, Value> pair : table.pairs)
      seconds.add(pair.getSecond());
    TypeProcessor processor = getTypeProcessor(seconds);
    List<Value> answer = new ArrayList<>();
    if (LambdaDCSExecutor.opts.superlativesReturnAllTopTies && rank == 1 && count == 1) {
      // Special case: Return all ties at the top
      Value topValue;
      switch (mode) {
        case argmax: topValue = processor.max(seconds); break;
        case argmin: topValue = processor.min(seconds); break;
        default: throw new LambdaDCSException(Type.invalidFormula, "Unknown superlative mode: %s", mode);
      }
      for (Pair<Value, Value> pair : table.pairs)
        if (topValue.equals(pair.getSecond()) && !answer.contains(pair.getFirst()))
          answer.add(pair.getFirst());
    } else {
      // Other cases
      List<Integer> indices;
      switch (mode) {
        case argmax: indices = processor.argsort(seconds); Collections.reverse(indices); break;
        case argmin: indices = processor.argsort(seconds); break;
        default: throw new LambdaDCSException(Type.invalidFormula, "Unknown superlative mode: %s", mode);
      }
      int from = Math.min(rank - 1, indices.size()),
          to = Math.min(from + count, indices.size());
      for (int index : indices.subList(from, to))
        answer.add(table.pairs.get(index).getFirst());
    }
    return new ExplicitUnaryDenotation(answer);
  }

  /**
   * Perform arithmetic operation.
   *
   * Currently each Value must be a NumberValue.
   */
  public static UnaryDenotation arithmetic(Collection<Value> child1D, Collection<Value> child2D, ArithmeticFormula.Mode mode) {
    TypeProcessor processor = getTypeProcessor(child1D, child2D);
    if (LambdaDCSExecutor.opts.arithmeticsFailOnMultipleElements) {
      if (child1D.size() > 1 && child2D.size() > 1)
        throw new LambdaDCSException(Type.nonSingletonList, "Cannot call %s when both denotations have > 1 values.", mode);
    }
    List<Value> answer = new ArrayList<>();
    switch (mode) {
      case add:
        for (Value v1 : child1D) for (Value v2 : child2D) answer.add(processor.add(v1, v2));
        break;
      case sub:
        for (Value v1 : child1D) for (Value v2 : child2D) answer.add(processor.sub(v1, v2));
        break;
      case mul:
        for (Value v1 : child1D) for (Value v2 : child2D) answer.add(processor.mul(v1, v2));
        break;
      case div:
        for (Value v1 : child1D) for (Value v2 : child2D) answer.add(processor.div(v1, v2));
        break;
      default:
        throw new LambdaDCSException(Type.invalidFormula, "Unknown arithmetic mode: %s", mode);
    }
    return new ExplicitUnaryDenotation(answer);
  }

  // ============================================================
  // Processor for each data type
  // ============================================================

  /**
   * Processor for each data type.
   */
  public abstract static class TypeProcessor {
    public abstract boolean isCompatible(Value v);
    // positive if v1 > v2 | negative if v1 < v2 | 0 if v1 == v2
    public int compareValues(Value v1, Value v2) { throw new LambdaDCSException(Type.typeMismatch, "Cannot compare values with " + getClass().getSimpleName()); }
    public Value sum(Collection<Value> values) { throw new LambdaDCSException(Type.typeMismatch, "Cannot compute sum with " + getClass().getSimpleName()); }
    public Value avg(Collection<Value> values) { throw new LambdaDCSException(Type.typeMismatch, "Cannot compute avg with " + getClass().getSimpleName()); }
    public Value add(Value v1, Value v2) { throw new LambdaDCSException(Type.typeMismatch, "Cannot compute add with " + getClass().getSimpleName()); }
    public Value sub(Value v1, Value v2) { throw new LambdaDCSException(Type.typeMismatch, "Cannot compute sub with " + getClass().getSimpleName()); }
    public Value mul(Value v1, Value v2) { throw new LambdaDCSException(Type.typeMismatch, "Cannot compute mul with " + getClass().getSimpleName()); }
    public Value div(Value v1, Value v2) { throw new LambdaDCSException(Type.typeMismatch, "Cannot compute div with " + getClass().getSimpleName()); }

    public Value max(Collection<Value> values) {
      Value max = null;
      for (Value value : values) {
        if (max == null || compareValues(max, value) < 0)
          max = value;
      }
      return max;
    }

    public Value min(Collection<Value> values) {
      Value min = null;
      for (Value value : values) {
        if (min == null || compareValues(min, value) > 0)
          min = value;
      }
      return min;
    }

    public List<Integer> argsort(List<Value> values) {
      List<Integer> indices = new ArrayList<>();
      for (int i = 0; i < values.size(); i++)
        indices.add(i);
      Collections.sort(indices, new Comparator<Integer>() {
        @Override
        public int compare(Integer o1, Integer o2) {
          return compareValues(values.get(o1), values.get(o2));
        }
      });
      return indices;
    }
  }

  /**
   * Handle NumberValue. All operations are possible.
   */
  public static class NumberProcessor extends TypeProcessor {
    public static TypeProcessor singleton = new NumberProcessor();

    @Override
    public boolean isCompatible(Value v) {
      return v instanceof NumberValue;
    }

    @Override
    public int compareValues(Value v1, Value v2) {
      double x1 = ((NumberValue) v1).value, x2 = ((NumberValue) v2).value;
      return (x1 > x2) ? 1 : (x1 < x2) ? -1 : 0;
    }

    @Override
    public Value sum(Collection<Value> values) {
      double sum = 0;
      for (Value value : values)
        sum += ((NumberValue) value).value;
      return new NumberValue(sum);
    }

    @Override
    public Value avg(Collection<Value> values) {
      double sum = 0;
      for (Value value : values)
        sum += ((NumberValue) value).value;
      return new NumberValue(sum / values.size());
    }

    @Override public Value add(Value v1, Value v2) { return new NumberValue(((NumberValue) v1).value + ((NumberValue) v2).value); }
    @Override public Value sub(Value v1, Value v2) { return new NumberValue(((NumberValue) v1).value - ((NumberValue) v2).value); }
    @Override public Value mul(Value v1, Value v2) { return new NumberValue(((NumberValue) v1).value * ((NumberValue) v2).value); }
    @Override public Value div(Value v1, Value v2) { return new NumberValue(((NumberValue) v1).value / ((NumberValue) v2).value); }
  }

  /**
   * Handle DateValue. Only comparison is possible.
   */
  public static class DateProcessor extends TypeProcessor {
    public static TypeProcessor singleton = new DateProcessor();

    @Override
    public boolean isCompatible(Value v) {
      return v instanceof DateValue;
    }

    @Override
    public int compareValues(Value v1, Value v2) {
      DateValue d1 = ((DateValue) v1), d2 = ((DateValue) v2);
      if (d1.year == -1 || d2.year == -1 || d1.year == d2.year) {
        if (d1.month == -1 || d2.month == -1 || d1.month == d2.month) {
          if (d1.day == -1 || d2.day == -1 || d1.day == d2.day) {
            return 0;
          } else {
            return d1.day - d2.day;
          }
        } else {
          return d1.month - d2.month;
        }
      } else {
        return d1.year - d2.year;
      }
    }
  }

  /**
   * Handle StringValue. Only comparison is possible.
   */
  public static class StringProcessor extends TypeProcessor {
    public static TypeProcessor singleton = new StringProcessor();

    @Override
    public boolean isCompatible(Value v) {
      return v instanceof StringValue;
    }

    String getString(Value v) {
      if (v instanceof StringValue) return ((StringValue) v).value;
      if (v instanceof NameValue) {
        NameValue nameValue = (NameValue) v;
        return (nameValue.description == null || nameValue.description.isEmpty()) ? nameValue.id : nameValue.description;
      }
      if (v instanceof NumberValue) return "" + ((NumberValue) v).value;
      return v.toString();
    }

    @Override
    public int compareValues(Value v1, Value v2) {
      return getString(v1).compareTo(getString(v2));
    }
  }

  /**
   * Get the TypeProcessor corresponding to the strictest type.
   */
  public static TypeProcessor getTypeProcessor(Value value) {
    return getTypeProcessor(Collections.singleton(value), Collections.emptySet());
  }

  /**
   * Get the TypeProcessor corresponding to the strictest type.
   */
  public static TypeProcessor getTypeProcessor(Value value1, Value value2) {
    return getTypeProcessor(Collections.singleton(value1), Collections.singleton(value2));
  }

  /**
   * Get the TypeProcessor corresponding to the strictest type.
   */
  public static TypeProcessor getTypeProcessor(Collection<Value> values) {
    return getTypeProcessor(values, Collections.emptySet());
  }

  /**
   * Get the TypeProcessor corresponding to the strictest type.
   */
  public static TypeProcessor getTypeProcessor(Collection<Value> values1, Collection<Value> values2) {
    boolean canBeNumber = true, canBeDate = true;
    for (Value value : values1) {
      if (!(value instanceof NumberValue)) canBeNumber = false;
      if (!(value instanceof DateValue)) canBeDate = false;
    }
    for (Value value : values2) {
      if (!(value instanceof NumberValue)) canBeNumber = false;
      if (!(value instanceof DateValue)) canBeDate = false;
    }
    if (canBeNumber) {
      return NumberProcessor.singleton;
    } else if (canBeDate) {
      return DateProcessor.singleton;
    } else {
      if (opts.allowStringComparison)
        return StringProcessor.singleton;
      else
        throw new LambdaDCSException(Type.typeMismatch, "Cannot compare values");
    }
  }
}
