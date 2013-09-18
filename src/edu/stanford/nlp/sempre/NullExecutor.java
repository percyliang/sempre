package edu.stanford.nlp.sempre;

/**
 * Assign null semantics to each formula.
 *
 * @author Percy Liang
 */
public class NullExecutor extends Executor {
  public Response execute(Formula formula) {
    return new Response(null);
  }
}
