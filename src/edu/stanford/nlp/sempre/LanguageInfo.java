package edu.stanford.nlp.sempre;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.*;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import fig.basic.Option;

import java.util.*;

/**
  * Interface with Stanford CoreNLP to do basic things like POS tagging and NER.
  * @author akchou
  */
@JsonIgnoreProperties(ignoreUnknown=true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LanguageInfo {
  public static class Options {
    @Option(gloss="What CoreNLP annotators to run")
    public List<String> annotators = Lists.newArrayList("tokenize", "ssplit", "pos", "lemma", "ner");

    @Option(gloss="Whether to use CoreNLP annotators")
      public boolean useAnnotators = true;

    @Option(gloss="Whether to be case sensitive")
      public boolean caseSensitive = false;
  }

  public static Options opts = new Options();
  public static StanfordCoreNLP pipeline = null;

  // Tokenization of input.
  @JsonProperty
  public final List<String> tokens;
  @JsonProperty
  public final List<String> lemmaTokens;  // Lemmatized version

  // Syntactic information from JavaNLP.
  @JsonProperty
  public final List<String> posTags;  // POS tags
  @JsonProperty
  public final List<String> nerTags;  // NER tags
  @JsonProperty
  public final List<String> nerValues;  // NER values (contains times, dates, etc.)

  public LanguageInfo() {
    this(new ArrayList<String>(),
         new ArrayList<String>(),
         new ArrayList<String>(),
         new ArrayList<String>(),
         new ArrayList<String>());
  }

  @JsonCreator
  public LanguageInfo(@JsonProperty("tokens") List<String> tokens,
                      @JsonProperty("lemmaTokens") List<String> lemmaTokens,
                      @JsonProperty("posTags") List<String> posTags,
                      @JsonProperty("nerTags") List<String> nerTags,
                      @JsonProperty("nerValues") List<String> nerValues) {
    this.tokens = tokens;
    this.lemmaTokens = lemmaTokens;
    this.posTags = posTags;
    this.nerTags = nerTags;
    this.nerValues = nerValues;
  }

  // TODO: don't muck with the POS tag; instead have a separate flag for isContent which looks at posTag != "MD" && lemma != "be" && lemma != "have"
  // Need to update TextToTextMatcher
  private static final String[] AUX_VERB_ARR = new String[]{"is", "are", "was",
      "were", "am", "be", "been", "will", "shall", "have", "has", "had",
      "would", "could", "should", "do", "does", "did", "can", "may", "might",
      "must", "seem"};
  private static final Set<String> AUX_VERBS = new HashSet<String>(Arrays.asList(AUX_VERB_ARR));
  private static final String AUX_VERB_TAG = "VBD-AUX";

  public static void initModels() {
    if (pipeline != null) return;
    Properties props = new Properties();
    props.put("annotators", Joiner.on(',').join(opts.annotators));
    if (opts.caseSensitive) {
      props.put("pos.model", "edu/stanford/nlp/models/pos-tagger/english-bidirectional/english-bidirectional-distsim.tagger");
      props.put("ner.model", "edu/stanford/nlp/models/ner/english.all.3class.distsim.crf.ser.gz,edu/stanford/nlp/models/ner/english.conll.4class.distsim.crf.ser.gz");
    } else {
      props.put("pos.model", "edu/stanford/nlp/models/pos-tagger/english-caseless-left3words-distsim.tagger");
      props.put("ner.model", "edu/stanford/nlp/models/ner/english.all.3class.caseless.distsim.crf.ser.gz,edu/stanford/nlp/models/ner/english.conll.4class.caseless.distsim.crf.ser.gz");
    }
    pipeline = new StanfordCoreNLP(props);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LanguageInfo that = (LanguageInfo) o;

    if (!lemmaTokens.equals(that.lemmaTokens)) return false;
    if (!nerTags.equals(that.nerTags)) return false;
    if (!posTags.equals(that.posTags)) return false;
    if (!tokens.equals(that.tokens)) return false;

    return true;
  }

  // Return a string representing the tokens between start and end.
  public String phrase(int start, int end) {
    return sliceSequence(tokens, start, end);
  }
  public String lemmaPhrase(int start, int end) {
    return sliceSequence(lemmaTokens, start, end);
  }

  private static String sliceSequence(List<String> items,
                                      int start,
                                      int end) {
    if (start >= end) throw new RuntimeException("Bad indices");
    if (end - start == 1) return items.get(start);
    StringBuilder out = new StringBuilder();
    for (int i = start; i < end; i++) {
      if (out.length() > 0) out.append(' ');
      out.append(items.get(i));
    }
    return out.toString();
  }

  public void analyze(String utterance) {
    // Stanford tokenizer doesn't break hyphens.
    // Replace hypens with spaces for utterances like
    // "Spanish-speaking countries" but not for "2012-03-28".
    StringBuilder buf = new StringBuilder(utterance);
    for (int i = 0; i < buf.length(); i++) {
      if (buf.charAt(i) == '-' && (i+1 < buf.length() && Character.isLetter(buf.charAt(i+1))))
        buf.setCharAt(i, ' ');
    }
    utterance = buf.toString();

    // Clear these so that analyze can hypothetically be called
    // multiple times.
    tokens.clear();
    posTags.clear();
    nerTags.clear();
    nerValues.clear();
    lemmaTokens.clear();

    if (opts.useAnnotators) {
      initModels();
      Annotation annotation = pipeline.process(utterance);

      for (CoreLabel token : annotation.get(CoreAnnotations.TokensAnnotation.class)) {
        String word = token.get(TextAnnotation.class);
        String wordLower = word.toLowerCase();
        if (opts.caseSensitive) {
          tokens.add(word);
        } else {
          tokens.add(wordLower);
        }
        posTags.add(
            AUX_VERBS.contains(wordLower) ?
            AUX_VERB_TAG :
            token.get(PartOfSpeechAnnotation.class));
        nerTags.add(token.get(NamedEntityTagAnnotation.class));
        lemmaTokens.add(token.get(LemmaAnnotation.class));
        nerValues.add(token.get(NormalizedNamedEntityTagAnnotation.class));
      }
    } else {
      // Create tokens crudely
      for (String token : utterance.trim().split("\\s+")) {
        tokens.add(token);
        lemmaTokens.add(token);
        try {
          Double.parseDouble(token);
          posTags.add("CD");
          nerTags.add("NUMBER");
          nerValues.add(token);
        } catch (NumberFormatException e ){
          posTags.add("UNK");
          nerTags.add("UNK");
          nerValues.add("UNK");
        }
      }
    }
  }

  // If all the tokens in [start, end) have the same nerValues, but not
  // start-1 and end+1 (in other words, [start, end) is maximal), then return
  // the normalizedTag.  Example: queryNerTag = "DATE".
  public String getNormalizedNerSpan(String queryTag, int start, int end) {
    String value = nerValues.get(start);
    if (!queryTag.equals(nerTags.get(start))) return null;
    if (start-1 >= 0 && value.equals(nerValues.get(start-1))) return null;
    if (end < nerValues.size() && value.equals(nerValues.get(end))) return null;
    for (int i = start+1; i < end; i++)
      if (!value.equals(nerValues.get(i))) return null;
    return value;
  }

  public String getCanonicalPos(int index) {
    return getCanonicalPos(posTags.get(index));
  }

  private String getCanonicalPos(String pos) {
    if (pos.startsWith("N")) return "N";
    if (pos.startsWith("V")) return "V";
    if (pos.startsWith("W")) return "W";
    return pos;
  }
}
