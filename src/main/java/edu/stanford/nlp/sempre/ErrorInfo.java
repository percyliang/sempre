package edu.stanford.nlp.sempre;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;
import fig.basic.IntPair;
import fig.basic.LispTree;
import fig.basic.MemUsage;

import java.util.*;

/**
 * Represents result of information extraction analysis of a sentence
 * (provided by some KnowledgeRetriever).
 *
 * @author emlozin
 */
public class ErrorInfo implements MemUsage.Instrumented {

    private Map<String, String> follow_up = new HashMap<>(); /**< Map of follow up questions-answers pairs*/
    private Map<String, List<String>> underspecified = new HashMap<>(); /**< Map of underspecified terms*/
//
//    public ErrorInfo() {
//        this.follow_up = new HashMap<>();
//        this.underspecified = new HashMap<>();
//    }

    public Map<String, List<String>> getCandidates(){
        return this.underspecified;
    }

    public Map<String, String> getFollowUps(){
        return this.follow_up;
    }

    @Override
    public long getBytes() {
        return MemUsage.objectSize(MemUsage.pointerSize * 2) + MemUsage.getBytes(follow_up);
    }
}
