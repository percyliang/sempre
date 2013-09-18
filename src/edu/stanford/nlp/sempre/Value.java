package edu.stanford.nlp.sempre;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import fig.basic.LispTree;
import fig.basic.LogInfo;

/**
 * Values represent denotations (or partial denotations).
 *
 * @author Percy Liang
 */
public abstract class Value {
  public abstract LispTree toLispTree();

  // |this| is target value, |that| is predicted value
  // Return a number [0,1] denoting how good |that| is correct.
  // Default implementation: just test for equality.
  // Subclasses should override with other.
  public double getCompatibility(Value that) {
    return this.equals(that) ? 1 : 0;
  }

  // Print using LogInfo.
  public void log() { LogInfo.logs("%s", toString()); }

  @JsonValue
  public String toString() { return toLispTree().toString(); }

  @JsonCreator
  public static Value fromString(String str) {
    return Values.fromLispTree(LispTree.proto.parseFromString(str));
  }

  @Override abstract public boolean equals(Object o);
  @Override abstract public int hashCode();
}
