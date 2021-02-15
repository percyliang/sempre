package edu.stanford.nlp.sempre.freebase.index;

public enum FbIndexField {

  TEXT("text"),
  MID("mid"),
  ID("id"),
  TYPES("types"),
  POPULARITY("popularity");

  private final String fieldName;

  FbIndexField(String fieldName) {
    this.fieldName = fieldName;
  }

  public String fieldName() {
    return fieldName;
  }
}
