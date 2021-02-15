package edu.stanford.nlp.sempre.interactive;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.ObjectArrays;
import com.google.common.collect.Sets;

import edu.stanford.nlp.sempre.*;
import fig.basic.LogInfo;
import fig.basic.Option;

/**
 * Handles action lambda DCS where the world has a flat structure, i.e. a list
 * of allitems all supporting the same operations supports ActionFormula here,
 * and does conversions of singleton sets
 * 
 * @author sidaw
 */
public class DALExecutor extends Executor {
  public static class Options {
    @Option(gloss = "Whether to convert NumberValue to int/double")
    public boolean convertNumberValues = true;
    @Option(gloss = "Whether to convert name values to string literal")
    public boolean convertNameValues = true;

    @Option(gloss = "Print stack trace on exception")
    public boolean printStackTrace = false;
    // the actual function will be called with the current ContextValue as its
    // last argument if marked by contextPrefix
    @Option(gloss = "Reduce verbosity by automatically appending, for example, edu.stanford.nlp.sempre to java calls")
    public String classPathPrefix = "edu.stanford.nlp.sempre";

    @Option(gloss = "The type of world used")
    public String worldType = "VoxelWorld";

    @Option(gloss = "the maximum number of primitive calls until we stop executing")
    public int maxSteps = 1000;

    @Option(gloss = "The maximum number of while calls")
    public int maxWhile = 20;
  }

  public static Options opts = new Options();

  @Override
  public Response execute(Formula formula, ContextValue context) {
    // We can do beta reduction here since macro substitution preserves the
    // denotation (unlike for lambda DCS).
    World world = World.fromContext(opts.worldType, context);
    formula = Formulas.betaReduction(formula);
    try {
      performActions((ActionFormula) formula, world);
      return new Response(new StringValue(world.toJSON()));
    } catch (Exception e) {
      // Comment this out if we expect lots of innocuous type checking failures
      if (opts.printStackTrace) {
        LogInfo.log("Failed to execute " + formula.toString());
        e.printStackTrace();
      }
      return new Response(ErrorValue.badJava(e.toString()));
    }
  }

  @SuppressWarnings("rawtypes")
  private void performActions(ActionFormula f, World world) {
    if (f.mode == ActionFormula.Mode.primitive) {
      // use reflection to call primitive stuff
      Value method = ((ValueFormula) f.args.get(0)).value;
      String id = ((NameValue) method).id;
      // all actions takes a fixed set as argument
      invoke(id, world, f.args.subList(1, f.args.size()).stream().map(x -> processSetFormula(x, world)).toArray());
      world.merge();
    } else if (f.mode == ActionFormula.Mode.sequential) {
      for (Formula child : f.args) {
        performActions((ActionFormula) child, world);
      }
    } else if (f.mode == ActionFormula.Mode.repeat) {
      Set<Object> arg = toSet(processSetFormula(f.args.get(0), world));
      if (arg.size() > 1)
        throw new RuntimeException("repeat has to take a single number");
      int times;
      if (!opts.convertNumberValues)
        times = (int) ((NumberValue) arg.iterator().next()).value;
      else
        times = (int) arg.iterator().next();

      for (int i = 0; i < times; i++)
        performActions((ActionFormula) f.args.get(1), world);
    } else if (f.mode == ActionFormula.Mode.conditional) {
      // using the empty set to represent false
      boolean cond = toSet(processSetFormula(f.args.get(0), world)).iterator().hasNext();
      if (cond)
        performActions((ActionFormula) f.args.get(1), world);
    } else if (f.mode == ActionFormula.Mode.whileloop) {
      // using the empty set to represent false
      boolean cond = toSet(processSetFormula(f.args.get(0), world)).iterator().hasNext();
      for (int i = 0; i < opts.maxWhile; i++) {
        if (cond)
          performActions((ActionFormula) f.args.get(1), world);
        else
          break;
        cond = toSet(processSetFormula(f.args.get(0), world)).iterator().hasNext();
      }
    } else if (f.mode == ActionFormula.Mode.forset) {
      // mostly deprecated
      Set<Object> selected = toSet(processSetFormula(f.args.get(0), world));
      Set<Item> prevSelected = world.selected;

      world.selected = toItemSet(selected);
      performActions((ActionFormula) f.args.get(1), world);

      world.selected = prevSelected;
      world.merge();
    } else if (f.mode == ActionFormula.Mode.foreach) {
      Set<Item> selected = toItemSet(toSet(processSetFormula(f.args.get(0), world)));
      Set<Item> prevSelected = world.selected;
      // CopyOnWriteArraySet<Object> fixedset =
      // Sets.newCopyOnWriteArraySet(selected);
      Iterator<Item> iterator = selected.iterator();
      while (iterator.hasNext()) {
        world.selected = (toItemSet(toSet(iterator.next())));
        performActions((ActionFormula) f.args.get(1), world);
      }
      world.selected = prevSelected;
      world.merge();

    } else if (f.mode == ActionFormula.Mode.isolate) {
      Set<Item> prevAll = world.allItems;
      // Set<Item> prevSelected = world.selected;
      // Set<Item> prevPrevious = world.previous;
      if (f.args.size() > 1)
        throw new RuntimeException("No longer supporting this isolate formula: " + f);

      world.allItems = Sets.newHashSet(world.selected);
      // world.selected = scope;
      // world.previous = scope;
      performActions((ActionFormula) f.args.get(0), world);

      world.allItems.addAll(prevAll); // merge, overriding;
      // world.selected = prevSelected;
      // world.previous = prevPrevious;
      world.merge();

    } else if (f.mode == ActionFormula.Mode.block || f.mode == ActionFormula.Mode.blockr) {
      // we should never mutate selected in actions
      Set<Item> prevSelected = world.selected;
      Set<Item> prevPrevious = world.previous;
      world.previous = world.selected;

      for (Formula child : f.args) {
        performActions((ActionFormula) child, world);
      }

      // restore on default blocks
      if (f.mode == ActionFormula.Mode.block) {
        world.selected = prevSelected;
        world.merge();
      }
      // LogInfo.logs("CBlocking prevselected=%s selected=%s all=%s",
      // prevSelected, world.selected, world.allitems);
      // LogInfo.logs("BlockingWorldIs %s", world.toJSON());
      world.previous = prevPrevious;
    }
    // } else if (f.mode == ActionFormula.Mode.let) {
    // // let declares a new local variable
    // // set access and reassigns the value of some variable
    // // block determines what is considered local scope
    // // for now the use case is just (:blk (:let x this) (:blah) (:set this
    // x))
    // Set<Item> varset = toItemSet(toSet(processSetFormula(f.args.get(1),
    // world)));
    // Value method = ((ValueFormula)f.args.get(0)).value;
    // String varname = ((NameValue)method).id;
    // world.variables.put(varname, varset);
    // } else if (f.mode == ActionFormula.Mode.set) {
    // Set<Item> varset = toItemSet(toSet(processSetFormula(f.args.get(1),
    // world)));
    // Value method = ((ValueFormula)f.args.get(0)).value;
    // String varname = ((NameValue)method).id;
    // world.variables.get(varname).clear();
    // world.variables.get(varname).addAll(varset);
    // }

  }

  @SuppressWarnings("unchecked")
  private Set<Object> toSet(Object maybeSet) {
    if (maybeSet instanceof Set)
      return (Set<Object>) maybeSet;
    else
      return Sets.newHashSet(maybeSet);
  }

  private Object toElement(Set<Object> set) {
    if (set.size() == 1) {
      return set.iterator().next();
    }
    return set;
  }

  private Set<Item> toItemSet(Set<Object> maybeItems) {
    Set<Item> itemset = maybeItems.stream().map(i -> (Item) i).collect(Collectors.toSet());
    return itemset;
  }

  static class SpecialSets {
    static String All = "*";
    static String EmptySet = "nothing";
    static String This = "this"; // current scope if it exists, otherwise the
                                 // globally marked object
    static String Previous = "prev"; // global variable for selected
    static String Selected = "selected"; // global variable for selected
  };

  // a subset of lambda dcs. no types, and no marks
  // if this gets any more complicated, you should consider the
  // LambdaDCSExecutor
  @SuppressWarnings("unchecked")
  private Object processSetFormula(Formula formula, final World world) {
    if (formula instanceof ValueFormula<?>) {
      Value v = ((ValueFormula<?>) formula).value;
      // special unary
      if (v instanceof NameValue) {
        String id = ((NameValue) v).id;
        // LogInfo.logs("%s : this %s, all: %s", id,
        // world.selected().toString(), world.allitems.toString());
        if (id.equals(SpecialSets.All))
          return world.all();
        if (id.equals(SpecialSets.This))
          return world.selected();
        if (id.equals(SpecialSets.Selected))
          return world.selected();
        if (id.equals(SpecialSets.EmptySet))
          return world.empty();
        if (id.equals(SpecialSets.Previous))
          return world.previous();
      }
      return toObject(((ValueFormula<?>) formula).value);
    }

    if (formula instanceof JoinFormula) {
      JoinFormula joinFormula = (JoinFormula) formula;
      if (joinFormula.relation instanceof ValueFormula) {
        String rel = ((ValueFormula<NameValue>) joinFormula.relation).value.id;
        Set<Object> unary = toSet(processSetFormula(joinFormula.child, world));
        return world.has(rel, unary);
      } else if (joinFormula.relation instanceof ReverseFormula) {
        ReverseFormula reverse = (ReverseFormula) joinFormula.relation;
        String rel = ((ValueFormula<NameValue>) reverse.child).value.id;
        Set<Object> unary = toSet(processSetFormula(joinFormula.child, world));
        return world.get(rel, toItemSet(unary));
      } else {
        throw new RuntimeException("relation can either be a value, or its reverse");
      }
    }

    if (formula instanceof MergeFormula) {
      MergeFormula mergeFormula = (MergeFormula) formula;
      MergeFormula.Mode mode = mergeFormula.mode;
      Set<Object> set1 = toSet(processSetFormula(mergeFormula.child1, world));
      Set<Object> set2 = toSet(processSetFormula(mergeFormula.child2, world));

      if (mode == MergeFormula.Mode.or)
        return Sets.union(set1, set2);
      if (mode == MergeFormula.Mode.and)
        return Sets.intersection(set1, set2);

    }

    if (formula instanceof NotFormula) {
      NotFormula notFormula = (NotFormula) formula;
      Set<Item> set1 = toItemSet(toSet(processSetFormula(notFormula.child, world)));
      return Sets.difference(world.allItems, set1);
    }

    if (formula instanceof AggregateFormula) {
      AggregateFormula aggregateFormula = (AggregateFormula) formula;
      Set<Object> set = toSet(processSetFormula(aggregateFormula.child, world));
      AggregateFormula.Mode mode = aggregateFormula.mode;
      if (mode == AggregateFormula.Mode.count)
        return Sets.newHashSet(set.size());
      if (mode == AggregateFormula.Mode.max)
        return Sets.newHashSet(set.stream().max((s, t) -> ((NumberValue) s).value > ((NumberValue) t).value ? 1 : -1));
      if (mode == AggregateFormula.Mode.min)
        return Sets.newHashSet(set.stream().max((s, t) -> ((NumberValue) s).value < ((NumberValue) t).value ? 1 : -1));
    }

    if (formula instanceof ArithmeticFormula) {
      ArithmeticFormula arithmeticFormula = (ArithmeticFormula) formula;
      Integer arg1 = (Integer) processSetFormula(arithmeticFormula.child1, world);
      Integer arg2 = (Integer) processSetFormula(arithmeticFormula.child2, world);
      ArithmeticFormula.Mode mode = arithmeticFormula.mode;
      if (mode == ArithmeticFormula.Mode.add)
        return arg1 + arg2;
      if (mode == ArithmeticFormula.Mode.sub)
        return arg1 - arg2;
      if (mode == ArithmeticFormula.Mode.mul)
        return arg1 * arg2;
      if (mode == ArithmeticFormula.Mode.div)
        return arg1 / arg2;
    }

    if (formula instanceof CallFormula) {
      CallFormula callFormula = (CallFormula) formula;
      @SuppressWarnings("rawtypes")
      Value method = ((ValueFormula) callFormula.func).value;
      String id = ((NameValue) method).id;
      // all actions takes a fixed set as argument
      return invoke(id, world, callFormula.args.stream().map(x -> processSetFormula(x, world)).toArray());
    }
    if (formula instanceof SuperlativeFormula) {
      throw new RuntimeException("SuperlativeFormula is not implemented");
    }
    throw new RuntimeException("ActionExecutor does not handle this formula type: " + formula.getClass());
  }

  // Example: id = "Math.cos". similar to JavaExecutor's invoke,
  // but matches arg by building singleton set as needed
  private Object invoke(String id, World thisObj, Object... args) {
    Method[] methods;
    Class<?> cls;
    String methodName;
    boolean isStatic = thisObj == null;

    if (isStatic) { // Static methods
      int i = id.lastIndexOf('.');
      if (i == -1) {
        throw new RuntimeException("Expected <class>.<method>, but got: " + id);
      }
      String className = id.substring(0, i);
      methodName = id.substring(i + 1);

      try {
        cls = Class.forName(className);
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
      methods = cls.getMethods();
    } else { // Instance methods
      cls = thisObj.getClass();
      methodName = id;
      methods = cls.getMethods();
    }

    // Find a suitable method
    List<Method> nameMatches = Lists.newArrayList();
    Method bestMethod = null;
    int bestCost = INVALID_TYPE_COST;
    for (Method m : methods) {
      if (!m.getName().equals(methodName))
        continue;
      m.setAccessible(true);
      nameMatches.add(m);
      if (isStatic != Modifier.isStatic(m.getModifiers()))
        continue;
      int cost = typeCastCost(m.getParameterTypes(), args);

      // append optional selected parameter when needed:
      if (cost == INVALID_TYPE_COST && args.length + 1 == m.getParameterCount()) {
        args = ObjectArrays.concat(args, thisObj.selected);
        cost = typeCastCost(m.getParameterTypes(), args);
      }

      if (cost < bestCost) {
        bestCost = cost;
        bestMethod = m;
      }
    }

    if (bestMethod != null) {
      try {
        return bestMethod.invoke(thisObj, args);
      } catch (InvocationTargetException e) {
        throw new RuntimeException(e.getCause());
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
    List<String> types = Lists.newArrayList();
    for (Object arg : args)
      types.add(arg.getClass().toString());
    throw new RuntimeException("Method " + methodName + " not found in class " + cls + " with arguments "
        + Arrays.asList(args) + " having types " + types + "; candidates: " + nameMatches);
  }

  private int typeCastCost(Class[] types, Object[] args) {
    if (types.length != args.length)
      return INVALID_TYPE_COST;
    int cost = 0;
    for (int i = 0; i < types.length; i++) {

      // deal with singleton sets
      if (types[i] == Set.class)
        args[i] = toSet(args[i]);
      if (types[i] != Set.class && args[i].getClass() == Set.class) {
        args[i] = toElement((Set<Object>) args[i]);
      }

      cost += typeCastCost(types[i], args[i]);
      if (cost >= INVALID_TYPE_COST) {
        LogInfo.dbgs("NOT COMPATIBLE: want %s, got %s with type %s", types[i], args[i], args[i].getClass());
        break;
      }
    }
    return cost;
  }

  private static Object toObject(Value value) {
    if (value instanceof NumberValue && opts.convertNumberValues) {
      // Unfortunately, NumberValues don't make a distinction between ints and
      // doubles, so this is a hack.
      double x = ((NumberValue) value).value;
      if (x == (int) x)
        return new Integer((int) x);
      return new Double(x);
    } else if (value instanceof NameValue && opts.convertNameValues) {
      String id = ((NameValue) value).id;
      return id;
    } else if (value instanceof BooleanValue) {
      return ((BooleanValue) value).value;
    } else if (value instanceof StringValue) {
      return ((StringValue) value).value;
    } else if (value instanceof ListValue) {
      List<Object> list = Lists.newArrayList();
      for (Value elem : ((ListValue) value).values)
        list.add(toObject(elem));
      return list;
    } else {
      return value; // Preserve the Value (which can be an object)
    }
  }

  // Return whether the object |arg| is compatible with |type|.
  // 0: perfect match
  // 1: don't match, but don't lose anything
  // 2: don't match, and can lose something
  // INVALID_TYPE_COST: impossible
  private int typeCastCost(Class<?> type, Object arg) {
    if (arg == null)
      return !type.isPrimitive() ? 0 : INVALID_TYPE_COST;
    if (type.isInstance(arg))
      return 0;
    if (type == Boolean.TYPE)
      return arg instanceof Boolean ? 0 : INVALID_TYPE_COST;
    else if (type == Integer.TYPE) {
      if (arg instanceof Integer)
        return 0;
      if (arg instanceof Long)
        return 1;
      return INVALID_TYPE_COST;
    }
    if (type == Long.TYPE) {
      if (arg instanceof Integer)
        return 1;
      if (arg instanceof Long)
        return 0;
      return INVALID_TYPE_COST;
    }
    if (type == Float.TYPE) {
      if (arg instanceof Integer)
        return 1;
      if (arg instanceof Long)
        return 1;
      if (arg instanceof Float)
        return 0;
      if (arg instanceof Double)
        return 2;
      return INVALID_TYPE_COST;
    }
    if (type == Double.TYPE) {
      if (arg instanceof Integer)
        return 1;
      if (arg instanceof Long)
        return 1;
      if (arg instanceof Float)
        return 1;
      if (arg instanceof Double)
        return 0;
      return INVALID_TYPE_COST;
    }
    return INVALID_TYPE_COST;
  }

  private static final int INVALID_TYPE_COST = 1000;
}
