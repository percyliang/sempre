package edu.stanford.nlp.sempre.roboy;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import fig.basic.LogInfo;
import fig.basic.MemUsage;

import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Represents result of information extraction analysis of a sentence
 * (provided by some KnowledgeRetriever).
 *
 * @author emlozin
 */
public class ErrorInfo implements MemUsage.Instrumented {
    private Gson gson = new Gson();
    private String utterance = new String();                 /**< Current user utterance*/
    private Map<String, List<String>> follow_up
            = new HashMap<>();                               /**< Map of entity - follow up questions pairs*/
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

    public void setFollowUps(Map<String, List<String>> follow_up){
        this.follow_up = follow_up;
    }

    public Map<String, List<String>> getCandidates(){
        return this.candidates;
    }

    public Map<String, Map<String,Double>> getScored(){
        return this.candidates_scored;
    }

    public Map<String, List<String>> getFollowUps(){
        return this.follow_up;
    }

//    public void addCandidates(ErrorInfo errorInfo){
//        for (String key: errorInfo.getCandidates().keySet()){
//            if (this.candidates.containsKey(key))
//            {
//                this.candidates.get(key).addAll(errorInfo.getCandidates().get(key));
//            }
//            else
//                this.candidates.put(key, errorInfo.getCandidates().get(key));
//        }
//    }

    public void addCandidates(ErrorInfo errorInfo){
        // Add all to list
        for (String key: errorInfo.getCandidates().keySet()){
            if (this.candidates.containsKey(key)) {
                // Check for common uris
                Map<String,Map<String,String>> uris = new HashMap<>();
                for (String candidate: this.candidates.get(key)) {
                    Type type = new TypeToken<Map<String, String>>(){}.getType();
                    Map<String, String> c = this.gson.fromJson(candidate, type);
                    if (uris.containsKey(c.get("URI"))) {
                        // URI already present
                        uris.get(c.get("URI")).put("Refcount",Double.toString(Double.parseDouble(uris.get(c.get("URI")).get("Refcount"))+Double.parseDouble(c.get("Refcount"))));
                    }
                    else
                        uris.put(c.get("URI"),c);
                }
                Map<String,Map<String,String>> new_uris = new HashMap<>();
                for (String candidate: errorInfo.getCandidates().get(key)) {
                    Type type = new TypeToken<Map<String, String>>(){}.getType();
                    Map<String, String> c = this.gson.fromJson(candidate, type);
                    if (new_uris.containsKey(c.get("URI"))) {
                        // URI already present
                        new_uris.get(c.get("URI")).put("Refcount",Double.toString(Double.parseDouble(new_uris.get(c.get("URI")).get("Refcount"))+Double.parseDouble(c.get("Refcount"))));
                    }
                    else
                        new_uris.put(c.get("URI"),c);
                }
                for (String uri: new_uris.keySet()) {
                    if (uris.containsKey(uri))
                    {
                        new_uris.get(uri).put("Refcount",Double.toString(Double.parseDouble(uris.get(uri).get("Refcount")) + Double.parseDouble(new_uris.get(uri).get("Refcount"))));
                        this.candidates.get(key).remove(this.gson.toJson(uris.get(uri)));
                        this.candidates.get(key).add(this.gson.toJson(new_uris.get(uri)));
                    }
                    else
                    {
                        this.candidates.get(key).add(this.gson.toJson(new_uris.get(uri)));
                    }
                }
            }
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
//                    System.out.println("Adding scores" + this.candidates_scored.get(key).get(record)
//                            + "and " + errorInfo.getScored().get(key).get(record));
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
