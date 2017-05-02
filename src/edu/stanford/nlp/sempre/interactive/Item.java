package edu.stanford.nlp.sempre.interactive;

import java.util.Set;

// Individual items with some properties
public abstract class Item {
  public Set<String> names;

  public abstract boolean selected(); // explicit global selection

  public abstract void select(boolean sel);

  public abstract void update(String rel, Object value);

  public abstract Object get(String rel);
}
