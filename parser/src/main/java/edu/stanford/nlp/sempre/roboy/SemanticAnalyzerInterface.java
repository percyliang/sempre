package edu.stanford.nlp.sempre.roboy;

import com.google.common.collect.Sets;

import edu.stanford.nlp.sempre.*;
import java.util.*;
import fig.basic.*;

/**
 * @brief SemanticAnalyserInterface
 */
public class SemanticAnalyzerInterface
{
    
    /**
     * @brief SemanticAnalyserInterface.Result
     */
    public class Result
    {
        private Master.Response resp;  // Parser response
        private Example ex;  // Example that was parsed
        private String parse;
        private String answer;
        private String followUpQ;
        private String followUpA;
        private String utteranceType;

        public Result(Master.Response resp, Executor executor) {
            ex = resp.getExample();
            if (
                ex.getPredDerivations().size() > 0 &&
                resp.getCandidateIndex() >= 0
            ) {
                Derivation deriv = resp.getDerivation();
                deriv.ensureExecuted(executor, ex.context);
                parse = deriv.getFormula().toString();
                answer = deriv.getValue().toString();
                if (deriv.followUps != null && deriv.followUps.size() > 0) {
                    followUpQ = deriv.followUps.get(0).getKey();
                    followUpA = deriv.followUps.get(0).getValue();
                }

                if (parse.contains("triple") || parse.contains("string"))
                  utteranceType = "statement";
                else
                  utteranceType = "question";
            }
        }

        public List<String> getTokens() {
            return ex.getTokens();
        }

        public String[] getLemmaTokens() {
            return ex.getLemmaTokens().toArray(new String[0]);
        }

        public String[] getPostags() {
            return ex.getPosTag().toArray(new String[0]);
        }

        public Map<String,Double> getRelations() {
            return ex.getRelation();
        }

        public String getSentiment() {
            return ex.getGenInfo().sentiment;
        }

        public boolean hasSuccessfulParse() {
            return parse != null && answer != null;
        }

        public boolean hasFollowUpQA() {
            return followUpQ != null && followUpA != null;
        }

        public String getParse() {
            return parse;
        }

        public String getAnswer() {
            return answer;
        }

        public String getFollowUpQ() {
            return followUpQ;
        }

        public String getFollowUpA() {
            return followUpA;
        }

        public String getType() {
            return utteranceType;
        }
    }

    public Session session;
    public Master master;
    public Builder builder;

    /**
     * @brief SemanticAnalyserInterface
     */
    public SemanticAnalyzerInterface(boolean interactive) {
        initOptions();  // Used instead of the OptionsParser from SEMPRE standalone client

        builder = new Builder();
        builder.build();
        Dataset dataset = new Dataset();
        dataset.read();
        Learner learner = new Learner(builder.parser, builder.params, dataset);
        learner.learn();

        master = new Master(builder);
        session = master.getSession("roboy");

        // Run an initial query to ensure that all executors are loaded
        new Result(master.processQuery(session, ""), builder.executor);

        if(interactive)
            master.runInteractivePrompt();
    }

    /**
     * @brief initOptions
     */
    private void initOptions() {
        Builder.opts.executor = "roboy.SparqlExecutor";
        Builder.opts.simple_executor = "JavaExecutor";
        FeatureExtractor.opts.featureDomains = Sets.newHashSet("rule");
        LanguageAnalyzer.opts.languageAnalyzer = "corenlp.CoreNLPAnalyzer";
        Learner.opts.maxTrainIters = 10;
        Params.opts.initWeightsRandomly = true;
        SimpleLexicon.opts.inPaths = Arrays.asList("resources_nlu/lexicons/roboy-demo.lexicon");
        SparqlExecutor.opts.endpointUrl = "http://dbpedia.org/sparql";
        Grammar.opts.inPaths = Arrays.asList("resources_nlu/roboy-final.grammar");
        Dataset.opts.inPaths.add(new Pair<String, String>("train", "resources_nlu/rpqa/dummy.examples"));
        SparqlExecutor.opts.endpointUrl = "http://dbpedia.org/sparql";
    }

    /**
     * @brief analyze
     */
    public Result analyze(String s) {
        return new Result(master.processQuery(session, s), builder.executor);
    }

    /**
     * @brief main
     */
    public static void main(String[] args) {
        SemanticAnalyzerInterface semanticAnalyzer = new SemanticAnalyzerInterface(true);
    }
}
