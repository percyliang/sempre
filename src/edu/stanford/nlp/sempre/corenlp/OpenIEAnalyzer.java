package edu.stanford.nlp.sempre.corenlp;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.LanguageInfo.DependencyEdge;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.util.CoreMap;

import java.util.Collection;
import java.util.Properties;
import java.io.*;
import java.util.*;

/**
 * OpenIEAnalyzer uses Stanford OpenIE pipeline to analyze an input string utterance
 * and return a RelationInfo object
 *
 * @author emlozin
 */
public class OpenIEAnalyzer extends RelationAnalyzer {

  public RelationInfo analyze(String utterance) {
    return new RelationInfo();
  }

  public static void main(String[] args) throws Exception {
    // Create the Stanford CoreNLP pipeline
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,depparse,natlog,openie");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    OpenIEAnalyzer analyzer = new OpenIEAnalyzer();
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
