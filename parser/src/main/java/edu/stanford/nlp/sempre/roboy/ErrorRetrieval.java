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
    private Map<String,List<String>> follow_ups;    /**< List of follow up questions */
    private String utterance;                       /**< Currently processed user utterance */
    private ContextValue context;                   /**< Context Value storing history of a conversation */
    private List<Derivation> derivations;           /**< List of predicted derivations */
    private UnderspecifiedInfo underInfo;                   /**< Error solutions object */

    public  String dbpediaUrl = ConfigManager.DB_SPARQL;    /**< DBpedia SPARQL endpoint */
    private SparqlUtils sparqlUtil = new SparqlUtils();     /**< SPARQL helper */

    // Postprocessing analyzers
    private Word2vec vec;                       /**< Word2vec object */
    private List<KnowledgeRetriever> helpers;   /**< List of error retrieval objects */
    private List<ScoringFunction> scorers;      /**< List of scoring functions objects */
    private LexiconGenerator lexiconGen;        /**< Lexicon generation helper */

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
        this.underInfo = new UnderspecifiedInfo();
        this.lexiconGen = new LexiconGenerator();

        // Add error retrieval mechanisms
        this.helpers = new ArrayList<>();
        this.helpers.add(new EntityRetriever());
        this.helpers.add(new MCGRetriever());
        try {
            this.vec = new Word2vec();
            this.helpers.add(new Word2VecRetriever(this.vec));
        }
        catch(Exception e){
            LogInfo.logs("Exception in Word2Vec: "+e.getMessage());
        }

        // Add scoring functions
        this.scorers = new ArrayList<>();
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
        this(null,null,null);
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
        this.underInfo = new UnderspecifiedInfo();
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
        this.underInfo = new UnderspecifiedInfo();
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
    public UnderspecifiedInfo getUnderInfo(){
        return this.underInfo;
    }


    /**
     * Function checking derivation for underspecified terms
     * @return A list of underspecified terms
     */
    public List<UnderspecifiedInfo> checkUnderspecified () {
        // Initialize underspecified terms
        List<String> foundUnder = new ArrayList<>();
        List<UnderspecifiedInfo> underTerms = new ArrayList<>();
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
                        underTerms.add(new UnderspecifiedInfo(entity, UnderspecifiedInfo.TermType.ENTITY));
                    }
                    else if (full_type.contains("Type")){
                        underTerms.add(new UnderspecifiedInfo(entity, UnderspecifiedInfo.TermType.TYPE));
                    }
                    else if (full_type.contains("Rel")){
                        underTerms.add(new UnderspecifiedInfo(entity, UnderspecifiedInfo.TermType.RELATION));
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
    public List<UnderspecifiedInfo> createCandidates(List<UnderspecifiedInfo> termList){
        for (UnderspecifiedInfo term:termList) {
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
    public List<UnderspecifiedInfo> createScores(List<UnderspecifiedInfo> termList){
        for (UnderspecifiedInfo term:termList) {
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
    public String getBestCandidate(UnderspecifiedInfo underInfo){
        // Sort all the scores
        List<Double> sorted = new ArrayList<>();
        sorted.addAll(underInfo.candidatesScores);
        Collections.sort(sorted);
        Collections.reverse(sorted);
        if (sorted.isEmpty())
            return null;
        int index = underInfo.candidatesScores.indexOf(sorted.get(0));
        // Best candidate
        String result = underInfo.candidates.get(index);

        if (sorted.get(0) < ConfigManager.FOLLOW_THRES || (sorted.size() > 1 && (sorted.get(1)/sorted.get(0)) > 0.8)) {
            List<String> candidates = new ArrayList<>();
            candidates.add(underInfo.candidatesInfo.get(index));
            double current = sorted.get(0);
            for (int i = 0; i < underInfo.candidatesScores.size(); i++){
                if (underInfo.candidatesScores.get(i)/current > 0.8 && i!=index)
                    candidates.add(underInfo.candidatesInfo.get(i));
            }
            this.underInfo.followUps.addAll(formQuestion(underInfo.term,candidates));
        }
//        if (ConfigManager.DEBUG > 0 && this.underInfo.followUps.isEmpty())
//        {
//            this.underInfo.followUps.addAll(formQuestion(underInfo.term,underInfo.candidatesInfo));
//        }
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
            LogInfo.logs(c);
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
                int rnd = new Random().nextInt(this.follow_ups.get("label").size());
                String question = String.format(this.follow_ups.get("label").get(rnd), term, desc);
                Map.Entry<String, String> entry = new java.util.AbstractMap.SimpleEntry<String, String>
                        (question, c_map.get("URI"));
                result.add(entry);
            }
            else {
                int rnd = new Random().nextInt(this.follow_ups.get("type").size());
                String relType = new String();
                if (c_map.get("URI").contains("type")){
                    relType = "type of thing";
                }
                else if (c_map.get("URI").contains("resource")){
                    relType = "individual thing";
                }
                else{
                    relType = "feature";
                }
                String question = String.format(this.follow_ups.get("type").get(rnd), term, c_map.get("Label"), relType);
                Map.Entry<String, String> entry = new java.util.AbstractMap.SimpleEntry<String, String>
                        (String.format(question, term, c_map.get("Label")), c_map.get("URI"));
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Function replacing missing terms with the best candidate
     * @param derivations    list of initial derivation
     * @param replacements   object storing best candidates
     */
    public void replace(List<Derivation> derivations, Map<String,String> replacements){
        List<Derivation> newDerivs = new ArrayList<>();
        for(Derivation deriv: derivations) {
            LispTree new_formula = deriv.getFormula().toLispTree();
            LispTree best_formula = deriv.getFormula().toLispTree();
            String formula = deriv.getFormula().toString();
            while (formula.contains("Open")) {
                int start = formula.indexOf("Open") + "Open".length();
                int end = formula.indexOf("''", start);
                if (start > formula.length() || start < 0 || end < 0 || end > formula.length())
                    break;
                String full_type = formula.substring(formula.indexOf("Open"), formula.indexOf("''") + 2);
                String entity = formula.substring(start, end).substring(formula.substring(start, end).indexOf("'") + 1);
                String best = replacements.get(entity);
                if (ConfigManager.DEBUG > 5)
                    LogInfo.logs("Forming: %s - %s", best, full_type);
                if (this.underInfo.candidates.contains(entity)) {
                    for (int i = 0; i < this.underInfo.followUps.size(); i++) {
                        Map.Entry<String, String> entry = new java.util.AbstractMap.SimpleEntry<String, String>
                                (this.underInfo.followUps.get(i).getKey(),
                                        Formulas.fromLispTree(replaceEntity(best_formula,
                                                full_type, this.underInfo.followUps.get(i).getValue())).toString());
                        if (deriv.followUps != null)
                            deriv.followUps.add(entry);
                        else
                            deriv.followUps = new ArrayList<>(Arrays.asList(entry));
                    }
                }
                if (best != null) {
                    new_formula = replaceEntity(new_formula, full_type, best);
                }
                formula = formula.substring(end + 2);
            }
            deriv.setFormula(Formulas.fromLispTree(new_formula));
            newDerivs.add(deriv);
        }
    }

    /**
     * Function replacing single entity in LispTree
     * @param new_formula     initial formula
     * @param replace         term to be replaced
     * @param key             term to replace
     */
    public LispTree replaceEntity(LispTree new_formula, String replace, String key) {
        LogInfo.logs("Old %s %s %s",replace,key,new_formula.toString());
        if (new_formula.toString().contains("has_type") && key.contains("type"))
            return Formula.fromString(new_formula.toString().replaceAll("has_type ".concat(replace),key.replaceAll("\\(","").replaceAll("\\)",""))).toLispTree();
        else
            return Formula.fromString(new_formula.toString().replaceAll(replace,key)).toLispTree();
//        if (new_formula!= null && new_formula.isLeaf()) {
//            LogInfo.logs("Leaf");
//            if (!key.contains(" "))
//                return LispTree.proto.newLeaf(new_formula.value.replaceAll(replace, key));
//            else{
//                return Formula.fromString(key).toLispTree();
//            }
//        }
//        else if (new_formula.children.get(0).value!= null && new_formula.children.get(0).value == "string"){
//            LogInfo.logs("String");
//            NameValue result = new NameValue(new_formula.children.get(1).value.replaceAll(replace, key));
//            return result.toLispTree();
//        }
//        else {
//            for (int i = 0; i < new_formula.children.size(); i++) {
//                new_formula.children.add(i,replaceEntity(new_formula.children.get(i), replace, key));
//                new_formula.children.remove(i+1);
//            }
//        }
//        LogInfo.logs("Old %s %s %s",replace,key,new_formula.toString());
//        return new_formula;
    }

    /**
     * Postprocessing function handling unknown terms error.
     */
    public List<Derivation> postprocess(){
        // Create and score candidates
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
            List<UnderspecifiedInfo> missingTerms = checkUnderspecified();
            if (ConfigManager.DEBUG > 0) {
                for (UnderspecifiedInfo i : missingTerms) {
                    LogInfo.logs("Detected underspecified term: %s %s", i.term, i.type.name());
                }
            }

            // Get candidate list with information
            missingTerms = createCandidates(missingTerms);
            if (ConfigManager.DEBUG > 1) {
                for (UnderspecifiedInfo termInfo : missingTerms) {
                    for (int i = 0; i < termInfo.candidatesInfo.size(); i++) {
                        LogInfo.logs("Final candidates: %s -> %s", termInfo.term, termInfo.candidatesInfo.get(i).toString());
                    }
                }
            }

            // Get candidate scores
            missingTerms = createScores(missingTerms);
            missingTerms = lexiconGen.createLexemes(missingTerms);
            if (ConfigManager.DEBUG > 1) {
                for (UnderspecifiedInfo termInfo : missingTerms) {
                    for (int i = 0; i < termInfo.candidatesScores.size(); i++) {
                        LogInfo.logs("Final scores: %s %f", termInfo.candidates.get(i).toString(), termInfo.candidatesScores.get(i));
                    }
                }
            }

            // Get replacements
            Map<String,String> replaces = new HashMap<>();
            for (UnderspecifiedInfo termInfo : missingTerms)
            {
                replaces.put(termInfo.term,getBestCandidate(termInfo));
            }
            if (ConfigManager.DEBUG > 1) {
                for (String key : replaces.keySet()) {
                    LogInfo.logs("Best replacement for %s : %s", key, replaces.get(key));
                }
                for (Map.Entry<String, String> entry : this.underInfo.followUps) {
                    LogInfo.logs("FollowUp question -> %s -> %s", entry.getKey(), entry.getValue());
                }
            }

            // Replace
            if (missingTerms.size() > 0)
            {
                // Replace with the best
                if (replaces.keySet().size() > 0)
                    replace(this.derivations, replaces);
            }
        }

        LogInfo.end_track();
        return this.derivations;
    }

}
