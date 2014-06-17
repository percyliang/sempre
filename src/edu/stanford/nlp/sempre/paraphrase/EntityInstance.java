package edu.stanford.nlp.sempre.paraphrase;

import edu.stanford.nlp.sempre.Formula;
import edu.stanford.nlp.sempre.SemType;

import java.util.Map;

public class EntityInstance {
  
  public final Context context;
  public final String entity;
  public final Interval interval;
  public final Formula formula;
  private Map<String,Double> featureVector;
  public final SemType semType;
  
  public EntityInstance(Context context, String entity, Interval interval,
      Formula formula, Map<String,Double> featureVector, SemType semType) {
    this.context = context;
    this.entity = entity;
    this.interval = interval;
    this.formula = formula;
    this.featureVector = featureVector;
    this.semType = semType;
  }
  
  public String toString() {
    return "context="+context+" entity="+entity + " formula="+formula+" features="+featureVector+" semType="+semType;
  }
}
