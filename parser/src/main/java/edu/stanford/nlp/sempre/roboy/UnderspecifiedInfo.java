package edu.stanford.nlp.sempre.roboy;

import com.google.gson.Gson;
import fig.basic.MemUsage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents result of information extraction analysis of a sentence
 * (provided by some KnowledgeRetriever).
 *
 * @author emlozin
 */
public class UnderspecifiedInfo implements MemUsage.Instrumented {
    public enum TermType{
        ENTITY, RELATION, TYPE
    };
    public String term = new String();                          /**< Underspecified term*/
    public TermType type;                                       /**< Underspecified term type*/
    public List<Map.Entry<String,String>> followUps
            = new ArrayList<>();                                /**< List of follow up questions pairs*/
    public List<String> candidates
            = new ArrayList<>();                                /**< List of candidates*/
    public List<String> candidatesInfo
            = new ArrayList<>();                                /**< List of candidates info*/
    public List<Double> candidatesScores
            = new ArrayList<>();                                /**< List of candidates with scores*/

    private Gson gson = new Gson();


    public UnderspecifiedInfo() {
        this(null,null);
    }

    public UnderspecifiedInfo(String term, TermType type) {
        this.term = term;
        this.type = type;
    }

    public void addCandidates(UnderspecifiedInfo newTerm){
        // Add all to list
        if (newTerm.candidatesInfo.size() != newTerm.candidates.size())
            throw new RuntimeException("Size mismatch in error retrieval");
        try {
            for (int i = 0; i < newTerm.candidates.size(); i++) {
                // Candidate is present
                if (this.candidates.contains(newTerm.candidates.get(i))) {
                    int index = this.candidates.indexOf(newTerm.candidates.get(i));
                    Map<String, String> nTerm = new HashMap<>();
                    nTerm = this.gson.fromJson(newTerm.candidatesInfo.get(i), nTerm.getClass());
                    Map<String, String> oTerm = this.gson.fromJson(this.candidatesInfo.get(index), nTerm.getClass());
                    // Add refcount
                    double newRefcount = Double.valueOf(oTerm.get("Refcount"))
                            + Double.valueOf(nTerm.get("Refcount"));
                    oTerm.put("Refcount",String.valueOf(newRefcount));
                    this.candidatesInfo.set(index,this.gson.toJson(oTerm));
                } else {
                    this.candidates.add(newTerm.candidates.get(i));
                    this.candidatesInfo.add(newTerm.candidatesInfo.get(i));
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void addScores(UnderspecifiedInfo newTerm){
        // Add all to list
        if (newTerm.candidatesScores.size() != newTerm.candidates.size() || newTerm.candidatesInfo.size() != newTerm.candidates.size()
                || this.candidatesInfo.size() != newTerm.candidatesInfo.size())
            throw new RuntimeException("Size mismatch in error retrieval");
        try {
            if (this.candidatesScores.isEmpty()){
                this.candidatesScores = newTerm.candidatesScores;
            }
            else {
                for (int i = 0; i < newTerm.candidates.size(); i++) {
                    // Add refcount
                    int index = this.candidates.indexOf(newTerm.candidates.get(i));
                    double newScore = this.candidatesScores.get(index)
                            + newTerm.candidatesScores.get(i);
                    if (this.candidatesScores.size() > index) {
                        this.candidatesScores.set(index, newScore);
                    }
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public long getBytes() {
        return MemUsage.objectSize(MemUsage.pointerSize * 2) + MemUsage.getBytes(term) + MemUsage.getBytes(type) + MemUsage.getBytes(followUps)
                + MemUsage.getBytes(candidates) + MemUsage.getBytes(candidatesInfo) + MemUsage.getBytes(candidatesScores);
    }
}
