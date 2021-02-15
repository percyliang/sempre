package edu.stanford.nlp.sempre.tables.lambdadcs;

import edu.stanford.nlp.sempre.*;
import fig.basic.LispTree;

/**
 * Represents a UnaryDenotation or a MappingDenotation.
 *
 * The following operations must be handled:
 * - Map
 *   - count(UL)
 *   - Aggregate: sum(UL), ...
 * - Combine
 *   - Merge: and(UL1, UL2), ...
 *   - Arithmetic: sub(UL1, UL2), ...
 *
 * Compose operations (join, superlative) are handled in BL.
 *
 * @author ppasupat
 */
public interface Unarylike {

  public LispTree toLispTree();
  public Value toValue();

  /** Return the name of the free variable. */
  public String getDomainVar();

  /** List of possible variable assignments */
  public UnaryDenotation domain();

  /** List of possible values. */
  public UnaryDenotation range();

  /** |key| => ??? */
  public UnaryDenotation get(Value key);

  /** ??? => |value| */
  public UnaryDenotation inverseGet(Value value);

  /** count and other aggregate operations */
  public Unarylike aggregate(AggregateFormula.Mode mode);

  /** Return a new Unarylike where only the values found in |upperBound|
   * and domain values found in |domainUpperBound| are kept */
  public Unarylike filter(UnaryDenotation upperBound, UnaryDenotation domainUpperBound);
}
