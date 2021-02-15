package edu.stanford.nlp.sempre;

public class BadFormulaException extends RuntimeException {
  public static final long serialVersionUID = 86586128316354597L;

  String message;

  public BadFormulaException(String message) { this.message = message; }

  // Combine multiple exceptions
  public BadFormulaException(BadFormulaException... exceptions) {
    StringBuilder builder = new StringBuilder();
    for (BadFormulaException exception : exceptions)
      builder.append(" | ").append(exception.message);
      //builder.append(exception).append("\n");
    this.message = builder.toString().substring(3);
  }

  @Override
  public String toString() { return message; }
}
