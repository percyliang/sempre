package edu.stanford.nlp.sempre.tables.lambdadcs;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.AggregateFormula.Mode;
import edu.stanford.nlp.sempre.tables.lambdadcs.LambdaDCSException.Type;
import fig.basic.LispTree;

/**
 * Mapping denotation: a mapping from variable assignment to values.
 *
 * Share the implementation with BinaryDenotation by using PairList.
 *
 * @author ppasupat
 */
public class MappingDenotation<PL extends PairList> implements Unarylike {

  protected final String domainVar;
  protected final PL pairList;

  protected MappingDenotation(String domainVar, PL pairList) {
    this.domainVar = domainVar;
    this.pairList = pairList;
  }

  @Override
  public String toString() {
    return toLispTree().toString();
  }

  @Override
  public LispTree toLispTree() {
    return LispTree.proto.L("mapping", domainVar, pairList.toLispTree());
  }

  @Override
  public ListValue toValue() {
    throw new LambdaDCSException(Type.notUnary, "Expected Unary; Mapping found: %s", this);
  }

  @Override
  public String getDomainVar() {
    return domainVar;
  }

  public BinaryDenotation<PL> asBinary() {
    return new BinaryDenotation<PL>(pairList);
  }

  @Override public UnaryDenotation domain() { return pairList.domain(); }
  @Override public UnaryDenotation range() { return pairList.range(); }
  @Override public UnaryDenotation get(Value key) { return pairList.get(key); }
  @Override public UnaryDenotation inverseGet(Value value) { return pairList.inverseGet(value); }
  @Override public Unarylike aggregate(Mode mode) {
    return new MappingDenotation<>(domainVar, pairList.aggregate(mode));
  }
  @Override public Unarylike filter(UnaryDenotation upperBound, UnaryDenotation domainUpperBound) {
    return new MappingDenotation<>(domainVar, pairList.filter(upperBound, domainUpperBound));
  }

}
