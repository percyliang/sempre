package edu.stanford.nlp.sempre.tables.lambdadcs;

import edu.stanford.nlp.sempre.*;
import fig.basic.*;

/**
 * Binary denotation: a mapping from value to value.
 * @author ppasupat
 */
public abstract class BinaryDenotation {

  @Override
  public String toString() {
    return toLispTree().toString();
  }

  public abstract LispTree toLispTree();

  public abstract TableValue toTableValue(KnowledgeGraph graph);

  /** Return all y such that for some x in firsts, (x,y) in binary */
  public abstract UnaryDenotation joinFirst(UnaryDenotation firsts, KnowledgeGraph graph);

  /** Return all x such that for some y in seconds, (x,y) in binary */
  public abstract UnaryDenotation joinSecond(UnaryDenotation seconds, KnowledgeGraph graph);

  /** Return all (y,x) such that (x,y) in binary */
  public abstract BinaryDenotation reverse();

  /** Return all (x,y) such that x in firsts and (x,y) in binary */
  public abstract ExplicitBinaryDenotation explicitlyFilterFirst(UnaryDenotation firsts, KnowledgeGraph graph);

  /** Return all (x,y) such that y in seconds and (x,y) in binary */
  public abstract ExplicitBinaryDenotation explicitlyFilterSecond(UnaryDenotation seconds, KnowledgeGraph graph);

}
