package edu.stanford.nlp.sempre.fbalignment.lexicons;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.stanford.nlp.sempre.Formula;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.StringUtils;
import fig.basic.MapUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LexicalEntry {

  public String textDescription; //the query as submitted to the lexicon
  public String normalizedTextDesc; //the query after normalization
  public Set<String> fbDescriptions; //descriptions matching the formula
  public Formula formula;
  public EntrySource source;
  public double popularity;
  public double distance;

  public LexicalEntry(String textDescription, String normalizedTextDesc, Set<String> fbDescriptions, Formula formula, EntrySource source, double popularity, double distance) {
    this.textDescription = textDescription;
    this.normalizedTextDesc = normalizedTextDesc;
    this.fbDescriptions = fbDescriptions;
    this.formula = formula;
    this.source = source;
    this.popularity = popularity;
    this.distance = distance;
  }

  public Formula getFormula() {
    return formula;
  }

  public double getPopularity() {
    return popularity;
  }

  public double getDistance() {
    return distance;
  }

  private String stringRepn;
  public String toString() {
    if (stringRepn == null) {
      stringRepn = textDescription + " (" + normalizedTextDesc + ")" +
          ", FB: " + fbDescriptions +
          ", formula: " + formula +
          ", source: " + source +
          ", popularity: " + popularity +
          ", distance: " + distance;
    }
    return stringRepn;
  }
  
  public static int computeEditDistance(String query, Set<String> descriptions) {

    int distance = Integer.MAX_VALUE;
    for (String description : descriptions) {
      int currDistance = StringUtils.editDistance(query, description.toLowerCase());
      if (currDistance < distance) {
        distance = currDistance;
      }
    }
    return Math.min(15, distance);
  }

  public static class BinaryLexicalEntry extends LexicalEntry {

    public String expectedType1;
    public String expectedType2;
    public String unitId = "";
    public String unitDescription = "";
    public Map<String,Double> alignmentScores;
    public String fullLexeme; //the lexeme as it is in the alignment without some normalization applied before uploading the lexicon

    public BinaryLexicalEntry(String textDescription, String normalizedTextDesc, Set<String> fbDescriptions, Formula formula,
                              EntrySource source, double popularity, String expectedType1, String expectedType2, Map<String,Double> alignmentScores, String fullLexeme) {
      super(textDescription, normalizedTextDesc, fbDescriptions, formula, source, popularity, computeEditDistance(textDescription, fbDescriptions));
      this.expectedType1 = expectedType1;
      this.expectedType2 = expectedType2;
      this.alignmentScores = alignmentScores;
      this.fullLexeme = fullLexeme;
    }

    public BinaryLexicalEntry(String textDescription, String normalizedTextDesc, Set<String> fbDescriptions, Formula formula, EntrySource source, double popularity,
                              String expectedType1, String expectedType2, String unitId, String unitDesc, Map<String,Double> alignmentScores, String fullLexeme) {
      super(textDescription, normalizedTextDesc, fbDescriptions, formula, source, popularity, computeEditDistance(textDescription, fbDescriptions));
      this.expectedType1 = expectedType1;
      this.expectedType2 = expectedType2;
      this.unitId = unitId;
      this.unitDescription = unitDesc;
      this.alignmentScores = alignmentScores;
      this.fullLexeme = fullLexeme;
      assert (fullLexeme.contains(normalizedTextDesc));
    }

    public boolean identicalFormulaInfo(Object other) {
      if (!(other instanceof BinaryLexicalEntry))
        return false;
      BinaryLexicalEntry otherBinary = (BinaryLexicalEntry) other;

      if (!formula.equals(otherBinary.formula))
        return false;
      if (Math.abs(popularity - otherBinary.popularity) > 0.000001)
        return false;
      if (!expectedType1.equals(otherBinary.expectedType1))
        return false;
      if (!expectedType2.equals(otherBinary.expectedType2))
        return false;
      if (!unitId.equals(otherBinary.unitId))
        return false;
      if (!unitDescription.equals(otherBinary.unitDescription))
        return false;
      return true;
    }

    public String getExpectedType1() {
      return expectedType1;
    }

    public String getExpectedType2() {
      return expectedType2;
    }

    public String getUnitId() {
      return unitId;
    }

    public String getUnitDescription() {
      return unitDescription;
    }

    private String stringRepn;
    public String toString() {
      if (stringRepn == null) {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        sb.append(", " + expectedType1 + " x " + expectedType2);
        if (unitId != null) {
          sb.append(", " + unitId + ":" + unitDescription);
        }
        if (alignmentScores.size() > 0)
          sb.append(", " + alignmentScores);
        stringRepn = sb.toString();
      }
      return stringRepn;
    }

    public boolean isFullLexemeEqualToNormalizedText() {
      return fullLexeme.equals(normalizedTextDesc);
    }

    public String[] getLeftContext() {
      if (fullLexeme.startsWith(normalizedTextDesc))
        return new String[]{};
      String leftContext = fullLexeme.substring(0, fullLexeme.indexOf(normalizedTextDesc)).trim();
      return leftContext.split("\\s+");
    }

    public String[] getRightContext() {
      if (fullLexeme.endsWith(normalizedTextDesc))
        return new String[]{};
      String rightContext = fullLexeme.substring(fullLexeme.indexOf(normalizedTextDesc) + normalizedTextDesc.length()).trim();
      return rightContext.split("\\s+");
    }

    public double jaccard() {
      double intersection = MapUtils.getDouble(alignmentScores, BinaryLexicon.INTERSECTION, 0.0); 
      if (intersection < 2.01)
        intersection = 0.0;
      double nlSize = MapUtils.getDouble(alignmentScores, BinaryLexicon.NL_TYPED, 0.0); 
      double fbSize = MapUtils.getDouble(alignmentScores, BinaryLexicon.FB_TYPED, 0.0);
      return intersection / (nlSize + fbSize - intersection + 5);
    }

    /** rids all counts and keeps jaccard only */
    public void retainJaccardOnly() {
      double jaccard = jaccard();
      alignmentScores.clear();
      alignmentScores.put("jaccard", jaccard);
    }
  }

  public static class EntityLexicalEntry extends LexicalEntry {

    public Set<String> types = new HashSet<String>();
    public Counter<String> tokenEditDistanceFeatures;

    public EntityLexicalEntry(String textDescription, String normalizedTextDesc, Set<String> fbDescriptions, Formula formula, EntrySource source, double popularity, double distance, Set<String> types, Counter<String> tokenEditDistanceFeatures) {
      super(textDescription, normalizedTextDesc, fbDescriptions, formula, source, popularity, distance);
      this.types = types;
      this.tokenEditDistanceFeatures = tokenEditDistanceFeatures;
    }

    public Set<String> getTypes() {
      return types;
    }

    public String toString() {
      return super.toString() + ", " + types;
    }
  }

  public static class UnaryLexicalEntry extends LexicalEntry {

    public Set<String> types = new HashSet<String>();
    public Map<String,Double> alignmentScores;

    public UnaryLexicalEntry(String textDescription, String normalizedTextDesc, Set<String> fbDescriptions, Formula formula, EntrySource source, double popularity,
        Map<String,Double> alignmentScores, Set<String> types) {
      super(textDescription, normalizedTextDesc, fbDescriptions, formula, source, popularity, computeEditDistance(textDescription, fbDescriptions));
      this.types = types;
      this.alignmentScores = alignmentScores;
    }

    public Set<String> getTypes() {
      return types;
    }

    String stringRepn;
    public String toString() {
      if (stringRepn == null)
        stringRepn = super.toString() + ", " + types;
      return stringRepn;
    }
  }
  
  /**
  * Holds the essential parts of a value in a lexicon
  * @author jonathanberant
  *
  */
 public static class LexiconValue {

   @JsonProperty public String lexeme;
   @JsonProperty public Formula formula;
   @JsonProperty public String source;
   @JsonProperty public Map<String,Double> features;

   @JsonCreator
   public LexiconValue(@JsonProperty("normLexeme") String lexeme,
                       @JsonProperty("formula") Formula formula,
                       @JsonProperty("source") String source,
                       @JsonProperty("features") Map<String,Double> features) {
     this.lexeme = lexeme;
     this.formula = formula;
     this.source = source;
     this.features = features;
   }
 }
}
