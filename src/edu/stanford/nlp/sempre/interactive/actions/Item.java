package edu.stanford.nlp.sempre.interactive.actions;

// Individual items with some properties
public abstract class Item {
  public abstract void update(String rel, Object value);
  public abstract Object get(String rel);
}