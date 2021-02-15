package edu.stanford.nlp.sempre;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import fig.basic.LispTree;
import fig.basic.LogInfo;

import java.util.Comparator;

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

  // (optional) String used for sorting Values. The default is to call toString()
  public String sortString() { return toString(); }

  // (optional) String without the LispTree structure. The default is to call toString()
  public String pureString() { return toString(); }

  @JsonCreator
  public static Value fromString(String str) {
    return Values.fromLispTree(LispTree.proto.parseFromString(str));
  }

  @Override public abstract boolean equals(Object o);
  @Override public abstract int hashCode();

  public static class ValueComparator implements Comparator<Value> {
    @Override
    public int compare(Value o1, Value o2) {
      return o1.toString().compareTo(o2.toString());
    }
  }
}
