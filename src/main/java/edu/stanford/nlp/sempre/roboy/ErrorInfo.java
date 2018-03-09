package edu.stanford.nlp.sempre.roboy;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fig.basic.LogInfo;
import fig.basic.MemUsage;

import java.lang.reflect.Type;
import java.util.*;

/**
 * Represents result of information extraction analysis of a sentence
 * (provided by some KnowledgeRetriever).
 *
 * @author emlozin
 */
public class ErrorInfo implements MemUsage.Instrumented {
    private String utterance = new String();                 /**< Current user utterance*/
    private Map<String, String> follow_up
            = new HashMap<>();                               /**< Map of follow up questions-answers pairs*/
    private Map<String, List<String>> candidates
            = new HashMap<>();                               /**< Map of underspecified terms*/
    private Map<String, Map<String,Double>> candidates_scored
            = new HashMap<>();                               /**< Map of underspecified terms paired with scores*/
//
//    public ErrorInfo() {
//        this.follow_up = new HashMap<>();
//        this.underspecified = new HashMap<>();
//    }

    public void setCandidates(Map<String, List<String>> candidates){
        this.candidates = candidates;
    }

    public void setScored(Map<String, Map<String,Double>> scores){
        this.candidates_scored = scores;
    }

    public void setFollowUps(Map<String, String> follow_up){
        this.follow_up = follow_up;
    }

    public Map<String, List<String>> getCandidates(){
        return this.candidates;
    }

    public Map<String, Map<String,Double>> getScored(){
        return this.candidates_scored;
    }

    public Map<String, String> getFollowUps(){
        return this.follow_up;
    }

    public void addCandidates(ErrorInfo errorInfo){
        for (String key: errorInfo.getCandidates().keySet()){
            if (this.candidates.containsKey(key))
                this.candidates.get(key).addAll(errorInfo.getCandidates().get(key));
            else
                this.candidates.put(key, errorInfo.getCandidates().get(key));
        }
    }

    public void addScores(ErrorInfo errorInfo){
        if (this.candidates_scored.isEmpty())
            this.candidates_scored = errorInfo.getScored();
        else {
            for (String key : this.candidates_scored.keySet()) {
                for (String record : this.candidates_scored.get(key).keySet()) {
                    double score = this.candidates_scored.get(key).get(record)
                            + errorInfo.getScored().get(key).get(record);
                    System.out.println("Adding scores" + this.candidates_scored.get(key).get(record)
                            + "and " + errorInfo.getScored().get(key).get(record));
                    this.candidates_scored.get(key).put(record, score);
                }
            }
        }
    }

    @Override
    public long getBytes() {
        return MemUsage.objectSize(MemUsage.pointerSize * 2) + MemUsage.getBytes(follow_up)
                + MemUsage.getBytes(candidates) + + MemUsage.getBytes(candidates_scored);
    }
}
