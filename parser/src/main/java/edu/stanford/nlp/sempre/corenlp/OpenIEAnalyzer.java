package edu.stanford.nlp.sempre.corenlp;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.util.CoreMap;

import java.util.Collection;
import java.util.Properties;
import java.io.*;

/**
 * OpenIEAnalyzer uses Stanford OpenIE pipeline to analyze an input string utterance
 * and return a RelationInfo object
 *
 * @author emlozin
 */
public class OpenIEAnalyzer extends RelationAnalyzer {

  // Function from CoreNLPAnalyzer.java
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

  public RelationInfo analyze(String utterance) {
    RelationInfo relationInfo = new RelationInfo();

    // Create the Stanford CoreNLP pipeline
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,depparse,natlog,openie");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    Annotation doc = new Annotation(utterance);
    pipeline.annotate(doc);

    // Loop over sentences in the document
    for (CoreMap sentence : doc.get(CoreAnnotations.SentencesAnnotation.class)) {
      // Get the OpenIE triples for the sentence
      Collection<RelationTriple> triples =
              sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
      // Print the triples
      System.out.println( "\n[relation analyzer:] OpenIE triples:");
      for (RelationTriple triple : triples) {
        System.out.println("(" +
                triple.subjectLemmaGloss() + "," +
                triple.relationLemmaGloss() + "," +
                triple.objectLemmaGloss()+ ")");
        System.out.println("Triple confidence: " + triple.confidence);
        StringBuilder sb= new StringBuilder();
        sb.append("(").append(triple.subjectLemmaGloss()).append(",");
        sb.append(triple.relationLemmaGloss()).append(",");
        sb.append(triple.objectLemmaGloss()).append(")");
        relationInfo.relations.put(sb.toString(),triple.confidence);
      }
      System.out.println( "[relation analyzer:] No more triples \n");
    }
    return relationInfo;
  }

  public static void main(String[] args) throws Exception {
    // Create the Stanford CoreNLP pipeline
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,depparse,natlog,openie");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    while (true) {
      try {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        // Annotate an example document.
        System.out.println("Enter some text:");
        String text = reader.readLine();
        Annotation doc = new Annotation(text);
        pipeline.annotate(doc);

        // Loop over sentences in the document
        for (CoreMap sentence : doc.get(CoreAnnotations.SentencesAnnotation.class)) {
          // Get the OpenIE triples for the sentence
          Collection<RelationTriple> triples =
    	          sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
          // Print the triples
          for (RelationTriple triple : triples) {
            System.out.println(triple.confidence + "\t" +
                triple.subjectLemmaGloss() + "\t" +
                triple.relationLemmaGloss() + "\t" +
                triple.objectLemmaGloss());
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}
