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

  // Print using LogInfo.
  public void log() { LogInfo.logs("%s", toString()); }

  @JsonValue
  public String toString() { return toLispTree().toString(); }

  @JsonCreator
  public static Value fromString(String str) {
    return Values.fromLispTree(LispTree.proto.parseFromString(str));
  }

  @Override public abstract boolean equals(Object o);
  @Override public abstract int hashCode();
}
