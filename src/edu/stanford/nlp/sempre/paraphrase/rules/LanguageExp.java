package edu.stanford.nlp.sempre.paraphrase.rules;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Joiner;

import edu.stanford.nlp.sempre.LanguageInfo;
import edu.stanford.nlp.sempre.paraphrase.Interval;
import fig.basic.LispTree;

/**
 * A list of language items
 * Matching a language expression basically amounts to matching each one of the items and building all possible
 * matches
 * @author jonathanberant
 *
 */
public class LanguageExp {
  
  private List<LangItem> items = new ArrayList<LangItem>();
  
  public LanguageExp(LispTree tree) {
    for(int i = 1; i < tree.children.size(); ++i)
      items.add(new LangItem(tree.child(i)));
  }
  
  public int itemCount() { return items.size(); }
  
  public String toString() {
    return Joiner.on(' ').join(items);
  }
  
  public List<LangItem> items() {
    return items;
  }
  
  public LangItem getItem(int i) {
    return items.get(i);
  }
  
  /**
   * Matching the entire utterance against the expression
   * @param info
   */
  public List<LangExpMatch> match(LanguageInfo utterance) {
    
    //generate the matches of the first item
    List<LangExpMatch> currMatches = new ArrayList<LanguageExp.LangExpMatch>();
    List<Interval> firstItemIntervals = items.get(0).match(utterance, 0);
    for(Interval interval: firstItemIntervals) {
      currMatches.add(new LangExpMatch(interval.start,interval.end));
    }
    //go over the rest of the items and match them
    for(int i = 1; i < items.size(); ++i) {
      LangItem currItem = items.get(i);
      List<LangExpMatch> newMatches = new ArrayList<LanguageExp.LangExpMatch>();
      for(LangExpMatch currMatch: currMatches) {
        List<Interval> intervals = currItem.match(utterance, currMatch.end());
        for(Interval interval: intervals) {
          LangExpMatch newLangExpMatch = new LangExpMatch(currMatch);
          newLangExpMatch.addInterval(interval);
          newMatches.add(newLangExpMatch);
        }
      }
      currMatches = newMatches;
    }
    //keep matches that match the entire utterance
    List<LangExpMatch> res = new ArrayList<LanguageExp.LangExpMatch>();
    for(LangExpMatch currMatch: currMatches) {
      if(currMatch.end==utterance.numTokens())
        res.add(currMatch);
    }
    return res;
  }
 
  public static class LangExpMatch {

    private ArrayList<Interval> itemMap;
    private int end;
    private int lastItemMatched=0;

    public LangExpMatch(int start, int end) {
      itemMap = new ArrayList<Interval>();
      itemMap.add(new Interval(start,end));
      this.end = end;
    }
    
    public LangExpMatch(LangExpMatch other) {
      itemMap = new ArrayList<Interval>();
      for(Interval otherInterval: other.itemMap) 
        itemMap.add(new Interval(otherInterval.start, otherInterval.end));
      
      this.end = other.end;
      this.lastItemMatched = other.lastItemMatched;
    }

    public void addInterval(Interval interval) {
      itemMap.add(interval);
      lastItemMatched++;
      this.end = interval.end;
    }
    
    public String toString() {
      return itemMap.toString() + " end: " + end;
    }
    
    public int end() { return end; }
    
    public Interval get(int index) {return itemMap.get(index); }
  }
}
