package edu.stanford.nlp.sempre.freebase.utils;

import fig.basic.LogInfo;
import fig.basic.Option;

import edu.stanford.nlp.sempre.freebase.utils.WordNet.*;

import java.io.File;
import java.io.IOException;
import java.util.*;


public class WnExpander {

  public static class Options {
    @Option(gloss = "Verbose") public int verbose = 0;
    @Option(gloss = "Path to Wordnet file")
    public String wnFile = "lib/wordnet-3.0-prolog";
    @Option(gloss = "Relations to expand with wordnet")
    public Set<String> wnRelations = new HashSet<>();
  }

  public static Options opts = new Options();

  private WordNet wn;
  private Set<EdgeType> edgeTypes = new HashSet<>();

  /**
   * Initializing wordnet and the relations to expand with
   *
   * @throws IOException
   */
  public WnExpander() throws IOException {
    wn = WordNet.loadPrologWordNet(new File(opts.wnFile));
    for (String wnRelation : opts.wnRelations) {
      switch (wnRelation) {
        case "derives":
          edgeTypes.add(EdgeType.DERIVES);
          break;
        case "derived_from":
          edgeTypes.add(EdgeType.DERIVED_FROM);
          break;
        case "hyponym":
          edgeTypes.add(EdgeType.HYPONYM);
          break;
        default:
          throw new RuntimeException("Invalid relation: " + wnRelation);
      }
    }
  }

  public Set<String> expandPhrase(String phrase) {

    // find synsetse for phrase
    Set<WordNetID> phraseSynsets = phraseToSynsets(phrase);
    // expand synsets
    for (EdgeType edgeType : edgeTypes)
      phraseSynsets.addAll(expandSynsets(phraseSynsets, edgeType));
    // find phrases for synsets
    Set<String> expansions = synsetsToPhrases(phraseSynsets);
    if (opts.verbose > 0) {
      for (String expansion : expansions)
        LogInfo.logs("WordNetExpansionLexicon: expanding %s to %s", phrase, expansion);
    }
    return expansions;
  }

  public Set<String> getSynonyms(String phrase) {
    Set<WordNetID> phraseSynsets = phraseToSynsets(phrase);
    Set<String> expansions = synsetsToPhrases(phraseSynsets);
    expansions.remove(phrase);
    return expansions;
  }

  public Set<String> getDerivations(String phrase) {
    Set<WordNetID> phraseSynsets = phraseToSynsets(phrase);
    Set<WordNetID> derivations = new HashSet<>();
    derivations.addAll(expandSynsets(phraseSynsets, EdgeType.DERIVED_FROM));
    derivations.addAll(expandSynsets(phraseSynsets, EdgeType.DERIVES));
    Set<String> expansions = synsetsToPhrases(derivations);
    expansions.remove(phrase);
    return expansions;
  }

  public Set<String> getHypernyms(String phrase) {
    Set<WordNetID> phraseSynsets = phraseToSynsets(phrase);
    Set<WordNetID> hypernyms = new HashSet<>();
    hypernyms.addAll(expandSynsets(phraseSynsets, EdgeType.HYPONYM));
    Set<String> expansions = synsetsToPhrases(hypernyms);
    expansions.remove(phrase);
    return expansions;
  }

  private Set<String> synsetsToPhrases(Set<WordNetID> phraseSynsets) {

    Set<String> res = new HashSet<>();
    for (WordNetID phraseSynset : phraseSynsets) {
      res.addAll(synsetToPhrases(phraseSynset));
    }
    return res;
  }

  private Collection<String> synsetToPhrases(WordNetID phraseSynset) {
    Set<String> res = new HashSet<>();
    List<WordNetID> wordTags = phraseSynset.get(EdgeType.SYNSET_HAS_WORDTAG);
    for (WordNetID wordTag : wordTags) {
      List<WordNetID> words = wordTag.get(EdgeType.WORDTAG_TO_WORD);
      for (WordNetID word : words) {
        res.add(((WordID) word).word);
      }
    }
    return res;
  }

  /** Given a phrase find all synsets containing this phrase */
  private Set<WordNetID> phraseToSynsets(String phrase) {

    List<WordNetID> wordTags = new LinkedList<>();
    WordID word = wn.getWordID(phrase);
    if (word != null)
      wordTags.addAll(word.get(EdgeType.WORD_TO_WORDTAG));
    Set<WordNetID> synsets = new HashSet<>();
    for (WordNetID wordTag : wordTags) {
      synsets.addAll(wordTag.get(EdgeType.WORDTAG_IN_SYNSET));
    }
    return synsets;
  }

  private List<WordNetID> expandSynset(WordNetID synset, EdgeType edgeType) {
    return synset.get(edgeType);
  }

  private Set<WordNetID> expandSynsets(Collection<WordNetID> synsets, EdgeType edgeType) {
    Set<WordNetID> res = new HashSet<>();
    for (WordNetID synset : synsets)
      res.addAll(expandSynset(synset, edgeType));
    return res;
  }

  public static void main(String[] args) throws IOException {

    WnExpander wnLexicon = new WnExpander();
    wnLexicon.expandPhrase("assassinate");
    System.out.println();
  }

}
