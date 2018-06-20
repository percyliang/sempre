package edu.stanford.nlp.sempre;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;
import fig.basic.IntPair;
import fig.basic.LispTree;
import fig.basic.MemUsage;

import java.util.*;

/**
 * Represents an linguistic analysis of a sentence (provided by some LanguageAnalyzer).
 *
 * @author akchou
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LanguageInfo implements MemUsage.Instrumented {

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

  private Map<String, IntPair> lemmaSpans;
  private Set<String> lowercasedSpans;



  public static class DependencyEdge {
    @JsonProperty
    public final String label;  // Dependency label
    @JsonProperty
    public final int modifier;  // Position of modifier

    @JsonCreator
    public DependencyEdge(@JsonProperty("label") String label, @JsonProperty("modifier") int modifier) {
      this.label = label;
      this.modifier = modifier;
    }

    @Override
    public String toString() {
      return label + "->" + modifier;
    }
  }

  @JsonProperty
  // Dependencies of each token, represented as a (relation, parentIndex) pair
  public final List<List<DependencyEdge>> dependencyChildren;

  public LanguageInfo() {
    this(new ArrayList<String>(),
        new ArrayList<String>(),
        new ArrayList<String>(),
        new ArrayList<String>(),
        new ArrayList<String>(),
        new ArrayList<List<DependencyEdge>>());
  }

  @JsonCreator
  public LanguageInfo(@JsonProperty("tokens") List<String> tokens,
      @JsonProperty("lemmaTokens") List<String> lemmaTokens,
      @JsonProperty("posTags") List<String> posTags,
      @JsonProperty("nerTags") List<String> nerTags,
      @JsonProperty("nerValues") List<String> nerValues,
      @JsonProperty("dependencyChildren") List<List<DependencyEdge>> dependencyChildren) {
    this.tokens = tokens;
    this.lemmaTokens = lemmaTokens;
    this.posTags = posTags;
    this.nerTags = nerTags;
    this.nerValues = nerValues;
    this.dependencyChildren = dependencyChildren;
  }

  // Return a string representing the tokens between start and end.
  public String phrase(int start, int end) {
    return sliceSequence(tokens, start, end);
  }
  public String lemmaPhrase(int start, int end) {
    return sliceSequence(lemmaTokens, start, end);
  }
  public String posSeq(int start, int end) {
    return sliceSequence(posTags, start, end);
  }

  public String canonicalPosSeq(int start, int end) {
    if (start >= end) throw new RuntimeException("Bad indices, start=" + start + ", end=" + end);
    if (end - start == 1) return LanguageUtils.getCanonicalPos(posTags.get(start));
    StringBuilder out = new StringBuilder();
    for (int i = start; i < end; i++) {
      if (out.length() > 0) out.append(' ');
      out.append(LanguageUtils.getCanonicalPos(posTags.get(i)));
    }
    return out.toString();
  }
  public String nerSeq(int start, int end) {
    return sliceSequence(nerTags, start, end);
  }

  private static String sliceSequence(List<String> items,
      int start,
      int end) {
    if (start >= end) throw new RuntimeException("Bad indices, start=" + start + ", end=" + end);
    if (end - start == 1) return items.get(start);
    StringBuilder out = new StringBuilder();
    for (int i = start; i < end; i++) {
      if (out.length() > 0) out.append(' ');
      out.append(items.get(i));
    }
    return out.toString();
  }

  // If all the tokens in [start, end) have the same nerValues, but not
  // start - 1 and end + 1 (in other words, [start, end) is maximal), then return
  // the normalizedTag.  Example: queryNerTag = "DATE".
  public String getNormalizedNerSpan(String queryTag, int start, int end) {
    String value = nerValues.get(start);
    if (value == null) return null;
    if (!queryTag.equals(nerTags.get(start))) return null;
    if (start - 1 >= 0 && value.equals(nerValues.get(start - 1))) return null;
    if (end < nerValues.size() && value.equals(nerValues.get(end))) return null;
    for (int i = start + 1; i < end; i++)
      if (!value.equals(nerValues.get(i))) return null;
    value = omitComparative(value);
    return value;
  }

  private String omitComparative(String value) {
    if (value.startsWith("<=") || value.startsWith(">="))
      return value.substring(2);
    if (value.startsWith("<") || value.startsWith(">"))
      return value.substring(1);
    return value;
  }

  public String getCanonicalPos(int index) {
    if (index == -1) return "OUT";
    return LanguageUtils.getCanonicalPos(posTags.get(index));
  }

  public boolean equalTokens(LanguageInfo other) {
    if (tokens.size() != other.tokens.size())
      return false;
    for (int i = 0; i < tokens.size(); ++i) {
      if (!tokens.get(i).equals(other.tokens.get(i)))
        return false;
    }
    return true;
  }

  public boolean equalLemmas(LanguageInfo other) {
    if (lemmaTokens.size() != other.lemmaTokens.size())
      return false;
    for (int i = 0; i < tokens.size(); ++i) {
      if (!lemmaTokens.get(i).equals(other.lemmaTokens.get(i)))
        return false;
    }
    return true;
  }

  public int numTokens() {
    return tokens.size();
  }

  public LanguageInfo remove(int startIndex, int endIndex) {

    if (startIndex > endIndex || startIndex < 0 || endIndex > numTokens())
      throw new RuntimeException("Illegal start or end index, start: " + startIndex + ", end: " + endIndex + ", info size: " + numTokens());

    LanguageInfo res = new LanguageInfo();
    for (int i = 0; i < numTokens(); ++i) {
      if (i < startIndex || i >= endIndex) {
        res.tokens.add(this.tokens.get(i));
        res.lemmaTokens.add(this.lemmaTokens.get(i));
        res.nerTags.add(this.nerTags.get(i));
        res.nerValues.add(this.nerValues.get(i));
        res.posTags.add(this.posTags.get(i));
      }
    }
    return res;
  }

  public void addSpan(LanguageInfo other, int start, int end) {
    for (int i = start; i < end; ++i) {
      this.tokens.add(other.tokens.get(i));
      this.lemmaTokens.add(other.lemmaTokens.get(i));
      this.posTags.add(other.posTags.get(i));
      this.nerTags.add(other.nerTags.get(i));
      this.nerValues.add(other.nerValues.get(i));
    }
  }

  public List<String> getSpanProperties(int start, int end) {
    List<String> res = new ArrayList<String>();
    res.add("lemmas=" + lemmaPhrase(start, end));
    res.add("pos=" + posSeq(start, end));
    res.add("ner=" + nerSeq(start, end));
    return res;
  }

  public void  addWordInfo(WordInfo wordInfo) {
    this.tokens.add(wordInfo.token);
    this.lemmaTokens.add(wordInfo.lemma);
    this.posTags.add(wordInfo.pos);
    this.nerTags.add(wordInfo.nerTag);
    this.nerValues.add(wordInfo.nerValue);
  }

  public void  addWordInfos(List<WordInfo> wordInfos) {
    for (WordInfo wInfo : wordInfos)
      addWordInfo(wInfo);
  }

  public WordInfo getWordInfo(int i) {
    return new WordInfo(tokens.get(i), lemmaTokens.get(i), posTags.get(i), nerTags.get(i), nerValues.get(i));
  }

  /**
   * returns spans of named entities
   * @return
   */
  public Set<IntPair> getNamedEntitySpans() {
    Set<IntPair> res = new LinkedHashSet<IntPair>();
    int start = -1;
    String prevTag = "O";

    for (int i = 0; i < nerTags.size(); ++i) {
      String currTag = nerTags.get(i);
      if (currTag.equals("O")) {
        if (!prevTag.equals("O")) {
          res.add(new IntPair(start, i));
          start = -1;
        }
      } else { // currNe is not "O"
        if (!currTag.equals(prevTag)) {
          if (!prevTag.equals("O")) {
            res.add(new IntPair(start, i));
          }
          start = i;
        }
      }
      prevTag = currTag;
    }
    if (start != -1)
      res.add(new IntPair(start, nerTags.size()));
    return res;
  }

  /**
   * returns spans of named entities
   * @return
   */
  public Set<IntPair> getProperNounSpans() {
    Set<IntPair> res = new LinkedHashSet<IntPair>();
    int start = -1;
    String prevTag = "O";

    for (int i = 0; i < posTags.size(); ++i) {
      String currTag = posTags.get(i);
      if (LanguageUtils.isProperNoun(currTag)) {
        if (!LanguageUtils.isProperNoun(prevTag))
          start = i;
      } else { // curr tag is not proper noun
        if (LanguageUtils.isProperNoun(prevTag)) {
          res.add(new IntPair(start, i));
          start = -1;
        }
      }
      prevTag = currTag;
    }
    if (start != -1)
      res.add(new IntPair(start, posTags.size()));
    return res;
  }

  public Set<IntPair> getNamedEntitiesAndProperNouns() {
    Set<IntPair> res = getNamedEntitySpans();
    res.addAll(getProperNounSpans());
    return res;
  }

  public Map<String, IntPair> getLemmaSpans() {
    if (lemmaSpans == null) {
      lemmaSpans = new HashMap<>();
      for (int i = 0; i < numTokens() - 1; ++i) {
        for (int j = i + 1; j < numTokens(); ++j)
          lemmaSpans.put(lemmaPhrase(i, j), new IntPair(i, j));
      }
    }
    return lemmaSpans;
  }

  public Set<String> getLowerCasedSpans() {
    if (lowercasedSpans == null) {
      lowercasedSpans = new HashSet<>();
      for (int i = 0; i < numTokens() - 1; ++i) {
        for (int j = i + 1; j < numTokens(); ++j)
          lowercasedSpans.add(phrase(i, j).toLowerCase());
      }
    }
    return lowercasedSpans;
  }

  public boolean matchLemmas(List<WordInfo> wordInfos) {
    for (int i = 0; i < numTokens(); ++i) {
      if (matchLemmasFromIndex(wordInfos, i))
        return true;
    }
    return false;
  }

  private boolean matchLemmasFromIndex(List<WordInfo> wordInfos, int start) {
    if (start + wordInfos.size() > numTokens())
      return false;
    for (int j = 0; j < wordInfos.size(); ++j) {
      if (!wordInfos.get(j).lemma.equals(lemmaTokens.get(start + j)))
        return false;
    }
    return true;
  }

  /**
   * Static methods with langauge utilities
   * @author jonathanberant
   *
   */
  public static class LanguageUtils {

    public static boolean sameProperNounClass(String noun1, String noun2) {
      if ((noun1.equals("NNP") || noun1.equals("NNPS")) &&
          (noun2.equals("NNP") || noun2.equals("NNPS")))
        return true;
      return false;
    }

    public static boolean isProperNoun(String pos) {
      return pos.startsWith("NNP");
    }

    public static boolean isSuperlative(String pos) { return pos.equals("RBS") || pos.equals("JJS"); }
    public static boolean isComparative(String pos) { return pos.equals("RBR") || pos.equals("JJR"); }


    public static boolean isEntity(LanguageInfo info, int i) {
      return isProperNoun(info.posTags.get(i)) || !(info.nerTags.get(i).equals("O"));
    }

    public static boolean isNN(String pos) {
      return pos.startsWith("NN") && !pos.startsWith("NNP");
    }

    public static boolean isContentWord(String pos) {
      return (pos.startsWith("N") || pos.startsWith("V") || pos.startsWith("J"));
    }

    public static String getLemmaPhrase(List<WordInfo> wordInfos) {
      String[] res = new String[wordInfos.size()];
      for (int i = 0; i < wordInfos.size(); ++i) {
        res[i] = wordInfos.get(i).lemma;
      }
      return Joiner.on(' ').join(res);
    }

    public static String getCanonicalPos(String pos) {
      if (pos.startsWith("N")) return "N";
      if (pos.startsWith("V")) return "V";
      if (pos.startsWith("W")) return "W";
      return pos;
    }

    // Uses a few rules to stem tokens
    public static String stem(String a) {
      int i = a.indexOf(' ');
      if (i != -1)
        return stem(a.substring(0, i)) + ' ' + stem(a.substring(i + 1));
      //Maybe we should just use the Stanford stemmer
      String res = a;
      //hard coded words
      if (a.equals("having") || a.equals("has")) res = "have";
      else if (a.equals("using")) res =  "use";
      else if (a.equals("including")) res =  "include";
      else if (a.equals("beginning")) res = "begin";
      else if (a.equals("utilizing")) res = "utilize";
      else if (a.equals("featuring")) res =  "feature";
      else if (a.equals("preceding")) res =  "precede";
      //rules
      else if (a.endsWith("ing")) res =  a.substring(0, a.length() - 3);
      else if (a.endsWith("s") && !a.equals("'s")) res =  a.substring(0, a.length() - 1);
      //don't return an empty string
      if (res.length() > 0) return res;
      return a;
    }

  }

  @Override
  public long getBytes() {
    return MemUsage.objectSize(MemUsage.pointerSize * 2) + MemUsage.getBytes(tokens) + MemUsage.getBytes(lemmaTokens)
        + MemUsage.getBytes(posTags) + MemUsage.getBytes(nerTags) + MemUsage.getBytes(nerValues)
        + MemUsage.getBytes(lemmaSpans);
  }

  public boolean isNumberAndDate(int index) {
    return posTags.get(index).equals("CD") && nerTags.get(index).equals("DATE");
  }

  public static boolean isContentWord(String pos) {
    return pos.equals("NN") || pos.equals("NNS") ||
            (pos.startsWith("V") && !pos.equals("VBD-AUX")) ||
            pos.startsWith("J");
  }

  public static class WordInfo {
    public final String token;
    public final String lemma;
    public final String pos;
    public final String nerTag;
    public final String nerValue;
    public WordInfo(String token, String lemma, String pos, String nerTag, String nerValue) {
      this.token = token; this.lemma = lemma; this.pos = pos; this.nerTag = nerTag; this.nerValue = nerValue;
    }

    public String toString() {
      return toLispTree().toString();
    }
    public LispTree toLispTree() {
      LispTree tree = LispTree.proto.newList();
      tree.addChild("wordinfo");
      tree.addChild(token);
      tree.addChild(lemma);
      tree.addChild(pos);
      tree.addChild(nerTag);
      return tree;
    }
  }
}
