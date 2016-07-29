package edu.stanford.nlp.sempre.interactive.actions;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import edu.stanford.nlp.sempre.ContextValue;
import edu.stanford.nlp.sempre.Json;
import edu.stanford.nlp.sempre.NaiveKnowledgeGraph;
import edu.stanford.nlp.sempre.StringValue;
import fig.basic.LogInfo;

enum CubeColor {
  Red(0), Orange(1), Yellow (2), Green(3), Blue(4), White(6), Black(7),
  Pink(8), Brown(9), Gray(10), Anchor(11), None(-5);
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
      if (c.name().equalsIgnoreCase(color)) return c;
    return CubeColor.None;
  }
}
enum Direction {
  Top, Bot, Left, Right, Front, Back, None;
  public static Direction fromString(String dir) {
    dir = dir.toLowerCase();
    if (dir.equals("up") || dir.equals("top"))
      return Direction.Top;
    if (dir.equals("down") || dir.equals("bot"))
      return Direction.Bot;
    if (dir.equals("left"))
      return Direction.Left;
    if (dir.equals("right"))
      return Direction.Right;
    if (dir.equals("front"))
      return Direction.Front;
    if (dir.equals("back"))
      return Direction.Back;
    return Direction.None;
  }
}

// the world of stacks
public class BlocksWorld extends FlatWorld {
  public Map<String,Set<Block>> vars;
  
  public static BlocksWorld fromContext(ContextValue context) {
    if (context == null || context.graph == null) {
      return fromJSON("[[3,3,1,\"Gray\",[\"S\"]],[4,4,1,\"Blue\",[]]]");
    }
    NaiveKnowledgeGraph graph = (NaiveKnowledgeGraph)context.graph;
    String wallString = ((StringValue)graph.triples.get(0).e1).value;
    return fromJSON(wallString);
  }
  
  public void reset(String name) {
    this.allitems.clear();
    this.selected.clear();
    final Random random = new Random(1);
    int randint = random.nextInt(5);
    if (name.equals("simple")) {
      this.allitems.add(new Block(1,1,0,CubeColor.Red.toString()));
      this.allitems.add(new Block(2,2,1,CubeColor.Orange.toString()));
      this.allitems.add(new Block(2,2,0,CubeColor.Orange.toString()));
      this.allitems.add(new Block(3,3,0,CubeColor.Yellow.toString()));
      this.allitems.add(new Block(1,3,0,CubeColor.Green.toString()));
      this.allitems.add(new Block(3,1,0,CubeColor.Blue.toString()));
    }
    if (name.equals("checker")) {
      for (int i = 3 ; i < 8; i++)
        for (int j = 3 ; j < 8; j++)
          if ((i + j) % 2 == 0)
            this.allitems.add(new Block(i,j,0, CubeColor.fromInt(randint).toString()));
    }
    if (name.equals("stick")) {
      for (int i = 0 ; i < 5; i++)
        this.allitems.add(new Block(4,4,i,CubeColor.fromInt(randint).toString()));
    }
    if (name.equals("corner")) {
      this.allitems.add(new Block(3,3,0,CubeColor.fromInt(randint).toString()));
      this.allitems.add(new Block(7,7,0,CubeColor.fromInt(randint).toString()));
      this.allitems.add(new Block(7,3,0,CubeColor.fromInt(randint).toString()));
      this.allitems.add(new Block(3,7,0,CubeColor.fromInt(randint).toString()));
    }
  }

  public void base(int x, int y) {
    Block basecube = new Block(x, y, 0, CubeColor.Anchor.toString());
    this.allitems.add(basecube);
    this.selected.add(basecube);
  }
  
  @SuppressWarnings("unchecked")
  public BlocksWorld(Set<Item> blockset) {
    super();
    this.allitems = blockset;
    this.selected = blockset.stream().filter(b -> ((Block)b).names.contains("S")).collect(Collectors.toSet());
    this.allitems.forEach(b -> ((Block)b).names.clear());
  }

  public String toJSON() {
    // return "testtest";
    this.allitems.forEach(b -> ((Block)b).names.remove("S"));
    this.selected.forEach(b -> ((Block)b).names.add("S"));
    return Json.writeValueAsStringHard(this.allitems.stream().map(c -> ((Block)c).toJSON()).collect(Collectors.toList()));
    // return this.worldlist.stream().map(c -> c.toJSON()).reduce("", (o, n) -> o+","+n);
  }

  private static BlocksWorld fromJSON(String wallString) {
    @SuppressWarnings("unchecked")
    List<List<Object>> cubestr = Json.readValueHard(wallString, List.class);
    Set<Item> cubes = cubestr.stream().map(c -> {return Block.fromJSONObject(c);})
        .collect(Collectors.toSet());
    //throw new RuntimeException(a.toString()+a.get(1).toString());
    BlocksWorld world = new BlocksWorld(cubes);
    // world.selected.addAll(cubes.stream().filter(b -> ((Block)b).names.contains("S")).collect(Collectors.toSet()));
    return world;
  }

  @Override
  public Set<Item> has(String rel, Set<Object> values) {
    // LogInfo.log(values);
    return this.allitems.stream().filter(i -> values.contains(i.get(rel)))
        .collect(Collectors.toSet());
  }

  @Override
  public Set<Object> get(String rel, Set<Item> subset) {
    return subset.stream().map(i -> i.get(rel))
        .collect(Collectors.toSet());
  }

  @Override
  public void update(String rel, Object value, Set<Item> selected) {
    selected.forEach(i -> i.update(rel, value));
  }
  
  // block world specific actions
  public void move(String dir, Set<Item> selected) {
    selected.forEach(b -> ((Block)b).move(Direction.fromString(dir)));
  }

  public void add(String color, String dir, Set<Item> selected) {
    Set<Item> extremeCubes = extremeCubes(dir, selected);
    this.allitems.addAll( extremeCubes.stream().map(
        c -> {
          Block d = ((Block)c).copy(Direction.fromString(dir));
          
          // a bit of a hack to deal with special anchor points, where adding to its top behaves differently
          if (d.color == CubeColor.Anchor && d.height == 1)
            d.height = d.height - 1;

          d.color = CubeColor.fromString(color);
          return d;}
        )
        .collect(Collectors.toList()) );
  }
  // get cubes at extreme positions
  private Set<Item> extremeCubes(String dirstr, Set<Item> selected) {
    Direction dir = Direction.fromString(dirstr);
    return selected.stream().map(c -> {
      Block d = (Block)c;
      while(allitems.contains(d.copy(dir)))
        d = d.copy(dir);
      return d;
    }).collect(Collectors.toSet());
  }

  //get cubes at extreme positions
  public Set<Item> veryx(String dirstr, Set<Item> selected) {
    Direction dir = Direction.fromString(dirstr);
    switch (dir) {
    case Back: return argmax(c -> c.row, selected);
    case Front: return argmax(c -> -c.row, selected);
    case Left: return argmax(c -> c.col, selected);
    case Right: return argmax(c -> -c.col, selected);
    case Top: return argmax(c -> c.height, selected);
    case Bot: return argmax(c -> -c.height, selected);
    default: throw new RuntimeException("invalid direction");
    }
  }
  public Set<Item> adj(String dirstr, Set<Item> selected) {
    Direction dir = Direction.fromString(dirstr);
    return selected.stream().map(c -> ((Block)c).copy(dir)).filter(c -> allitems.contains(c))
        .collect(Collectors.toSet());
  }
  
  public static Set<Item> argmax(Function<Block, Integer> f, Set<Item> items) {
    int maxvalue = Integer.MIN_VALUE;
    for (Item i : items) {
      int cvalue = f.apply((Block)i);
      if (cvalue > maxvalue) maxvalue = cvalue;
    }
    final int maxValue = maxvalue;
    return items.stream().filter(c -> f.apply((Block)c) >= maxValue).collect(Collectors.toSet());
  }
}
