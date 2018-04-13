package edu.stanford.nlp.sempre;

import fig.basic.LispTree;
import fig.basic.MemUsage;

import java.util.*;

/**
 * Represents result of general feature extraction.
 *
 * @author emlozin
 */
public class GeneralInfo implements MemUsage.Instrumented {

    public String sentiment;          /**< Utterance sentiment */
    public int sentiment_type;        /**< Utterance sentiment type */
    public List<String> keywords;     /**< Keywords - topics of utterance */

    public GeneralInfo() {
        this.sentiment = new String();
        this.keywords = new ArrayList<>();
        this.sentiment_type = 0;
    }

    public GeneralInfo(String text) {
        this.sentiment = text.substring(0,text.indexOf(":"));
        this.keywords = Arrays.asList(text.substring(text.indexOf(":")+1).split(","));
    }

    public String toString() {
        String result = this.sentiment.concat(":");
        result = result.concat(String.join(",", this.keywords));
        return result;
    }

    public LispTree toLispTree() {
        LispTree tree = LispTree.proto.newList();
        tree.addChild("generalInfo");
        tree.addChild(this.sentiment);
        tree.addChild(String.join(",",this.keywords));
        return tree;
    }

    @Override
    public long getBytes() {
        return MemUsage.objectSize(MemUsage.pointerSize * 2)
                + MemUsage.getBytes(sentiment)
                + MemUsage.getBytes(sentiment_type)
                + MemUsage.getBytes(keywords) ;
    }
}
