package edu.stanford.nlp.sempre.tables.lambdadcs;

import java.util.*;

import fig.basic.Option;

public class LambdaDCSException extends RuntimeException {
  public static class Options {
    @Option(gloss = "do not generate message to save time") public boolean noErrorMessage = false;
  }
  public static Options opts = new Options();

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
    notMapping,

    // Other errors
    unknown,

    // Placeholder (used to represent partial formulas in DPDParser)
    placeholder,

  };

  public final Type type;
  public final String message;

  public LambdaDCSException(Type type, String message, Object... args) {
    this.type = type;
    if (opts.noErrorMessage)
      this.message = "";
    else if (args.length == 0)
      this.message = message;
    else
      this.message = String.format(message, args);
  }

  @Override
  public String toString() {
    return "" + type + ": " + message;
  }

  private static final Collection<Type> UNRECOVERABLE = new ArrayList<>();
  static {
    UNRECOVERABLE.add(Type.invalidFormula);
    UNRECOVERABLE.add(Type.typeMismatch);
  }

  public static boolean isUnrecoverable(String error) {
    try {
      String typeString = error.substring(0, error.indexOf(":"));
      Type type = Type.valueOf(typeString);
      return UNRECOVERABLE.contains(type);
    } catch (Exception e) {
      return false; // Be conservative
    }
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof LambdaDCSException)) return false;
    LambdaDCSException that = (LambdaDCSException) o;
    return type == that.type &&
        ((message == null && that.message == null) || message.equals(that.message));
  }

  @Override
  public int hashCode() {
    return type.hashCode() + message.hashCode();
  }

}
