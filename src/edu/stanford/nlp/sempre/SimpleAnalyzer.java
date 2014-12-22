package edu.stanford.nlp.sempre;

import java.util.Arrays;

/**
 * SimpleAnalyzer takes an utterance and applies simple methods to pre-process
 *
 * @author akchou
 */
public class SimpleAnalyzer extends LanguageAnalyzer {

  // Stanford tokenizer doesn't break hyphens.
  // Replace hypens with spaces for utterances like
  // "Spanish-speaking countries" but not for "2012-03-28".
  public static String breakHyphens(String utterance) {
    StringBuilder buf = new StringBuilder(utterance);
    for (int i = 0; i < buf.length(); i++) {
      if (buf.charAt(i) == '-' && (i + 1 < buf.length() && Character.isLetter(buf.charAt(i + 1))))
        buf.setCharAt(i, ' ');
    }
    return buf.toString();
  }

  private static final String[] numbers = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"};

  public LanguageInfo analyze(String utterance) {
    LanguageInfo languageInfo = new LanguageInfo();

    // Clear these so that analyze can hypothetically be called
    // multiple times.
    languageInfo.tokens.clear();
    languageInfo.posTags.clear();
    languageInfo.nerTags.clear();
    languageInfo.nerValues.clear();
    languageInfo.lemmaTokens.clear();

    // Break hyphens
    utterance = breakHyphens(utterance);

    // Default analysis - create tokens crudely
    utterance = utterance.replaceAll(",", " ,");
    utterance = utterance.replaceAll("\\.", " .");
    utterance = utterance.replaceAll("\\?", " ?");
    utterance = utterance.replaceAll("'", " '");
    utterance = utterance.replaceAll("\\[", " [ ");
    utterance = utterance.replaceAll("\\]", " ] ");
    utterance = utterance.trim();
    if (!utterance.equals("")) {
      for (String token : utterance.split("\\s+")) {
        languageInfo.tokens.add(LanguageAnalyzer.opts.lowerCaseTokens ? token.toLowerCase() : token);
        String lemma = token;
        if (token.endsWith("s"))
          lemma = token.substring(0, token.length() - 1);
        languageInfo.lemmaTokens.add(LanguageAnalyzer.opts.lowerCaseTokens ? lemma.toLowerCase() : lemma);

        // Is it a written out number?
        int x = Arrays.asList(numbers).indexOf(token);
        if (x != -1) {
          languageInfo.posTags.add("CD");
          languageInfo.nerTags.add("NUMBER");
          languageInfo.nerValues.add(x + "");
          continue;
        }

        try {
          Double.parseDouble(token);
          languageInfo.posTags.add("CD");
          if (token.length() == 4)
            languageInfo.nerTags.add("DATE");
          else
            languageInfo.nerTags.add("NUMBER");
          languageInfo.nerValues.add(token);
        } catch (NumberFormatException e) {
          // Guess that capitalized nouns are proper
          if (Character.isUpperCase(token.charAt(0)))
            languageInfo.posTags.add("NNP");
          else
            languageInfo.posTags.add("UNK");
          languageInfo.nerTags.add("UNK");
          languageInfo.nerValues.add("UNK");
        }
      }
    }
    return languageInfo;
  }
}
