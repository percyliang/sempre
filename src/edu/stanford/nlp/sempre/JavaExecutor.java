package edu.stanford.nlp.sempre;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import fig.basic.MapUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
  // To simplify logical forms, define some shortcuts.
  private Map<String, String> shortcuts = Maps.newHashMap();

  public JavaExecutor() {
    String className = BasicFunctions.class.getName();

    shortcuts.put("+", className + ".plus");
    shortcuts.put("-", className + ".minus");
    shortcuts.put("*", className + ".times");
    shortcuts.put("/", className + ".divide");

    shortcuts.put("<", className + ".lessThan");
    shortcuts.put("<=", className + ".lessThanEq");
    shortcuts.put("==", className + ".equals");
    shortcuts.put(">", className + ".greaterThan");
    shortcuts.put(">=", className + ".greaterThanEq");

    shortcuts.put("if", className + ".ifThenElse");
  }

  private static class BasicFunctions {
    public static double plus(double x, double y) { return x + y; }
    public static double minus(double x, double y) { return x - y; }
    public static double times(double x, double y) { return x * y; }
    public static double divide(double x, double y) { return x / y; }

    public static boolean lessThan(double x, double y) { return x < y; }
    public static boolean lessThanEq(double x, double y) { return x <= y; }
    public static boolean equals(double x, double y) { return x == y; }
    public static boolean greaterThan(double x, double y) { return x > y; }
    public static boolean greaterThanEq(double x, double y) { return x >= y; }

    public static Object ifThenElse(boolean b, Object x, Object y) { return b ? x : y; }
  }

  public Response execute(Formula formula) {
    formula = Formulas.betaReduction(formula);
    return new Response(recurse(formula));
  }

  private Value recurse(Formula formula) {
    if (formula instanceof ValueFormula)
      return ((ValueFormula)formula).value;

    if (formula instanceof CallFormula) {
      // Recurse
      CallFormula call = (CallFormula)formula;
      Value func = recurse(call.func);
      List<Value> args = Lists.newArrayList();
      for (Formula arg : call.args)
        args.add(recurse(arg));

      if (!(func instanceof NameValue))
        throw new RuntimeException("Invalid func: " + call.func + " => " + func);

      String id = ((NameValue)func).id;
      id = MapUtils.get(shortcuts, id, id);

      if (id.startsWith("."))  // Instance method
        return toValue(invoke(id.substring(1), toObject(args.get(0)), toObjects(args.subList(1, args.size()))));
      else
        return toValue(invoke(id, null, toObjects(args)));
    }

    throw new RuntimeException("Invalid formula: " + formula);
  }

  private Value toValue(Object obj) {
    if (obj instanceof Boolean) return new BooleanValue((Boolean)obj);
    if (obj instanceof Integer) return new NumberValue(((Integer)obj).intValue());
    if (obj instanceof Double) return new NumberValue(((Double)obj).doubleValue());
    if (obj instanceof String) return new StringValue((String)obj);
    throw new RuntimeException("Invalid object: " + obj);
  }

  private Object[] toObjects(List<Value> values) {
    Object[] result = new Object[values.size()];
    for (int i = 0; i < values.size(); i++)
      result[i] = toObject(values.get(i));
    return result;
  }

  private Object toObject(Value value) {
    if (value instanceof NumberValue) {
      // Unfortunately, we don't make a distinction between ints and doubles, so this is a hack.
      double x = ((NumberValue)value).value;
      if (x == (int)x) return new Integer((int)x);
      return new Double((int)x);
    } else if (value instanceof BooleanValue) {
      return ((BooleanValue)value).value;
    } else if (value instanceof StringValue) {
      return ((StringValue)value).value;
    } else {
      throw new RuntimeException("Unhandled: " + value);
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
      if (i == -1) throw new RuntimeException("Expected <class>.<method>, but got: " + id);
      String className = id.substring(0, i);
      methodName = id.substring(i+1);

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
    for (Method m : methods) {
      if (!m.getName().equals(methodName)) continue;
      nameMatches.add(m);
      if (isStatic != Modifier.isStatic(m.getModifiers())) continue;
      if (!isCompatible(m.getParameterTypes(), args)) continue;
      //logs("  %s", m);
      try {
        return m.invoke(thisObj, args);
      } catch (InvocationTargetException e) {
        throw new RuntimeException(e.getCause());
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
    throw new RuntimeException("Method " + methodName + " not found in class " + cls + " with arguments " + Arrays.asList(args) + "; candidates: " + nameMatches);
  }

  private boolean isCompatible(Class[] types, Object[] args) {
    if (types.length != args.length) return false;
    for (int i = 0; i < types.length; i++) {
      if (!isCompatible(types[i], args[i]))
        return false;
    }
    return true;
  }

  private boolean isCompatible(Class<?> type, Object arg) {
    if (arg == null) return !type.isPrimitive();
    if (type.isInstance(arg)) return true;
    // Order matters here for type checking: try to match the most restrictive types first.
    if (type == Boolean.TYPE) return arg instanceof Boolean;
    if (type == Integer.TYPE) return arg instanceof Integer || arg instanceof Long;
    if (type == Long.TYPE) return arg instanceof Integer || arg instanceof Long;
    if (type == Float.TYPE) return arg instanceof Integer || arg instanceof Long || arg instanceof Float || arg instanceof Double;
    if (type == Double.TYPE) return arg instanceof Integer || arg instanceof Long || arg instanceof Float || arg instanceof Double;
    return false;
  }
}
