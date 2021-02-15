package edu.stanford.nlp.sempre.interactive.voxelurn;

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
import edu.stanford.nlp.sempre.interactive.Item;
import edu.stanford.nlp.sempre.interactive.World;
import fig.basic.Option;

// the world of stacks
public class VoxelWorld extends World {
  public static class Options {
    @Option(gloss = "maximum number of cubes to convert")
    public int maxBlocks = 1024 ^ 2;
  }

  public static Options opts = new Options();

  public final static String SELECT = "S";

  public static VoxelWorld fromContext(ContextValue context) {
    if (context == null || context.graph == null) {
      return fromJSON("[[3,3,1,\"gray\",[\"S\"]],[4,4,1,\"blue\",[]]]");
    }
    NaiveKnowledgeGraph graph = (NaiveKnowledgeGraph) context.graph;
    String wallString = ((StringValue) graph.triples.get(0).e1).value;
    return fromJSON(wallString);
  }

  public void base(int x, int y) {
    Voxel basecube = new Voxel(x, y, 0, Color.Fake.toString());
    this.allItems = new HashSet<>(this.allItems);
    this.selected = new HashSet<>(this.selected);
    allItems.add(basecube);
    selected.add(basecube);
  }

  public Set<Item> origin() {
    for (Item i : allItems) {
      Voxel b = (Voxel) i;
      if (b.col == 0 && b.row == 0 & b.height == 0)
        return Sets.newHashSet(b);
    }
    Voxel basecube = new Voxel(0, 0, 0, Color.Fake.toString());
    return Sets.newHashSet(basecube);
  }

  @SuppressWarnings("unchecked")
  public VoxelWorld(Set<Item> blockset) {
    super();
    this.allItems = blockset;
    this.selected = blockset.stream().filter(b -> ((Voxel) b).names.contains(SELECT)).collect(Collectors.toSet());
    this.selected.forEach(i -> i.names.remove(SELECT));
  }

  // we only use names S to communicate with the client, internally its just the
  // select variable
  @Override
  public String toJSON() {
    // selected thats no longer in the world gets nothing
    // allitems.removeIf(c -> ((Block)c).color == CubeColor.Fake &&
    // !this.selected.contains(c));
    // allitems.stream().filter(c -> selected.contains(c)).forEach(i ->
    // i.names.add(SELECT));

    return Json.writeValueAsStringHard(allItems.stream().map(c -> {
      Voxel b = ((Voxel) c).clone();
      if (selected.contains(b))
        b.names.add("S");
      return b.toJSON();
    }).collect(Collectors.toList()));
    // return this.worldlist.stream().map(c -> c.toJSON()).reduce("", (o, n) ->
    // o+","+n);
  }

  private static VoxelWorld fromJSON(String wallString) {
    @SuppressWarnings("unchecked")
    List<List<Object>> cubestr = Json.readValueHard(wallString, List.class);
    Set<Item> cubes = cubestr.stream().map(c -> {
      return Voxel.fromJSONObject(c);
    }).collect(Collectors.toSet());
    // throw new RuntimeException(a.toString()+a.get(1).toString());
    VoxelWorld world = new VoxelWorld(cubes);
    // world.previous.addAll(world.selected);
    // we can only use previous within a block;
    return world;
  }

  @Override
  public Set<Item> has(String rel, Set<Object> values) {
    // LogInfo.log(values);
    return this.allItems.stream().filter(i -> values.contains(i.get(rel))).collect(Collectors.toSet());
  }

  @Override
  public Set<Object> get(String rel, Set<Item> subset) {
    return subset.stream().map(i -> i.get(rel)).collect(Collectors.toSet());
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
    Sets.difference(selected, allItems).forEach(i -> ((Voxel) i).color = Color.Fake);
    allItems.removeIf(c -> ((Voxel) c).color.equals(Color.Fake) && !this.selected.contains(c));
    allItems.addAll(selected);
    if (allItems.size() > opts.maxBlocks) {
      throw new RuntimeException(
          String.format("Number of blocks (%d) exceeds the upperlimit %d", allItems.size(), opts.maxBlocks));
    }
    // keyConsistency();
  }

  // block world specific actions, overriding move
  public void move(String dir, Set<Item> selected) {
    // allitems.removeAll(selected);
    selected.forEach(b -> ((Voxel) b).move(Direction.fromString(dir)));
    keyConsistency();
    // allitems.addAll(selected); // this is not overriding
  }

  public void add(String colorstr, String dirstr, Set<Item> selected) {
    Direction dir = Direction.fromString(dirstr);
    Color color = Color.fromString(colorstr);

    if (dir == Direction.None) { // add here
      selected.forEach(b -> ((Voxel) b).color = color);
    } else {
      Set<Item> extremeCubes = extremeCubes(dir, selected);
      this.allItems.addAll(extremeCubes.stream().map(c -> {
        Voxel d = ((Voxel) c).copy(dir);
        d.color = color;
        return d;
      }).collect(Collectors.toList()));
    }
  }

  // get cubes at extreme positions
  public Set<Item> veryx(String dirstr, Set<Item> selected) {
    Direction dir = Direction.fromString(dirstr);
    switch (dir) {
    case Back:
      return argmax(c -> c.row, selected);
    case Front:
      return argmax(c -> -c.row, selected);
    case Left:
      return argmax(c -> c.col, selected);
    case Right:
      return argmax(c -> -c.col, selected);
    case Top:
      return argmax(c -> c.height, selected);
    case Bot:
      return argmax(c -> -c.height, selected);
    default:
      throw new RuntimeException("invalid direction");
    }
  }

  // return retrieved from allitems, along with any potential selectors which
  // are empty.
  public Set<Item> adj(String dirstr, Set<Item> selected) {
    Direction dir = Direction.fromString(dirstr);
    Set<Item> selectors = selected.stream().map(c -> {
      Voxel b = ((Voxel) c).copy(dir);
      b.color = Color.Fake;
      return b;
    }).collect(Collectors.toSet());

    this.allItems.addAll(selectors);

    Set<Item> actual = allItems.stream().filter(c -> selectors.contains(c)).collect(Collectors.toSet());

    return actual;
  }

  public static Set<Item> argmax(Function<Voxel, Integer> f, Set<Item> items) {
    int maxvalue = Integer.MIN_VALUE;
    for (Item i : items) {
      int cvalue = f.apply((Voxel) i);
      if (cvalue > maxvalue)
        maxvalue = cvalue;
    }
    final int maxValue = maxvalue;
    return items.stream().filter(c -> f.apply((Voxel) c) >= maxValue).collect(Collectors.toSet());
  }

  @Override
  public void noop() {
    keyConsistency();
  }

  // get cubes at the outer locations
  private Set<Item> extremeCubes(Direction dir, Set<Item> selected) {
    Set<Item> realCubes = realBlocks(allItems);
    return selected.stream().map(c -> {
      Voxel d = (Voxel) c;
      while (realCubes.contains(d.copy(dir)))
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

  private void keyConsistency() {
    refreshSet(allItems);
    refreshSet(selected);
    refreshSet(previous);
  }

  private Set<Item> realBlocks(Set<Item> all) {
    return all.stream().filter(b -> !((Voxel) b).color.equals(Color.Fake)).collect(Collectors.toSet());
  }
}
