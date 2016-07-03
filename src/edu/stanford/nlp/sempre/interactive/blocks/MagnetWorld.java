package edu.stanford.nlp.sempre.interactive.blocks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.base.Function;

import edu.stanford.nlp.sempre.ContextValue;
import edu.stanford.nlp.sempre.Json;
import edu.stanford.nlp.sempre.NaiveKnowledgeGraph;
import edu.stanford.nlp.sempre.NumberValue;
import edu.stanford.nlp.sempre.StringValue;
import edu.stanford.nlp.util.CollectionUtils;
import fig.basic.Option;
/**
 * Functional primitives
 * @author Sida Wang
 */

enum Color {
  Red(0), Orange(1), Yellow (2), Green(3), Blue(4), Purple(5), Brown(6), Cyan(7), None(8);
  private final int value;
  private static final int MAXCOLOR = 7;
  Color(int value) { this.value = value; }
  public int toInt() { return this.value; }
  public boolean Compare(int i){return value == i;}
  public static Color fromInt(int intc) {
    for(Color c : Color.values())
    {
      if (c.value == intc) return c;
    }
    return Color.None;
  }
  public static Color fromString(String color) {
    for(Color c : Color.values())
    {
      if (c.name().equalsIgnoreCase(color)) return c;
    }
    return Color.None;
  }
}
enum Direction {
  Up, Down, Left, Right, Front, Back, None;
  public static Direction fromString(String dir) {
    if (dir.equalsIgnoreCase("up"))
      return Direction.Up;
    if (dir.equalsIgnoreCase("down"))
      return Direction.Down;
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

public final class MagnetWorld {
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
  private MagnetWorld() { }
  

  public static String root(Function<World, World> action, ContextValue context) {
    World world = World.fromContext(context);
    world = action.apply(world);
    return world.toJSON();
  }
  
  // control flow stuff
  public static Function<World, World> repeat(NumberValue times, 
      Function<World, World> action) {
    return new Function<World, World>() {
      public World apply(World w) {
        World snew = w;
        for (int i = 0; i < times.value ; i++) {
          snew = action.apply(snew);
        }
        return snew;
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
  
  // actions
  public static Function<World, World> remove(String direction) {
    return remove(direction, filter(selected()));
  }
  public static Function<World, World> remove(String direction, Function<World, Set<Cube>> cubesf) {
    return new Function<World, World>() {
      public World apply(World w) {
        Set<Cube> cubes = cubesf.apply(w);
        w.remove(cubes);
        return w;
      }
    };
  }
  
  public static Function<World, World> move(String dir, Function<World, Set<Cube>> cubesf) {
    return new Function<World, World>() {
      public World apply(World w) {
        Set<Cube> cubes = cubesf.apply(w);
        w.move(Direction.fromString(dir), cubes);
        return w;
      }
    };
  }
  
  public static Function<World, World> add(String color, String dir, Function<World, Set<Cube>> cubesf) {
    return new Function<World, World>() {
      public World apply(World w) {
        Set<Cube> cubes = cubesf.apply(w);
        w.add(color, Direction.fromString(dir), cubes);
        return w;
      }
    };
  }
  
  public static Function<World, World> addName(Function<World, Set<Cube>> cubesf, String name) {
    return new Function<World, World>() {
      public World apply(World w) {
        Set<Cube> cubes = cubesf.apply(w);
        cubes.stream().map(c -> {c.names.add(name); return c;});
        return w;
      }
    };
  }
  
  public static Function<World, World> removeName(Function<World, Set<Cube>> cubesf, String name) {
    return new Function<World, World>() {
      public World apply(World w) {
        Set<Cube> cubes = cubesf.apply(w);
        cubes.stream().map(c -> {c.names.remove(name); return c;});
        return w;
      }
    };
  }

  public static Function<World, List<Cube>> setop(String op,
      Function<World, Set<Cube>>  f1,
      Function<World, Set<Cube>>  f2) {
    return new Function<World, List<Cube>>() {
      @Override
      public List<Cube> apply(World w) {
        Set<Cube> l1 = f1.apply(w);
        Set<Cube> l2 = f2.apply(w);
        Collection<Cube> retval;
        if (op.equals("or") )
          retval = CollectionUtils.union(l1, l2);
        else if (op.equals("and"))
          retval = CollectionUtils.intersection(l1, l2);
        else if (op.equals("diff"))
          retval = CollectionUtils.diff(l1, l2);
        else
          throw new RuntimeException("logic operator not supported: " + op);
        return retval.stream().collect(Collectors.toList());
      };
    };
  }
  
  // convert simple cube filters
  public static Function<World, Set<Cube>> filter(Function<World, Set<Cube>> cubesf, Function<Cube, Boolean> simplef) {
    return new Function<World, Set<Cube>>() {
      public Set<Cube> apply(World w) {
        Set<Cube> cubes = cubesf.apply(w);
        return cubes.stream().filter( c -> simplef.apply(c) ).collect(Collectors.toSet());
      }
    };
  }
  
  public static Function<World, Set<Cube>> filter(Function<Cube, Boolean> simplef) {
    return new Function<World, Set<Cube>>() {
      public Set<Cube> apply(World w) {
        return w.worldlist.stream().filter( c -> simplef.apply(c) ).collect(Collectors.toSet());
      }
    };
  }

  
  public static Function<Cube, Boolean> colored(Color color) {
    return new Function<Cube, Boolean>() {
      public Boolean apply(Cube c) {
        return c.color == color;
      }
    };
  }
  
  public static Function<Cube, Boolean> selected() {
    return new Function<Cube, Boolean>() {
      public Boolean apply(Cube c) {
        return named("S").apply(c);
      }
    };
  }
  
  public static Function<Cube, Boolean> named(String name) {
    return new Function<Cube, Boolean>() {
      public Boolean apply(Cube c) {
        return c.names.contains(name);
      }
    };
  }
  
  public static Function<Cube, Boolean> truef() {
    return new Function<Cube, Boolean>() {
      public Boolean apply(Cube s) {
        return true;
      }
    };
  }
    

  // handles color and name
  public static Function<Cube, Boolean> equals(Function<Cube, Object> propf, String value) {
    return new Function<Cube, Boolean>() {
      @SuppressWarnings("unchecked")
      public Boolean apply(Cube c) {
        Object prop = propf.apply(c);
        if (prop instanceof Color)
          return ((Color) prop).name().equalsIgnoreCase(value);
        else if (prop instanceof Set)
          return ((Set<String>) prop).contains(value);
        else
          throw new RuntimeException("unsupported equality");
      }
    };
  }
  // numerical stuff
  public static Function<Cube, Boolean> compare(String comp, Function<Cube, NumberValue> g1, Function<Cube, NumberValue> g2) {
    return new Function<Cube, Boolean>() {
      public Boolean apply(Cube s) {
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
        Object propval;
        if (property.equals("height"))
          propval = new NumberValue(s.height, property);
        else if (property.equals("row"))
          propval = new NumberValue(s.row, property);
        else if (property.equals("col"))
          propval = new NumberValue(s.col, property);
        else if (property.equals("color"))
          propval = s.color;
        else if (property.equals("name"))
          propval = s.names;
        else
          throw new RuntimeException("property " + property + " is not supported.");
        return propval;
      }
    };
  }
  
  public static Function<Cube, NumberValue> constant(NumberValue constant) {
    return new Function<Cube, NumberValue>() {
      public NumberValue apply(Cube s) {
        return constant;
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
  
  // domain specific set to set functions, like, all stacks, inside, leftof, rightof, topmost, botmost etc.
  public static Function<World, Set<Cube>> domainf(Function<World, Set<Cube>> cubesf, String fname) {
    return new Function<World, Set<Cube>>() {
      @Override
      public Set<Cube> apply(World w) {
        return null;
      }
    };
  }
}

// the world of stacks
class World {
  public static final int worldSize = MagnetWorld.opts.worldSize;
  // alternative representation
  // row, col, basically 
  private List<Cube>[][] stacks;
  //world list is always up to date,
  // with world array used only when needed
  public List<Cube> worldlist;
  // this is probably the place to deal with disconnected stuff
  @SuppressWarnings("unchecked")
  private void updateWorldArray() {
    this.stacks = (List<Cube>[][]) new Object[worldSize][worldSize];
    for (Cube c : worldlist) {
      stacks[c.row][c.col].set(c.height, c); // basic dedup, by coverage
    }
  }
  private void updateWorldList() {
    this.worldlist = new ArrayList<>();
    for (int r = 0; r < worldSize; r++) {
      for (int c = 0; c < worldSize; c++) {
        if (stacks[r][c]!=null)
          worldlist.addAll(stacks[r][c].stream().filter(cu -> cu != null).collect(Collectors.toList()));
      }
    }
  }
  

  public World(List<Cube> worldlist) {
    this.stacks = (List<Cube>[][]) new Object[worldSize][worldSize];
    this.worldlist = worldlist;
  }
  
  public void push() {}
  public void pop() {}
  
  public void remove(Set<Cube> cubes) {
    this.worldlist.removeAll(cubes);
  }
  public void add(String color, Direction dir, Set<Cube> cubes) {
    add(Color.fromString(color), dir, cubes);
  }
  public void add(Color color, Direction dir, Set<Cube> cubes) {
    this.worldlist.addAll( cubes.stream().map(
          c -> {Cube d = c.move(dir).clone(); d.color = color; return d;}
        )
        .collect(Collectors.toList()) );
  }
  public void move(Direction dir, Set<Cube> cubes) {
    this.worldlist.stream().filter(c -> cubes.contains(c)).map( c -> c.move(dir));
  }
  public void select(Set<Cube> cubes) {
    this.worldlist.stream().map( c -> cubes.contains(c)? c.select() : c.unselect() );
  }
  public void selectadd(Set<Cube> cubes) {
    this.worldlist.stream().map( c -> cubes.contains(c)? c.select() : c );
  }
  public void unselect(Set<Cube> cubes) {
    this.worldlist.stream().map( c -> cubes.contains(c)? c.unselect() : c );
  }
  public String toJSON() {
    List<List<Integer>> intwall = new ArrayList<>();
    updateWorldArray();
    updateWorldList();
    for (Cube c: this.worldlist) {
      int pos = (c.col-1) * worldSize + (c.row - 1);
      if (intwall.get(pos) == null)
        intwall.add(pos, new ArrayList<>());
      intwall.get(pos).add(c.intColor()); // assumes that things are sorted by height
    }
    return Json.writeValueAsStringHard(intwall);
  }
  
  public static World fromContext(ContextValue context) {
    NaiveKnowledgeGraph graph = (NaiveKnowledgeGraph)context.graph;
    String wallString = ((StringValue)graph.triples.get(0).e1).value;
    return stringToWorld(wallString);
  }

  private static World stringToWorld(String wallString) {
    @SuppressWarnings("unchecked")
    List<List<Integer>> intwall = Json.readValueHard(wallString, List.class);
    //throw new RuntimeException(a.toString()+a.get(1).toString());
    List<Cube> cubes = new ArrayList<>();
    for (int i = 0; i < intwall.size(); i++) {
      List<Integer> intstack = intwall.get(i);
      for (int h = 0; h < intstack.size(); h++) {
        Cube c = new Cube(1 + i % worldSize,
            1 + i / worldSize, h, intstack.get(h));
        cubes.add(c);
      }
    }
    return new World(cubes);
  }
  
}

//individual stacks
class Cube {
  public Color color;
  public int row, col, height;
  public boolean selected;
  public Set<String> names;
  public Cube(int row, int col, int height, int color) {
    this.row = row; this.col = col; this.height = height;
    this.color = Color.fromInt(color);
    this.selected = color >= 10;
    this.names = new HashSet<>();
  }
  public int intColor() {
    return this.selected ? color.toInt() + 10 : color.toInt();
  }
  public Cube select() {
    this.selected = true;
    return this;
  }
  public Cube unselect() {
    this.selected = false;
    return this;
  }
  public Cube move(Direction dir) {
    switch (dir) {
      case Back: this.col += 1; break;
      case Front: this.col -= 1; break;
      case Left: this.row += 1; break;
      case Right: this.row -= 1; break;
      case Up: this.height = 1; break;
      case Down: this.height -= 1; break;
    }
    return this;
  }
  @Override
  public Cube clone() {
    Cube c = new Cube(this.row, this.col, this.height, this.intColor());
    return c;
  }
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + col;
    result = prime * result + ((color == null) ? 0 : color.hashCode());
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
    if (color != other.color)
      return false;
    if (height != other.height)
      return false;
    if (row != other.row)
      return false;
    return true;
  }
  
}
