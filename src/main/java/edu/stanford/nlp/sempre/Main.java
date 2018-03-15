package edu.stanford.nlp.sempre;

import java.lang.reflect.Constructor;


import fig.basic.Option;
import fig.exec.Execution;

/**
 * Entry point for the semantic parser.
 *
 * @author Percy Liang
 */
public class Main implements Runnable {
 @Option public boolean interactive = false;
 @Option public boolean server = false;
 @Option public boolean socket = false;
 // TODO: Change to setting
 @Option public String masterType = "edu.stanford.nlp.sempre.Master";

 public void run() {
  Builder builder = new Builder();
  builder.build();

  Dataset dataset = new Dataset();
  dataset.read();

  Learner learner = new Learner(builder.parser, builder.params, dataset);
  learner.learn();

  if (server || interactive || socket) {
   Master master = createMaster(masterType, builder);
   if (server)
    master.runServer();
   if (interactive) {
    master.runInteractivePrompt();
//    master.runSocketPrompt();
   }
   if (socket)
    master.runSocketPrompt();
  }
 }

 public Master createMaster(String masterType, Builder builder) {
  try {
   Class<?> masterClass = Class.forName(masterType);
   Constructor<?> constructor = masterClass.getConstructor(Builder.class);
   return (Master)constructor.newInstance(builder);
  } catch (Throwable t) {
   t.printStackTrace();
  }
  return null;
 }

 public static void main(String[] args) {
//   String[] arguments = "-FeatureExtractor.featureDomains rule -Dataset.inPaths train:data/rpqa-train.examples -Learner.maxTrainIters 1 -Builder.executor roboy.SparqlExecutor -Builder.simple_executor JavaExecutor -FeatureExtractor.featureDomains rule -Parser.coarsePrune true -JoinFn.typeInference true -Grammar.inPaths resources/roboy-demo.grammar -Main.interactive true -SimpleLexicon.inPaths resources/lexicons/roboy-dbpedia.lexicon -LanguageAnalyzer.languageAnalyzer corenlp.CoreNLPAnalyzer -Main.server true".split(" ");
  String[] arguments = "-Builder.executor roboy.SparqlExecutor -Builder.simple_executor JavaExecutor -FeatureExtractor.featureDomains rule -Parser.coarsePrune true -JoinFn.typeInference true -Grammar.inPaths resources/roboy-demo.grammar -Main.interactive true -SimpleLexicon.inPaths resources/lexicons/roboy-dbpedia.lexicon -LanguageAnalyzer.languageAnalyzer corenlp.CoreNLPAnalyzer -Main.server true".split(" ");
//  String[] arguments = "-Builder.executor roboy.SparqlExecutor -Builder.simple_executor JavaExecutor -FeatureExtractor.featureDomains rule -Parser.coarsePrune true -JoinFn.typeInference true -Grammar.inPaths resources/roboy-demo.grammar -SimpleLexicon.inPaths resources/lexicons/roboy-dbpedia.lexicon -LanguageAnalyzer.languageAnalyzer corenlp.CoreNLPAnalyzer -Main.socket true".split(" ");
  Execution.run(arguments, "Main", new Main(), Master.getOptionsParser());
 }
}
