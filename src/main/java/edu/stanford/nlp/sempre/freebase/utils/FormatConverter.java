package edu.stanford.nlp.sempre.freebase.utils;

public final class FormatConverter {
  private FormatConverter() { }

  public static final String FULL_FB_PREFIX = "http://rdf.freebase.com/ns/";
  public static final String SHORT_FB_PREFIX = "fb:";

  public static String fromDotToSlash(String dotString) {
    if (dotString.startsWith("http")) {
      return dotString.substring(dotString.lastIndexOf('/')).replace('.', '/');
    } else {
      if (dotString.startsWith("/"))
        return dotString;
      return "/" + dotString.substring(dotString.indexOf(':') + 1).replace('.', '/');
    }
  }

  public static String toShortPrefix(String str) {
    return str.replace(FULL_FB_PREFIX, SHORT_FB_PREFIX);
  }

  public static String fromNoPrefixMidToDot(String mid) {

    if (mid.startsWith("fb:m") || mid.startsWith("/m/"))
      throw new RuntimeException("This mid has a prefix: " + mid);
    return SHORT_FB_PREFIX + "m." + mid;

  }

  /** converts from slash notation to dot notation */
  public static String fromSlashToDot(String slashString, boolean strict) {

    if (!(slashString.charAt(0) == '/')) {
      if (strict) {
        throw new IllegalArgumentException("Not a legal slash string: " + slashString);
      } else
        return slashString;
    }

    return SHORT_FB_PREFIX + slashString.substring(1).replace('/', '.');
  }

  /**
   * convert from <b>[/award/award_winning_work/awards_won,
   * /award/award_honor/award_winner]</b> to <b>(lambda x
   * (fb:award.award_winning_work.awards_won (fb:award.award_honor.award_winner
   * (var x))))</b>
   */
  public static String fromCvtBinaryToLispTree(String str) {

    boolean reversed = false;
    if (str.startsWith("!")) {
      reversed = true;
      str = str.substring(1);
    }
    // strip brackets
    str = str.substring(1, str.length() - 1);
    String[] tokens = str.split(",");
    if (tokens.length == 1) {
      return reversed ? "!" + fromSlashToDot(tokens[0].trim(), false) : fromSlashToDot(tokens[0].trim(), false);
    } else {
      String property1 = fromSlashToDot(tokens[0].trim(), false);
      String property2 = fromSlashToDot(tokens[1].trim(), false);
      return propertiesToCompositeLispTree(property1, property2, reversed);
    }
  }

  public static String propertiesToCompositeLispTree(String property1,
                                                     String property2, boolean reversed) {

    StringBuilder sb = new StringBuilder();
    if (reversed) {
      sb.append("(lambda x (!" + property2 + " (!" + property1 + " (var x))))");
    } else {
      sb.append("(lambda x (" + property1 + " (" + property2 + " (var x))))");
    }
    return sb.toString();
  }


}
