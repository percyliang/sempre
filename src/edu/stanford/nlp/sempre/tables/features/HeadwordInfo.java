package edu.stanford.nlp.sempre.tables.features;

import edu.stanford.nlp.sempre.*;

/**
 * Information about the headword of the utterance.
 *
 * Examples:
 * - Which person is the fastest? ==> (which, person)
 * - Who is the fastest person? ==> (who, person)
 * - How many cars are red? ==> (how many, red)
 * - Who is the fastest? ==> [skipped]
 *
 * Currently a simple heuristic is used to find the headword.
 * @author ppasupat
 *
 */
public class HeadwordInfo {

  public final String questionWord;
  public final String headword;

  public HeadwordInfo(String questionWord, String headword) {
    this.questionWord = questionWord;
    this.headword = headword;
  }

  public String toString() {
    return "(" + questionWord + "," + headword + ")";
  }

  public String questionWordTuple() {
    return "(" + questionWord + ",*)";
  }

  public String headwordTuple() {
    return "(*," + headword + ")";
  }

  // Heuristics: find the first N* after the first W*
  // Example: tell me [who] is the first [person] ... --> person
  public static HeadwordInfo analyze(LanguageInfo langInfo) {
    String questionWord = null;
    for (int i = 0; i < langInfo.numTokens(); i++) {
      String posTag = langInfo.posTags.get(i);
      if (posTag.startsWith("W")) {
        questionWord = langInfo.lemmaTokens.get(i);
        if (questionWord.equals("how")) {
          // Possibly "how many", "how much", ...
          if (i + 1 < langInfo.numTokens() && langInfo.posTags.get(i + 1).startsWith("J"))
            questionWord += " " + langInfo.lemmaTokens.get(i + 1);
        }
      } else if (posTag.startsWith("N") && questionWord != null) {
        return new HeadwordInfo(questionWord, langInfo.lemmaTokens.get(i));
      }
    }
    return null;
  }

}
