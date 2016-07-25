package edu.stanford.nlp.sempre.interactive.actions;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import edu.stanford.nlp.sempre.*;
import fig.basic.Option;

/**
 * Handles action lambda DCS
 * where the world has a flat structure, i.e. a list of allitems all supporting the same operations
 * @author Sida Wang
 */
public class ActionExecutor extends Executor {
  public static class Options {
    @Option(gloss = "Whether to convert NumberValue to int/double") public boolean convertNumberValues = true;
    @Option(gloss = "Whether to convert name values to string literal") public boolean convertNameValues = true;

    @Option(gloss = "Print stack trace on exception") public boolean printStackTrace = false;
    // the actual function will be called with the current ContextValue as its last argument if marked by contextPrefix
    @Option(gloss = "Reduce verbosity by automatically appending, for example, edu.stanford.nlp.sempre to java calls")
    public String classPathPrefix = "edu.stanford.nlp.sempre";

    @Option(gloss = "The type of FlatWorld used")
    public String FlatWorldType = "BlocksWorld";
  }
  public static Options opts = new Options();
  
  public static final String STAR = "*";
  public static final String SELECTED = "this";

  
  public Response execute(Formula formula, ContextValue context) {
    // We can do beta reduction here since macro substitution preserves the
    // denotation (unlike for lambda DCS).
    FlatWorld world = FlatWorld.fromContext(opts.FlatWorldType, context);
    formula = Formulas.betaReduction(formula);
    performActions((ActionFormula)formula, world);
    try {
      return new Response(new StringValue(world.toJSON()));
    } catch (Exception e) {
      // Comment this out if we expect lots of innocuous type checking failures
      if (opts.printStackTrace) e.printStackTrace();
      return new Response(ErrorValue.badJava(e.toString()));
    }
  }

  private void performActions(ActionFormula f, FlatWorld world) {
    if (f.mode == ActionFormula.Mode.primitive) {
      // use reflection to call primitive stuff
      Value method  = ((ValueFormula)f.args.get(0)).value;
      String id = ((NameValue)method).id;
      // all actions takes a fixed set as argument
      invoke(id, world, f.args.subList(1, f.args.size()).stream().map(x -> processSetFormula(x, world)).toArray());
    }
    else if (f.mode == ActionFormula.Mode.sequential) {
      for (Formula child : f.args) {
        performActions((ActionFormula)child, world);
      }
    } else if (f.mode == ActionFormula.Mode.repeat) {
      Set<Object> arg = toSet(processSetFormula(f.args.get(0), world));
      if (arg.size() > 1) throw new RuntimeException("repeat has to take a single number");
      
      int times;
      if (!opts.convertNumberValues)
        times = (int)((NumberValue)arg.iterator().next()).value;
      else 
        times = (int)arg.iterator().next();

      for (int i = 0; i < times; i++)
        performActions((ActionFormula)f.args.get(1), world);
    } else if (f.mode == ActionFormula.Mode.conditional) {
      // using the empty set to represent false
      boolean cond = toSet(processSetFormula(f.args.get(0), world)).iterator().hasNext();
      if (cond) performActions((ActionFormula)f.args.get(1), world);
    } else if (f.mode == ActionFormula.Mode.scope) {
      // using the empty set to represent false
      Set<Object> currentscope = toSet(processSetFormula(f.args.get(0), world));
      world.push();
      world.select(toItemSet(currentscope));
      world.pop();
      performActions((ActionFormula)f.args.get(1), world);
    }
  }


  private Set<Object> toSet(Object maybeSet) {
    if (maybeSet instanceof Set) return (Set) maybeSet;
    else return Sets.newHashSet(maybeSet);
  }
  private Set<Item> toItemSet(Set<Object> maybeItems) {
    Set<Item> itemset = maybeItems.stream().map(i -> (Item)i)
        .collect(Collectors.toSet());
    return itemset;
  }

  // a subset of lambda dcs. no types, and no marks. actually implemented with predicates...
  // if this gets any more complicated, then should just use LambdaDCSExecutor
  @SuppressWarnings("unchecked")
  private Object processSetFormula(Formula formula, final FlatWorld world) {
    if (formula instanceof ValueFormula<?>) {
      Value v = ((ValueFormula<?>) formula).value;
      // special unary
      if (v instanceof NameValue) {
        String id = ((NameValue) v).id;
        if (id.equals(ActionExecutor.STAR))
          return world.all();
        if (id.equals(ActionExecutor.SELECTED))
          return world.selected();
      } 
      return toObject(((ValueFormula<?>) formula).value);
    }

    if (formula instanceof JoinFormula) {
      JoinFormula joinFormula = (JoinFormula)formula;
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

    if (formula instanceof MergeFormula)  {
      MergeFormula mergeFormula = (MergeFormula)formula;
      MergeFormula.Mode mode = mergeFormula.mode;
      Set<Object> set1 = toSet(processSetFormula(mergeFormula.child1, world)); 
      Set<Object> set2 = toSet(processSetFormula(mergeFormula.child2, world));
      if (mode == MergeFormula.Mode.or)
        return Sets.union(set1, set2);
      if (mode == MergeFormula.Mode.and)
        return Sets.intersection(set1, set2);
    }
    
    if (formula instanceof NotFormula)  {
      NotFormula notFormula = (NotFormula)formula;
      Set<Object> set1 = toSet(processSetFormula(notFormula.child, world)); 
      return Sets.difference(world.allitems, set1);
    }

    if (formula instanceof AggregateFormula)  {
      AggregateFormula aggregateFormula = (AggregateFormula)formula;
      Set<Object> set = toSet(processSetFormula(aggregateFormula.child, world)); 
      AggregateFormula.Mode mode = aggregateFormula.mode;
      if (mode == AggregateFormula.Mode.count)
        return Sets.newHashSet(set.size());
      if (mode == AggregateFormula.Mode.max)
        return Sets.newHashSet(set.stream().max((s,t) -> ((NumberValue)s).value > ((NumberValue)t).value ? 1 : -1));
      if (mode == AggregateFormula.Mode.min)
        return Sets.newHashSet(set.stream().max((s,t) -> ((NumberValue)s).value < ((NumberValue)t).value ? 1 : -1));
    }
    
    if (formula instanceof CallFormula)  {
    }
    
    if (formula instanceof SuperlativeFormula)  {
    }
    
    throw new RuntimeException("Should never get here");
  }

  // Example: id = "Math.cos". similar to JavaExecutor's invoke,
  // but matches arg by building singleton set as needed
  private Object invoke(String id, Object thisObj, Object ... args) {
    Method[] methods;
    Class<?> cls;
    String methodName;
    boolean isStatic = thisObj == null;

    if (isStatic) {  // Static methods
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
    } else {  // Instance methods
      cls = thisObj.getClass();
      methodName = id;
      methods = cls.getMethods();
    }

    // Find a suitable method
    List<Method> nameMatches = Lists.newArrayList();
    Method bestMethod = null;
    int bestCost = INVALID_TYPE_COST;
    for (Method m : methods) {
      if (!m.getName().equals(methodName)) continue;
      m.setAccessible(true);
      nameMatches.add(m);
      if (isStatic != Modifier.isStatic(m.getModifiers())) continue;
      int cost = typeCastCost(m.getParameterTypes(), args);
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
    throw new RuntimeException("Method " + methodName + " not found in class " + cls + " with arguments " + Arrays.asList(args) + " having types " + types + "; candidates: " + nameMatches);
  }

  private int typeCastCost(Class[] types, Object[] args) {
    if (types.length != args.length) return INVALID_TYPE_COST;
    int cost = 0;
    for (int i = 0; i < types.length; i++) {

      if (types[i] == Set.class)
        args[i] = toSet(args[i]);

      cost += typeCastCost(types[i], args[i]);
      if (cost >= INVALID_TYPE_COST) {
        // LogInfo.dbgs("NOT COMPATIBLE: want %s, got %s with type %s", types[i], args[i], args[i].getClass());
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
      if (x == (int) x) return new Integer((int) x);
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
      return value;  // Preserve the Value (which can be an object)
    }
  }
  // Return whether the object |arg| is compatible with |type|.
  // 0: perfect match
  // 1: don't match, but don't lose anything
  // 2: don't match, and can lose something
  // INVALID_TYPE_COST: impossible
  private int typeCastCost(Class<?> type, Object arg) {
    if (arg == null) return !type.isPrimitive() ? 0 : INVALID_TYPE_COST;
    if (type.isInstance(arg)) return 0;
    if (type == Boolean.TYPE) return arg instanceof Boolean ? 0 : INVALID_TYPE_COST;
    else if (type == Integer.TYPE) {
      if (arg instanceof Integer) return 0;
      if (arg instanceof Long) return 1;
      return INVALID_TYPE_COST;
    }
    if (type == Long.TYPE) {
      if (arg instanceof Integer) return 1;
      if (arg instanceof Long) return 0;
      return INVALID_TYPE_COST;
    }
    if (type == Float.TYPE) {
      if (arg instanceof Integer) return 1;
      if (arg instanceof Long) return 1;
      if (arg instanceof Float) return 0;
      if (arg instanceof Double) return 2;
      return INVALID_TYPE_COST;
    }
    if (type == Double.TYPE) {
      if (arg instanceof Integer) return 1;
      if (arg instanceof Long) return 1;
      if (arg instanceof Float) return 1;
      if (arg instanceof Double) return 0;
      return INVALID_TYPE_COST;
    }
    return INVALID_TYPE_COST;
  }

  private static final int INVALID_TYPE_COST = 1000;
}
