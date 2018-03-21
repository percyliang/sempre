package edu.stanford.nlp.sempre.roboy;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.roboy.config.ConfigManager;
import edu.stanford.nlp.sempre.roboy.error.*;
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
    private Map<String,List<String>> follow_ups;                 /**< Enabling/disabling follow_up questions */
    private String utterance;                   /**< Currently processed user utterance */
    private ContextValue context;               /**< Context Value storing history of a conversation */
    private List<Derivation> derivations;       /**< List of predicted derivations */
    private UnspecInfo underInfo;                /**< Error solutions object */

    public  String dbpediaUrl = ConfigManager.DB_SPARQL;
    private SparqlUtils sparqlUtil = new SparqlUtils();


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
        this.follow_ups = ConfigManager.FOLLOW_UPS;
        this.utterance = utterance;
        this.context = context;
        this.derivations = derivations;
        this.underInfo = new UnspecInfo();


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
        this.follow_ups = ConfigManager.FOLLOW_UPS;
        this.utterance = null;
        this.context = null;
        this.derivations = null;
        this.underInfo = new UnspecInfo();

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
        this.underInfo = new UnspecInfo();
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
        this.underInfo = new UnspecInfo();
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
    public UnspecInfo getUnderInfo(){
        return this.underInfo;
    }


    /**
     * Function checking derivation for underspecified terms
     * @return A list of underspecified terms
     */
    public List<UnspecInfo> checkUnderspecified () {
        // Initialize underspecified terms
        List<String> foundUnder = new ArrayList<>();
        List<UnspecInfo> underTerms = new ArrayList<>();
        for (Derivation deriv : this.derivations) {
            String formula = deriv.getFormula().toString();
            while (formula.contains("Open")){
                int start = formula.indexOf("Open")+"Open".length();
                int end = formula.indexOf("''",start);
                if (start > formula.length() || start < 0 || end < 0 ||end > formula.length())
                    break;
                String full_type = formula.substring(formula.indexOf("Open"),formula.indexOf("''")+2);
                String entity = formula.substring(start,end).substring(formula.substring(start,end).indexOf("'")+1);
                if (!foundUnder.contains(entity)){
                    foundUnder.add(entity);
                    if (full_type.contains("Entity")){
                        underTerms.add(new UnspecInfo(entity, UnspecInfo.TermType.ENTITY));
                    }
                    else if (full_type.contains("Type")){
                        underTerms.add(new UnspecInfo(entity, UnspecInfo.TermType.TYPE));
                    }
                    else if (full_type.contains("Relation")){
                        underTerms.add(new UnspecInfo(entity, UnspecInfo.TermType.RELATION));
                    }
                }
                formula = formula.substring(end+2);
            }
        }
        return underTerms;
    }

    /**
     * Function creating potential new ontology concepts matching unknown term.
     * @param termList      list of underspecified terms
     * @return A list of candidates with information about them
     */
    public List<UnspecInfo> createCandidates(List<UnspecInfo> termList){
        for (UnspecInfo term:termList) {
            LogInfo.begin_track("Analyzing term: %s", term.term);
            for (KnowledgeRetriever helper : this.helpers)
            {
                term.addCandidates(helper.analyze(term));
            }
            LogInfo.end_track();
        }
        return termList;
    }

    /**
     * Function scoring single derivation candidates
     * @param termList      list of underspecified terms
     * @return A list of candidates with information about them
     */
    public List<UnspecInfo> createScores(List<UnspecInfo> termList){
        for (UnspecInfo term:termList) {
            LogInfo.begin_track("Scoring term: %s", term.term);
            for (ScoringFunction scorer : this.scorers)
            {
                term.addScores(scorer.score(term, this.context));
            }
            LogInfo.end_track();
        }
        return termList;
    }

    /**
     * Function extracting best candidate and forming follow up question if needed
     *
     * @param underInfo        current information about candidates
     * @return Best candidate
     */
    public String getBestCandidate(UnspecInfo underInfo){
        // Sort all the scores
        List<Double> sorted = underInfo.candidatesScores;
        Collections.sort(sorted);
        Collections.reverse(sorted);
        int index = underInfo.candidatesScores.indexOf(sorted.get(0));
        // Best candidate
        String result = underInfo.candidates.get(index);
        if (sorted.get(0) < ConfigManager.FOLLOW_THRES || (sorted.get(1)/sorted.get(0)) > 0.5) {
            List<String> candidates = new ArrayList<>();
            candidates.add(underInfo.candidatesInfo.get(index));
            double current = sorted.get(0);
            for (int i = 1; i < sorted.size(); i++){
                if (sorted.get(i)/current > 0.5)
                    candidates.add(underInfo.candidatesInfo.get(underInfo.candidatesScores.indexOf(sorted.get(i))));
                else
                    break;
            }
            this.underInfo.followUps.addAll(formQuestion(underInfo.term,candidates));
        }
        if (ConfigManager.DEBUG > 0)
        {
            this.underInfo.followUps.addAll(formQuestion(underInfo.term,underInfo.candidatesInfo));
        }
        return result;
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
                    new_desc = new_desc.concat(desc.substring(desc.indexOf(")")+1));
                    desc = new_desc;
                }
                desc = desc.replaceAll(" is ", ", ");
                desc = desc.replaceAll(" was ", ", ");
                int rnd = new Random().nextInt(this.follow_ups.get("abstract").size());
                String question = String.format(this.follow_ups.get("abstract").get(rnd), term, c_map.get("Label"), desc);
                Map.Entry<String, String> entry = new java.util.AbstractMap.SimpleEntry<String, String>
                        (question, c_map.get("URI"));
                result.add(entry);
            }
            else {
                int rnd = new Random().nextInt(this.follow_ups.get("label").size());
                String question = String.format(this.follow_ups.get("label").get(rnd), term, c_map.get("Label"), desc);
                Map.Entry<String, String> entry = new java.util.AbstractMap.SimpleEntry<String, String>
                        (String.format(question, term, c_map.get("Label")), c_map.get("URI"));
                result.add(entry);
            }
        }
        if (ConfigManager.DEBUG > 3 && result.size()>0){
            LogInfo.logs("Question:%s", result.get(0).getKey());
        }
        return result;
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
            if (ConfigManager.DEBUG > 5)
                LogInfo.logs("Forming: %s - %s", best, full_type);
            if (this.underInfo.candidates.contains(entity)) {
                for (int i = 0; i < this.underInfo.followUps.size(); i++) {
                    Map.Entry<String, String> entry = new java.util.AbstractMap.SimpleEntry<String, String>
                            (this.underInfo.followUps.get(i).getKey(),
                                    Formulas.fromLispTree(replaceEntity(best_formula,
                                            full_type, this.underInfo.followUps.get(i).getValue())).toString());
                    if (deriv.followUps!=null)
                        deriv.followUps.add(entry);
                    else
                        deriv.followUps = new ArrayList<>(Arrays.asList(entry));
                }
            }
            if (best!=null) {
                new_formula = replaceEntity(new_formula, full_type, best);
            }
            formula = formula.substring(end+2);
        }
        deriv.setFormula(Formulas.fromLispTree(new_formula));
        return deriv;
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
            int start = formula.indexOf("Open")+"Open".length();
            int end = formula.indexOf("''",start);
            if (start > formula.length() || start < 0 || end < 0 ||end+2 > formula.length())
                break;
            String full_type = formula.substring(formula.indexOf("Open"),formula.indexOf("''")+2);
            String term = formula.substring(start,end).substring(formula.substring(start,end).indexOf("'")+1);
            if (full_type.contains("Entity")){
                if (result.getCandidates().containsKey(term)) {
                    for (String candidate : result.getCandidates().get(term)) {
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
            }
            else if (full_type.contains("Type")){
                if (result.getCandidates().containsKey(term)) {
                    for (String candidate : result.getCandidates().get(term)) {
                        Type type = new TypeToken<Map<String, String>>() {
                        }.getType();
                        Map<String, String> entry = this.gson.fromJson(candidate, type);
                        Map<String, String> lexeme = new HashMap();
                        lexeme.put("lexeme", entry.get("Label").toLowerCase());
                        lexeme.put("formula", entry.get("URI"));
                        lexeme.put("type", "ClassNoun");
                        lexeme.put("features", " {score:" + Double.toString(result.getScored().get(term).get(candidate)) + "} ");
                        // Add with knowledge base label
                        lexemes.add(gson.toJson(lexeme));
                        lexeme.put("lexeme", term);
                        lexemes.add(gson.toJson(lexeme));
                    }
                }
            }
            else if (full_type.contains("Relation")){
                if (result.getCandidates().containsKey(term)) {
                    for (String candidate : result.getCandidates().get(term)) {
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
            }
            formula = formula.substring(end+2);
        }
        return lexemes;
    }

    /**
     * Postprocessing function handling unknown terms error.
     */
    public List<Derivation> postprocess(){
        // Create and score candidates
        ErrorInfo result = new ErrorInfo();
        Set<String> update = new HashSet<>();
        LogInfo.begin_track("Error retrieval:");

        // Remove derivations that are the same
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
        if (ConfigManager.DEBUG > 0)
            LogInfo.logs("Checking %d derivations",this.derivations.size());

        if (this.derivations != null) {
            // Get term
            List<UnspecInfo> missingTerms = checkUnderspecified();
            if (ConfigManager.DEBUG > 0) {
                for (UnspecInfo i : missingTerms) {
                    LogInfo.logs("Detected underspecified term: %s %s", i.term, i.type.name());
                }
            }

            // Get candidate list with information
            missingTerms = createCandidates(missingTerms);
            if (ConfigManager.DEBUG > 1) {
                for (UnspecInfo termInfo : missingTerms) {
                    for (int i = 0; i < termInfo.candidatesInfo.size(); i++) {
                        LogInfo.logs("Final candidates: %s", termInfo.candidatesInfo.get(i).toString());
                    }
                }
            }

            // Get candidate scores
            missingTerms = createScores(missingTerms);
            if (ConfigManager.DEBUG > 1) {
                for (UnspecInfo termInfo : missingTerms) {
                    for (int i = 0; i < termInfo.candidatesScores.size(); i++) {
                        LogInfo.logs("Final scores: %s %f", termInfo.candidates.get(i), termInfo.candidatesScores.get(i));
                    }
                }
            }

            // Get replacements
            Map<String,String> replaces = new HashMap<>();
            for (UnspecInfo termInfo : missingTerms)
            {
                replaces.put(termInfo.term,getBestCandidate(termInfo));
            }
            if (ConfigManager.DEBUG > 1) {
                for (String key : replaces.keySet()) {
                    LogInfo.logs("Best replacement for %s : %s", key, replaces.get(key));
                }
                for (UnspecInfo termInfo : missingTerms) {
                    for (Map.Entry<String, String> entry : termInfo.followUps) {
                        LogInfo.logs("FollowUp question for %s", entry.getKey());
                    }
                }
            }

            // Replace
            for (Derivation deriv: this.derivations){
                // Replace with the best
                if (ConfigManager.DEBUG > 2)
                    LogInfo.logs("Derivation %s", replaces.entrySet().toString());
                if (replaces.keySet().size() > 0)
                    replace(deriv,replaces);
            }
        }

        LogInfo.end_track();
        return this.derivations;
    }

}
