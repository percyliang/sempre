package edu.stanford.nlp.sempre.interactive;

public class BadInteractionException extends RuntimeException {

  public static BadInteractionException nonSenseDefinition(String head) {
    String message = String.format("Definitions should make sense and useable by yourself and others"
        + "-- using more than 10 words, " + "or more than 15 characters in a word is not allowed."
        + "If your definition is not non-sense," + "please paste this message in our bugs channel (head: %s)", head);
    return new BadInteractionException(message);
  }

  public static BadInteractionException headIsCore(String head) {
    String message = String.format(
        "Redefining the core language is not allowed, " + "please reword your command and try again (head: %s)", head);
    return new BadInteractionException(message);
  }

  public static BadInteractionException headIsEmpty(String head) {
    String message = String.format("Cannot define with an empty head (head: %s)", head);
    return new BadInteractionException(message);
  }

  public BadInteractionException() {
    // TODO Auto-generated constructor stub
  }

  public BadInteractionException(String message) {
    super(message);
    // TODO Auto-generated constructor stub
  }

  public BadInteractionException(Throwable cause) {
    super(cause);
    // TODO Auto-generated constructor stub
  }

  public BadInteractionException(String message, Throwable cause) {
    super(message, cause);
    // TODO Auto-generated constructor stub
  }

  public BadInteractionException(String message, Throwable cause, boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
    // TODO Auto-generated constructor stub
  }

}
