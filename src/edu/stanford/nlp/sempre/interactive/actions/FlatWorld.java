package edu.stanford.nlp.sempre.interactive.actions;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import edu.stanford.nlp.sempre.ContextValue;

// flat world is just a list of allitems where actions can be performed on them

public abstract class FlatWorld {
  // supports variables, and perhaps scoping
  public Set<Item> allitems;
  
  public HashMap<String, Set<Item>> localVariables;
  public HashMap<String, Set<Item>> globalVariables;
  
  public static FlatWorld fromContext(String worldname, ContextValue context) {
    if (worldname.equals("BlocksWorld"))
      return BlocksWorld.fromContext(context);
    throw new RuntimeException("World does not exist: " + worldname);
  }
  
  // there are some annoying issues with mutable objects.
  // The current strategy is to keep allitems up to date on each mutable operation
  public abstract String toJSON();
  public abstract Set<Item> has(String rel, Set<Object> values);
  public abstract Set<Object> get(String rel, Set<Item> subset);
  public abstract void update(String rel, Object value, Set<Item> selected);
  // public abstract void select(Set<Item> set);
  
  public FlatWorld() {
    this.allitems = new HashSet<>();
    this.localVariables = new HashMap<>();
    this.globalVariables = new HashMap<>();
  }
  // general actions, flatness means these actions can be performed on all allitems
  public void remove(Set<Item> selected) {
    allitems.removeAll(selected);
  }
  // current standards for "this", which is the current scope if it exists, or selected item if not
  // the local variable this
  public Set<Item> current() {
    if (selected == null)
      return selected();
    return selected;
  }
  public Set<Item> get(String varname) {
    return allitems.stream().filter(i -> i.selected()).collect(Collectors.toSet());
  }
  // explicit and global selections
  public void select(Set<Item> set) {
    allitems.forEach(i -> i.select(false));
    set.forEach(i -> i.select(true));
  }
  public Set<Item> selected() {
    return allitems.stream().filter(i -> i.selected()).collect(Collectors.toSet());
  }
  public Set<Item> all() {
    return allitems;
  }
  public Set<Item> empty() {
    return new HashSet<>();
  }
}
