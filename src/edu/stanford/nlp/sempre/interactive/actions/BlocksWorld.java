package edu.stanford.nlp.sempre.interactive.actions;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.testng.collections.Sets;

import edu.stanford.nlp.sempre.*;

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

// the world of stacks
public class BlocksWorld extends FlatWorld {

  public Map<String,Set<Block>> vars;
  // this is probably the place to deal with disconnected stuff
  private int stackLevel = 0;
  
  @SuppressWarnings("unchecked")
  public BlocksWorld(Set<Item> blockset) {
    super();
    this.allitems = blockset;
  }

  public String toJSON() {
    // updateWorldArray();
    // updateWorldList();
    // return "testtest";
    return Json.writeValueAsStringHard(this.allitems.stream().map(c -> ((Block)c).toJSON()).collect(Collectors.toList()));
    // return this.worldlist.stream().map(c -> c.toJSON()).reduce("", (o, n) -> o+","+n);
  }

  public static BlocksWorld fromContext(ContextValue context) {
    if (context == null || context.graph == null) {
      return fromJSON("[[3,3,1,\"Gray\",[\"S\"]],[4,4,1,\"Blue\",[]]]");
    }
    NaiveKnowledgeGraph graph = (NaiveKnowledgeGraph)context.graph;
    String wallString = ((StringValue)graph.triples.get(0).e1).value;
    return fromJSON(wallString);
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
  
}
