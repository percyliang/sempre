package edu.stanford.nlp.sempre.interactive.actions;
import java.util.Set;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import edu.stanford.nlp.sempre.ContextValue;

// flat world is just a list of allitems where actions can be performed on them

public abstract class FlatWorld {
  // supports variables, and perhaps scoping
  public Set<Item> selected;
  public Set<Item> allitems;
  public LinkedList<Set<Item>> stack;
  
  public static FlatWorld fromContext(String worldname, ContextValue context) {
    if (worldname.equals("BlocksWorld"))
      return BlocksWorld.fromContext(context);
    throw new RuntimeException("World does not exist: " + worldname);
  }
  
  // special relations cannot really be implemented as triple
  public abstract Set<Item> has(String rel, Set<Object> values);
  public abstract Set<Object> get(String rel, Set<Item> subset);
  public abstract void update(String rel, Object value);

  public FlatWorld() {
    this.allitems = Sets.newHashSet();
    this.selected = Sets.newHashSet();
    this.stack = Lists.newLinkedList();
  }
  
  // general actions, flatness means these actions can be performed on all allitems
  public void remove() {
    allitems.removeAll(selected);
    selected.clear();
  }
  // dd more actions that are called by 
  
  // selections
  public void select(Set<Item> set) {
    selected.addAll(set);
  }
  public void push() {
    stack.push(selected);
  }
  public void pop() {
    selected = stack.pop();
  }
  
  // basic sets
  public Set<Item> selected() {
    return Sets.intersection(selected, allitems);
  }
  public Set<Item> all() {
    return allitems;
  }
  public Set<Item> nothing() {
    return Sets.newHashSet();
  }

  public String toJSON() {
    // TODO Auto-generated method stub
    return null;
  } 
}
