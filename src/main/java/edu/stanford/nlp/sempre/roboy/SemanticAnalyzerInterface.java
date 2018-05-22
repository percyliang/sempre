package edu.stanford.nlp.sempre.roboy;

import com.google.common.collect.Sets;

import edu.stanford.nlp.sempre.*;
import java.util.*;

public class SemanticAnalyzerInterface {
    
    public class Result {

        public Result(Example example) {
            ex = example;
        }

        private Example ex;  // Example that was parsed

        public List<String> getTokens() {
            return ex.getTokens();
        }

        public List<String> getLemmaTokens() {
            return ex.getLemmaTokens();
        }

        public List<String> getPostags() {
            return ex.getPosTag();
        }

        public Map<String,Double> getRelations() {
            return ex.getRelation();
        }

        public String getSentiment() {
            return ex.getGenInfo().sentiment;
        }
    }

    public Session session;
    public Master master;

    public SemanticAnalyzerInterface(boolean interactive) {
        initOptions();  // Used instead of the OptionsParser from SEMPRE standalone client

        Builder builder = new Builder();
        builder.build();
        Dataset dataset = new Dataset();
        dataset.read();
        Learner learner = new Learner(builder.parser, builder.params, dataset);
        learner.learn();

        master = new Master(builder);
        session = master.getSession("roboy");

        if(interactive)
            master.runInteractivePrompt();
    }

    private void initOptions() {
        Builder.opts.executor = "roboy.SparqlExecutor";
        Builder.opts.simple_executor = "JavaExecutor";
        FeatureExtractor.opts.featureDomains = Sets.newHashSet("rule");
        LanguageAnalyzer.opts.languageAnalyzer = "corenlp.CoreNLPAnalyzer";
        Learner.opts.maxTrainIters = 10;
        Params.opts.initWeightsRandomly = true;
        SimpleLexicon.opts.inPaths = Arrays.asList("resources/lexicons/roboy-demo.lexicon");
        SparqlExecutor.opts.endpointUrl = "http://dbpedia.org/sparql";
        Grammar.opts.inPaths = Arrays.asList("resources/roboy-final.grammar");

        // Dataset.inPaths train:data/rpqa/dummy.examples
        // Derivation.derivComparator ScoredDerivationComparator
        // Grammar.tags error
    }

    public Result analyze(String s) {
        Master.Response resp = master.processQuery(session, s);
        return new Result(resp.ex);
    }

    public static void main(String[] args) {
        SemanticAnalyzerInterface semanticAnalyzer = new SemanticAnalyzerInterface(true);
    }
}
