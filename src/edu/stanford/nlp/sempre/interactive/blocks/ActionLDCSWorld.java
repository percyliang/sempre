package edu.stanford.nlp.sempre.interactive.blocks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.function.*;

import org.testng.collections.Lists;

import com.google.common.collect.Sets;

import edu.stanford.nlp.sempre.ContextValue;
import edu.stanford.nlp.sempre.Json;
import edu.stanford.nlp.sempre.NaiveKnowledgeGraph;
import edu.stanford.nlp.sempre.NumberValue;
import edu.stanford.nlp.sempre.StringValue;
import edu.stanford.nlp.sempre.Value;
import edu.stanford.nlp.sempre.interactive.blocks.StacksWorld.Color;
import edu.stanford.nlp.util.CollectionUtils;
import fig.basic.Option;
/**
 * Functional primitives
 * @author Sida Wang
 */

enum CubeColor {
  Red(0), Orange(1), Yellow (2), Green(3), Blue(4), White(6), Black(7), Pink(8), Brown(9), Gray(10), None(-5);
  private final int value;
  private static final int MAXCOLOR = 7;
  CubeColor(int value) { this.value = value; }
  public int toInt() { return this.value; }
  public boolean Compare(int i){return value == i;}
  public static CubeColor fromInt(int intc) {
    for(CubeColor c : CubeColor.values())
    {
      if (intc < 0) return CubeColor.None;
      if (c.value == intc % (CubeColor.values().length-1)) return c;
    }
    return CubeColor.None;
  }
  public static CubeColor fromString(String color) {
    for(CubeColor c : CubeColor.values())
    {
      if (c.name().equalsIgnoreCase(color)) return c;
    }
    return CubeColor.None;
  }
}
enum Direction {
  Top, Bot, Left, Right, Front, Back, None;
  public static Direction fromString(String dir) {
    if (dir.equalsIgnoreCase("up") || dir.equalsIgnoreCase("top"))
      return Direction.Top;
    if (dir.equalsIgnoreCase("down") || dir.equalsIgnoreCase("bot"))
      return Direction.Bot;
    if (dir.equalsIgnoreCase("left"))
      return Direction.Left;
    if (dir.equalsIgnoreCase("right"))
      return Direction.Right;
    if (dir.equalsIgnoreCase("front"))
      return Direction.Front;
    if (dir.equalsIgnoreCase("back"))
      return Direction.Back;
    return Direction.None;
  }
}

public final class ActionLDCSWorld {
  public static class Options {
    @Option(gloss = "Verbosity")
    public int verbose = 0;
    @Option(gloss = "How many times to repeat")
    public int iterLimit = 20;
    @Option(gloss = "number of colums, so worldSize/numCols is the number of sheets")
    public int worldSize  = 16;
  }
  public static Options opts = new Options();
  public static final String COLOR = "COLOR";

  private static final Random random = new Random(1);
  private ActionLDCSWorld() { }

  public static String root(Function<World, World> action, ContextValue context) {
    World world = World.fromContext(context);
    world.worldList = world.worldList.stream().map(c -> {c.age++; return c; }).collect(Collectors.toList());
    world = action.apply(world);
    world.applyPhysics();
    return world.toJSON();
  }

  public static String reset(String name) {
    World world = new World(Lists.newArrayList());
    int randint = random.nextInt(5);
    if (name.equals("simple")) {
      world.worldList.add(new Cube(1,1,0,CubeColor.Red.toString()));
      world.worldList.add(new Cube(2,2,1,CubeColor.Orange.toString()));
      world.worldList.add(new Cube(2,2,0,CubeColor.Orange.toString()));
      world.worldList.add(new Cube(3,3,0,CubeColor.Yellow.toString()));
      world.worldList.add(new Cube(1,3,0,CubeColor.Green.toString()));
      world.worldList.add(new Cube(3,1,0,CubeColor.Blue.toString()));
      return world.toJSON();
    }
    if (name.equals("checker")) {
      for (int i = 3 ; i < 8; i++)
        for (int j = 3 ; j < 8; j++)
          if ((i + j) % 2 == 0)
            world.worldList.add(new Cube(i,j,0, CubeColor.fromInt(randint).toString()));
      return world.toJSON();
    }
    if (name.equals("stick")) {
      for (int i = 0 ; i < 5; i++)
        world.worldList.add(new Cube(4,4,i,CubeColor.fromInt(randint).toString()));
      return world.toJSON();
    }

    if (name.equals("corner")) {
      world.worldList.add(new Cube(3,3,0,CubeColor.fromInt(randint).toString()));
      world.worldList.add(new Cube(7,7,0,CubeColor.fromInt(randint).toString()));
      world.worldList.add(new Cube(7,3,0,CubeColor.fromInt(randint).toString()));
      world.worldList.add(new Cube(3,7,0,CubeColor.fromInt(randint).toString()));
      return world.toJSON();
    }

    world = base(new NumberValue(4), new NumberValue(4)).apply(world);
    return world.toJSON();
  }

  public static Function<World, World> base(NumberValue x, NumberValue y) {
    return w -> {
      Cube basecube = new Cube((int)x.value, (int)y.value, 0, CubeColor.Gray.toString());
      basecube.names.add("base");
      basecube.names.add("S");
      w.worldList.add(basecube);
      return w;
    };
  }

  // Control flow
  public static Function<World, World> repeat(NumberValue times, 
      Function<World, World> action) {
    return w -> {
      for (int i = 0; i < times.value ; i++) {
        w = action.apply(w);
      }
      return w;
    };
  }
  public static Function<World, World> seq(Function<World, World> s1, 
      Function<World, World> s2) {
    return new Function<World, World>() {
      public World apply(World w) {
        return s2.apply(s1.apply(w));
      }
    };
  }
  public static Function<World, World> scope(Function<World, World> action) {
    return new Function<World, World>() {
      public World apply(World w) {
        w.push();
        w = action.apply(w);
        w.pop();
        return w;
      }
    };
  }
  public static Function<World, World> physics() {
    return new Function<World, World>() {
      public World apply(World w) {
        return w;
      }
    };
  }

  public static Function<World, World> remove(Function<World, Set<Cube>> cubesf) {    
    return new Function<World, World>() {
      public World apply(World w) {
        Set<Cube> cubes = cubesf.apply(w);
        w.worldList.removeAll(cubes);
        return w;
      }
    };
  }

  public static Function<World, World> move(String dir, Function<World, Set<Cube>> cubesf) {
    return new Function<World, World>() {
      public World apply(World w) {
        Set<Cube> cubes = cubesf.apply(w);
        for (Cube c : w.worldList) {
          if (cubes.contains(c)) c.move(Direction.fromString(dir));
        }
        return w;
      }
    };
  }

  public static Function<World, World> add(String color, String dir, Function<World, Set<Cube>> cubesf) {
    return new Function<World, World>() {
      public World apply(World w) {
        Set<Cube> cubes = addHelper(dir, cubesf).apply(w);
        w.worldList.addAll( cubes.stream().map(
            c -> {Cube d = c.copy(Direction.fromString(dir)); d.color = CubeColor.fromString(color); return d;}
            )
            .collect(Collectors.toList()) );
        return w;
      }
    };
  }

  // set some value
  public static Function<World, World> change(String rel, Function<World, Set<Object>> valuesf, Function<World, Set<Cube>> cubesf) {
    return w -> {

      Set<Object> val = valuesf.apply(w);
      Set<Cube> cubes = cubesf.apply(w);

      // we just take the first value
      cubes.forEach(c -> c.set(rel, val.iterator().next()));
      return w;
    };
  }
  // X = cubes [];
  public static Function<World, World> mark(Function<World, Set<Cube>> cubesf) {
    return name(cubesf, "S");
  }
  public static Function<World, Set<Cube>> marked() {
    return named("S");
  }
  public static Function<World, Set<Cube>> base() {
    return named("base");
  }

  public static Function<World, World> name(Function<World, Set<Cube>> cubesf, String name) {
    return new Function<World, World>() {
      public World apply(World w) {
        String namestack = w.stackName(name);
        Set<Cube> cubes = cubesf.apply(w);

        for (Cube c : w.worldList) {
          if (cubes.contains(c)) c.names.add(namestack);
          else c.names.remove(namestack);
        }
        return w;
      }
    };
  }

  public static Function<World, Set<Cube>> setops(String op,
      Function<World, Set<Cube>>  f1,
      Function<World, Set<Cube>>  f2) {
    return setop(op, f1, f2);
  }
  public static Function<World, Set<Value>> setopv(String op,
      Function<World, Set<Value>>  f1,
      Function<World, Set<Value>>  f2) {
    return setop(op, f1, f2);
  }

  // Set operations, union, intersection, diff
  public static <T> Function<World, Set<T>> setop(String op,
      Function<World, Set<T>>  f1,
      Function<World, Set<T>>  f2) {
    return new Function<World, Set<T>>() {
      @Override
      public Set<T> apply(World w) {
        Set<T> l1 = f1.apply(w);
        Set<T> l2 = f2.apply(w);
        Collection<T> retval;
        if (op.equals("or") )
          retval = CollectionUtils.union(l1, l2);
        else if (op.equals("and"))
          retval = CollectionUtils.intersection(l1, l2);
        else if (op.equals("sub") || op.equals("diff") || op.equals("except"))
          retval = CollectionUtils.diff(l1, l2);
        else
          throw new RuntimeException("unsupported set operation: " + op);
        return retval.stream().collect(Collectors.toSet());
      };
    };
  }


  // handles color and name
  public static Function<Cube, Boolean> equals(Function<Cube, Object> propf, String value) {
    return new Function<Cube, Boolean>() {
      @SuppressWarnings("unchecked")
      public Boolean apply(Cube c) {
        Object prop = propf.apply(c);
        if (prop instanceof CubeColor)
          return ((CubeColor) prop).name().equalsIgnoreCase(value);
        else if (prop instanceof Set)
          return ((Set<String>) prop).contains(value);
        else
          throw new RuntimeException("unsupported equality");
      }
    };
  }

  public static Function<Cube, NumberValue> negative(Function<Cube, NumberValue> getf) {
    return new Function<Cube, NumberValue>() {
      public NumberValue apply(Cube s) {
        NumberValue orig = getf.apply(s);
        return new NumberValue(-orig.value, orig.unit);
      }
    };
  }

  public static Function<Cube, Object> get(String property) {
    return new Function<Cube, Object>() {
      public Object apply(Cube s) {
        return s.get(property);
      }
    };
  }

  // performs arithmetics
  public static Function<Cube, NumberValue> arith(String op, Function<Cube, NumberValue> f1, Function<Cube, NumberValue> f2) {
    return new Function<Cube, NumberValue>() {
      public NumberValue apply(Cube s) {
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

  public static Function<World, Set<Cube>> argmin(Function<World, Set<Cube>> cubesf,
      Function<Cube, NumberValue> f) {
    return argmax(cubesf, negative(f));
  }

  public static Function<World, Set<Cube>> argmax(Function<World, Set<Cube>> cubesf, Function<Cube, NumberValue> f) {
    return new Function<World, Set<Cube>>() {
      @Override
      public Set<Cube> apply(World w) {
        int maxvalue = Integer.MIN_VALUE;
        Set<Cube> cubes = cubesf.apply(w);
        for (Cube c : cubes) {
          int cvalue = (int)f.apply(c).value;
          if (cvalue > maxvalue) maxvalue = cvalue;
        }
        final int maxValue = maxvalue;
        return cubes.stream().filter(c -> f.apply(c).value >= maxValue).collect(Collectors.toSet());
      }
    };
  }

  public static Function<World, Set<Cube>> named(String name) {
    return new Function<World, Set<Cube>>() {
      @Override
      public Set<Cube> apply(World w) {
        return w.worldList.stream().filter(c -> c.names.contains(w.stackName(name))).collect(Collectors.toSet());
      }
    };
  }

  // get neighbors left of, right of, etc.
  public static Function<World, Set<Cube>> ofs(String dirstr, Function<World, Set<Cube>> cubesf) {
    return w -> {
      Direction dir = Direction.fromString(dirstr);
      Set<Cube> cubes = cubesf.apply(w);
      Set<Cube> allcubes = w.worldList.stream().collect(Collectors.toSet());
      return cubes.stream().map(c -> c.copy(dir)).filter(c -> allcubes.contains(c))
          .collect(Collectors.toSet());
    };
  }

  // get some other value
  public static Function<World, Set<Object>> ofv(String rel, Function<World, Set<Cube>> cubesf) {
    return w -> {
      Set<Cube> cubes = cubesf.apply(w);
      return cubes.stream().map(c -> c.get(rel))
          .collect(Collectors.toSet());
    };
  }

  public static Function<World, Set<Cube>> has(String rel, Function<World, Set<Object>> valuef) {
    return w -> {
      Set<Object> values = valuef.apply(w);
      Set<Cube> allcubes = w.worldList.stream().collect(Collectors.toSet());
      return allcubes.stream().filter(c -> values.contains(c.get(rel)))
          .collect(Collectors.toSet());
    };
  }

  public static Function<World, Set<Object>> val(String oneValue) {
    CubeColor color = CubeColor.fromString(oneValue);
    if (color != CubeColor.None) return single(color);
    else throw new RuntimeException("not a color: " + oneValue);
  }

  public static Function<World, Set<Object>> single(Object oneValue) {
    return w -> Sets.newHashSet(oneValue);
  }

  // get cubes at extreme positions
  public static Function<World, Set<Cube>> addHelper(String dirstr, Function<World, Set<Cube>> cubesf) {
    return w -> {
      Direction dir = Direction.fromString(dirstr);
      Set<Cube> cubes = cubesf.apply(w);
      Set<Cube> allcubes = w.worldList.stream().collect(Collectors.toSet());
      return cubes.stream().map(c -> {
        while(allcubes.contains(c.copy(dir)))
          c = c.copy(dir);
        return c;
      }).collect(Collectors.toSet());
    };
  }

  //get cubes at extreme positions
  public static Function<World, Set<Cube>> veryx(String dirstr, Function<World, Set<Cube>> cubesf) {
    Direction dir = Direction.fromString(dirstr);
    switch (dir) {
    case Back: return argmax(cubesf, c -> new NumberValue(c.row));
    case Front: return argmax(cubesf, c -> new NumberValue(-c.row));
    case Left: return argmax(cubesf, c -> new NumberValue(c.col));
    case Right: return argmax(cubesf, c -> new NumberValue(-c.col));
    case Top: return argmax(cubesf, c -> new NumberValue(c.height));
    case Bot: return argmax(cubesf, c -> new NumberValue(-c.height));
    default: throw new RuntimeException("invalid direction");
    }
  }

  public static Function<World, Set<Cube>> sets(String fname) {
    if (fname.equals("all"))
      return (w -> w.worldList.stream().collect(Collectors.toSet()));
    if (fname.equals("none"))
      return (w -> new HashSet<Cube>());
    return w -> (w.worldList.stream().collect(Collectors.toSet()));
  }

  public static Function<World, Object> lift(Object constant) {
    return w -> constant;
  }
  public static Function<World, NumberValue> lift(NumberValue constant) {
    return w -> constant;
  }
  public static Function<World, Set<Object>> lift(Set<Object> constant) {
    return w -> constant;
  }
}

// the world of stacks
class World {
  public static final int worldSize = ActionLDCSWorld.opts.worldSize;

  // two representations: set for dedup, list potentially for copying
  public Set<Cube> worldSet;
  public List<Cube> worldList;

  public Map<String,Set<Cube>> vars;
  // this is probably the place to deal with disconnected stuff
  private int stackLevel = 0;

  @SuppressWarnings("unchecked")
  private void updateWorldMap() {
    worldSet = worldList.stream().collect(Collectors.toSet());
  }

  private void updateWorldList() {
    worldList = worldSet.stream().collect(Collectors.toList());
  }

  public void applyPhysics() {
    updateWorldMap();
    updateWorldList();
  }

  @SuppressWarnings("unchecked")
  public World(List<Cube> worldlist) {
    this.worldList = worldlist;
    updateWorldMap();
  }

  public void push() {stackLevel++;}
  public void pop() {stackLevel--;}
  // local variables starts with _
  public String stackName(String name) {
    //if (name.contains("[],"))
    //  throw new RuntimeException("the name should not contain JSON characters");
    if (name.startsWith("_"))
      return stackLevel + name;
    else return name;
  }

  public String toJSON() {
    // updateWorldArray();
    // updateWorldList();
    // return "testtest";
    return Json.writeValueAsStringHard(this.worldList.stream().map(c -> c.toJSON()).collect(Collectors.toList()));
    // return this.worldlist.stream().map(c -> c.toJSON()).reduce("", (o, n) -> o+","+n);
  }

  public static World fromContext(ContextValue context) {
    NaiveKnowledgeGraph graph = (NaiveKnowledgeGraph)context.graph;
    String wallString = ((StringValue)graph.triples.get(0).e1).value;
    return fromJSON(wallString);
  }

  private static World fromJSON(String wallString) {
    @SuppressWarnings("unchecked")
    List<List<Object>> cubestr = Json.readValueHard(wallString, List.class);
    List<Cube> cubes = cubestr.stream().map(c -> {return Cube.fromJSONObject(c);})
        .collect(Collectors.toList());
    //throw new RuntimeException(a.toString()+a.get(1).toString());
    return new World(cubes);
  }
}

//individual stacks
class Cube {
  public CubeColor color;
  int row, col, height;
  int age;
  public Set<String> names;

  public Cube(int row, int col, int height, String color) {
    this(row, col, height);
    this.color = CubeColor.fromString(color);
  }
  public Cube() {
    this.row = Integer.MAX_VALUE; this.col = Integer.MAX_VALUE; this.height = Integer.MAX_VALUE;
    this.color = CubeColor.fromString("None");
    this.names = new HashSet<>();
    this.age = 0;
  }
  // used as a key
  public Cube(int row, int col, int height) {
    this();
    this.row = row; this.col = col; this.height = height;
  }

  public Cube move(Direction dir) {
    switch (dir) {
    case Back: this.row +=1; break;
    case Front: this.row -= 1; break;
    case Left: this.col += 1; break;
    case Right: this.col -= 1; break;
    case Top: this.height += 1; break;
    case Bot: this.height -= 1; break;
    case None: break;
    }
    return this;
  }
  public Cube copy(Direction dir) {
    Cube c = this.clone();
    switch (dir) {
    case Back: c.row += 1; break;
    case Front: c.row -= 1; break;
    case Left: c.col += 1; break;
    case Right: c.col -= 1; break;
    case Top: c.height += 1; break;
    case Bot: c.height -= 1; break;
    case None: break;
    }
    return c;
  }
  public Object get(String property) {
    Object propval;
    if (property.equals("height"))
      propval = new NumberValue(this.height);
    else if (property.equals("row"))
      propval = new NumberValue(this.row);
    else if (property.equals("col"))
      propval = new NumberValue(this.col);
    else if (property.equals("age"))
      propval = new NumberValue(this.age);
    else if (property.equals("color"))
      propval = this.color;
    else if (property.equals("name"))
      propval = this.names;
    else
      throw new RuntimeException("getting property " + property + " is not supported.");
    return propval;
  }

  public void set(String property, Object value) {
    if (property.equals("height") && value instanceof NumberValue)
      this.height = (int)((NumberValue)value).value;
    else if (property.equals("row") && value instanceof NumberValue)
      this.row = (int)((NumberValue)value).value;
    else if (property.equals("col") && value instanceof NumberValue)
      this.height = (int)((NumberValue)value).value;
    else if (property.equals("color") && value instanceof String)
      this.color = CubeColor.fromString(value.toString());
    else
      throw new RuntimeException("setting property " + property + " is not supported.");
  }

  @SuppressWarnings("unchecked")
  public static Cube fromJSON(String json) {
    List<Object> props = Json.readValueHard(json, List.class);
    return fromJSONObject(props);
  }
  @SuppressWarnings("unchecked")
  public static Cube fromJSONObject(List<Object> props) {
    Cube retcube = new Cube();
    retcube.row = ((Integer)props.get(0));
    retcube.col = ((Integer)props.get(1));
    retcube.height = ((Integer)props.get(2));
    retcube.color = CubeColor.fromString(((String)props.get(3)));

    retcube.names.addAll((List<String>)props.get(4));
    return retcube;
  }
  public Object toJSON() {
    List<String> globalNames = names.stream().collect(Collectors.toList());
    List<Object> cube = Lists.newArrayList(row, col, height, color.toString(), globalNames);
    return cube;
  }

  @Override
  public Cube clone() {
    Cube c = new Cube(this.row, this.col, this.height, this.color.toString());
    return c;
  }
  @Override
  public int hashCode() {
    final int prime = 53;
    int result = 1;
    result = prime * result + col;
    result = prime * result + height;
    result = prime * result + row;
    return result;
  }
  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Cube other = (Cube) obj;
    if (col != other.col)
      return false;
    if (height != other.height)
      return false;
    if (row != other.row)
      return false;
    return true;
  }

}
