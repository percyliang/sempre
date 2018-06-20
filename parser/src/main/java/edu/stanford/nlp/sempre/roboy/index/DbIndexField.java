package edu.stanford.nlp.sempre.roboy.index;

public enum DbIndexField {

  TEXT("text"),
  MID("mid"),
  ID("id"),
  TYPES("types"),
  POPULARITY("popularity");

  private final String fieldName;

  DbIndexField(String fieldName) {
    this.fieldName = fieldName;
  }

  public String fieldName() {
    return fieldName;
  }
}
