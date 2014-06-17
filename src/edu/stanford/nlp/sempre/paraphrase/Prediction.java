package edu.stanford.nlp.sempre.paraphrase;

import edu.stanford.nlp.sempre.Executor;
import edu.stanford.nlp.sempre.Formula;
import edu.stanford.nlp.sempre.Value;

public class Prediction implements Comparable<Prediction> {

  public final Formula formula;
  public final double score;
  Value value = null;
  double compatibility;
  
  public Prediction(Formula f, double s) {
    this.formula = f;
    this.score = s;
  }
  
  public boolean isExecuted() { return value != null; }

  public void ensureExecuted(Executor executor) {
    if (!isExecuted()) {
      Executor.Response response = executor.execute(formula);
      value = response.value;
    }
  }
  
  @Override
  public int compareTo(Prediction o) {
    if(score>o.score)
      return -1;
    if(score<o.score)
      return 1;
    return 0;
  }
  
  public Value getValue() {
    return value;
  }
}
