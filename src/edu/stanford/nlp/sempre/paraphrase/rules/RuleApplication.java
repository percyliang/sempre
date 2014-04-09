package edu.stanford.nlp.sempre.paraphrase.rules;

import edu.stanford.nlp.sempre.FeatureVector;
import edu.stanford.nlp.sempre.LanguageInfo;
import fig.basic.LogInfo;

public class RuleApplication {

  public final LanguageInfo antecedent;
  public final LanguageInfo consequent;
  public final ApplicationInfo appInfo;
  private FeatureVector localFeatureVector = new FeatureVector();

  public RuleApplication(LanguageInfo antecedent, LanguageInfo consequent,
      ApplicationInfo appInfo) {
    this.antecedent = antecedent;
    this.consequent = consequent;
    this.appInfo = appInfo;
  }
  
  public void addFeatures(FeatureVector other) {
    localFeatureVector.add(other);
  }

  public FeatureVector features() {return localFeatureVector; }
  
  public RuleApplication reverse() {
    RuleApplication res;
    if(appInfo.type.equals(RuleApplier.DELETE))
      res = new RuleApplication(consequent, antecedent, new ApplicationInfo(RuleApplier.INSERT, appInfo.value));
    else if(appInfo.type.equals(RuleApplier.INSERT))
      res = new RuleApplication(consequent, antecedent, new ApplicationInfo(RuleApplier.DELETE, appInfo.value));
    else
      res = new RuleApplication(consequent, antecedent, new ApplicationInfo(appInfo.type, appInfo.value));
    res.localFeatureVector.add(localFeatureVector);
    return res;
  }
  
  public void log() {
    LogInfo.logs("RuleApplication: [%s]: %s-->%s",appInfo.type,
        antecedent.phrase(0, antecedent.numTokens()),consequent.phrase(0, consequent.numTokens()));
  }
  
  public static class ApplicationInfo {
    public final String type;
    public final String value;
    
    public ApplicationInfo(String type, String value) {
      this.type = type;
      this.value = value;
    }
  }
}
