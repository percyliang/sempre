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
   * Join between a BinaryDenotation and a UnarylikeDenotation.
   */
  public static Unarylike genericJoin(Binarylike b, Unarylike u) {
    if (u instanceof UnaryDenotation)
      return b.joinOnKey((UnaryDenotation) u);
    Map<Value, Collection<Value>> mapping = new HashMap<>();
    if (u.domain().size() != Integer.MAX_VALUE) {
      for (Value value : u.domain())
        mapping.put(value, b.joinOnKey(u.get(value)));
      return new MappingDenotation<>(u.getDomainVar(), new ExplicitPairList(mapping));
    } else {
      ExplicitPairList binary = b.explicitlyFilterOnKey(u.range()).pairList;
      for (Map.Entry<Value, UnaryDenotation> entry : binary.mapping.entrySet()) {
        for (Value key : u.inverseGet(entry.getKey())) {
          if (!mapping.containsKey(key))
            mapping.put(key, new ArrayList<>(entry.getValue()));
          else
            mapping.get(key).addAll(entry.getValue());
        }
      }
      return new MappingDenotation<>(u.getDomainVar(), new ExplicitPairList(mapping));
    }
  }

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
   * Helper: Check if the two Unarylikes have the same domain variable and return it.
   * Throw an exception if the domain variables are not the same.
   */
  public static String checkDomainVars(Unarylike u1, Unarylike u2) {
    if (u1 == null || u1.getDomainVar() == null) return u2.getDomainVar();
    if (u2 == null || u2.getDomainVar() == null) return u1.getDomainVar();
    if (u1.getDomainVar().equals(u2.getDomainVar())) return u1.getDomainVar();
    throw new LambdaDCSException(Type.invalidFormula, "Different domain variables: %s != %s",
        u1.getDomainVar(), u2.getDomainVar());
  }

  /**
   * Merge values.
   */
  public static Unarylike merge(Unarylike u1, Unarylike u2, MergeFormula.Mode mode) {
    if (u1 instanceof UnaryDenotation && u2 instanceof UnaryDenotation)
      return ((UnaryDenotation) u1).merge((UnaryDenotation) u2, mode);
    // MappingDenotation: Go over the union of the domains
    String domainVar = checkDomainVars(u1, u2);
    Map<Value, UnaryDenotation> answer = new HashMap<>();
    for (Value key : u1.domain()) {
      if (!answer.containsKey(key))
        answer.put(key, u1.get(key).merge(u2.get(key), mode));
    }
    for (Value key : u2.domain()) {
      if (!answer.containsKey(key))
        answer.put(key, u1.get(key).merge(u2.get(key), mode));
    }
    if (!answer.containsKey(null))
      answer.put(null, u1.get(null).merge(u2.get(null), mode));
    answer.entrySet().removeIf(e -> e.getValue().size() == 0);
    return new MappingDenotation<>(domainVar, new ExplicitPairList(answer));
  }

  /**
   * Perform arithmetic operation. Currently each Value must be a NumberValue.
   *
   * If arithmeticsFailOnMultipleElements is specified, throw an error if
   *   both children contain more than 1 Value.
   */
  public static Unarylike arithmetic(Unarylike u1, Unarylike u2, ArithmeticFormula.Mode mode) {
    TypeProcessor processor = getTypeProcessor(u1.range(), u2.range());
    if (u1 instanceof UnaryDenotation && u2 instanceof UnaryDenotation)
      return arithmeticUnary((UnaryDenotation) u1, (UnaryDenotation) u2, mode, processor);
    // MappingDenotation: Go over the union of the domains
    String domainVar = checkDomainVars(u1, u2);
    Map<Value, UnaryDenotation> answer = new HashMap<>();
    for (Value key : u1.domain()) {
      if (!answer.containsKey(key))
        answer.put(key, arithmeticUnary(u1.get(key), u2.get(key), mode, processor));
    }
    for (Value key : u2.domain()) {
      if (!answer.containsKey(key))
        answer.put(key, arithmeticUnary(u1.get(key), u2.get(key), mode, processor));
    }
    if (!answer.containsKey(null))
      answer.put(null, arithmeticUnary(u1.get(null), u2.get(null), mode, processor));
    answer.entrySet().removeIf(e -> e.getValue().size() == 0);
    return new MappingDenotation<>(domainVar, new ExplicitPairList(answer));
  }

  public static UnaryDenotation arithmeticUnary(UnaryDenotation u1, UnaryDenotation u2,
      ArithmeticFormula.Mode mode, TypeProcessor processor) {
    if (LambdaDCSExecutor.opts.arithmeticsFailOnEmptyLists && (u1.size() == 0 || u2.size() == 0))
      throw new LambdaDCSException(Type.emptyList, "Cannot call %s on an empty list.", mode);
    if (LambdaDCSExecutor.opts.arithmeticsFailOnMultipleElements && u1.size() > 1 && u2.size() > 1)
      throw new LambdaDCSException(Type.nonSingletonList, "Cannot call %s when both denotations have > 1 values.", mode);
    if (processor == null) processor = getTypeProcessor(u1, u2);
    List<Value> answer = new ArrayList<>();
    switch (mode) {
      case add: for (Value v1 : u1) for (Value v2 : u2) answer.add(processor.add(v1, v2)); break;
      case sub: for (Value v1 : u1) for (Value v2 : u2) answer.add(processor.sub(v1, v2)); break;
      case mul: for (Value v1 : u1) for (Value v2 : u2) answer.add(processor.mul(v1, v2)); break;
      case div: for (Value v1 : u1) for (Value v2 : u2) answer.add(processor.div(v1, v2)); break;
      default:
        throw new LambdaDCSException(Type.invalidFormula, "Unknown arithmetic mode: %s", mode);
    }
    return new ExplicitUnaryDenotation(answer);
  }

  /**
   * Compute the superlative (argmax/min rank count head (reverse relation)).
   *
   * Note that |relation| is reversed so that |relation| can be directly joined with |head|.
   *
   * If opt.aggregateReturnAllTopTies is true, (argmin 1 1 ...) and (argmax 1 1 ...) will return
   * the list of all values that produce the min or max. Otherwise, only 1 arbitrary value will be returned.
   */
  public static Unarylike superlative(int rank, int count, Unarylike head,
      Binarylike relation, SuperlativeFormula.Mode mode) {
    // Filter the relation with the possible keys
    BinaryDenotation<ExplicitPairList> filtered = relation.explicitlyFilterOnKey(head.range());
    TypeProcessor processor = getTypeProcessor(filtered.pairList.range());
    if (head instanceof UnaryDenotation) {
      return superlativeUnary(rank, count, filtered.pairList.pairs, mode, processor);
    }
    // MappingDenotation: Go over the domain
    Map<Value, UnaryDenotation> answer = new HashMap<>();
    for (Value key : head.domain()) {
      BinaryDenotation<ExplicitPairList> refiltered = filtered.explicitlyFilterOnKey(head.get(key));
      answer.put(key, superlativeUnary(rank, count, refiltered.pairList.pairs, mode, processor));
    }
    return new MappingDenotation<>(head.getDomainVar(), new ExplicitPairList(answer));
  }

  /**
   * Perform superlative on a list of (value, key).
   */
  public static UnaryDenotation superlativeUnary(int rank, int count, List<Pair<Value, Value>> pairs,
      SuperlativeFormula.Mode mode, TypeProcessor processor) {
    if (rank <= 0 || count <= 0 || rank >= 1000000 || count >= 100000)
      LogInfo.fails("Invalid superlative (rank = %d, count = %d)", rank, count);
    if (pairs.isEmpty()) {
      if (LambdaDCSExecutor.opts.superlativesFailOnEmptyLists)
        throw new LambdaDCSException(Type.emptyList, "Cannot call %s on an empty list.", mode);
      return UnaryDenotation.EMPTY;
    }
    List<Value> values = new ArrayList<>(), answer = new ArrayList<>();
    for (Pair<Value, Value> pair : pairs)
      values.add(pair.getFirst());
    if (processor == null) processor = getTypeProcessor(values);
    if (LambdaDCSExecutor.opts.superlativesReturnAllTopTies && rank == 1 && count == 1) {
      // Special case: Return all ties at the top
      Value topValue;
      switch (mode) {
        case argmax: topValue = processor.max(values); break;
        case argmin: topValue = processor.min(values); break;
        default: throw new LambdaDCSException(Type.invalidFormula, "Unknown superlative mode: %s", mode);
      }
      for (Pair<Value, Value> pair : pairs)
        if (topValue.equals(pair.getFirst()) && !answer.contains(pair.getSecond()))
          answer.add(pair.getSecond());
    } else {
      // Other cases
      List<Integer> indices;
      switch (mode) {
        case argmax: indices = processor.argsort(values); Collections.reverse(indices); break;
        case argmin: indices = processor.argsort(values); break;
        default: throw new LambdaDCSException(Type.invalidFormula, "Unknown superlative mode: %s", mode);
      }
      int from = Math.min(rank - 1, indices.size()),
          to = Math.min(from + count, indices.size());
      for (int index : indices.subList(from, to))
        answer.add(pairs.get(index).getSecond());
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
    // Is the value v compatible with this processor?
    public abstract boolean isCompatible(Value v);
    // Is the collection sortable? (Is there a total order on the elements?)
    public abstract boolean isSortable(Collection<Value> values);
    // positive if v1 > v2 | negative if v1 < v2 | 0 if v1 == v2
    public int compareValues(Value v1, Value v2) { throw new LambdaDCSException(Type.typeMismatch, "Cannot compare values with " + getClass().getSimpleName()); }
    public Value sum(Collection<Value> values) { throw new LambdaDCSException(Type.typeMismatch, "Cannot compute sum with " + getClass().getSimpleName()); }
    public Value avg(Collection<Value> values) { throw new LambdaDCSException(Type.typeMismatch, "Cannot compute avg with " + getClass().getSimpleName()); }
    public Value add(Value v1, Value v2) { throw new LambdaDCSException(Type.typeMismatch, "Cannot compute add with " + getClass().getSimpleName()); }
    public Value sub(Value v1, Value v2) { throw new LambdaDCSException(Type.typeMismatch, "Cannot compute sub with " + getClass().getSimpleName()); }
    public Value mul(Value v1, Value v2) { throw new LambdaDCSException(Type.typeMismatch, "Cannot compute mul with " + getClass().getSimpleName()); }
    public Value div(Value v1, Value v2) { throw new LambdaDCSException(Type.typeMismatch, "Cannot compute div with " + getClass().getSimpleName()); }

    public Value max(Collection<Value> values) {
      if (!isSortable(values))
        throw new LambdaDCSException(Type.typeMismatch, "Values cannot be sorted.");
      Value max = null;
      for (Value value : values) {
        if (max == null || compareValues(max, value) < 0)
          max = value;
      }
      return max;
    }

    public Value min(Collection<Value> values) {
      if (!isSortable(values))
        throw new LambdaDCSException(Type.typeMismatch, "Values cannot be sorted.");
      Value min = null;
      for (Value value : values) {
        if (min == null || compareValues(min, value) > 0)
          min = value;
      }
      return min;
    }

    public List<Integer> argsort(List<Value> values) {
      if (!isSortable(values))
        throw new LambdaDCSException(Type.typeMismatch, "Values cannot be sorted.");
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
    public boolean isSortable(Collection<Value> values) {
      return true;
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
    public boolean isSortable(Collection<Value> values) {
      DateValue firstDate = null;
      for (Value value : values) {
        DateValue date = (DateValue) value;
        if (firstDate == null) {
          firstDate = date;
        } else {
          if ((firstDate.year == -1) != (date.year == -1)) return false;
          if ((firstDate.month == -1) != (date.month == -1)) return false;
          if ((firstDate.day == -1) != (date.day == -1)) return false;
        }
      }
      return true;
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
      throw new LambdaDCSException(Type.typeMismatch, "Cannot compare values");
    }
  }

}
