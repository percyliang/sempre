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
 * (provided by some RelationAnalyzer).
 *
 * @author emlozin
 */
public class RelationInfo implements MemUsage.Instrumented {

    public final Map<String, Double> relations; /**< Map of relations with probability */

    public RelationInfo() {
        this.relations = new HashMap<String,Double>();
    }

    @Override
    public long getBytes() {
        return MemUsage.objectSize(MemUsage.pointerSize * 2) + MemUsage.getBytes(relations);
    }
}
