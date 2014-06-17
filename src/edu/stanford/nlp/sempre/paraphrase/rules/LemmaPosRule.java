package edu.stanford.nlp.sempre.paraphrase.rules;

public class LemmaPosRule {
  
  private LemmaPosSequence lhs;
  private LemmaPosSequence rhs;
  
  public LemmaPosRule(LemmaPosSequence lhs, LemmaPosSequence rhs) {
    this.lhs = lhs;
    this.rhs = rhs;
  }
  
  public boolean isEmpty() {
    return lhs.isEmpty() && rhs.isEmpty();
  }
      
  public LemmaPosRule reverseRule() {
    return new LemmaPosRule(rhs, lhs);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((lhs == null) ? 0 : lhs.hashCode());
    result = prime * result + ((rhs == null) ? 0 : rhs.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    LemmaPosRule other = (LemmaPosRule) obj;
    if (lhs == null) {
      if (other.lhs != null)
        return false;
    } else if (!lhs.equals(other.lhs))
      return false;
    if (rhs == null) {
      if (other.rhs != null)
        return false;
    } else if (!rhs.equals(other.rhs))
      return false;
    return true;
  }
  
  public String toString() {
    return lhs.toString()+"-->"+rhs.toString();
  }
  
  
}
