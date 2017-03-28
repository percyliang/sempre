package edu.stanford.nlp.sempre.interactive;

import java.util.HashSet;
import java.util.Set;

import edu.stanford.nlp.sempre.ContextValue;
import edu.stanford.nlp.sempre.interactive.voxelurn.VoxelWorld;

/**
 * The world consists of Items, and tracks allItems: the whole world selected:
 * the set of items in focus, usually, but not necessarily a subset of allItems
 * previous: previously selected items, to handle more without variables
 * implementation: voxelurn.VoxelWorld
 * 
 * @author sidaw
 **/
public abstract class World {
  // supports variables, and perhaps scoping
  public Set<Item> allItems;
  public Set<Item> selected;
  public Set<Item> previous;

  public static World fromContext(String worldname, ContextValue context) {
    if (worldname.equals("VoxelWorld"))
      return VoxelWorld.fromContext(context);
    throw new RuntimeException("World does not exist: " + worldname);
  }

  // there are some annoying issues with mutable objects.
  // The current strategy is to keep allitems up to date on each mutable
  // operation
  public abstract String toJSON();

  public abstract Set<Item> has(String rel, Set<Object> values);

  public abstract Set<Object> get(String rel, Set<Item> subset);

  public abstract void update(String rel, Object value, Set<Item> selected);

  public abstract void merge();
  // public abstract void select(Set<Item> set);

  public World() {
    this.allItems = new HashSet<>();
    this.selected = new HashSet<>();
    this.previous = new HashSet<>();
  }

  // general actions, flatness means these actions can be performed on allitems
  public void remove(Set<Item> selected) {
    allItems = new HashSet<>(allItems);
    allItems.removeAll(selected);
    // this.selected.removeAll(selected);
  }

  // it is bad to ever mutate select, which will break scoping
  public void select(Set<Item> set) {
    this.selected = set;
  }

  public void noop() {
  }

  public Set<Item> selected() {
    return this.selected;
  }

  public Set<Item> previous() {
    return this.previous;
  }

  public Set<Item> all() {
    return allItems;
  }

  public Set<Item> empty() {
    return new HashSet<>();
  }

}
