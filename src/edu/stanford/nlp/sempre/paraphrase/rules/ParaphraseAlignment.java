package edu.stanford.nlp.sempre.paraphrase.rules;

import edu.stanford.nlp.sempre.paraphrase.Interval;

public class ParaphraseAlignment {
  
  private int[] sourceAlignment;
  private int[] targetAlignment;
  
  public ParaphraseAlignment(int[] sourceAlignment, int[] targetAlignment) {
    this.sourceAlignment = sourceAlignment;
    this.targetAlignment = targetAlignment;
  }
  
  public Interval getSourceInterval() {
    return getInterval(sourceAlignment);
  }
  
  public Interval getTargetInterval() {
    return getInterval(targetAlignment);
  }
  
  /**
   * Returns the interval of the alignment gap for the source or null if there is none
   * implements a small DFA 
   * @param alignment
   * @return
   */
  private Interval getInterval(int[] alignment) {
    int state=0;
    int start=-1;
    Interval interval=null;
     
    for(int i=0; i < alignment.length; ++i) {
      if(state==0 && alignment[i]==-1) {
        state=1;
        start = i;
      }
      else if(state==1 && alignment[i]!=-1) {
        state=2;
        interval = new Interval(start, i);
      }
      else if(state==2 && alignment[i]==-1) {
        return null;
      }     
    }
    if(state==0)
      return new Interval(0, 0);
    if(state==1)
      return new Interval(start, alignment.length);
    return interval;   
  }
  
  public boolean isLegalSingleGap() {
    Interval sourceInterval = getSourceInterval();
    Interval targetInterval = getTargetInterval();
    if(sourceInterval==null || targetInterval==null)
      return false;
    if(sourceInterval.length()==0 || targetInterval.length()==0)
      return true;
    if(sourceInterval.start==0 && targetInterval.start==0)
      return true;
    if(sourceInterval.end==sourceAlignment.length && targetInterval.end==targetAlignment.length)
      return true;
    if(sourceInterval.start!=0 && targetInterval.start!=0 && 
        sourceInterval.end!=sourceAlignment.length && targetInterval.end!=targetAlignment.length)
      return true;
    return false;   
  }
  
 
 
}
