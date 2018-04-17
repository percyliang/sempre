package edu.stanford.nlp.sempre.freebase.lexicons;

import edu.stanford.nlp.sempre.freebase.utils.FileUtils;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import fig.basic.LogInfo;
import fig.basic.Option;

import java.util.Arrays;
import java.util.List;

public final class TokenLevelMatchFeatures {
  private TokenLevelMatchFeatures() { }

  public static class Options {
    @Option(gloss = "Verbose") public int verbose = 0;
  }

  public static Options opts = new Options();

  public static boolean usePrefix = false;
  public static boolean useSuffix = false;

  public static boolean useQueryIsPrefix = true;
  public static boolean useAnswerIsPrefix = true;
  public static boolean useQueryIsSuffix = true;
  public static boolean useAnswerIsSuffix = true;
  public static boolean useQueryEqualAnswer = true;


  public static boolean useDiffSet = false;
  public static boolean useEqualSet = false;
  public static boolean useDiffFirstName = false;


  public static Counter<String> extractFeatures(String query, String answer) {

    Counter<String> res = new ClassicCounter<String>();
    query = FileUtils.omitPunct(query).toLowerCase();
    answer = FileUtils.omitPunct(answer).toLowerCase();

    String[] queryTokens = query.split("\\s+");
    String[] answerTokens = answer.split("\\s+");
    if (usePrefix) {
      boolean prefix = isPrefix(queryTokens, answerTokens);
      res.incrementCount("prefix", prefix ? 1 : 0);
    }
    if (useSuffix) {
      boolean suffix = isSuffix(queryTokens, answerTokens);
      res.incrementCount("suffix", suffix ? 1 : 0);
    }
    if (useDiffSet) {
      boolean diffSet = isDiffSet(queryTokens, answerTokens);
      res.incrementCount("diffset", diffSet ? 1 : 0);
    }

    if (useEqualSet) {
      boolean equalSet = isEqualSet(queryTokens, answerTokens);
      res.incrementCount("equalset", equalSet ? 1 : 0);
    }
    if (useDiffFirstName) {
      boolean diffFirstName = isDiffFirstName(queryTokens, answerTokens);
      res.incrementCount("diff_firstname", diffFirstName ? 1 : 0);
    }
    if (useQueryIsPrefix) {
      boolean queryIsPrefix = isFirstPrefixOfSecond(queryTokens, answerTokens);
      res.incrementCount("queryIsPrefix", queryIsPrefix ? 1 : 0);
    }
    if (useAnswerIsPrefix) {
      boolean answerIsPrefix = isFirstPrefixOfSecond(answerTokens, queryTokens);
      res.incrementCount("answerIsPrefix", answerIsPrefix ? 1 : 0);
    }
    if (useQueryIsSuffix) {
      boolean queryIsSuffix = isFirstSuffixOfSecond(queryTokens, answerTokens);
      res.incrementCount("queryIsSuffix", queryIsSuffix ? 1 : 0);
    }
    if (useAnswerIsSuffix) {
      boolean answerIsSuffix = isFirstSuffixOfSecond(answerTokens, queryTokens);
      res.incrementCount("answerIsSuffix", answerIsSuffix ? 1 : 0);
    }
    if (useQueryEqualAnswer) {
      res.incrementCount("equal", isEqual(queryTokens, answerTokens) ? 1 : 0);
    }

    return res;
  }

  private static boolean isPrefix(String[] queryTokens, String[] answerTokens) {
    int min = Math.min(queryTokens.length, answerTokens.length);
    for (int i = 0; i < min; ++i) {
      if (!queryTokens[i].equals(answerTokens[i]))
        return false;
    }
    return true;
  }

  private static boolean isEqual(String[] queryTokens, String[] answerTokens) {

    if (queryTokens.length != answerTokens.length)
      return false;
    for (int i = 0; i < queryTokens.length; ++i) {
      if (!queryTokens[i].equals(answerTokens[i]))
        return false;
    }
    return true;
  }

  private static boolean isFirstPrefixOfSecond(String[] tokens1, String[] tokens2) {
    if (tokens1.length >= tokens2.length)
      return false;

    for (int i = 0; i < tokens1.length; ++i) {
      if (!tokens1[i].equals(tokens2[i]))
        return false;
    }
    return true;
  }


  private static boolean isSuffix(String[] queryTokens, String[] answerTokens) {
    int min = Math.min(queryTokens.length, answerTokens.length);
    for (int i = 0; i < min; ++i) {
      if (!queryTokens[queryTokens.length - 1 - i].equals(answerTokens[answerTokens.length - 1 - i]))
        return false;
    }
    return true;
  }

  private static boolean isFirstSuffixOfSecond(String[] tokens1, String[] tokens2) {

    if (tokens1.length >= tokens2.length)
      return false;

    for (int i = 0; i < tokens1.length; ++i) {
      if (!tokens1[tokens1.length - 1 - i].equals(tokens2[tokens2.length - 1 - i]))
        return false;
    }
    return true;
  }

  private static boolean isDiffFirstName(String[] queryTokens, String[] answerTokens) {

    return (queryTokens.length == 2 && answerTokens.length == 2 && queryTokens[1].equals(answerTokens[1])
        && !queryTokens[1].equals(answerTokens[0]));
  }

  private static boolean isDiffSet(String[] queryTokens, String[] answerTokens) {

    List<String> queryList = Arrays.asList(queryTokens);
    List<String> answerList = Arrays.asList(answerTokens);
    return queryList.containsAll(answerList) || answerList.containsAll(queryList);
  }

  public static int diffSetSize(String query, String answer) {

    query = FileUtils.omitPunct(query);
    answer = FileUtils.omitPunct(answer);

    String[] queryTokens = query.toLowerCase().split("\\s+");
    String[] answerTokens = answer.toLowerCase().split("\\s+");

    List<String> queryList = Arrays.asList(queryTokens);
    List<String> answerList = Arrays.asList(answerTokens);
    boolean queryContains = queryList.containsAll(answerList);
    boolean answerContains = answerList.containsAll(queryList);
    if (!queryContains && !answerContains)
      return Integer.MAX_VALUE;
    if (queryContains) {
      return queryTokens.length - answerTokens.length;
    }
    return answerTokens.length - queryTokens.length;
  }

  private static boolean isEqualSet(String[] queryTokens, String[] answerTokens) {

    List<String> queryList = Arrays.asList(queryTokens);
    List<String> answerList = Arrays.asList(answerTokens);
    return queryList.containsAll(answerList) && answerList.containsAll(queryList);
  }

  public static Counter<String> extractTokenMatchFeatures(List<String> source, List<String> target, boolean strict) {

    if (opts.verbose >= 1) {
      LogInfo.log("SOURCE: " + source);
      LogInfo.log("TARGET: " + target);
    }

    Counter<String> res = new ClassicCounter<String>();
    for (int i = 0; i < source.size(); ++i) {
      for (int j = 0; j < target.size(); ++j) {

        if (target.get(j).length() <= 2) // do not match very short words
          continue;

        int matchLength = findLongestMatch(source, target, i, j, strict);
        double cover = (double) matchLength / target.size();
        if (opts.verbose >= 1) {
          if (cover > 0) {
            LogInfo.logs("Source index %s, target index %s, cover %s", i, j, cover);
          }
        }
        if (cover > 0) {
          if (cover > 0.9999) {
            res.setCount("equal", 1);
          } else if (j == 0 && res.getCount("prefix") < cover) {
            res.setCount("prefix", cover);
          } else if (j + matchLength == target.size() && res.getCount("suffix") < cover) {
            res.incrementCount("suffix", cover);
          } else if (j > 0 && j + matchLength < target.size() && res.getCount("overlap") < cover) {
            res.incrementCount("overlap", cover);
          }
        }
      }
    }
    return res;
  }

  private static int findLongestMatch(List<String> source, List<String> target,
                                      int i, int j, boolean strict) {

    int match = 0;

    for (int offset = 0; i + offset < source.size() && j + offset < target.size(); ++offset) {
      if (strict) {
        if (source.get(i + offset).equals(target.get(j + offset)))
          match++;
        else
          break;
      } else {
        if (source.get(i + offset).startsWith(target.get(j + offset)) || target.get(j + offset).startsWith(source.get(i + offset)))
          match++;
        else
          break;
      }
    }
    return match;
  }
}
