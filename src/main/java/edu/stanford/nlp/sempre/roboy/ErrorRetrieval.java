package edu.stanford.nlp.sempre.roboy;

import edu.stanford.nlp.sempre.ContextValue;
import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.roboy.error.*;
import edu.stanford.nlp.sempre.roboy.lexicons.word2vec.Word2vec;
import edu.stanford.nlp.sempre.roboy.score.ContextScore;
import edu.stanford.nlp.sempre.roboy.score.ProbabilityScore;
import edu.stanford.nlp.sempre.roboy.score.ScoringFunction;
import edu.stanford.nlp.sempre.roboy.score.Word2VecScore;
import fig.basic.LogInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Error retrieval class. Handles dealing with unknown terms error.
 * Provides scores for derivation and updates lexicon.
 *
 * @author emlozin
 */
public class ErrorRetrieval {
    private String utterance;                   /**< Currently processed user utterance */
    private ContextValue context;               /**< Context Value storing history of a conversation */
    private List<Derivation> derivations;       /**< List of predicted derivations */
    private ErrorInfo errorInfo;                /**< Error solutions object */

    // Postprocessing analyzers
    private Word2vec vec;                       /**< Word2vec object */
    private List<KnowledgeRetriever> helpers;   /**< List of error retrieval objects */
    private List<ScoringFunction> scorers;      /**< List of scoring functions objects */

    /**
     * A constructor.
     * Creates ErrorRetrieval object.
     * @param context       current context of a conversation provided by the parser
     * @param derivations   example storing information about the current utterance provided by the parser
     */
    public ErrorRetrieval(String utterance, ContextValue context, List<Derivation> derivations){
        // Create base objects
        this.utterance = utterance;
        this.context = context;
        this.derivations = derivations;
        this.errorInfo = new ErrorInfo();

        // Adding error retrieval mechanisms
//        LogInfo.begin_track("Building error retrieval module:");
        // Adding analyzers
        this.helpers = new ArrayList<>();
        this.helpers.add(new EntityRetriever());
//        LogInfo.logs("added Entity Retriever");
        this.helpers.add(new MCGRetriever());
//        LogInfo.logs("added MCG Retriever");
        try {
            this.vec = new Word2vec();
//            LogInfo.logs("word2vec Added");
            this.helpers.add(new Word2VecRetriever(this.vec));
//            LogInfo.logs("added Word2Vec Retriever");
        }catch(Exception e){
            LogInfo.logs("Exception in Word2Vec: "+e.getMessage());
        }
        LogInfo.end_track();

        // TODO: Add scoring functions
        this.scorers.add(new Word2VecScore(this.vec));
        this.scorers.add(new ContextScore(this.vec));
        this.scorers.add(new ProbabilityScore(this.vec));
    }

    /**
     * A constructor.
     * Creates ErrorRetrieval object.
     */
    public ErrorRetrieval(){
        // Create base objects
        this.utterance = null;
        this.context = null;
        this.derivations = null;
        this.errorInfo = new ErrorInfo();

        // Adding error retrieval mechanisms
//        LogInfo.begin_track("Building error retrieval module:");
        // Adding analyzers
        this.helpers = new ArrayList<>();
        this.helpers.add(new LabelRetriever());
//        LogInfo.logs("added Label Retriever");
        this.helpers.add(new EntityRetriever());
//        LogInfo.logs("added Entity Retriever");
        this.helpers.add(new MCGRetriever());
//        LogInfo.logs("added MCG Retriever");
        try {
            this.vec = new Word2vec();
//            LogInfo.logs("word2vec Added");
            this.helpers.add(new Word2VecRetriever(this.vec));
//            LogInfo.logs("added Word2Vec Retriever");
        }catch(Exception e){
            LogInfo.errors("Exception in Word2Vec: "+e.getMessage());
        }
//        LogInfo.end_track();


        // TODO: Add scoring functions
        this.scorers = new ArrayList<>();
        this.scorers.add(new Word2VecScore(this.vec));
        this.scorers.add(new ContextScore(this.vec));
    }

    /**
     * Updating function.
     * Updates context and example values.
     * @param context   current context of a conversation provided by the parser
     * @param derivations        example storing information about the current utterance provided by the parser
     */
    public void updateModule(String utterance, ContextValue context, List<Derivation> derivations){
        this.utterance = utterance;
        this.context = context;
        this.derivations = derivations;
        this.errorInfo = new ErrorInfo();
    }

    /**
     * Updating function.
     * Updates context.
     * @param context   current context of a conversation provided by the parser
     */
    public void updateContext(ContextValue context){
        this.context = context;
    }

    /**
     * Updating function.
     * Updates example.
     * @param derivations   example storing information about the current utterance provided by the parser
     */
    public void updateExample(List<Derivation> derivations){
        this.derivations = derivations;
        this.errorInfo = new ErrorInfo();
    }

    /**
     * Updating function.
     * Updates utterance.
     * @param utterance     currently processed user utterance
     */
    public void updateUtterance(String utterance){
        this.utterance = utterance;
    }

    /**
     * Getter function for error informations.
     */
    public ErrorInfo getErrorInfo(){
        return this.errorInfo;
    }

    /**
     * Function creating potential new ontology concepts matching unknown term.
     * @param deriv   single potential derivation
     */
    public ErrorInfo createCandidates(Derivation deriv){
        ErrorInfo result = new ErrorInfo();
        for (KnowledgeRetriever helper : this.helpers) {
            result.addCandidates(helper.analyze(deriv));
        }
        return result;
    }

    /**
     * Function scoring single derivation candidates
     * @param errorInfo   object storing created candidates
     */
    public ErrorInfo createScores(ErrorInfo errorInfo){
        for (ScoringFunction scorer : this.scorers) {
            errorInfo.addScores(scorer.score(errorInfo, this.context));
        }
        return errorInfo;
    }

    /**
     * Postprocessing function handling unknown terms error.
     */
    public List<Derivation> postprocess(){
        LogInfo.begin_track("Error retrieval:");
        // Create candidates
        if (this.derivations != null) {
            for (Derivation deriv : this.derivations) {
                LogInfo.logs("Checked formula: " + deriv.getFormula().toString());
                ErrorInfo result = createCandidates(deriv);
                result = createScores(result);
                for (String entity : result.getScored().keySet()) {
                    for(String key: result.getScored().get(entity).keySet()) {
                        LogInfo.logs("Results: " + key + "->" + result.getScored().get(entity).get(key));
                    }
                }
            }
        }
        LogInfo.end_track();

        // TODO: Process scores

        return this.derivations;
    }

}
