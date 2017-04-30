package edu.stanford.nlp.sempre.tables.features;

import java.util.concurrent.ExecutionException;

import com.google.common.cache.*;

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
    return "Q=" + questionWord + ",H=" + headword;
  }

  public String questionWordTuple() {
    return "Q=" + questionWord;
  }

  public String headwordTuple() {
    return "H=" + headword;
  }

  // Caching
  private static final LoadingCache<Example, HeadwordInfo> cache = CacheBuilder
      .newBuilder().maximumSize(20)
      .build(new CacheLoader<Example, HeadwordInfo>() {
        @Override
        public HeadwordInfo load(Example ex) throws Exception {
          LanguageInfo langInfo = ex.languageInfo;
          String questionWord = "", headWord = "";
          for (int i = 0; i < langInfo.numTokens(); i++) {
            String token = langInfo.lemmaTokens.get(i), posTag = langInfo.posTags.get(i);
            if (posTag.startsWith("W")) {
              if ("who".equals(token) || "where".equals(token) || "when".equals(token)) {
                // These are treated as head words
                headWord = token;
                //LogInfo.logs("HEADWORD: %s => %s | %s", ex.utterance, questionWord, headWord);
                return new HeadwordInfo(questionWord.trim(), headWord.trim());
              }
              questionWord += " " + token;
              if (token.equals("how")) {
                // Possibly "how many", "how much", ...
                if (i + 1 < langInfo.numTokens() && langInfo.posTags.get(i + 1).startsWith("J"))
                  questionWord += " " + langInfo.lemmaTokens.get(i + 1);
              }
            } else if (posTag.startsWith("N") && !questionWord.isEmpty()) {
              if ("number".equals(token)) {
                questionWord += " " + token;
              } else {
                headWord += " " + token;
                while (i + 1 < langInfo.numTokens() && langInfo.posTags.get(i + 1).startsWith("N")) {
                  i++;
                  headWord += " " + langInfo.lemmaTokens.get(i);
                }
                //LogInfo.logs("HEADWORD: %s => %s | %s", ex.utterance, questionWord, headWord);
                return new HeadwordInfo(questionWord.trim(), headWord.trim());
              }
            }
          }
          //LogInfo.logs("HEADWORD: %s => NULL", ex.utterance);
          return new HeadwordInfo("", "");
        }
      });

  public static HeadwordInfo getHeadwordInfo(Example ex) {
    try {
      return cache.get(ex);
    } catch (ExecutionException e) {
      throw new RuntimeException(e.getCause());
    }
  }

}
