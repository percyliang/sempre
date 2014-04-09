package edu.stanford.nlp.sempre.paraphrase;

import java.util.HashSet;
import java.util.Set;

import edu.stanford.nlp.sempre.FeatureMatcher;
import fig.basic.Option;

public class ParaphraseFeatureMatcher implements FeatureMatcher {
  
  public static class Options {
    @Option(gloss = "feature domains we allow")
    public Set<String> featureDomains = new HashSet<String>();
    @Option(gloss = "constraints on features")
    public Set<String> featureConstraints = new HashSet<String>();
  }
  public static Options opts = new Options();
  
  public static boolean containsDomain(String domain) {
    return opts.featureDomains.contains(domain);
  }
  
  @Override
  public boolean matches(String feature) {
    if(opts.featureConstraints.contains("unlex")) {
      if(feature.contains("tokens=") || feature.contains("lemmas="))
        return false;
    }
    return true;
  }
}
