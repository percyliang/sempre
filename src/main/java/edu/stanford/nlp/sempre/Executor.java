package edu.stanford.nlp.sempre;

import fig.basic.Evaluation;

/**
 * An Executor takes a logical form (Formula) and computes its denotation
 * (Value).
 *
 * @author Percy Liang
 */
public abstract class Executor {
  public static class Response {
    public Response(Value value) { this(value, new Evaluation()); }
    public Response(Value value, Evaluation stats) {
      this.value = value;
      this.stats = stats;
    }
    public final Value value;
    public final Evaluation stats;
  }

  // Execute the formula in the given context.
  public abstract Response execute(Formula formula, ContextValue context);
}
