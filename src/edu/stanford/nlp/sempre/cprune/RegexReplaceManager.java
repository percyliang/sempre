package edu.stanford.nlp.sempre.cprune;

import java.util.*;

public class RegexReplaceManager {
  static Map<String, java.util.regex.Pattern> dict = new HashMap<>();

  public static String replace(String source, String regex, String replacement) {
    if (!dict.containsKey(regex)) {
      dict.put(regex, java.util.regex.Pattern.compile(regex));
    }
    java.util.regex.Pattern regexPattern = dict.get(regex);
    return regexPattern.matcher(source).replaceAll(replacement);
  }
}
