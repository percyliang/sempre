package edu.stanford.nlp.sempre.tables.lambdadcs;

public class LambdaDCSException extends RuntimeException {
  private static final long serialVersionUID = -9174017483530966223L;

  public enum Type {

    // Unknown formula parameters (e.g., superlative that is not argmax or argmin)
    // Should not occur. Otherwise there is a serious bug in the code.
    invalidFormula,

    // Trying to perform an operation on unsupported denotations
    emptyList,
    nonSingletonList,
    infiniteList,

    // Type mismatch
    typeMismatch,
    notUnary,
    notBinary,

    // Other errors
    unknown,

  };

  public final Type type;
  public final String message;

  public LambdaDCSException(Type type, String message, Object... args) {
    this.type = type;
    this.message = String.format(message, args);
  }

  @Override
  public String toString() {
    return "" + type + ": " + message;
  }

}
