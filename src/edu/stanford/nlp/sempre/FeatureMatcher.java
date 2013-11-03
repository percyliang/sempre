package edu.stanford.nlp.sempre;

public interface FeatureMatcher {
  public boolean matches(String feature);
}

class AllFeatureMatcher implements FeatureMatcher {
  private AllFeatureMatcher() { }
  @Override
  public boolean matches(String feature) { return true; }
  public static final AllFeatureMatcher matcher = new AllFeatureMatcher();
}

class ExactFeatureMatcher implements FeatureMatcher {
  private String match;
  public ExactFeatureMatcher(String match) { this.match = match; }
  @Override
  public boolean matches(String feature) { return feature.equals(match); }
}

class DenotationFeatureMatcher implements FeatureMatcher {
  @Override
  public boolean matches(String feature) {
    return feature.startsWith("denotation-size") ||
        feature.startsWith("count-denotation-size");
  }
  public static final DenotationFeatureMatcher matcher = new DenotationFeatureMatcher();
}
