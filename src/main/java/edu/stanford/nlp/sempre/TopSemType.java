package edu.stanford.nlp.sempre;

import fig.basic.LispTree;

// Represents any possible value.
public class TopSemType extends SemType {
  public boolean isValid() { return true; }
  public SemType meet(SemType that) { return that; }
  public SemType apply(SemType that) { return this; }
  public SemType reverse() { return this; }
  public LispTree toLispTree() { return LispTree.proto.newLeaf("top"); }
}
