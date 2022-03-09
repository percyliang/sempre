package edu.stanford.nlp.sempre.tables.lambdadcs;

import edu.stanford.nlp.sempre.*;
import fig.basic.LispTree;

public interface PairList {

  // ============================================================
  // Representation
  // ============================================================

  public String toString();
  public LispTree toLispTree();
  public PairListValue toValue();

  // ============================================================
  // Getter
  // ============================================================

  public UnaryDenotation domain();
  public UnaryDenotation range();
  public UnaryDenotation get(Value key);
  public UnaryDenotation inverseGet(Value value);

  // ============================================================
  // Operations
  // ============================================================

  public PairList aggregate(AggregateFormula.Mode mode);
  public PairList filter(UnaryDenotation upperBound, UnaryDenotation domainUpperBound);
  public PairList reverse();
  public UnaryDenotation joinOnKey(UnaryDenotation keys);
  public UnaryDenotation joinOnValue(UnaryDenotation values);
  public ExplicitPairList explicitlyFilterOnKey(UnaryDenotation keys);
  public ExplicitPairList explicitlyFilterOnValue(UnaryDenotation values);

}
