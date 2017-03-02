package edu.stanford.nlp.sempre;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

import fig.basic.*;


/**
 * Represents a small knowledge graph (much smaller than Freebase).
 *
 * A KnowledgeGraph can be created from either
 * - a list of triples, or
 * - other data format (e.g., web tables in CSV format)
 *
 * @author ppasupat
 */
public abstract class KnowledgeGraph {

  public static KnowledgeGraph fromLispTree(LispTree tree) {
    if ("graph".equals(tree.child(0).value)) {
      if (tree.children.size() > 1 && tree.child(1).isLeaf()) {
        // Use a specific subclass of KnowledgeGraph
        try {
          String className = tree.child(1).value;
          Class<?> classObject = Class.forName(SempreUtils.resolveClassName(className));
          return (KnowledgeGraph) classObject.getDeclaredMethod("fromLispTree", LispTree.class).invoke(null, tree);
        } catch (InvocationTargetException e) {
          e.getCause().printStackTrace();
          LogInfo.fail(e.getCause());
          throw new RuntimeException(e);
        } catch (IllegalAccessException | IllegalArgumentException |
            NoSuchMethodException | SecurityException | ClassNotFoundException e) {
          e.printStackTrace();
          throw new RuntimeException(e);
        }
      } else {
        // (graph (a1 r1 b1) (a2 r2 b2) ...) -- explicit triples
        return NaiveKnowledgeGraph.fromLispTree(tree);
      }
    } else {
      throw new RuntimeException("Cannot convert " + tree + " to KnowledgeGraph.");
    }
  }

  // ============================================================
  // Helper methods
  // ============================================================

  /** Reverse the pairs */
  public static List<Pair<Value, Value>> getReversedPairs(Collection<Pair<Value, Value>> pairs) {
    List<Pair<Value, Value>> reversed = new ArrayList<>();
    for (Pair<Value, Value> pair : pairs)
      reversed.add(new Pair<>(pair.getSecond(), pair.getFirst()));
    return reversed;
  }

  // ============================================================
  // Abstract methods
  // ============================================================

  public abstract LispTree toLispTree();
  public abstract LispTree toShortLispTree();
  @Override public String toString() { return toLispTree().toString(); }

  /** Return all y such that x in firsts and (x,r,y) in graph */
  public abstract List<Value> joinFirst(Value r, Collection<Value> firsts);

  /** Return all x such that y in seconds and (x,r,y) in graph */
  public abstract List<Value> joinSecond(Value r, Collection<Value> seconds);

  /** Return all (x,y) such that x in firsts and (x,r,y) in graph */
  public abstract List<Pair<Value, Value>> filterFirst(Value r, Collection<Value> firsts);

  /** Return all (x,y) such that y in seconds and (x,r,y) in graph */
  public abstract List<Pair<Value, Value>> filterSecond(Value r, Collection<Value> seconds);

  /** Clean up cached data. Default to doing nothing. */
  public void clean() { }

}
