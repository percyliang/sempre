package edu.stanford.nlp.sempre;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import fig.basic.Evaluation;
import fig.basic.LispTree;
import fig.basic.LogInfo;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

/**
 * An example corresponds roughly to an input-output pair, the basic unit which
 * we make predictions on.  The Example object stores both the input,
 * preprocessing, and output of the parser.
 *
 * @author Percy Liang
 * @author Roy Frostig
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Example {
  //// Information from the input file.

  // Unique identifier for this example.
  @JsonProperty public final String id;

  // Input utterance
  @JsonProperty public final String utterance;

  // Context
  @JsonProperty public ContextValue context;

  // What we should try to predict.
  @JsonProperty public Formula targetFormula;  // Logical form (e.g., database query)
  public String formulaType;
  public List<Formula> alternativeFormulas;    // Alternative logical form (less canonical)
  @JsonProperty public Value targetValue;      // Denotation (e.g., answer)

  //// Information after preprocessing (e.g., tokenization, POS tagging, NER, syntactic parsing, etc.).
  public LanguageInfo languageInfo = null;

  //// Information after Information Extraction step.
  public RelationInfo relationInfo = null;

  //// Information after General Info Extraction step.
  public GeneralInfo genInfo = null;

  //// Output of the parser.

  // Predicted derivations (sorted by score).
  public List<Derivation> predDerivations;

  // Temporary state while parsing an Example (see Derivation.java for analogous structure).
  private Map<String, Object> tempState;

  // Statistics relating to processing the example.
  public Evaluation evaluation;

  public static class Builder {
    private String id;
    private String utterance;
    private ContextValue context;
    private Formula targetFormula;
    private Value targetValue;
    private GeneralInfo genInfo;
    private LanguageInfo languageInfo;
    private RelationInfo relationInfo;

    public Builder setId(String id) { this.id = id; return this; }
    public Builder setUtterance(String utterance) { this.utterance = utterance; return this; }
    public Builder setContext(ContextValue context) { this.context = context; return this; }
    public Builder setTargetFormula(Formula targetFormula) { this.targetFormula = targetFormula; return this; }
    public Builder setTargetValue(Value targetValue) { this.targetValue = targetValue; return this; }
    public Builder setGenInfo(GeneralInfo genInfo) { this.genInfo = genInfo; return this; }
    public Builder setLanguageInfo(LanguageInfo languageInfo) { this.languageInfo = languageInfo; return this; }
    public Builder setRelationInfo(RelationInfo relationInfo) { this.relationInfo = relationInfo; return this; }
    public Builder withExample(Example ex) {
      setId(ex.id);
      setUtterance(ex.utterance);
      setGenInfo(ex.getGenInfo());
      setContext(ex.context);
      setTargetFormula(ex.targetFormula);
      setTargetValue(ex.targetValue);
      return this;
    }
    public Example createExample() {
      return new Example(id, utterance, context, targetFormula, targetValue, genInfo, languageInfo, relationInfo);
    }
  }

  @JsonCreator
  public Example(@JsonProperty("id") String id,
                 @JsonProperty("utterance") String utterance,
                 @JsonProperty("context") ContextValue context,
                 @JsonProperty("targetFormula") Formula targetFormula,
                 @JsonProperty("targetValue") Value targetValue,
                 @JsonProperty("generalInfo") GeneralInfo genInfo,
                 @JsonProperty("languageInfo") LanguageInfo languageInfo,
                 @JsonProperty("relationInfo") RelationInfo relationInfo) {
    this.id = id;
    this.utterance = utterance;
    this.context = context;
    this.targetFormula = targetFormula;
    if (this.targetFormula!= null && this.targetFormula.toString().contains("triples"))
      this.formulaType = "statement";
    else
      this.formulaType = "question";
    this.targetValue = targetValue;
    this.languageInfo = languageInfo;
    this.relationInfo = relationInfo;
    this.genInfo = genInfo;
  }


  @JsonCreator
  public Example(@JsonProperty("id") String id,
                 @JsonProperty("utterance") String utterance,
                 @JsonProperty("context") ContextValue context,
                 @JsonProperty("targetFormula") Formula targetFormula,
                 @JsonProperty("targetValue") Value targetValue) {
    this.id = id;
    this.utterance = utterance;
    this.context = context;
    this.targetFormula = targetFormula;
    this.targetValue = targetValue;
    if (this.targetFormula!= null && this.targetFormula.toString().contains("triples"))
      this.formulaType = "statement";
    else
      this.formulaType = "question";
    this.genInfo = new GeneralInfo();
    this.languageInfo = new LanguageInfo();
    this.relationInfo = new RelationInfo();
  }

  // Accessors
  public String getId() { return id; }
  public String getUtterance() { return utterance; }
  public GeneralInfo getGenInfo() { return genInfo; }
  public LanguageInfo getLanInfo() { return languageInfo; }
  public RelationInfo getRelInfo() { return relationInfo; }
  public int numTokens() { return languageInfo.tokens.size(); }
  public List<Derivation> getPredDerivations() {
//    Set<Derivation> hs = new HashSet<>();
//    hs.addAll(predDerivations);
//    predDerivations.clear();
//    predDerivations.addAll(hs);
    return predDerivations;
  }

  public void setContext(ContextValue context) { this.context = context; }
  public void setTargetFormula(Formula targetFormula) { this.targetFormula = targetFormula; }
  public void setAlternativeFormulas(List<Formula> alternativeFormulas) { this.alternativeFormulas = alternativeFormulas; }
  public void addAlternativeFormula(Formula alternativeFormula) {
    if (this.alternativeFormulas == null)
      this.alternativeFormulas = new ArrayList<>();
    this.alternativeFormulas.add(alternativeFormula);
  }
  public void setTargetValue(Value targetValue) { this.targetValue = targetValue; }

  public String spanString(int start, int end) {
    return String.format("%d:%d[%s]", start, end, start != -1 ? phraseString(start, end) : "...");
  }
  public String phraseString(int start, int end) {
    return Joiner.on(' ').join(languageInfo.tokens.subList(start, end));
  }

  // Return a string representing the tokens between start and end.
  public List<String> getTokens() { return languageInfo.tokens; }
  public List<String> getLemmaTokens() { return languageInfo.lemmaTokens; }
  public String token(int i) { return languageInfo.tokens.get(i); }
  public String lemmaToken(int i) { return languageInfo.lemmaTokens.get(i); }
  public String posTag(int i) { return languageInfo.posTags.get(i); }
  public List<String> getPosTag() { return languageInfo.posTags; }
  public String phrase(int start, int end) { return languageInfo.phrase(start, end); }
  public String lemmaPhrase(int start, int end) { return languageInfo.lemmaPhrase(start, end); }
  public Map<String,Double> getRelation() { return relationInfo.relations; }
  public String getType(){return formulaType;}

  public String toJson() { return Json.writeValueAsStringHard(this); }
  public static Example fromJson(String json) { return Json.readValueHard(json, Example.class); }

  public static Example fromLispTree(LispTree tree, String defaultId) {
    Builder b = new Builder().setId(defaultId);

    for (int i = 1; i < tree.children.size(); i++) {
      LispTree arg = tree.child(i);
      String label = arg.child(0).value;
      if ("id".equals(label)) {
        b.setId(arg.child(1).value);
      } else if ("utterance".equals(label)) {
        b.setUtterance(arg.child(1).value);
      } else if ("canonicalUtterance".equals(label)) {
        b.setUtterance(arg.child(1).value);
      } else if ("targetFormula".equals(label)) {
        b.setTargetFormula(Formulas.fromLispTree(arg.child(1)));
      } else if ("targetValue".equals(label) || "targetValues".equals(label)) {
        if (arg.children.size() != 2)
          throw new RuntimeException("Expect one target value");
        b.setTargetValue(Values.fromLispTree(arg.child(1)));
      } else if ("context".equals(label)) {
        b.setContext(new ContextValue(arg));
      }
    }
    b.setLanguageInfo(new LanguageInfo());
    b.setRelationInfo(new RelationInfo());

    Example ex = b.createExample();

    for (int i = 1; i < tree.children.size(); i++) {
      LispTree arg = tree.child(i);
      String label = arg.child(0).value;
      if ("tokens".equals(label)) {
        for (LispTree child : arg.child(1).children)
          ex.languageInfo.tokens.add(child.value);
      } else if ("lemmaTokens".equals(label)) {
        for (LispTree child : arg.child(1).children)
          ex.languageInfo.lemmaTokens.add(child.value);
      } else if ("posTags".equals(label)) {
        for (LispTree child : arg.child(1).children)
          ex.languageInfo.posTags.add(child.value);
      } else if ("nerTags".equals(label)) {
        for (LispTree child : arg.child(1).children)
          ex.languageInfo.nerTags.add(child.value);
      } else if ("nerValues".equals(label)) {
        for (LispTree child : arg.child(1).children)
          ex.languageInfo.nerValues.add("null".equals(child.value) ? null : child.value);
      } else if ("alternativeFormula".equals(label)) {
        ex.addAlternativeFormula(Formulas.fromLispTree(arg.child(1)));
      } else if ("evaluation".equals(label)) {
        ex.evaluation = Evaluation.fromLispTree(arg.child(1));
      } else if ("predDerivations".equals(label)) {
        // Featurized
        ex.predDerivations = new ArrayList<>();
        for (int j = 1; j < arg.children.size(); j++)
          ex.predDerivations.add(derivationFromLispTree(arg.child(j)));
      } else if ("rawDerivations".equals(label) || "derivations".equals(label)) {
        // Unfeaturized
        ex.predDerivations = new ArrayList<>();
        for (int j = 1; j < arg.children.size(); j++)
          ex.predDerivations.add(rawDerivationFromLispTree(arg.child(j)));
      } else if (!Sets.newHashSet("id", "utterance", "targetFormula", "targetValue", "targetValues", "context", "original").contains(label)) {
        throw new RuntimeException("Invalid example argument: " + arg);
      }
    }

    return ex;
  }

  public void preprocess() {
    // Get Full information
    InfoAnalyzer.CoreNLPInfo info = InfoAnalyzer.getSingleton().analyze(this.utterance);
    this.languageInfo = info.lanInfo;
    this.relationInfo = info.relInfo;
    this.genInfo = info.senInfo;

    // Update context
    List<ContextValue.Exchange> newExchanges = new ArrayList<ContextValue.Exchange>();
    newExchanges.add(new ContextValue.Exchange(this.utterance, null, null, this.genInfo));
    if (context != null)
      context = context.withNewExchange(newExchanges);
    else
      context = new ContextValue(null, null, newExchanges, null);

    this.targetValue = TargetValuePreprocessor.getSingleton().preprocess(this.targetValue, this);
  }

  public void log() {
    LogInfo.begin_track("Example: %s", utterance);
    LogInfo.logs("Tokens: %s", getTokens());
    LogInfo.logs("Lemmatized tokens: %s", getLemmaTokens());
    LogInfo.logs("POS tags: %s", languageInfo.posTags);
    LogInfo.logs("NER tags: %s", languageInfo.nerTags);
    LogInfo.logs("NER values: %s", languageInfo.nerValues);
    if (context != null)
      LogInfo.logs("context: %s", context);
    if (targetFormula != null)
      LogInfo.logs("targetFormula: %s", targetFormula);
    if (targetValue != null)
      LogInfo.logs("targetValue: %s", targetValue);
    LogInfo.logs("Sentiment: %s", genInfo.sentiment);
    LogInfo.logs("Keywords: %s", genInfo.keywords.toString());
    LogInfo.logs("Dependency children: %s", languageInfo.dependencyChildren);
    LogInfo.logs("Extracted relations: %s", relationInfo.relations.toString());
    LogInfo.end_track();
  }

  public void logWithoutContext() {
    LogInfo.begin_track("Example: %s", utterance);
    LogInfo.logs("Tokens: %s", getTokens());
    LogInfo.logs("Lemmatized tokens: %s", getLemmaTokens());
    LogInfo.logs("POS tags: %s", languageInfo.posTags);
    LogInfo.logs("NER tags: %s", languageInfo.nerTags);
    LogInfo.logs("NER values: %s", languageInfo.nerValues);
    if (targetFormula != null)
      LogInfo.logs("targetFormula: %s", targetFormula);
    if (targetValue != null)
      LogInfo.logs("targetValue: %s", targetValue);
    LogInfo.logs("Sentiment: %s", genInfo.sentiment);
    LogInfo.logs("Keywords: %s", genInfo.keywords.toString());
    LogInfo.logs("Dependency children: %s", languageInfo.dependencyChildren);
    LogInfo.logs("Extracted relations: %s", relationInfo.relations.toString());
    LogInfo.end_track();
  }

  public List<Derivation> getCorrectDerivations() {
    List<Derivation> res = new ArrayList<>();
    for (Derivation deriv : predDerivations) {
      if (deriv.compatibility == Double.NaN)
        throw new RuntimeException("Compatibility is not set");
      if (deriv.compatibility > 0)
        res.add(deriv);
    }
    return res;
  }

  public LispTree toLispTree(boolean outputPredDerivations) {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("example");

    if (id != null)
      tree.addChild(LispTree.proto.newList("id", id));
    if (utterance != null)
      tree.addChild(LispTree.proto.newList("utterance", utterance));
    if (targetFormula != null)
      tree.addChild(LispTree.proto.newList("targetFormula", targetFormula.toLispTree()));
    if (targetValue != null)
      tree.addChild(LispTree.proto.newList("targetValue", targetValue.toLispTree()));

    if (languageInfo != null) {
      if (languageInfo.tokens != null)
        tree.addChild(LispTree.proto.newList("tokens", LispTree.proto.newList(languageInfo.tokens)));
      if (languageInfo.posTags != null)
        tree.addChild(LispTree.proto.newList("posTags", Joiner.on(' ').join(languageInfo.posTags)));
      if (languageInfo.nerTags != null)
        tree.addChild(LispTree.proto.newList("nerTags", Joiner.on(' ').join(languageInfo.nerTags)));
    }

    if (relationInfo != null) {
      if (relationInfo.relations != null)
        tree.addChild(LispTree.proto.newList("relations", LispTree.proto.newList(relationInfo.relations)));
    }

    if (evaluation != null)
      tree.addChild(LispTree.proto.newList("evaluation", evaluation.toLispTree()));

    if (predDerivations != null && outputPredDerivations) {
      LispTree list = LispTree.proto.newList();
      list.addChild("predDerivations");
      for (Derivation deriv : predDerivations)
        list.addChild(derivationToLispTree(deriv));
      tree.addChild(list);
    }

    return tree;
  }

  /**
   * Parse a featurized derivation.
   *
   * Format:
   *   ({compatibility} {prob} {score} {value|null} {formula} {features})
   *   where {features} = (({key} {value}) ({key} {value}) ...)
   */
  public static Derivation derivationFromLispTree(LispTree item) {
    Derivation.Builder b = new Derivation.Builder()
        .cat(Rule.rootCat)
        .start(-1)
        .end(-1)
        .rule(Rule.nullRule)
        .children(new ArrayList<Derivation>());
    int i = 0;

    b.compatibility(Double.parseDouble(item.child(i++).value));
    b.prob(Double.parseDouble(item.child(i++).value));
    b.score(Double.parseDouble(item.child(i++).value));

    LispTree valueTree = item.child(i++);
    if (!valueTree.isLeaf() || !"null".equals(valueTree.value))
      b.value(Values.fromLispTree(valueTree));

    b.formula(Formulas.fromLispTree(item.child(i++)));

    FeatureVector fv = new FeatureVector();
    LispTree features = item.child(i++);
    for (int j = 0; j < features.children.size(); j++)
      fv.addFromString(features.child(j).child(0).value, Double.parseDouble(features.child(j).child(1).value));

    b.localFeatureVector(fv);

    return b.createDerivation();
  }

  public static LispTree derivationToLispTree(Derivation deriv) {
    LispTree item = LispTree.proto.newList();

    item.addChild(deriv.compatibility + "");
    item.addChild(deriv.prob + "");
    item.addChild(deriv.score + "");
    if (deriv.value != null)
      item.addChild(deriv.value.toLispTree());
    else
      item.addChild("null");
    item.addChild(deriv.formula.toLispTree());

    HashMap<String, Double> features = new HashMap<>();
    deriv.incrementAllFeatureVector(1, features);
    item.addChild(LispTree.proto.newList(features));

    return item;
  }

  /**
   * Parse a LispTree with the format created by deriv.toLispTree().
   * Due to the complexity, rules and children are not parsed.
   *
   * Format:
   *   (derivation [(formula {formula})] [(value {value})] [(type {type})]
   *               [(canonicalUtterance {canonicalUtterance})])
   * @param item
   * @return
   */
  public static Derivation rawDerivationFromLispTree(LispTree item) {
    Derivation.Builder b = new Derivation.Builder()
    .cat(Rule.rootCat)
    .start(-1).end(-1)
    .rule(Rule.nullRule)
    .children(new ArrayList<Derivation>());
    for (int i = 1; i < item.children.size(); i++) {
      LispTree arg = item.child(i);
      String label = arg.child(0).value;
      if ("formula".equals(label)) {
        b.formula(Formulas.fromLispTree(arg.child(1)));
      } else if ("value".equals(label)) {
        b.value(Values.fromLispTree(arg.child(1)));
      } else if ("type".equals(label)) {
        b.type(SemType.fromLispTree(arg.child(1)));
      } else if ("canonicalUtterance".equals(label)) {
        b.canonicalUtterance(arg.child(1).value);
      } else {
        throw new RuntimeException("Invalid example argument: " + arg);
      }
    }
    return b.createDerivation();
  }

  public static LispTree rawDerivationToLispTree(Derivation deriv) {
    return deriv.toLispTree();
  }

  public Map<String, Object> getTempState() {
    // Create the tempState if it doesn't exist.
    if (tempState == null)
      tempState = new HashMap<String, Object>();
    return tempState;
  }
  public void clearTempState() {
    tempState = null;
  }

  /**
   * Clean up things to save memory
   */
  public void clean() {
    predDerivations.clear();
    if (context.graph != null)
      context.graph.clean();
  }
}
