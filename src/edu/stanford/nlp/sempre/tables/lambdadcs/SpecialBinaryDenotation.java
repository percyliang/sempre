package edu.stanford.nlp.sempre.tables.lambdadcs;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.lambdadcs.LambdaDCSException.Type;
import fig.basic.LispTree;

/**
 * Special binary denotations.
 *
 * This includes:
 * - comparison (!=, <, <=, >, >=)
 *
 * TODO(ice): In the future, we want to support
 * - independent clause (:)
 * - other functions (STRSTARTS, STRENDS)
 *
 * @author ppasupat
 */
public class SpecialBinaryDenotation extends BinaryDenotation {

  public static final Map<String, String> COMPARATOR_REVERSE = new HashMap<>();
  static {
    COMPARATOR_REVERSE.put("!=", "!=");
    COMPARATOR_REVERSE.put("<", ">=");
    COMPARATOR_REVERSE.put(">", "<=");
    COMPARATOR_REVERSE.put(">=", "<");
    COMPARATOR_REVERSE.put("<=", ">");
  }

  String name;

  SpecialBinaryDenotation(String name) {
    this.name = name;
  }

  public static SpecialBinaryDenotation create(Value value) {
    if (isSpecial(value))
      return new SpecialBinaryDenotation(((NameValue) value).id);
    throw new LambdaDCSException(Type.invalidFormula, "Cannot create SpecialBinaryDenotation from %s", value);
  }

  public static boolean isSpecial(Value value) {
    return value instanceof NameValue && COMPARATOR_REVERSE.containsKey(((NameValue) value).id);
  }

  @Override
  public LispTree toLispTree() {
    return LispTree.proto.newList("binary", name);
  }

  @Override
  public TableValue toTableValue(KnowledgeGraph graph) {
    throw new LambdaDCSException(Type.infiniteList, "Cannot convert to TableValue: %s", toLispTree());
  }

  @Override
  public UnaryDenotation joinFirst(UnaryDenotation firsts, KnowledgeGraph graph) {
    return InfiniteUnaryDenotation.create(COMPARATOR_REVERSE.get(name), firsts);
  }

  @Override
  public UnaryDenotation joinSecond(UnaryDenotation seconds, KnowledgeGraph graph) {
    return InfiniteUnaryDenotation.create(name, seconds);
  }

  @Override
  public BinaryDenotation reverse() {
    return new SpecialBinaryDenotation(COMPARATOR_REVERSE.get(name));
  }

  @Override
  public ExplicitBinaryDenotation explicitlyFilterFirst(UnaryDenotation firsts, KnowledgeGraph graph) {
    throw new LambdaDCSException(Type.infiniteList, "Cannot call explicitlyFilter* on SpecialBinaryDenotation");
  }

  @Override
  public ExplicitBinaryDenotation explicitlyFilterSecond(UnaryDenotation seconds, KnowledgeGraph graph) {
    throw new LambdaDCSException(Type.infiniteList, "Cannot call explicitlyFilter* on SpecialBinaryDenotation");
  }

}
