package edu.stanford.nlp.sempre;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import fig.basic.MapUtils;
import fig.basic.Option;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * JavaExecutor takes a Formula which is composed recursively of CallFormulas,
 * does reflection, and returns a Value.
 *
 * @author Percy Liang
 */
public class JavaExecutor extends Executor {
  public static class Options {
    @Option(gloss = "Whether to convert NumberValue to int/double") public boolean convertNumberValues = true;
    @Option(gloss = "Print stack trace on exception") public boolean printStackTrace = false;
    // the actual function will be called with the current ContextValue as its last argument if marked by contextPrefix
    @Option(gloss = "Formula in the grammar whose name startsWith contextPrefix is context sensitive") 
    public String contextPrefix = "context:";
    @Option(gloss = "Reduce verbosity by automatically appending, for example, edu.stanford.nlp.sempre to java calls")
    public String classPathPrefix = ""; // e.g. "edu.stanford.nlp.sempre";
  }
  public static Options opts = new Options();

  private static JavaExecutor defaultExecutor = new JavaExecutor();

  // To simplify logical forms, define some shortcuts.
  private Map<String, String> shortcuts = Maps.newHashMap();

  public JavaExecutor() {
    String className = BasicFunctions.class.getName();

    shortcuts.put("+", className + ".plus");
    shortcuts.put("-", className + ".minus");
    shortcuts.put("*", className + ".times");
    shortcuts.put("/", className + ".divide");
    shortcuts.put("%", className + ".mod");
    shortcuts.put("!", className + ".not");

    shortcuts.put("<", className + ".lessThan");
    shortcuts.put("<=", className + ".lessThanEq");
    shortcuts.put("==", className + ".equals");
    shortcuts.put(">", className + ".greaterThan");
    shortcuts.put(">=", className + ".greaterThanEq");

    shortcuts.put("if", className + ".ifThenElse");
    shortcuts.put("map", className + ".map");
    shortcuts.put("reduce", className + ".reduce");
    shortcuts.put("select", className + ".select");
    shortcuts.put("range", className + ".range");
  }

  public static class BasicFunctions {
    public static double plus(double x, double y) { return x + y; }
    public static int plus(int x, int y) { return x + y; }
    public static int minus(int x, int y) { return x - y; }
    public static double minus(double x, double y) { return x - y; }
    public static int times(int x, int y) { return x * y; }
    public static double times(double x, double y) { return x * y; }
    public static int divide(int x, int y) { return x / y; }
    public static double divide(double x, double y) { return x / y; }
    public static int mod(int x, int y) { return x % y; }
    public static boolean not(boolean x) { return !x; }

    public static boolean lessThan(double x, double y) { return x < y; }
    public static boolean lessThanEq(double x, double y) { return x <= y; }
    public static boolean equals(double x, double y) { return x == y; }
    public static boolean greaterThan(double x, double y) { return x > y; }
    public static boolean greaterThanEq(double x, double y) { return x >= y; }

    public static Object ifThenElse(boolean b, Object x, Object y) { return b ? x : y; }

    // For very simple string concatenation
    public static String plus(String a, String b) { return a + b; }
    public static String plus(String a, String b, String c) {
      return a + b + c;
    }
    public static String plus(String a, String b, String c, String d) {
      return a + b + c + d;
    }
    public static String plus(String a, String b, String c, String d, String e) {
      return a + b + c + d + e;
    }
    public static String plus(String a, String b, String c, String d, String e, String f) {
      return a + b + c + d + e + f;
    }
    public static String plus(String a, String b, String c, String d, String e, String f, String g) {
      return a + b + c + d + e + f + g;
    }
    public static String plus(String a, String b, String c, String d, String e, String f, String g, String h) {
      return a + b + c + d + e + f + g + h;
    }
    public static String plus(String a, String b, String c, String d, String e, String f, String g, String h, String i) {
      return a + b + c + d + e + f + g + h + i;
    }
    public static String plus(String a, String b, String c, String d, String e, String f, String g, String h, String i, String j) {
      return a + b + c + d + e + f + g + h + i + j;
    }
    public static String plus(String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k) {
      return a + b + c + d + e + f + g + h + i + j + k;
    }
    public static String plus(String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l) {
      return a + b + c + d + e + f + g + h + i + j + k + l;
    }
    public static String plus(String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l, String m) {
      return a + b + c + d + e + f + g + h + i + j + k + l + m;
    }
    public static String plus(String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l, String m, String n) {
      return a + b + c + d + e + f + g + h + i + j + k + l + m + n;
    }
    public static String plus(String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l, String m, String n, String o) {
      return a + b + c + d + e + f + g + h + i + j + k + l + m + n + o;
    }
    private static String toString(Object x) {
      if (x instanceof String)
        return (String) x;
      else if (x instanceof Value)
        return (x instanceof NameValue) ? ((NameValue) x).id : ((StringValue) x).value;
      else
        return null;
    }

    // Apply func to each element of |list| and return the resulting list.
    public static List<Object> map(List<Object> list, LambdaFormula func) {
      List<Object> newList = new ArrayList<Object>();
      for (Object elem : list) {
        Object newElem = apply(func, elem);
        newList.add(newElem);
      }
      return newList;
    }

    // list = [3, 5, 2], func = (lambda x (lambda y (call + (var x) (var y))))
    // Returns (3 + 5) + 2 = 10
    public static Object reduce(List<Object> list, LambdaFormula func) {
      if (list.size() == 0) return null;
      Object x = list.get(0);
      for (int i = 1; i < list.size(); i++)
        x = apply(func, x, list.get(i));
      return x;
    }

    // Return elements x of |list| such that func(x) is true.
    public static List<Object> select(List<Object> list, LambdaFormula func) {
      List<Object> newList = new ArrayList<Object>();
      for (Object elem : list) {
        Object test = apply(func, elem);
        if ((Boolean) test)
          newList.add(elem);
      }
      return newList;
    }

    private static Object apply(LambdaFormula func, Object x) {
      // Apply the function func to x.  In order to do that, need to convert x into a value.
      Formula formula = Formulas.lambdaApply(func, new ValueFormula<Value>(toValue(x)));
      return defaultExecutor.processFormula(formula, null);
    }
    private static Object apply(LambdaFormula func, Object x, Object y) {
      // Apply the function func to x and y.  In order to do that, need to convert x into a value.
      Formula formula = Formulas.lambdaApply(func, new ValueFormula<Value>(toValue(x)));
      formula = Formulas.lambdaApply((LambdaFormula) formula, new ValueFormula<Value>(toValue(y)));
      return defaultExecutor.processFormula(formula, null);
    }

    public static List<Integer> range(int start, int end) {
      List<Integer> result = new ArrayList<Integer>();
      for (int i = start; i < end; i++)
        result.add(i);
      return result;
    }
  }

  public Response execute(Formula formula, ContextValue context) {
    // We can do beta reduction here since macro substitution preserves the
    // denotation (unlike for lambda DCS).
    formula = Formulas.betaReduction(formula);
    try {
      return new Response(toValue(processFormula(formula, context)));
    } catch (Exception e) {
      // Comment this out if we expect lots of innocuous type checking failures
      if (opts.printStackTrace) e.printStackTrace();
      return new Response(ErrorValue.badJava(e.toString()));
    }
  }

  private Object processFormula(Formula formula, ContextValue context) {
    if (formula instanceof ValueFormula)  // Unpack value and convert to object (e.g., for ints)
      return toObject(((ValueFormula) formula).value);

    if (formula instanceof CallFormula) {  // Invoke the function.
      // Recurse
      CallFormula call = (CallFormula) formula;
      Object func = processFormula(call.func, context);
      List<Object> args = Lists.newArrayList();
      for (Formula arg : call.args) {
        args.add(processFormula(arg, context));
      }

      if (!(func instanceof NameValue))
        throw new RuntimeException("Invalid func: " + call.func + " => " + func);

      String id = ((NameValue) func).id;
      if (id.indexOf(opts.contextPrefix) != -1) {
        args.add(context);
        id = id.replace(opts.contextPrefix, "");
      }
      id = MapUtils.get(shortcuts, id, id);
      
      // classPathPrefix, like edu.stanford.nlp.sempre.interactive
      if (!Strings.isNullOrEmpty(opts.classPathPrefix) && !id.startsWith(".") && !id.startsWith(opts.classPathPrefix)) {
        id = opts.classPathPrefix + "." + id;
      }

      if (id.startsWith(".")) // Instance method
        return invoke(id.substring(1), args.get(0), args.subList(1, args.size()).toArray(new Object[0]));

      else  // Static method
        return invoke(id, null, args.toArray(new Object[0]));
    }

    // Just pass it through...
    return formula;
  }

  // Convert the Object back to a Value
  private static Value toValue(Object obj) {
    if (obj instanceof Value) return (Value) obj;
    if (obj instanceof Boolean) return new BooleanValue((Boolean) obj);
    if (obj instanceof Integer) return new NumberValue(((Integer) obj).intValue());
    if (obj instanceof Double) return new NumberValue(((Double) obj).doubleValue());
    if (obj instanceof String) return new StringValue((String) obj);
    if (obj instanceof List) {
      List<Value> list = Lists.newArrayList();
      for (Object elem : (List) obj)
        list.add(toValue(elem));
      return new ListValue(list);
    }
    throw new RuntimeException("Unhandled object: " + obj + " with class " + obj.getClass());
  }

  // Convert a Value (which are specified in the formulas) to an Object (which
  // many Java functions take).
  private static Object toObject(Value value) {
    if (value instanceof NumberValue && opts.convertNumberValues) {
      // Unfortunately, NumberValues don't make a distinction between ints and
      // doubles, so this is a hack.
      double x = ((NumberValue) value).value;
      if (x == (int) x) return new Integer((int) x);
      return new Double(x);
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

  // Example: id = "Math.cos"
  private Object invoke(String id, Object thisObj, Object[] args) {
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
      cost += typeCastCost(types[i], args[i]);
      if (cost >= INVALID_TYPE_COST) {
        // LogInfo.dbgs("NOT COMPATIBLE: want %s, got %s with type %s", types[i], args[i], args[i].getClass());
        break;
      }
    }
    return cost;
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
