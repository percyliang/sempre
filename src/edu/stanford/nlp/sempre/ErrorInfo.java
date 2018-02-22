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
 * (provided by some KnowledgeHelper).
 *
 * @author emlozin
 */
public class ErrorInfo implements MemUsage.Instrumented {

    public final Map<String, String> follow_up; /**< Map of follow up questions-answers pairs*/
    public final Map<String, String> underspecified; /**< Map of underspecified terms*/

    public ErrorInfo() {
        this.follow_up = new HashMap<String,String>();
        this.underspecified = new HashMap<String,String>();
    }

    @Override
    public long getBytes() {
        return MemUsage.objectSize(MemUsage.pointerSize * 2) + MemUsage.getBytes(follow_up);
    }
}
