package edu.stanford.nlp.sempre.interactive.blocks;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.Lists;
import fig.basic.*;
import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.interactive.blocks.StacksWorld.Color;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
/**
 * Functional primitives
 * @author Sida Wang
 */
public final class RicherStacksWorld {
  public static class Options {
    @Option(gloss = "Verbosity")
    public int verbose = 0;
    @Option(gloss = "How many times to repeat")
    public int iterLimit = 20;
    @Option(gloss = "size of the world")
    public int totalSize  = 64;
    @Option(gloss = "number of colums, so worldSize/numCols is the number of sheets")
    public int worldSize  = 8;
  }
  public static Options opts = new Options();
  public static final String COLOR = "COLOR";

  private static final Random random = new Random(1);
  private RicherStacksWorld() { }
  
  public static String getWorld(String worldname) {
    List<Stack> world = new ArrayList<>();
    for (int i = 0; i < opts.totalSize; i++) {
      world.add(new Stack(i));
    }
    if (!worldname.equals("emptyworld")) {
      for (int i = 0; i < opts.totalSize; i++) {
          if (random.nextInt(100) > 10) continue;
          int height = random.nextInt(4);
          int color = random.nextInt(Color.MAXCOLOR);
          for (int j = 0; j < height; j++) {
            world.get(i).addCube(color);
            if (random.nextInt(100) > 10) color = random.nextInt(Color.MAXCOLOR);
          }
      }
    }
    world.get(opts.totalSize/2 + opts.worldSize/2).mark = true;
    return wallToString(world);
  }
  
  public enum Color {
    Red(0), Orange(1), Yellow (2), Green(3), Blue(4), Purple(5), Brown(6), Cyan(6), Transparent(9), Mark(10);
    private final int value;
    private static final int MAXCOLOR = 6;
    private Color(int value) { this.value = value; }
    public int intValue() { return this.value; }
    public static NumberValue fromInt(int i) { return new NumberValue(i, COLOR); }
    public NumberValue toNumVal() { return new NumberValue(this.value, COLOR); }
    public static boolean isMark(int m) {return m==Mark.intValue();}
  }
  
  public static boolean colorsEqual(NumberValue c1, NumberValue c2)
  {
    if (!c1.unit.equals(COLOR) || !c2.unit.equals(COLOR)) return false;
    int c1v = (int) c1.value; int c2v = (int) c2.value;
    if (c1v != c2v) return false;
    return true;
  }

  // wallString consists of [[1,2,3],[2,1,2],[2,3,4]]
  public static List<Stack> getWallFromContext(Ref<ContextValue> context) {
    NaiveKnowledgeGraph graph = (NaiveKnowledgeGraph)context.value.graph;
    String wallString = ((StringValue)graph.triples.get(0).e1).value;
    return stringToWall(wallString);
  }
  //wallString consists of [[1,2,3],[2,1,2],[2,3,4]]
  public static ContextValue getContextFromWall(List<Stack> wall) {
    String wallString = wallToString(wall);
    LispTree tree = LispTree.proto.parseFromString(
        "(context (graph NaiveKnowledgeGraph ((string " + wallString + ") (name b) (name c))))");
    return new ContextValue(tree);
  }

  public static List<Stack> stringToWall(String wallString) {
    List<Stack> wall = new ArrayList<>();
    @SuppressWarnings("unchecked")
    List<List<Integer>> intwall = Json.readValueHard(wallString, List.class);
    //throw new RuntimeException(a.toString()+a.get(1).toString());

    boolean unmarked = true;
    for (int i = 0; i < intwall.size(); i++) {
      List<Integer> intstack = intwall.get(i);
      Stack stack = new Stack(i);
      
      for (Integer intcube : intstack) {
        if (intcube.intValue() == Color.Mark.intValue()) {
          stack.mark = true;
          unmarked = false;
        }
        else
          stack.addCube(intcube);
      }
      wall.add(stack);
    }
    if (unmarked) { // if nothing is marked, mark something random
      // wall.get(random.nextInt(wall.size())).mark = true;
    }
    return wall;
  }

  public static String wallToString(List<Stack> wall) {
    List<List<Integer>> intwall = new ArrayList<>();
    for (Stack stack : wall) {
      List<Integer> intstack = new ArrayList<>();
      for (NumberValue cube : stack.cubes) {
        intstack.add((int)cube.value);
      }
      if (stack.isMarked())
        intstack.add(Color.Mark.intValue());
      intwall.add(intstack);
    }
    return Json.writeValueAsStringHard(intwall);
  }
  
  public static String root(Function<Stack, Stack> mapfunc, Ref<ContextValue> context) {
    List<Stack> stacks = getWallFromContext(context);
    for (Stack s : stacks) {
        mapfunc.apply(s);
    }   
    return wallToString(stacks);
  }
  
  // gate an action by some condition
  public static Function<Stack, Stack> iff(Function<Stack, Boolean> filterfunc, Function<Stack, Stack> mapfunc) {
    return new Function<Stack, Stack>() {
      public Stack apply(Stack s) {
        if (filterfunc.apply(s).booleanValue())
          return mapfunc.apply(s);
        return s;
      }
    };
  }
  
  // actions and their combinations
 
  public static Function<Stack, Stack> remove() {
    return new Function<Stack, Stack>() {
      public Stack apply(Stack s) {
        if(s.isMarked()) s.remove();
        return s;
      }
    };
  }
  
  public static Function<Stack, Stack> add(NumberValue color) {
    return new Function<Stack, Stack>() {
      public Stack apply(Stack s) {
        if(s.isMarked()) s.cubes.add(color);
        return s;
      }
    };
  }
  

  public static Function<Stack, Stack> seq(Function<Stack, Stack> f,
      Function<Stack, Stack> g) {
    return Functions.compose(g, f);
  }

  public static Function<Stack, Stack> repeat(Function<Stack, NumberValue> times, 
      Function<Stack, Stack> f) {
    return new Function<Stack, Stack>() {
      public Stack apply(Stack s) {
        Stack snew = s;
        for (int i = 0; i < times.apply(s).value; i++) {
          snew = f.apply(s);
        }
        return snew;
      }
    };
  }
 
  // filters and their combinations
  
  public static Function<Stack, Boolean> top(NumberValue color) {
    return new Function<Stack, Boolean>() {
      public Boolean apply(Stack s) {
        return new Boolean(s.topColor(color));
      }
    };
  }
  
  public static Function<Stack, Boolean> has(NumberValue color) {
    return new Function<Stack, Boolean>() {
      public Boolean apply(Stack s) {
        return new Boolean(s.contains(color));
      }
    };
  }

  public static Function<Stack, Boolean> filter(Function<Stack, NumberValue> getter, String operation, NumberValue val) {
    return new Function<Stack, Boolean>() {
      public Boolean apply(Stack s) {
        double propval = getter.apply(s).value;
        
        if (operation.equals(">") && propval > val.value)
          return new Boolean(true);
        else if (operation.equals(">=") && propval >= val.value)
          return new Boolean(true);
        else if (operation.equals("=") && propval == val.value)
          return new Boolean(true);
        else if (operation.equals("<=") && propval <= val.value)
          return new Boolean(true);
        else if (operation.equals("<") && propval < val.value)
          return new Boolean(true);
        else if (operation.equals("%2=") && (int)propval % 2 == (int)val.value)
          return new Boolean(true);
        else if (operation.equals("%3=") && (int)propval % 3 == (int)val.value)
          return new Boolean(true);
        else
          return new Boolean(false);
      }
    };
  }
  
  public static Function<Stack, Boolean> compare(Function<Stack, NumberValue> g1, String comp, Function<Stack, NumberValue> g2) {
    return new Function<Stack, Boolean>() {
      public Boolean apply(Stack s) {
        double propval = g1.apply(s).value;
        NumberValue val = g2.apply(s);
        if (comp.equals(">") && propval > val.value)
          return new Boolean(true);
        else if (comp.equals(">=") && propval >= val.value)
          return new Boolean(true);
        else if (comp.equals("=") && (int)propval == (int)val.value)
          return new Boolean(true);
        else if (comp.equals("<=") && propval <= val.value)
          return new Boolean(true);
        else if (comp.equals("<") && propval < val.value)
          return new Boolean(true);
        else if (comp.equals("!=") && propval != val.value)
          return new Boolean(true);
        else
          return new Boolean(false);
      }
    };
  }
  
  
  public static Function<Stack, Boolean> not(Function<Stack, Boolean> f) {
    return new Function<Stack, Boolean>() {
      public Boolean apply(Stack s) {
        boolean val = f.apply(s);
        return new Boolean(!val);
      }
    };
  }
  
  public static Function<Stack, Boolean> all() {
    return new Function<Stack, Boolean>() {
      public Boolean apply(Stack s) {
        return true;
      }
    };
  }
  
  public static Function<Stack, Boolean> none() {
    return new Function<Stack, Boolean>() {
      public Boolean apply(Stack s) {
        return false;
      }
    };
  }


  public static Function<Stack, Boolean> logic(String op, Function<Stack, Boolean> f1, Function<Stack, Boolean> f2) {
    return new Function<Stack, Boolean>() {
      public Boolean apply(Stack s) {
        boolean opval;
        boolean val1 = f1.apply(s);
        boolean val2 = f2.apply(s);
        if ( op.equals("or") )
          opval = val1 || val2;
        else if (op.equals("and"))
          opval = val1 && val2;
        else if (op.equals("diff"))
          opval = val1 && !val2;
        else if (op.equals("not"))
          opval = !val1;
        else
          opval = val1;
        return new Boolean(opval);
      }
    };
  }
  
  public static Function<Stack, NumberValue> negative(Function<Stack, NumberValue> getf) {
    return new Function<Stack, NumberValue>() {
      public NumberValue apply(Stack s) {
        NumberValue orig = getf.apply(s);
        return new NumberValue(-orig.value, orig.unit);
      }
    };
  }
  public static Function<Stack, NumberValue> get(String property) {
    return new Function<Stack, NumberValue>() {
      public NumberValue apply(Stack s) {
        int propval;
        if (property.equals("height"))
          propval = s.getHeight();
        else if (property.equals("row"))
          propval = s.row;
        else if (property.equals("col"))
          propval = s.col;
        else
          throw new RuntimeException("property " + property + " is not supported.");
        return new NumberValue(propval, property);
      }
    };
  }
  
  public static Function<Stack, NumberValue> constant(NumberValue constant) {
    return new Function<Stack, NumberValue>() {
      public NumberValue apply(Stack s) {
        return constant;
      }
    };
  }
  
  // performs arithmetics
  public static Function<Stack, NumberValue> arith(String op, Function<Stack, NumberValue> f1, Function<Stack, NumberValue> f2) {
    return new Function<Stack, NumberValue>() {
      public NumberValue apply(Stack s) {
        double val1 = f1.apply(s).value;
        double val2 = f2.apply(s).value;
        if (op.equals("+")) {
          return new NumberValue(val1 + val2);
        } else if (op.equals("-")) {
          return new NumberValue(val1 - val2);
        } else if (op.equals("%")) {
          return new NumberValue(val1 % val2);
        }  else
          throw new RuntimeException("operator " + op + " is not supported.");
      }
    };
  }
  
  public static Function<Stack, Boolean> rect(Function<Stack, Boolean> cond, Ref<ContextValue> context) {
    List<Stack> stacks = getWallFromContext(context);
    int minr = stacks.stream().filter((Stack s) -> cond.apply(s))
        .min(Comparator.comparingInt((Stack s) -> s.row))
        .orElse(Stack.defStack()).row;
    int maxr = stacks.stream().filter((Stack s) -> cond.apply(s))
        .max(Comparator.comparingInt((Stack s) -> s.row))
        .orElse(Stack.defStack()).row;
    int minc = stacks.stream().filter((Stack s) -> cond.apply(s))
        .min(Comparator.comparingInt((Stack s) -> s.col))
        .orElse(Stack.defStack()).col;
    int maxc = stacks.stream().filter((Stack s) -> cond.apply(s))
        .max(Comparator.comparingInt((Stack s) -> s.col))
        .orElse(Stack.defStack()).col;
    
    return new Function<Stack, Boolean>() {
      public Boolean apply(Stack s) {
        return (s.col >= minc && s.col <= maxc) && (s.row >= minr && s.row <= maxr);
      }
    };
  }
  public static Function<Stack, Boolean> shift(Function<Stack, Boolean> cond, Function<Stack, NumberValue> fr, Function<Stack, NumberValue> fc, Ref<ContextValue> context) {
    int worldsize = RicherStacksWorld.opts.worldSize;
    List<Stack> stacks = getWallFromContext(context);
    boolean[][] stacksByPosition = new boolean[worldsize][worldsize];
    for (Stack s : stacks) {
      stacksByPosition[s.row-1][s.col-1] = cond.apply(s);
    }
    return new Function<Stack, Boolean>() {
      public Boolean apply(Stack s) {
        int row = s.row - (int)fr.apply(s).value;
        int col = s.col - (int)fc.apply(s).value;
        if (row > worldsize || row < 1) return false;
        if (col > worldsize || col < 1) return false;
        
        return stacksByPosition[row-1][col-1];
      }
    };
  }
 
  
  public static Function<Stack, Boolean> argmin(Function<Stack, Boolean> cond,
      Function<Stack, NumberValue> f, Ref<ContextValue> context) {
    return argmax(cond, negative(f), context);
  }
  
  public static Function<Stack, Boolean> argmax(Function<Stack, Boolean> cond,
      Function<Stack, NumberValue> f, Ref<ContextValue> context) {
    List<Stack> stacks = getWallFromContext(context);
    int maxvalue = Integer.MIN_VALUE;
    for (Stack s : stacks) {
      if (cond.apply(s)) {
        int cvalue = (int)f.apply(s).value;
        if (cvalue > maxvalue) maxvalue = cvalue;
      }
    }
    final int maxValue = maxvalue;
    return new Function<Stack, Boolean>() {
      public Boolean apply(Stack s) {
        if (cond.apply(s) && (int)f.apply(s).value == maxValue)
          return new Boolean(true);
        else return new Boolean(false);
      }
    };
  }
 
  
  public static Function<Stack, Boolean> shift(Function<Stack, NumberValue> fr, Function<Stack, NumberValue> fc, Ref<ContextValue> context) {
    return shift(marked(), fr, fc, context);
  }
  
  public static Function<Stack, Boolean> argmin(Function<Stack, NumberValue> f, Ref<ContextValue> context) {
    return argmin(marked(), f, context);
  }
  
  public static Function<Stack, Boolean> argmax(Function<Stack, NumberValue> f, Ref<ContextValue> context) {
    return argmax(marked(), f, context);
  }
  
  // performs some side effects on the context value
  public static Function<Stack, Stack> mark(Function<Stack, Boolean> cond, Ref<ContextValue> context) {
    return new Function<Stack, Stack>() {
      public Stack apply(Stack s) {
        List<Stack> stacks = getWallFromContext(context);
        for (Stack sc : stacks) {
          if (cond.apply(sc)) {
            sc.mark = true;
          }
        }
        context.value = getContextFromWall(stacks);
        s.mark = true;
        return s;
      }
    };
  }
  
  public static Function<Stack, Stack> unmark(Function<Stack, Boolean> cond, Ref<ContextValue> context) {
    return new Function<Stack, Stack>() {
      public Stack apply(Stack s) {
        List<Stack> stacks = getWallFromContext(context);
        for (Stack sc : stacks) {
          if (cond.apply(sc)) {
            sc.mark = false;
          }
        }
        context.value = getContextFromWall(stacks);
        s.mark = false;
        return s;
      }
    };
  }
  
  public static Function<Stack, Boolean> marked() {
    return new Function<Stack, Boolean>() {
      public Boolean apply(Stack s) {
        return s.isMarked();
      }
    };
  }
}

class Stack {
  public List<NumberValue> cubes;
  public int row, col;
  public boolean mark;
  
  public Stack(int pos) {
    this.row = 1 + pos % RicherStacksWorld.opts.worldSize;
    this.col = 1 + pos / RicherStacksWorld.opts.worldSize;
    this.cubes = new ArrayList<NumberValue>();
    this.mark = false;
  }
  public int getHeight() {
    return this.cubes.size();
  }
  public boolean topColor(NumberValue color) {
    if (this.cubes == null || this.cubes.size() == 0) return false;
    return this.cubes.get(this.cubes.size()-1).equals(color);
  }
  public boolean contains(NumberValue color) {
    if (this.cubes == null || this.cubes.size() == 0) return false;
    return this.cubes.contains(color);
  }
  public void remove() {
    if (this.cubes == null || this.cubes.size() == 0) return;
    this.cubes.remove(cubes.size() - 1);
  }
  public void addCube(Integer cube) {
    this.cubes.add(new NumberValue(cube.intValue(), RicherStacksWorld.COLOR));
  }
  public void addCube(Color cube) {
    this.cubes.add(new NumberValue(cube.intValue(), RicherStacksWorld.COLOR));
  }
  public void addCube(NumberValue cube) {
    this.cubes.add(cube);
  }
  public boolean isMarked() {
    return this.mark;
  }
  
  public static Stack defStack(){
    Stack s = new Stack(0);
    s.row = Integer.MIN_VALUE;
    s.col = Integer.MIN_VALUE;
    return s;
  }
}
