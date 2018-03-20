package edu.stanford.nlp.sempre.roboy;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.roboy.config.ConfigManager;
import edu.stanford.nlp.sempre.roboy.error.*;
import edu.stanford.nlp.sempre.roboy.lexicons.LexicalEntry;
import edu.stanford.nlp.sempre.roboy.lexicons.word2vec.Word2vec;
import edu.stanford.nlp.sempre.roboy.score.*;
import edu.stanford.nlp.sempre.roboy.utils.SparqlUtils;
import fig.basic.LispTree;
import fig.basic.LogInfo;
import fig.basic.Option;
import sun.rmi.runtime.Log;

import java.lang.reflect.Type;
import java.util.*;

/**
 * Error retrieval class. Handles dealing with unknown terms error.
 * Provides scores for derivation and updates lexicon.
 *
 * @author emlozin
 */
public class ErrorRetrieval {
    private Gson gson = new Gson();
    private boolean follow_ups;                 /**< Enabling/disabling follow_up questions */
    private String utterance;                   /**< Currently processed user utterance */
    private ContextValue context;               /**< Context Value storing history of a conversation */
    private List<Derivation> derivations;       /**< List of predicted derivations */
    private ErrorInfo errorInfo;                /**< Error solutions object */

    public  String dbpediaUrl = ConfigManager.DB_SPARQL;
    private SparqlUtils sparqlUtil = new SparqlUtils();


    // Postprocessing analyzers
    private Word2vec vec;                       /**< Word2vec object */
    private List<KnowledgeRetriever> helpers;   /**< List of error retrieval objects */
    private List<ScoringFunction> scorers;      /**< List of scoring functions objects */

    public static class Options {
        @Option(gloss = "Enabling follow-up questions") public boolean followUps = false;
    }
    public static ErrorRetrieval.Options opts = new ErrorRetrieval.Options();
    /**
     * A constructor.
     * Creates ErrorRetrieval object.
     * @param context       current context of a conversation provided by the parser
     * @param derivations   example storing information about the current utterance provided by the parser
     */
    public ErrorRetrieval(String utterance, ContextValue context, List<Derivation> derivations){
        // Create base objects
        this.follow_ups = opts.followUps;
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
        this.follow_ups = opts.followUps;
        this.utterance = null;
        this.context = null;
        this.derivations = null;
        this.errorInfo = new ErrorInfo();

        // Adding error retrieval mechanisms
        // LogInfo.begin_track("Building error retrieval module:");
        // Adding analyzers
        this.helpers = new ArrayList<>();
        this.helpers.add(new LabelRetriever());
        //LogInfo.logs("added Label Retriever");
        this.helpers.add(new EntityRetriever());
        //LogInfo.logs("added Entity Retriever");
        this.helpers.add(new MCGRetriever());
        //LogInfo.logs("added MCG Retriever");
        try {
            this.vec = new Word2vec();
            //LogInfo.logs("word2vec Added");
            this.helpers.add(new Word2VecRetriever(this.vec));
            //LogInfo.logs("added Word2Vec Retriever");
        }catch(Exception e){
            LogInfo.errors("Exception in Word2Vec: "+e.getMessage());
        }
        //LogInfo.end_track();


        // TODO: Add scoring functions
        this.scorers = new ArrayList<>();
        this.scorers.add(new Word2VecScore(this.vec));
        this.scorers.add(new ContextScore(this.vec));
        this.scorers.add(new SimilarityScore(this.vec));
        this.scorers.add(new ProbabilityScore(this.vec));

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
     * @param deriv         single potential derivation
     * @param errorInfo     information about potential candidates
     */
    public ErrorInfo createCandidates(Derivation deriv, ErrorInfo errorInfo){
        String formula = deriv.getFormula().toString();
        while (formula.contains("Open")){
            int start = formula.indexOf("Open")+"Open".length();
            int end = formula.indexOf("''",start);
            if (start > formula.length() || start < 0 || end < 0 ||end > formula.length())
                break;
            String term = formula.substring(start,end).substring(formula.substring(start,end).indexOf("'")+1);
            if (!errorInfo.getCandidates().containsKey(term)) {
                for (KnowledgeRetriever helper : this.helpers) {
                    errorInfo.addCandidates(helper.analyze(deriv));
                }
                return errorInfo;
            }
            formula = formula.substring(end);
        }
        return errorInfo;
    }

    /**
     * Function scoring single derivation candidates
     * @param deriv         single potential derivation
     * @param errorInfo   object storing created candidates
     */
    public ErrorInfo createScores(Derivation deriv, ErrorInfo errorInfo){
        String formula = deriv.getFormula().toString();
        while (formula.contains("Open")){
            int start = formula.indexOf("Open")+"Open".length();
            int end = formula.indexOf("''",start);
            if (start > formula.length() || start < 0 || end < 0 ||end > formula.length())
                break;
            String term = formula.substring(start,end).substring(formula.substring(start,end).indexOf("'")+1);
            if (!errorInfo.getScored().containsKey(term)) {
                for (ScoringFunction scorer : this.scorers) {
                    errorInfo.addScores(scorer.score(errorInfo, this.context));
                }
                return errorInfo;
            }
            formula = formula.substring(end);
        }
        return errorInfo;
    }

    /**
     * Function updating lexicon
     * @param lexemes   set of lexical entries to be added
     */
    public void updateLexicon(Set<String> lexemes) {
        for (String lexeme: lexemes){
//            LogInfo.logs("New lexeme: %s",lexeme);
            SimpleLexicon.getSingleton().add(lexeme);
        }
    }

    /**
     * Function updating lexicon
     * @param deriv     derivation candidate
     * @param result    information about candidates
     */
    public Set<String> createLexemes(Derivation deriv, ErrorInfo result)
    {
        String formula = deriv.getFormula().toString();
        Set<String> lexemes = new HashSet<>();
        if (result.getCandidates() == null) {
            return lexemes;
        }
        while (formula.contains("Open")){
            LogInfo.logs(formula);
            int start = formula.indexOf("Open")+"Open".length();
            int end = formula.indexOf("''",start);
            if (start > formula.length() || start < 0 || end < 0 ||end+2 > formula.length())
                break;
            String full_type = formula.substring(formula.indexOf("Open"),formula.indexOf("''")+2);
            String term = formula.substring(start,end).substring(formula.substring(start,end).indexOf("'")+1);
            if (full_type.contains("Entity")){
                for (String candidate: result.getCandidates().get(term))
                {
                    if ((result.getScored().get(term).get(candidate)) > ConfigManager.LEXICON_THRES) {
                        Type type = new TypeToken<Map<String, String>>() {
                        }.getType();
                        Map<String, String> entry = this.gson.fromJson(candidate, type);
                        Map<String, String> lexeme = new HashMap();
                        lexeme.put("lexeme", entry.get("Label").toLowerCase());
                        lexeme.put("formula", entry.get("URI"));
                        lexeme.put("type", "NewEntity");
                        lexeme.put("features", " {score:" + Double.toString(result.getScored().get(term).get(candidate)) + "} ");
                        // Add with knowledge base label
                        lexemes.add(gson.toJson(lexeme));
                        lexeme.put("lexeme", term);
                        lexemes.add(gson.toJson(lexeme));
                    }
                }
            }
            else if (full_type.contains("Type")){
                for (String candidate: result.getCandidates().get(term))
                {
                    if ((result.getScored().get(term).get(candidate)) > ConfigManager.LEXICON_THRES) {
                        Type type = new TypeToken<Map<String, String>>() {
                        }.getType();
                        Map<String, String> entry = this.gson.fromJson(candidate, type);
                        Map<String, String> lexeme = new HashMap();
                        lexeme.put("lexeme", entry.get("Label").toLowerCase());
                        lexeme.put("formula", entry.get("URI"));
                        lexeme.put("type", "NewType");
                        lexeme.put("features", " {score:" + Double.toString(result.getScored().get(term).get(candidate)) + "} ");
                        // Add with knowledge base label
                        lexemes.add(gson.toJson(lexeme));
                        lexeme.put("lexeme", term);
                        lexemes.add(gson.toJson(lexeme));
                    }
                }
            }
            else if (full_type.contains("Relation")){
                for (String candidate: result.getCandidates().get(term))
                {
                    if ((result.getScored().get(term).get(candidate)) > ConfigManager.LEXICON_THRES) {
                        Type type = new TypeToken<Map<String, String>>() {
                        }.getType();
                        Map<String, String> entry = this.gson.fromJson(candidate, type);
                        Map<String, String> lexeme = new HashMap();
                        lexeme.put("lexeme", entry.get("Label").toLowerCase());
                        lexeme.put("formula", entry.get("URI"));
                        lexeme.put("type", "NewRelation");
                        lexeme.put("features", " {score:" + Double.toString(result.getScored().get(term).get(candidate)) + "} ");
                        // Add with knowledge base label
                        lexemes.add(gson.toJson(lexeme));
                        lexeme.put("lexeme", term);
                        lexemes.add(gson.toJson(lexeme));
                    }
                }
            }
            formula = formula.substring(end+2);
        }
        return lexemes;
    }

    /**
     * Function replacing single entity in LispTree
     * @param new_formula     initial formula
     * @param replace         term to be replaced
     * @param key             term to replace
     */
    public LispTree replaceEntity(LispTree new_formula, String replace, String key) {
        if (new_formula!= null && new_formula.isLeaf()) {
            LispTree listTree = LispTree.proto.newLeaf(new_formula.value.replaceAll(replace, key));
            return listTree;
        }
        else if (new_formula.children.get(0).value!= null && new_formula.children.get(0).value == "string"){
            NameValue result = new NameValue(new_formula.children.get(1).value.replaceAll(replace, key));
            return result.toLispTree();
        }
        else {
            for (int i = 0; i < new_formula.children.size(); i++) {
                new_formula.children.add(i,replaceEntity(new_formula.children.get(i), replace, key));
                new_formula.children.remove(i+1);
            }
        }
        return new_formula;
    }


    /**
     * Function forming questions.
     *
     * @param term          term that is underspecified
     * @param candidate     list of potential candidates
     * @return Follow-up question
     */
    public List<Map.Entry<String,String>> formQuestion(String term, List<String> candidate) {
        List<Map.Entry<String,String>> result = new ArrayList<>();
        for (String c:candidate) {
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> c_map = this.gson.fromJson(c, type);
            String desc = sparqlUtil.returnDescr(c_map.get("URI"),dbpediaUrl);
            if (desc!=null) {
                if (desc.contains(".")) {
                    desc = desc.substring(0, desc.indexOf("."));
                }
                if (desc.contains("(")) {
                    String new_desc = desc.substring(0, desc.indexOf("("));
                    new_desc = new_desc.concat(desc.substring(desc.indexOf(")")+2));
                    desc = new_desc;
                }
                desc = desc.replaceAll(" is ", ", ");
                desc = desc.replaceAll(" was ", ", ");
                Map.Entry<String, String> entry = new java.util.AbstractMap.SimpleEntry<String, String>
                        (String.format("Did you mean %s as %s, %s?", term, c_map.get("Label"), desc), c_map.get("URI"));
                result.add(entry);
                if (ConfigManager.DEBUG > 3)
                    LogInfo.logs("Question:%s", entry.getKey());
            }
            else {
                Map.Entry<String, String> entry = new java.util.AbstractMap.SimpleEntry<String, String>
                        (String.format("Did you mean %s as %s?", term, c_map.get("Label")), c_map.get("URI"));
                result.add(entry);
                if (ConfigManager.DEBUG > 3)
                    LogInfo.logs("Question:%s", entry.getKey());
            }
        }
        return result;
    }

    /**
     * Function scoring candidates in a list
     *
     * @param errorInfo        current information about candidates
     * @param term             underspecified term
     * @return Best candidate
     */
    public String getBestCandidate(ErrorInfo errorInfo, String term){
        String result = new String();
        Map<String,Double> candidate = errorInfo.getScored().get(term);
        List<Map.Entry<String,Double>> sorted = new ArrayList<>(candidate.entrySet());
        sorted.sort((o1, o2) -> o2.getValue().compareTo(o1.getValue()));
        Map.Entry<String,Double> best = sorted.get(0);
        for (Map.Entry<String,Double> entry : sorted)
        {
            if (this.errorInfo.getFollowUps().containsKey(term)) {
                this.errorInfo.getFollowUps().get(term).add(entry.getKey());
            }
            else {
                this.errorInfo.getFollowUps().put(term, new ArrayList<>(Arrays.asList(entry.getKey())));
            }
        }

        if (ConfigManager.DEBUG > 3) {
            for (String key : this.errorInfo.getFollowUps().keySet()) {
                LogInfo.logs("FollowUp %s -> %s", key, String.join(" ", this.errorInfo.getFollowUps().get(key)));
            }
        }
        return best.getKey();
    }

    /**
     * Function replacing missing terms with the best candidate
     * @param deriv       initial derivation
     * @param replacements   object storing best candidates
     */
    public Derivation replace(Derivation deriv, Map<String,String> replacements){
        LispTree new_formula = deriv.getFormula().toLispTree();
        LispTree best_formula = deriv.getFormula().toLispTree();
        String formula = deriv.getFormula().toString();
        while (formula.contains("Open")){
            int start = formula.indexOf("Open")+"Open".length();
            int end = formula.indexOf("''",start);
            if (start > formula.length() || start < 0 || end < 0 ||end > formula.length())
                break;
            String full_type = formula.substring(formula.indexOf("Open"),formula.indexOf("''")+2);
            String entity = formula.substring(start,end).substring(formula.substring(start,end).indexOf("'")+1);
            String best = replacements.get(entity);
            if (ConfigManager.DEBUG > 1)
                LogInfo.logs("Forming: %s|%s|%s", entity, best, full_type);
            if (errorInfo.getFollowUps().containsKey(entity)) {
                List<String> c = errorInfo.getFollowUps().get(entity);
                List<Map.Entry<String, String>> questions = formQuestion(entity, c);
                for (int i = 0; i < questions.size(); i++) {
                    Map.Entry<String, String> entry = new java.util.AbstractMap.SimpleEntry<String, String>
                            (questions.get(i).getKey(),
                                    Formulas.fromLispTree(replaceEntity(best_formula,
                                            full_type, questions.get(i).getValue())).toString());
                    if (deriv.followUps!=null)
                        deriv.followUps.add(entry);
                    else
                        deriv.followUps = new ArrayList<>(Arrays.asList(entry));
                }
            }
            if (best!=null) {
                Type type = new TypeToken<Map<String, String>>() {
                }.getType();
                Map<String, String> cand = this.gson.fromJson(best, type);
                new_formula = replaceEntity(new_formula, full_type, cand.get("URI"));
                if (ConfigManager.DEBUG > 1)
                    LogInfo.logs("Replaced: %s", new_formula.toString());
            }
            formula = formula.substring(end+2);
        }
        deriv.setFormula(Formulas.fromLispTree(new_formula));
        return deriv;
    }

    /**
     * Postprocessing function handling unknown terms error.
     */
    public List<Derivation> postprocess(){
        // Create and score candidates
        ErrorInfo result = new ErrorInfo();
        Set<String> update = new HashSet<>();
        LogInfo.begin_track("Error retrieval:");
        List<Derivation> remove = new ArrayList();
        List<String> formulas = new ArrayList();
        for (Derivation deriv : this.derivations) {
            if (!String.join(" ", formulas).contains(deriv.formula.toString()))
                formulas.add(deriv.formula.toString());
            else {
                remove.add(deriv);
                continue;
            }
        }
        this.derivations.removeAll(remove);
        LogInfo.logs("%d",this.derivations.size());
        if (this.derivations != null) {
            // Get candidates
            for (Derivation deriv : this.derivations) {
                // Check if something is missing
                //LogInfo.logs("Checked formula: " + deriv.getFormula().toString());
                result = createCandidates(deriv, result);
                result = createScores(deriv, result);
                update.addAll(createLexemes(deriv,result));
            }
            // Get replacements
            Map<String,String> replaces = new HashMap<>();
            for (String term: result.getScored().keySet()){
                if (follow_ups)
                    replaces.put(term,getBestCandidate(result, term));
                else
                    replaces.put(term,result.getScored().get(term).entrySet().stream().max((entry1, entry2) -> entry1.getValue() > entry2.getValue() ? 1 : -1).get().getKey());
            }
            // Replace
            for (Derivation deriv: this.derivations){
                // Replace with the best
                if (ConfigManager.DEBUG > 5)
                    LogInfo.logs("Derivation %s", replaces.entrySet().toString());
                if (replaces.keySet().size() > 0)
                    replace(deriv,replaces);
            }
//            for (String key: result.getCandidates().keySet()) {
//                List<SimpleLexicon.Entry> entries = SimpleLexicon.getSingleton().lookup(key);
//                for (SimpleLexicon.Entry e : entries)
//                    LogInfo.logs("New Entry: %s", e.toString());
//            }
        }

        // Show results
        for (String entity : result.getScored().keySet()) {
            for(String key: result.getScored().get(entity).keySet()) {
                LogInfo.logs("Results: " + key + "->" + result.getScored().get(entity).get(key));
            }
            // Save new entries
            updateLexicon(update);
        }
        LogInfo.end_track();
        return this.derivations;
    }

}
