package edu.stanford.nlp.sempre.corenlp;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.LanguageInfo.DependencyEdge;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.CoreAnnotations.*;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;

import com.google.common.collect.Lists;
import com.google.common.base.Joiner;

import fig.basic.*;

import java.io.*;
import java.util.*;

/**
 * CoreNLPAnalyzer uses Stanford CoreNLP pipeline to analyze an input string utterance
 * and return a LanguageInfo object
 *
 * @author akchou
 */
public class CoreNLPAnalyzer extends LanguageAnalyzer {
  public static class Options {
    @Option(gloss = "What CoreNLP annotators to run")
    public List<String> annotators = Lists.newArrayList("tokenize", "ssplit", "pos", "lemma", "ner", "parse");

    @Option(gloss = "Whether to use case-sensitive models")
    public boolean caseSensitive = false;
  }

  public static Options opts = new Options();

  // TODO(pliang): don't muck with the POS tag; instead have a separate flag
  // for isContent which looks at posTag != "MD" && lemma != "be" && lemma !=
  // "have"
  // Need to update TextToTextMatcher
  private static final String[] AUX_VERB_ARR = new String[] {"is", "are", "was",
    "were", "am", "be", "been", "will", "shall", "have", "has", "had",
    "would", "could", "should", "do", "does", "did", "can", "may", "might",
    "must", "seem"};
  private static final Set<String> AUX_VERBS = new HashSet<String>(Arrays.asList(AUX_VERB_ARR));
  private static final String AUX_VERB_TAG = "VBD-AUX";

  public static StanfordCoreNLP pipeline = null;

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

  public LanguageInfo analyze(String utterance) {
    LanguageInfo languageInfo = new LanguageInfo();

    // Clear these so that analyze can hypothetically be called
    // multiple times.
    languageInfo.tokens.clear();
    languageInfo.posTags.clear();
    languageInfo.nerTags.clear();
    languageInfo.nerValues.clear();
    languageInfo.lemmaTokens.clear();
    languageInfo.dependencyChildren.clear();

    // Break hyphens
    utterance = breakHyphens(utterance);

    // Run Stanford CoreNLP
    initModels();
    Annotation annotation = pipeline.process(utterance);

    for (CoreLabel token : annotation.get(CoreAnnotations.TokensAnnotation.class)) {
      String word = token.get(TextAnnotation.class);
      String wordLower = word.toLowerCase();
      if (LanguageAnalyzer.opts.lowerCaseTokens) {
        languageInfo.tokens.add(wordLower);
      } else {
        languageInfo.tokens.add(word);
      }
      languageInfo.posTags.add(
          AUX_VERBS.contains(wordLower) ?
              AUX_VERB_TAG :
                token.get(PartOfSpeechAnnotation.class));
      languageInfo.nerTags.add(token.get(NamedEntityTagAnnotation.class));
      languageInfo.lemmaTokens.add(token.get(LemmaAnnotation.class));
      languageInfo.nerValues.add(token.get(NormalizedNamedEntityTagAnnotation.class));
    }

    // Fills in a stanford dependency graph for constructing a feature
    for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
      SemanticGraph ccDeps = sentence.get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
      if (ccDeps == null) continue;
      int sentenceBegin = sentence.get(CoreAnnotations.TokenBeginAnnotation.class);

      // Iterate over all tokens and their dependencies
      for (int sourceTokenIndex = sentenceBegin;
           sourceTokenIndex < sentence.get(CoreAnnotations.TokenEndAnnotation.class);
           sourceTokenIndex++) {
        final ArrayList<DependencyEdge> outgoing = new ArrayList<DependencyEdge>();
        languageInfo.dependencyChildren.add(outgoing);
        IndexedWord node = ccDeps.getNodeByIndexSafe(sourceTokenIndex - sentenceBegin + 1);  // + 1 for ROOT
        if (node != null) {
          for (SemanticGraphEdge edge : ccDeps.outgoingEdgeList(node)) {
            final String relation = edge.getRelation().toString();
            final int targetTokenIndex = sentenceBegin + edge.getTarget().index() - 1;
            outgoing.add(new DependencyEdge(relation, targetTokenIndex));
          }
        }
      }
    }
    return languageInfo;
  }

  // Test on example sentence.
  public static void main(String[] args) {
    CoreNLPAnalyzer analyzer = new CoreNLPAnalyzer();
    while (true) {
      try {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Enter some text:");
        String text = reader.readLine();
        LanguageInfo langInfo = analyzer.analyze(text);
        LogInfo.begin_track("Analyzing \"%s\"", text);
        LogInfo.logs("tokens: %s", langInfo.tokens);
        LogInfo.logs("lemmaTokens: %s", langInfo.lemmaTokens);
        LogInfo.logs("posTags: %s", langInfo.posTags);
        LogInfo.logs("nerTags: %s", langInfo.nerTags);
        LogInfo.logs("nerValues: %s", langInfo.nerValues);
        LogInfo.logs("dependencyChildren: %s", langInfo.dependencyChildren);
        LogInfo.end_track();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}
