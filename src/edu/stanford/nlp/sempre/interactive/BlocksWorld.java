package edu.stanford.nlp.sempre.interactive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;

import edu.stanford.nlp.sempre.ContextValue;
import edu.stanford.nlp.sempre.Json;
import edu.stanford.nlp.sempre.NaiveKnowledgeGraph;
import edu.stanford.nlp.sempre.StringValue;
import fig.basic.Option;

enum CubeColor {
  Red(0), Orange(1), Yellow (2), Green(3), Blue(4), White(6), Black(7),
  Pink(8), Brown(9), Gray(10), Fake(11), None(-5);
  private final int value;
  private static final int MAXCOLOR = 7;
  CubeColor(int value) { this.value = value; }
  public int toInt() { return this.value; }
  public boolean Compare(int i){return value == i;}

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
  public static class Options {
    @Option(gloss = "maximum number of cubes to convert")
    public int maxBlocks = 1024^2;
  }
  public static Options opts = new Options();
  
  public final static String SELECT = "S";

  public static BlocksWorld fromContext(ContextValue context) {
    if (context == null || context.graph == null) {
      return fromJSON("[[3,3,1,\"Gray\",[\"S\"]],[4,4,1,\"Blue\",[]]]");
    }
    NaiveKnowledgeGraph graph = (NaiveKnowledgeGraph)context.graph;
    String wallString = ((StringValue)graph.triples.get(0).e1).value;
    return fromJSON(wallString);
  }

  public void base(int x, int y) {
    Block basecube = new Block(x, y, 0, CubeColor.Fake.toString());
    this.allitems = new HashSet<>(this.allitems);
    this.selected = new HashSet<>(this.selected);
    allitems.add(basecube);
    selected.add(basecube);
  }
  
  public Set<Item> origin() {
    for (Item i : allitems) {
      Block b = (Block)i;
      if (b.col==0 && b.row==0 & b.height==0)
        return Sets.newHashSet(b);
    }
    Block basecube = new Block(0, 0, 0, CubeColor.Fake.toString());
    return Sets.newHashSet(basecube);
  }
 
  @SuppressWarnings("unchecked")
  public BlocksWorld(Set<Item> blockset) {
    super();
    this.allitems = blockset;
    this.selected = blockset.stream().filter(b -> ((Block)b).names.contains(SELECT)).collect(Collectors.toSet());
    this.selected.forEach(i -> i.names.remove(SELECT));
  }

  // we only use names S to communicate with the client, internally its just the select variable
  public String toJSON() {
    // selected thats no longer in the world gets nothing
    
    
    if (allitems.size() > opts.maxBlocks)
      throw new RuntimeException("Number of blocks exceeds the upperlimit: " + opts.maxBlocks);
    
    //allitems.removeIf(c -> ((Block)c).color == CubeColor.Fake && !this.selected.contains(c));
    //allitems.stream().filter(c -> selected.contains(c)).forEach(i -> i.names.add(SELECT));
    
    
    return Json.writeValueAsStringHard(allitems.stream()
        .map(c -> {
          Block b = ((Block)c).clone();
          if (selected.contains(b))
            b.names.add("S");
          return b.toJSON();
        }).collect(Collectors.toList()));
    // return this.worldlist.stream().map(c -> c.toJSON()).reduce("", (o, n) -> o+","+n);
  }

  private static BlocksWorld fromJSON(String wallString) {
    @SuppressWarnings("unchecked")
    List<List<Object>> cubestr = Json.readValueHard(wallString, List.class);
    Set<Item> cubes = cubestr.stream().map(c -> {return Block.fromJSONObject(c);})
        .collect(Collectors.toSet());
    // throw new RuntimeException(a.toString()+a.get(1).toString());
    BlocksWorld world = new BlocksWorld(cubes);
    // world.previous.addAll(world.selected);
    // we can only use previous within a block;
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
    keyConsistency();
  }
  
  // if selected is no longer in all, make it fake colored, and add to all;
  // likewise, if some fake colored block is no longer selected, remove it
  @Override
  public void merge() {
    Sets.difference(selected, allitems).forEach(i -> ((Block)i).color = CubeColor.Fake);
    allitems.removeIf(c -> ((Block)c).color == CubeColor.Fake && !this.selected.contains(c));
    allitems.addAll(selected);
    // keyConsistency();
  }

  // block world specific actions, overriding move
  public void move(String dir, Set<Item> selected) {
    // allitems.removeAll(selected);
    selected.forEach(b -> ((Block)b).move(Direction.fromString(dir)));
    keyConsistency();
    // allitems.addAll(selected); // this is not overriding
  }
  
  public void add(String colorstr, String dirstr, Set<Item> selected) {
    Direction dir = Direction.fromString(dirstr);
    CubeColor color = CubeColor.fromString(colorstr);
    
    if (dir == Direction.None) { // add here
      selected.forEach(b -> ((Block)b).color = color);
    } else {
      Set<Item> extremeCubes = extremeCubes(dir, selected);
      this.allitems.addAll(extremeCubes.stream().map(
          c -> {
            Block d = ((Block)c).copy(dir);
            d.color = color;
            return d;}
          )
          .collect(Collectors.toList()));
    }
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

  // return retrieved from allitems, along with any potential selectors which are empty.
  public Set<Item> adj(String dirstr, Set<Item> selected) {
    Direction dir = Direction.fromString(dirstr);
    Set<Item> selectors = selected.stream()
        .map(c -> {Block b= ((Block)c).copy(dir); b.color = CubeColor.Fake; return b;})
        .collect(Collectors.toSet());

    this.allitems.addAll(selectors);
    
    Set<Item> actual = allitems.stream().filter(c -> selectors.contains(c))
        .collect(Collectors.toSet());
    
    return actual;
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
  
  @Override
  public void noop() {
    keyConsistency();
  }
  
  // get cubes at the outer locations
  private Set<Item> extremeCubes(Direction dir, Set<Item> selected) {
    Set<Item> realCubes = realBlocks(allitems);
    return selected.stream().map(c -> {
      Block d = (Block)c;
      while(realCubes.contains(d.copy(dir)))
        d = d.copy(dir);
      return d;
    }).collect(Collectors.toSet());
  }
  
  // ensures key coherence on mutations
  private void refreshSet(Set<Item> set) {
    List<Item> s = new LinkedList<>(set);
    set.clear();
    set.addAll(s);
  }
  
  private synchronized void keyConsistency() {
    refreshSet(allitems);
    refreshSet(selected);
    refreshSet(previous);
  }
  
  private Set<Item> realBlocks(Set<Item> all) {
    return all.stream().filter(b-> ((Block)b).color != CubeColor.Fake)
        .collect(Collectors.toSet());
  }
}
