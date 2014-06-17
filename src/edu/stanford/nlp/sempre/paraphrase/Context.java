package edu.stanford.nlp.sempre.paraphrase;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The context of a phrase represented as |lhs| and |rhs|
 */
public class Context {

  @JsonProperty public final String lhs;
  @JsonProperty public final String rhs;
  private Set<String> lhsTokens;
  private Set<String> rhsTokens;
  
  public Context(String lhs,String rhs) {
    this.lhs = lhs;
    this.rhs = rhs;
    String[] lhsArray = lhs.split("\\s+");
    String[] rhsArray = rhs.split("\\s+");
    lhsTokens = new HashSet<String>(Arrays.asList(lhsArray));
    rhsTokens = new HashSet<String>(Arrays.asList(rhsArray));
  }
  
  public Context(List<String> tokens, Interval interval) {  
    lhs = Joiner.on(' ').join(tokens.subList(0, interval.start));
    rhs = Joiner.on(' ').join(tokens.subList(interval.end, tokens.size()));
    String[] lhsArray = lhs.split("\\s+");
    String[] rhsArray = rhs.split("\\s+");
    lhsTokens = new HashSet<String>(Arrays.asList(lhsArray));
    rhsTokens = new HashSet<String>(Arrays.asList(rhsArray));
  }
  
  @JsonCreator
  public Context(@JsonProperty("context") String context) {
    String[] tokens = context.split("__");
    this.lhs = tokens[0].substring(1);
    this.rhs = tokens[1].substring(0,tokens[1].length()-1);
    String[] lhsArray = lhs.split("\\s+");
    String[] rhsArray = rhs.split("\\s+");
    lhsTokens = new HashSet<String>(Arrays.asList(lhsArray));
    rhsTokens = new HashSet<String>(Arrays.asList(rhsArray));
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
    Context other = (Context) obj;
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

  @Override
  public String toString() {
    return "["+lhs+"__"+rhs+"]";
  }
  
  public String toUtteranceString() {
    return lhs + " X " + rhs;
  }
  
  public Set<String> getLhsTokens() {
    return lhsTokens;
  }
  
  public Set<String> getRhsTokens() {
    return rhsTokens;
  }
}
