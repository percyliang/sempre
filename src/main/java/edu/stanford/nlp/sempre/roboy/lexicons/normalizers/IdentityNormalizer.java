package edu.stanford.nlp.sempre.roboy.lexicons.normalizers;

public class IdentityNormalizer implements EntryNormalizer {

  @Override
  public String normalize(String str) {
    return str;
  }

}
