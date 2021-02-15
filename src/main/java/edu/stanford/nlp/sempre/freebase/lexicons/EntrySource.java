package edu.stanford.nlp.sempre.freebase.lexicons;


public enum EntrySource {

  ALIGNMENT("ALIGNMENT"),
  STRING_MATCH("STRING_MATCH"),
  HARD_CODED("HARD"),
  LUCENE("LUCENE"),
  GRAPHPROP("GRAPHPROP"),
  FBSEARCH("FBSEARCH"),
  FEEDBACK("FEEDBACK");

  EntrySource(String source) {
    this.source = source;
  }

  private final String source;

  public String toString() {
    return source;
  }
  public static EntrySource parseSourceDesc(String desc) {

    if (desc.equals("HARD"))
      return HARD_CODED;
    if (desc.startsWith("fb:m."))
      return STRING_MATCH;
    if (desc.equals("STRING_MATCH"))
      return STRING_MATCH;
    if (desc.equals("NO_MID"))
      return ALIGNMENT;
    if (desc.equals("ALIGNMENT"))
      return ALIGNMENT;
    if (desc.equals("LUCENE"))
      return LUCENE;
    if (desc.equals("GRAPHPROP"))
      return GRAPHPROP;
    if (desc.equals("FBSEARCH"))
      return FBSEARCH;
    if (desc.equals("FEEDBACK"))
      return FEEDBACK;
    throw new RuntimeException("Description is not legal: " + desc);

  }

}
