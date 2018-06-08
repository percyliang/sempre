package edu.stanford.nlp.sempre.freebase.lexicons.normalizers;

import edu.stanford.nlp.util.ArrayUtils;

import java.util.Set;

/**
 * Deletes the preposition at the end
 *
 * @author jonathanberant
 */
public class PrepDropNormalizer implements EntryNormalizer {

  public static Set<String> prepositions = ArrayUtils.asSet(new String[]{"in", "on", "of", "for", "about", "at", "from", "to", "with"});
  @Override
  public String normalize(String str) {
    String res = stripPrep(str);
    return stripPrep(res);
  }

  public static String stripPrep(String str) {

    String[] tokens = str.split("\\s+");
    if (tokens.length == 1)
      return str;
    if (!prepositions.contains(tokens[tokens.length - 1]))
      return str;
    else {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < tokens.length - 1; ++i) {
        sb.append(tokens[i] + " ");
      }
      return sb.toString().trim();
    }
  }
}
