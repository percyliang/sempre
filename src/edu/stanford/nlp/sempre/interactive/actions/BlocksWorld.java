package edu.stanford.nlp.sempre.interactive.actions;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import edu.stanford.nlp.sempre.ContextValue;
import edu.stanford.nlp.sempre.Json;
import edu.stanford.nlp.sempre.NaiveKnowledgeGraph;
import edu.stanford.nlp.sempre.StringValue;
import fig.basic.LogInfo;

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
  
  @SuppressWarnings("unchecked")
  public BlocksWorld(Set<Item> blockset) {
    super();
    this.allitems = blockset;
    this.selected = blockset.stream().filter(b -> ((Block)b).names.contains("S")).collect(Collectors.toSet());
  }

  public String toJSON() {
    // return "testtest";
    this.selected().forEach(b -> ((Block)b).names.add("S"));
    return Json.writeValueAsStringHard(this.allitems.stream().map(c -> ((Block)c).toJSON()).collect(Collectors.toList()));
    // return this.worldlist.stream().map(c -> c.toJSON()).reduce("", (o, n) -> o+","+n);
  }

  private static BlocksWorld fromJSON(String wallString) {
    @SuppressWarnings("unchecked")
    List<List<Object>> cubestr = Json.readValueHard(wallString, List.class);
    Set<Item> cubes = cubestr.stream().map(c -> {return Block.fromJSONObject(c);})
        .collect(Collectors.toSet());
    //throw new RuntimeException(a.toString()+a.get(1).toString());
    return new BlocksWorld(cubes);
  }

  @Override
  public Set<Item> has(String rel, Set<Object> values) {
    LogInfo.log(values);
    return this.allitems.stream().filter(i -> values.contains(i.get(rel)))
        .collect(Collectors.toSet());
  }

  @Override
  public Set<Object> get(String rel, Set<Item> subset) {
    return subset.stream().map(i -> i.get(rel))
        .collect(Collectors.toSet());
  }

  @Override
  public void update(String rel, Object value) {
    this.selected.forEach(i -> i.update(rel, value));
  }
  
  // block world specific actions
  public void move(String dir) {
    this.selected.forEach(b -> ((Block)b).move(Direction.fromString(dir)));
  }

  public void add(String color, String dir) {
    Set<Item> extremeCubes = extremeCubes(dir);
    this.allitems.addAll( extremeCubes.stream().map(
        c -> {Block d = ((Block)c).copy(Direction.fromString(dir)); d.color = CubeColor.fromString(color); return d;}
        )
        .collect(Collectors.toList()) );
  }
  // get cubes at extreme positions
  private Set<Item> extremeCubes(String dirstr) {
    Direction dir = Direction.fromString(dirstr);
    return selected.stream().map(c -> {
      Block d = (Block)c;
      while(allitems.contains(d.copy(dir)))
        d = d.copy(dir);
      return d;
    }).collect(Collectors.toSet());
  }

  //get cubes at extreme positions
  public Set<Item> veryx(String dirstr, Set<Item> items) {
    Direction dir = Direction.fromString(dirstr);
    switch (dir) {
    case Back: return argmax(items, c -> c.row);
    case Front: return argmax(items, c -> -c.row);
    case Left: return argmax(items, c -> c.col);
    case Right: return argmax(items, c -> -c.col);
    case Top: return argmax(items, c -> c.height);
    case Bot: return argmax(items, c -> -c.height);
    default: throw new RuntimeException("invalid direction");
    }
  }
  
  public static Set<Item> argmax(Set<Item> items, Function<Block, Integer> f) {
    int maxvalue = Integer.MIN_VALUE;
    for (Item i : items) {
      int cvalue = f.apply((Block)i);
      if (cvalue > maxvalue) maxvalue = cvalue;
    }
    final int maxValue = maxvalue;
    return items.stream().filter(c -> f.apply((Block)c) >= maxValue).collect(Collectors.toSet());
  }
}
