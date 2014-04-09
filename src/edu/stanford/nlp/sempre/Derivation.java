package edu.stanford.nlp.sempre;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import fig.basic.*;

import java.util.*;

/**
 * A Derivation corresponds to the production of a (partial) logical form
 * |formula| from a span of the utterance [start, end). Contains the formula and
 * what was used to produce it (like a search state). Each derivation is created
 * by a grammar rule and has some features and a score.
 *
 * @author Percy Liang
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Derivation implements SemanticFn.Callable {
  public static class Options {
    @Option(gloss = "When printing derivations, to show values (could be quite verbose)")
    public boolean showValues = true;
    @Option(gloss = "When printing derivations, to show rules")
    public boolean showRules = false;
  }

  public static Options opts = new Options();

  //// Basic fields: created by the constructor.

  // Span that the derivation is built over
  @JsonProperty public final String cat;
  @JsonProperty public final int start;
  @JsonProperty public final int end;

  // If this derivation is composed of other derivations
  @JsonProperty
  public final Rule rule;  // Which rule was used to produce this derivation?  Set to nullRule if not.
  @JsonProperty
  public final List<Derivation> children;  // Corresponds to the RHS of the rule.

  //// SemanticFn fields: read/written by SemanticFn.
  // Note: SemanticFn should only depend on Formula and the Freebase type
  // information.  This could be its own class, but expose more right now to
  // be more flexible.

  @JsonProperty
  public final Formula formula; // Logical form produced by this derivation
  @JsonProperty
  public final SemType type; // Type corresponding to that logical form

  //// Fields produced by feature extractor, evaluation, etc.

  final List<String> localChoices = new ArrayList<String>();  // Just for printing/debugging.

  // Information for scoring
  @JsonProperty final private FeatureVector localFeatureVector;  // Features
  @JsonProperty double score = Double.NaN;  // Weighted combination of features

  // What the formula evaluates to (optionally set later; only non-null for the root Derivation)
  @JsonProperty Value value;
  @JsonProperty Evaluation executorStats;

  // Number in [0, 1] denoting how correct the value is.
  @JsonProperty double compatibility = Double.NaN;
  // Probability (normalized exp of score).
  @JsonProperty double prob = Double.NaN;

  // Miscellaneous statistics which are filled in by BeamParser
  // TODO: wrap this in an object BeamParserStats
  int maxBeamPosition = -1;  // Lowest position that this tree or any of its children is on the beam (after sorting)
  int maxUnsortedBeamPosition = -1;  // Lowest position that this tree or any of its children is on the beam (before sorting)
  int preSortBeamPosition = -1;
  int postSortBeamPosition = -1;
  //caching the hashcode
  int hashCode = -1;

  // Initially, derivation is created with these parameters.
  // Then, SemanticFn is called to create many copies of this derivation with different

  // TODO: why do we need these constructors if we have the Builder?
  /** Constructor for humans. */
  public Derivation(String cat, int start, int end,
      Rule rule,
      List<Derivation> children,
      Formula formula,
      SemType type) {
    this.cat = cat;
    this.start = start;
    this.end = end;
    this.rule = rule;
    this.children = children;
    this.formula = formula;
    this.type = type;
    this.localFeatureVector = new FeatureVector();
  }

  /** Constructor for humans. */
  public Derivation(String cat, int start, int end,
      Rule rule,
      List<Derivation> children) {
    this(cat, start, end, rule, children, null, null);
  }

  /** Builder for everyone. */
  public static class Builder {
    private String cat;
    private int start;
    private int end;
    private Rule rule;
    private List<Derivation> children;
    private Formula formula;
    private SemType type;
    private FeatureVector localFeatureVector = new FeatureVector();
    private double score = Double.NaN;
    private Value value;
    private Evaluation executorStats;
    private double compatibility = Double.NaN;
    private double prob = Double.NaN;

    public Builder cat(String cat) {
      this.cat = cat;
      return this;
    }
    public Builder start(int start) {
      this.start = start;
      return this;
    }
    public Builder end(int end) {
      this.end = end;
      return this;
    }
    public Builder rule(Rule rule) {
      this.rule = rule;
      return this;
    }
    public Builder children(List<Derivation> children) {
      this.children = children;
      return this;
    }
    public Builder formula(Formula formula) {
      this.formula = formula;
      return this;
    }
    public Builder type(SemType type) {
      this.type = type;
      return this;
    }
    public Builder localFeatureVector(FeatureVector localFeatureVector) {
      this.localFeatureVector = localFeatureVector;
      return this;
    }
    public Builder score(double score) {
      this.score = score;
      return this;
    }
    public Builder value(Value value) {
      this.value = value;
      return this;
    }
    public Builder executorStats(Evaluation executorStats) {
      this.executorStats = executorStats;
      return this;
    }
    public Builder compatibility(double compatibility) {
      this.compatibility = compatibility;
      return this;
    }
    public Builder prob(double prob) {
      this.prob = prob;
      return this;
    }
    public Builder withStringFormulaFrom(String value) {
      this.formula = new ValueFormula<StringValue>(new StringValue(value));
      this.type = SemType.stringType;
      return this;
    }
    public Builder withFormulaFrom(Derivation deriv) {
      this.formula = deriv.formula;
      this.type = deriv.type;
      return this;
    }
    public Builder withCallable(SemanticFn.Callable c) {
      this.cat = c.getCat();
      this.start = c.getStart();
      this.end = c.getEnd();
      this.rule = c.getRule();
      this.children = c.getChildren();
      return this;
    }
    public Derivation createDerivation() {
      return new Derivation(
          cat, start, end, rule, children, formula, type,
          localFeatureVector, score, value, executorStats, compatibility, prob);
    }
  }

  @JsonCreator
  Derivation(@JsonProperty("cat") String cat,
      @JsonProperty("start") int start,
      @JsonProperty("end") int end,
      @JsonProperty("rule") Rule rule,
      @JsonProperty("children") List<Derivation> children,
      @JsonProperty("formula") Formula formula,
      @JsonProperty("type") SemType type,
      @JsonProperty("localFeatureVector") FeatureVector localFeatureVector,
      @JsonProperty("score") double score,
      @JsonProperty("value") Value value,
      @JsonProperty("executorStats") Evaluation executorStats,
      @JsonProperty("compatibility") double compatibility,
      @JsonProperty("prob") double prob) {
    this.cat = cat;
    this.start = start;
    this.end = end;
    this.rule = rule;
    this.children = children;
    this.formula = formula;
    this.type = type;
    this.localFeatureVector = localFeatureVector;
    this.score = score;
    this.value = value;
    this.executorStats = executorStats;
    this.compatibility = compatibility;
    this.prob = prob;
  }

  public Formula getFormula() { return formula; }
  public double getScore() { return score; }
  public double getProb() { return prob; }
  public double getCompatibility() { return compatibility; }
  public List<Derivation> getChildren() { return children; }
  public Value getValue() { return value; }
  public boolean isExecuted() { return value != null; }
  public int getMaxBeamPosition() { return maxBeamPosition; }
  public String getCat() { return cat; }
  public int getStart() { return start; }
  public int getEnd() { return end; }
  public Rule getRule() { return rule; }
  public Evaluation getExecutorStats() { return executorStats; }

  public Derivation child(int i) { return children.get(i); }
  public String childStringValue(int i) {
    return Formulas.getString(children.get(i).formula);
  }

  // Return whether |deriv| is the top Derivation.
  public boolean isRoot(int numTokens) {
    return cat.equals(Rule.rootCat) && start == 0 && end == numTokens;
  }

  // Functions that operate on features.
  public void addFeature(String domain, String name) { addFeature(domain, name, 1); }
  public void addFeature(String domain, String name, double value) { this.localFeatureVector.add(domain, name, value); }
  public void addFeatureWithBias(String domain, String name, double value) { this.localFeatureVector.addWithBias(domain, name, value); }
  public void addFeatures(FeatureVector fv) { this.localFeatureVector.add(fv); }
  public void addFeatures(Derivation deriv) { this.localFeatureVector.add(deriv.localFeatureVector); }

  /**
   * Recursively compute the score for each node in derivation. Update |score|
   * field as well as return its value.
   */
  public double computeScore(Params params) {
    score = localFeatureVector.dotProduct(params);
    if (children != null)
      for (Derivation child : children)
        score += child.computeScore(params);
    return score;
  }

  /**
   * Same as |computeScore()| but without recursion (assumes children are
   * already scored).
   */
  public double computeScoreLocal(Params params) {
    score = localFeatureVector.dotProduct(params);
    if (children != null)
      for (Derivation child : children)
        score += child.score;
    return score;
  }

  public void ensureExecuted(Executor executor) {
    if (!isExecuted()) {
      StopWatchSet.begin("Executor.execute");
      Executor.Response response = executor.execute(formula);
      StopWatchSet.end();
      value = response.value;
      executorStats = response.stats;
    }
  }

  /** Copy execution result and stats from another Derivation. */
  public void setExecResults(Derivation deriv) {
    if (isExecuted())
      throw new IllegalStateException("setExecResults() on an executed Derivation");
    value = deriv.value;
    executorStats = deriv.executorStats;
    // TODO: why do we need this?
    localFeatureVector.add(deriv.localFeatureVector, DenotationFeatureMatcher.matcher);
  }

  @Override
  public boolean equals(Object thatObj) {
    //if(thatObj == null || getClass() != thatObj.getClass()) return false;
    Derivation that = (Derivation) thatObj;
    if (!this.cat.equals(that.cat)) return false;
    if (this.start != that.start) return false;
    if (this.end != that.end) return false;
    if (!this.rule.equals(that.rule)) return false;
    if (!this.children.equals(that.children)) return false;
    if (!this.formula.equals(that.formula)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    if(hashCode==-1) {
      int hash = 0x7ed55d16;
      hash = hash * 0xd3a2646c + cat.hashCode();
      hash = hash * 0xd3a2646c + start;
      hash = hash * 0xd3a2646c + end;
      hash = hash * 0xd3a2646c + rule.hashCode();
      hash = hash * 0xd3a2646c + children.hashCode();
      hash = hash * 0xd3a2646c + formula.hashCode();
//      Boolean b = isCompleteDerivation();
//      hash = hash * 0xd3a2646c + b.hashCode(); 
      hashCode = hash;
    }
    return hashCode;
  }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("derivation");
    //tree.addChild(LispTree.proto.newList("span", cat+"["+start+":"+end+"]"));
    if (formula != null)
      tree.addChild(LispTree.proto.newList("formula", formula.toLispTree()));
    if (value != null) {
      if (opts.showValues)
        tree.addChild(LispTree.proto.newList("value", value.toLispTree()));
      else if (value instanceof ListValue)
        tree.addChild(((ListValue) value).values.size() + " values");
    }
    if (type != null)
      tree.addChild(LispTree.proto.newList("type", type.toLispTree()));
    if (opts.showRules) {
      if (rule != null) tree.addChild(getRuleLispTree());
    }
    return tree;
  }

  /**
   * lisp tree showing the entire parse tree
   * @return
   */
  public LispTree toRecursiveLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("derivation");
    tree.addChild(LispTree.proto.newList("span", cat+"["+start+":"+end+"]"));
    if (formula != null)
      tree.addChild(LispTree.proto.newList("formula", formula.toLispTree()));
    for(Derivation child: children)
      tree.addChild(child.toRecursiveLispTree());
    return tree;
  }

  public String toRecursiveString() {
    return toRecursiveLispTree().toString();
  }

  private LispTree getRuleLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("rules");
    getRuleLispTreeRecurs(tree);
    return tree;
  }

  private void getRuleLispTreeRecurs(LispTree tree) {
    if (children.size() > 0) {
      tree.addChild(LispTree.proto.newList("rule", rule.toLispTree()));
      for (Derivation child : children) {
        child.getRuleLispTreeRecurs(tree);
      }
    }
  }

  public String startEndString(List<String> tokens) {
    return start + ":" + end + tokens.subList(start, end);
  }
  public String toString() { return toLispTree().toString(); }

  public void incrementAllFeatureVector(double factor, Map<String, Double> map) {
    incrementAllFeatureVector(factor, map, AllFeatureMatcher.matcher);
  }
  public void incrementAllFeatureVector(double factor, Map<String, Double> map, FeatureMatcher updateFeatureMatcher) {
    localFeatureVector.increment(factor, map, updateFeatureMatcher);
    for (Derivation child : children)
      child.incrementAllFeatureVector(factor, map, updateFeatureMatcher);
  }

  public Map<String, Double> getAllFeatureVector() {
    Map<String, Double> m = new HashMap<String, Double>();
    incrementAllFeatureVector(1.0d, m, AllFeatureMatcher.matcher);
    return m;
  }

  // TODO: this is crazy inefficient
  public Double getAllFeatureVector(String featureName) {
    Map<String, Double> m = new HashMap<String, Double>();
    incrementAllFeatureVector(1.0d, m, new ExactFeatureMatcher(featureName));
    return MapUtils.get(m,featureName,0.0);
  }

  public void incrementAllChoices(int factor, Map<String, Integer> map) {
    if (opts.showRules)
      MapUtils.incr(map, "[" + start + ":" + end + "] " + rule.toString(), 1);
    for (String choice : localChoices)
      MapUtils.incr(map, choice, factor);
    for (Derivation child : children)
      child.incrementAllChoices(factor, map);
  }

  //methods added to allow checking if a derivation is complete and completing it
  public boolean isCompleteDerivation() {return true;}
  public List<Derivation> complete(Example ex) {
    throw new RuntimeException("Can not complete a derivation that is already complete, use isCompleteDerivation()");
  }


  // Used to compare derivations by score.
  public static class ScoredDerivationComparator implements Comparator<Derivation> {
    @Override
    public int compare(Derivation deriv1, Derivation deriv2) {
      if (deriv1.score > deriv2.score) return -1;
      if (deriv1.score < deriv2.score) return +1;
      // Ensure reproducible randomness
      if (deriv1.hashCode() < deriv2.hashCode()) return -1;
      if (deriv1.hashCode() > deriv2.hashCode()) return +1;
      return 0;
    }
  }

  public static void sortByScore(List<Derivation> trees) {
    Collections.sort(trees, new ScoredDerivationComparator());
  }

  // Generate a probability distribution over derivations given their scores.
  public static double[] getProbs(List<Derivation> derivations, double temperature) {
    double[] probs = new double[derivations.size()];
    for (int i = 0; i < derivations.size(); i++)
      probs[i] = derivations.get(i).getScore() / temperature;
    if (probs.length > 0)
      NumUtils.expNormalize(probs);
    return probs;
  }
}
