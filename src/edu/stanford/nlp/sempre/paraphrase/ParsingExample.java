package edu.stanford.nlp.sempre.paraphrase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;

import edu.stanford.nlp.sempre.Evaluation;
import edu.stanford.nlp.sempre.Formula;
import edu.stanford.nlp.sempre.Json;
import edu.stanford.nlp.sempre.LanguageInfo;
import edu.stanford.nlp.sempre.Value;
import fig.basic.LogInfo;

/**
 * ParsingExample is an input-out pair. The ParsingExample object stores both the input,
 * preprocessing, and output of the parser.
 * In ParsingExample we have derivations that are paraphrase mapped to a formula
 * @author jonathanberant
 *
 */
@JsonIgnoreProperties(ignoreUnknown=true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParsingExample {
  
  public static class JsonViews {
    public static class WithDerivations {}
    public static class WithDPChart {}
  }

  //// Information from the input file.

  // Unique identifier for this example.
  @JsonProperty public final String id;

  // Input utterance
  @JsonProperty public final String utterance;

  // What we should try to predict.
  @JsonProperty public Formula targetFormula;  // Logical form
  @JsonProperty public Value targetValue;  // Answer

  //// Information after preprocessing (e.g., tokenization, POS tagging, NER, syntactic parsing, etc.).
  @JsonProperty public LanguageInfo languageInfo = null;

  //// Output of the parser.
  // Predicted derivations: sorted by score.
  @JsonProperty @JsonView(JsonViews.WithDerivations.class)
  List<ParaphraseDerivation> predParaDeriv = new ArrayList<>();

  private Evaluation evaluation;

  public static class Builder {
    private String id;
    private String utterance;
    private Formula targetFormula;
    private Value targetValue;
    private List<ParaphraseDerivation> predProofDers;
    private LanguageInfo languageInfo;

    public Builder setId(String id) {
      this.id = id;
      return this;
    }
    public Builder setUtterance(String utterance) {
      this.utterance = utterance;
      return this;
    }
    public Builder setTargetFormula(Formula targetFormula) {
      this.targetFormula = targetFormula;
      return this;
    }
    public Builder setTargetValue(Value targetValue) {
      this.targetValue = targetValue;
      return this;
    }
    public Builder setPredProofs(List<ParaphraseDerivation> predProofDers) {
      this.predProofDers = predProofDers;
      return this;
    }
    public Builder setLanguageInfo(LanguageInfo languageInfo) {
      this.languageInfo = languageInfo;
      return this;
    }
    public Builder withExample(ParsingExample ex) {
      setId(ex.id);
      setUtterance(ex.utterance);
      setTargetFormula(ex.targetFormula);
      setTargetValue(ex.targetValue);
      setPredProofs(ex.predParaDeriv);
      return this;
    }

    public ParsingExample createExample() {
      return new ParsingExample(
          id, utterance, targetFormula,
          targetValue, predProofDers, languageInfo);
    }
  }

  @JsonCreator
  public ParsingExample(@JsonProperty("id") String id,
                 @JsonProperty("utterance") String utterance,
                 @JsonProperty("targetFormula") Formula targetFormula,
                 @JsonProperty("targetValue") Value targetValue,
                 @JsonProperty("predProofDers") List<ParaphraseDerivation> predProofDers,
                 @JsonProperty("languageInfo") LanguageInfo languageInfo) {
    this.id = id;
    this.utterance = utterance;
    this.targetFormula = targetFormula;
    this.targetValue = targetValue;
    this.predParaDeriv = predProofDers == null ? new ArrayList<ParaphraseDerivation>() : predProofDers;
    this.languageInfo = languageInfo;
  }

  // Accessors
  public String getId() { return id; }
  public String getUtterance() { return utterance; }
  public Evaluation getEvaluation() { return evaluation; }
  public int numTokens() { return languageInfo.tokens.size(); }
  public List<ParaphraseDerivation> getProofDerivations() { return predParaDeriv; }
  public void setTargetFormula(Formula targetFormula) {
    this.targetFormula = targetFormula;
  }
  public void setTargetValue(Value targetValue) {
    this.targetValue = targetValue;
  }

  public void setEvaluation(Evaluation eval) { evaluation = eval; }

  public String toJson() {
    return Json.writeValueAsStringHard(this);
  }

  public static ParsingExample fromJson(String json) {
    return Json.readValueHard(json, ParsingExample.class);
  }

  public void preprocess() {
    if (this.languageInfo == null)
      this.languageInfo = new LanguageInfo();
    this.languageInfo.analyze(this.utterance);
    this.log();
  }
  
  public List<String> getTokens() {
    return languageInfo.tokens;
  }
  public List<String> getLemmaTokens() { return languageInfo.lemmaTokens; }
  

  public void log() {
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
    LogInfo.end_track();
  }

  public void clearPredDerivations() {
    predParaDeriv.clear();
  }
  
  public void addPrediction(ParaphraseDerivation p) {
    predParaDeriv.add(p);
  }
  
  public void addPredictions(Collection<ParaphraseDerivation> c) {
    predParaDeriv.addAll(c);
  }

  public void sortDerivations() {
    Collections.sort(predParaDeriv, new ParaphraseDerivation.ParaphraseDerivationComparator());
  }
  
  public void clear() {
    for(ParaphraseDerivation paraphraseDerivation: predParaDeriv)
      paraphraseDerivation.clear();
    predParaDeriv.clear();
  }
}
