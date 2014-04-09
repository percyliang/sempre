package edu.stanford.nlp.sempre.paraphrase.rules;

import java.util.List;

import com.google.common.base.Joiner;

public class LemmaPosSequence {
  
  private List<LemmaAndPos> sequence;
  
  public LemmaPosSequence(List<LemmaAndPos> sequence) {
    this.sequence = sequence;
  }
  
  public boolean isEmpty() {
    return sequence.size()==0;
  }
  public String toString() {
    return Joiner.on(',').join(sequence);
  }
  
  @Override
  public int hashCode() {
    return toString().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    LemmaPosSequence other = (LemmaPosSequence) obj;
    if(this.sequence.size()!=other.sequence.size())
      return false;
    for(int i = 0; i<this.sequence.size();++i) {
      if(!sequence.get(i).equals(other.sequence.get(i)))
        return false;
    }
    return true;
  }

  public static class LemmaAndPos {
    
    public final String lemma;
    public final String pos;
    
    public LemmaAndPos(String lemma, String pos) {
      this.lemma = lemma;
      this.pos = pos;
    }
    
    public String toString() {
      return lemma+"/"+pos;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      LemmaAndPos other = (LemmaAndPos) obj;
      if (lemma == null) {
        if (other.lemma != null)
          return false;
      } else if (!lemma.equals(other.lemma))
        return false;
      if (pos == null) {
        if (other.pos != null)
          return false;
      } else if (!pos.equals(other.pos))
        return false;
      return true;
    }
    
    
  }
}
