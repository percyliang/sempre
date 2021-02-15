package edu.stanford.nlp.sempre.freebase.lexicons.normalizers;

import edu.stanford.nlp.util.ArrayUtils;

import java.io.Serializable;
import java.util.Set;

/**
 * Normalizes a string by omitting adverbs, determiners, quasi modals, modals
 * and "be"
 *
 * @author jonathanberant
 */
public class BinaryNormalizer implements EntryNormalizer, Serializable {


  private static final long serialVersionUID = -4704293835712088190L;

  public static Set<String> adverbs = ArrayUtils.asSet(new String[]{"also", "very", "currently", "originally", "really"});
  public static Set<String> determiners = ArrayUtils.asSet(new String[]{"the", "a", "an"});
  public static Set<String> quasiModals = ArrayUtils.asSet(new String[]{"used to "});
  public static Set<String> modals = ArrayUtils.asSet(new String[]{"will "});
  public static Set<String> be = ArrayUtils.asSet(new String[]{"'m", "am", "'re", "are", "'s", "is", "was", "were", "be", "being"});

  public String normalize(String binary) {

    String res = binary.toLowerCase();
    res = omitAdverbs(res);
    res = omitDeterminers(res);
    res = stripQuasiModals(res);
    res = stripModals(res);
    res = stripBe(res);

    if (res.length() == 0)
      return binary;
    return res;
  }


  private static String stripBe(String res) {

    String[] tokens = res.split("\\s+");
    int i;
    for (i = 0; i < tokens.length; ++i) {
      if (!be.contains(tokens[i]))
        break;
    }

    StringBuilder sb = new StringBuilder();
    for (; i < tokens.length; ++i) {
      sb.append(tokens[i] + " ");
    }

    return sb.toString().trim();
  }


  private static String stripModals(String res) {

    for (String modal : modals) {
      if (res.startsWith(modal)) {
        res = res.substring(modal.length());
        return res;
      }
    }
    return res;
  }


  private static String stripQuasiModals(String res) {

    for (String quasiModal : quasiModals) {
      if (res.startsWith(quasiModal)) {
        res = res.substring(quasiModal.length());
        return res;
      }
    }
    return res;
  }


  private static String omitAdverbs(String res) {

    String[] tokens = res.split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < tokens.length; ++i) {
      if (!adverbs.contains(tokens[i]))
        sb.append(tokens[i] + " ");
    }
    return sb.toString().trim();
  }

  private static String omitDeterminers(String res) {

    String[] tokens = res.split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < tokens.length; ++i) {
      if (!determiners.contains(tokens[i]))
        sb.append(tokens[i] + " ");
    }
    return sb.toString().trim();
  }


}
