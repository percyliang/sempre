package edu.stanford.nlp.sempre.paraphrase.rules;

import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import com.google.common.base.Joiner;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

public class VerbSemClassMatcher {

  // TOKEN REGEX TO MATCH VERB AND SEMANTIC CLASS
  //matching non-wh objects
  public static final TokenSequencePattern p1 = TokenSequencePattern.compile
      (
          "[ { word:\"what\"} ] ([ { word:/.*/ } ]* ) [ { lemma:\"be\" } | {lemma:\"do\"} ] [ { word:/.*/ } ]* " +
          "[ { tag:/NNP.*/ } ]+ ([ { tag:/VB.*/} ] ) [ { word:/.*/ } ]* ");
  //matching wh objects
  public static final TokenSequencePattern p2 = TokenSequencePattern.compile
      (
          "( [ { word:/when|where|who/} ]) [ { word:/.*/ } ]*  [ { lemma:\"be\" } | {lemma:\"do\"} ] [ { word:/.*/ } ]* [ { tag:/NNP.*/ } ]+ "  + 
          "([ { tag:/VB.*/} ] ) [ { word:/.*/ } ]* ");
  //matching non-wh subjects
  public static final TokenSequencePattern p3 = TokenSequencePattern.compile
      (
          "[ { word:\"what\"} ] ([ { tag:\"NN\" } | { tag:\"NNS\" }] ) "  + 
          "([ { tag:/VB.*/} & !{lemma:/be|do|have/}] ) [ { word:/.*/ } ]* [ { tag:/NNP.*/ } ]+ [ { word:/.*/ } ]* ");
  //matching wh subjects
  public static final TokenSequencePattern p4 = TokenSequencePattern.compile
      (
          "([ { word:\"who\"} ]) ([ { tag:/VB.*/} & !{lemma:/be|do|have/} ] ) [ { word:/.*/ } ]* [ { tag:/NNP.*/ } ]+ [ { word:/.*/ } ]* ");

  private Properties props = new Properties();
  private StanfordCoreNLP pipeline;  
  private static VerbSemClassMatcher extractor = null;

  public static VerbSemClassMatcher getInstance() {
    if(extractor==null) extractor =  new VerbSemClassMatcher();
    return extractor;
  }

  private VerbSemClassMatcher() {
    props.put("annotators", "tokenize, ssplit, pos, lemma");
    props.put("pos.model", "edu/stanford/nlp/models/pos-tagger/english-caseless-left3words-distsim.tagger"); //caseless model
    pipeline = new StanfordCoreNLP(props);
  }

  public VerbSemClassMatch match(String sentence) {

    Annotation annotation = new Annotation(sentence.toLowerCase());
    pipeline.annotate(annotation);
    TokenSequenceMatcher m1 = p1.getMatcher(annotation.get(CoreAnnotations.TokensAnnotation.class));
    if(m1.find()) 
      return new VerbSemClassMatch(extractMatch(m1.groupNodes(2)), extractMatch(m1.groupNodes(1)), false);
    TokenSequenceMatcher m2 = p2.getMatcher(annotation.get(CoreAnnotations.TokensAnnotation.class));
    if(m2.find()) 
      return new VerbSemClassMatch(extractMatch(m2.groupNodes(2)), extractMatch(m2.groupNodes(1)), false);
    TokenSequenceMatcher m3 = p3.getMatcher(annotation.get(CoreAnnotations.TokensAnnotation.class));
    if(m3.find()) 
      return new VerbSemClassMatch(extractMatch(m3.groupNodes(2)), extractMatch(m3.groupNodes(1)), true);
    TokenSequenceMatcher m4 = p3.getMatcher(annotation.get(CoreAnnotations.TokensAnnotation.class));
    if(m4.find()) 
      return new VerbSemClassMatch(extractMatch(m4.groupNodes(2)), extractMatch(m4.groupNodes(1)), true);
    return null;
  }

  public String extractMatch(List<? extends CoreMap> maps) {
    List<String> res = new LinkedList<String>();
    for(CoreMap map: maps) 
      res.add(map.get(LemmaAnnotation.class));
    return Joiner.on(' ').join(res);
  }

  public static class VerbSemClassMatch {

    public final String verb;
    public final String semClass;
    public final boolean isSubj;

    public VerbSemClassMatch(String verb, String semClass, boolean isSubj) {
      this.verb = verb;
      this.semClass = semClass;
      this.isSubj = isSubj;
    }    
    
    public String toString() {
      return verb+"\t"+semClass+"\t"+isSubj;
    }
  }
}
