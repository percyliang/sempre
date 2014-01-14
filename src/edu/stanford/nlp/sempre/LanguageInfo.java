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
//import edu.stanford.nlp.sempre.transformations.TransUtils;
import fig.basic.IntPair;
import fig.basic.LispTree;
import fig.basic.Option;
import fig.basic.MemUsage;

import java.util.*;

/**
 * Interface with Stanford CoreNLP to do basic things like POS tagging and NER.
 * @author akchou
 */
@JsonIgnoreProperties(ignoreUnknown=true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LanguageInfo implements MemUsage.Instrumented {
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

  private Map<String,IntPair> lemmaSpans;

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
  public String posSeq(int start, int end) {
    return sliceSequence(posTags, start, end);
  }
  
  public String canonicalPosSeq(int start, int end) {
    if (start >= end) throw new RuntimeException("Bad indices, start="+start+", end="+end);
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
    if (start >= end) throw new RuntimeException("Bad indices, start="+start+", end="+end);
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
    return LanguageUtils.getCanonicalPos(posTags.get(index));
  }

  public boolean equalTokens(LanguageInfo other) {
    if(tokens.size()!=other.tokens.size())
      return false;
    for(int i = 0; i < tokens.size(); ++i) {
      if(!tokens.get(i).equals(other.tokens.get(i)))
        return false;
    }
    return true;
  }

  public boolean equalLemmas(LanguageInfo other) {
    if(lemmaTokens.size()!=other.lemmaTokens.size())
      return false;
    for(int i = 0; i < tokens.size(); ++i) {
      if(!lemmaTokens.get(i).equals(other.lemmaTokens.get(i)))
        return false;
    }
    return true;
  }

  public int numTokens() {
    return tokens.size();
  }

  public LanguageInfo remove(int startIndex, int endIndex) {

    if(startIndex > endIndex || startIndex<0 || endIndex > numTokens())
      throw new RuntimeException("Illegal start or end index, start: " + startIndex + ", end: " + endIndex+", info size: " + numTokens());

    LanguageInfo res = new LanguageInfo();
    for(int i = 0; i < numTokens(); ++i) {
      if(i<startIndex || i>=endIndex) {
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
    for(int i = start; i < end; ++i) {
      this.tokens.add(other.tokens.get(i));
      this.lemmaTokens.add(other.lemmaTokens.get(i));
      this.posTags.add(other.posTags.get(i));
      this.nerTags.add(other.nerTags.get(i));
      this.nerValues.add(other.nerValues.get(i));
    }
  }

  public List<String> getSpanProperties(int start, int end) { 
    List<String> res =new ArrayList<String>();
    res.add("lemmas="+lemmaPhrase(start, end));
    res.add("pos="+posSeq(start, end));
    res.add("ner="+nerSeq(start, end));
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
    for(WordInfo wInfo: wordInfos)
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
    int start=-1;
    String prevTag = "O";

    for(int i = 0; i < nerTags.size(); ++i) {
      String currTag = nerTags.get(i);
      if(currTag.equals("O")) {
        if(!prevTag.equals("O")) {
          res.add(new IntPair(start,i));
          start=-1;
        }
      }
      else { //currNe is not "O"
        if(!currTag.equals(prevTag)) {
          if(!prevTag.equals("O")) {
            res.add(new IntPair(start,i));
          }
          start=i;
        }
      }
      prevTag = currTag;
    }
    if(start!=-1)
      res.add(new IntPair(start,nerTags.size()));
    return res;
  }

  /**
   * returns spans of named entities
   * @return
   */
  public Set<IntPair> getProperNounSpans() {
    Set<IntPair> res = new LinkedHashSet<IntPair>();
    int start=-1;
    String prevTag = "O";

    for(int i = 0; i < posTags.size(); ++i) {
      String currTag = posTags.get(i);
      if(LanguageUtils.isProperNoun(currTag)) {
        if(!LanguageUtils.isProperNoun(prevTag))
          start=i;
      }
      else { //curr tag is not proper noun
        if(LanguageUtils.isProperNoun(prevTag)) {
          res.add(new IntPair(start,i));
          start=-1;
        }
      }
      prevTag = currTag;
    }
    if(start!=-1)
      res.add(new IntPair(start,posTags.size()));
    return res;
  }

  public Map<String,IntPair> getLemmaSpans() {
    if(lemmaSpans==null) {
      lemmaSpans = new HashMap<String,IntPair>();
      for(int i = 0; i < numTokens()-1; ++i) {
        for(int j = i+1; j < numTokens(); ++j)
          lemmaSpans.put(lemmaPhrase(i, j),new IntPair(i,j));
      }
    }
    return lemmaSpans;
  }
  
  public boolean matchLemmas(List<WordInfo> wordInfos) {
    for(int i = 0; i < numTokens(); ++i) {
      if(matchLemmasFromIndex(wordInfos,i))
        return true;
    }
    return false;
  }

  private boolean matchLemmasFromIndex(List<WordInfo> wordInfos, int start) {

    if(start+wordInfos.size()>numTokens())
      return false;
    for(int j = 0; j < wordInfos.size();++j) {
      if(!wordInfos.get(j).lemma.equals(lemmaTokens.get(start+j)))
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
      if(( noun1.equals("NNP")|| noun1.equals("NNPS")) &&
          ( noun2.equals("NNP")|| noun2.equals("NNPS")))
        return true;
      return false;
    }

    public static boolean isProperNoun(String pos) {
      return pos.startsWith("NNP");
    }

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
      for(int i = 0; i < wordInfos.size(); ++i) {
        res[i]=wordInfos.get(i).lemma;
      }
      return Joiner.on(' ').join(res);
    }

    public static String getCanonicalPos(String pos) {
      if (pos.startsWith("N")) return "N";
      if (pos.startsWith("V")) return "V";
      if (pos.startsWith("W")) return "W";
      return pos;
    }
  }

  @Override
  public long getBytes() {
    return MemUsage.objectSize(MemUsage.pointerSize*2)+MemUsage.getBytes(tokens)+MemUsage.getBytes(lemmaTokens)
        +MemUsage.getBytes(posTags)+MemUsage.getBytes(nerTags)+MemUsage.getBytes(nerValues)
        +MemUsage.getBytes(lemmaSpans);
  }

  public static class WordInfo {
    public final String token;
    public final String lemma;
    public final String pos;
    public final String nerTag;
    public final String nerValue;
    public WordInfo(String token, String lemma, String pos, String nerTag, String nerValue) {
      this.token = token; this.lemma=lemma; this.pos = pos; this.nerTag=nerTag; this.nerValue=nerValue;
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
