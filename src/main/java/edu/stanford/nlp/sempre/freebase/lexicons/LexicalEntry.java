package edu.stanford.nlp.sempre.freebase.lexicons;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import edu.stanford.nlp.sempre.Formula;
import edu.stanford.nlp.sempre.SemType;
import edu.stanford.nlp.sempre.SemTypeHierarchy;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.StringUtils;
import fig.basic.LispTree;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class LexicalEntry {

  public final String textDescription; // the query as submitted to the lexicon
  public final String normalizedTextDesc; // the query after normalization
  public final Set<String> fbDescriptions; // descriptions matching the formula
  public final Formula formula;
  public EntrySource source;
  public final double popularity;
  public final double distance;


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

  // Input: set of types coming from the lexicon {fb:common.topic, fb:people.person, ...}
  // Output: remove any element which is in the transitive closure. {fb:people.person, ...}
  // TODO(pliang): replace HashSet with something lighter weight.
  public static SemType setToType(Set<String> types) {
    Set<String> resultTypes = new HashSet<>(types);
    for (String entityType : types) {
      for (String supertype : SemTypeHierarchy.singleton.getSupertypes(entityType)) {
        if (!supertype.equals(entityType))
          resultTypes.remove(supertype);
      }
    }
    return SemType.newUnionSemType(resultTypes);
  }

  public static class BinaryLexicalEntry extends LexicalEntry {

    public String expectedType1;
    public String expectedType2;
    public String unitId = "";
    public String unitDescription = "";
    public Map<String, Double> alignmentScores;
    public String fullLexeme; // the lexeme as it is in the alignment without some normalization applied before uploading the lexicon

    public BinaryLexicalEntry(String textDescription, String normalizedTextDesc, Set<String> fbDescriptions, Formula formula, EntrySource source, double popularity,
        String expectedType1, String expectedType2, String unitId, String unitDesc, Map<String, Double> alignmentScores, String fullLexeme) {
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
  }

  public static class EntityLexicalEntry extends LexicalEntry {

    public Set<String> types = new HashSet<>();
    public SemType type;
    public Counter<String> entityFeatures;

    public EntityLexicalEntry(String textDescription, String normalizedTextDesc, Set<String> fbDescriptions,
                              Formula formula, EntrySource source, double popularity, double distance, Set<String> types,
                              Counter<String> entityFeatures) {
      super(textDescription, normalizedTextDesc, fbDescriptions, formula, source, popularity, distance);
      this.types = types;
      this.type = setToType(types);
      this.entityFeatures = entityFeatures;
    }

    public String toString() {
      return super.toString() + ", " + types;
    }
  }

  public static class UnaryLexicalEntry extends LexicalEntry {

    public Set<String> types = new HashSet<String>();
    public SemType type;
    public Map<String, Double> alignmentScores;

    public UnaryLexicalEntry(String textDescription, String normalizedTextDesc, Set<String> fbDescriptions, Formula formula, EntrySource source, double popularity,
        Map<String, Double> alignmentScores, Set<String> types) {
      super(textDescription, normalizedTextDesc, fbDescriptions, formula, source, popularity, computeEditDistance(textDescription, fbDescriptions));
      this.types = types;
      this.type = setToType(types);
      this.alignmentScores = alignmentScores;
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
    @JsonProperty public Map<String, Double> features;

    @JsonCreator
    public LexiconValue(@JsonProperty("normLexeme") String lexeme,
        @JsonProperty("formula") Formula formula,
        @JsonProperty("source") String source,
        @JsonProperty("features") Map<String, Double> features) {
      this.lexeme = lexeme;
      this.formula = formula;
      this.source = source;
      this.features = features;
    }
  }

  public static class LexicalEntrySerializer {
    // Utilities that should move into fig later.
    static Counter<String> counterFromLispTree(LispTree tree) {
      Counter<String> counter = new ClassicCounter<String>();
      for (int i = 0; i < tree.children.size(); i++)
        counter.incrementCount(tree.child(i).child(0).value, Double.parseDouble(tree.child(i).child(1).value));
      return counter;
    }
    static LispTree counterToLispTree(Counter<String> counter) {
      LispTree tree = LispTree.proto.newList();
      for (String feature : counter.keySet())
        tree.addChild(LispTree.proto.newList(feature, "" + counter.getCount(feature)));
      return tree;
    }

    static Map<String, Double> featureMapFromLispTree(LispTree tree) {
      Map<String, Double> featureMap = new TreeMap<String, Double>();
      for (int i = 0; i < tree.children.size(); i++)
        featureMap.put(tree.child(i).child(0).value, Double.parseDouble(tree.child(i).child(1).value));
      return featureMap;
    }

    static LispTree featureMapToLispTree(Map<String, Double> featureMap) {
      LispTree tree = LispTree.proto.newList();
      for (String feature : featureMap.keySet())
        tree.addChild(LispTree.proto.newList(feature, "" + featureMap.get(feature)));
      return tree;
    }


    static Set<String> setFromLispTree(LispTree tree) {
      Set<String> set = new HashSet<String>();
      for (int i = 0; i < tree.children.size(); i++)
        set.add(tree.child(i).value);
      return set;
    }
    static LispTree setToLispTree(Set<String> set) {
      LispTree tree = LispTree.proto.newList();
      for (String x : set)
        tree.addChild(x);
      return tree;
    }

    static String[] stringArrayFromLispTree(LispTree tree) {
      String[] result = new String[tree.children.size()];
      for (int i = 0; i < tree.children.size(); i++)
        result[i] = tree.child(i).value;
      return result;
    }
    static LispTree stringArrayToLispTree(String[] array) {
      LispTree tree = LispTree.proto.newList();
      for (String x : array)
        tree.addChild(x);
      return tree;
    }

    public static LexicalEntry entryFromLispTree(LispTree tree) {
      int i = 1;
      if (tree.child(0).value.equals("entity")) {

        String textDescription = tree.child(i++).value;
        String normalizedTextDesc = tree.child(i++).value;
        Set<String> fbDescriptions = setFromLispTree(tree.child(i++));
        Formula formula = Formula.fromString(tree.child(i++).value);
        EntrySource source = EntrySource.parseSourceDesc(tree.child(i++).value);
        double popularity = Double.parseDouble(tree.child(i++).value);
        double distance = Double.parseDouble(tree.child(i++).value);
        Set<String> types = setFromLispTree(tree.child(i++));
        Counter<String> tokenEditDistanceFeatures = counterFromLispTree(tree.child(i++));

        return new LexicalEntry.EntityLexicalEntry(
            textDescription, normalizedTextDesc, fbDescriptions, formula,
            source, popularity, distance, types, tokenEditDistanceFeatures);
      } else if (tree.child(0).value.equals("unary")) {
        String textDescription = tree.child(i++).value;
        String normalizedTextDesc = tree.child(i++).value;
        Set<String> fbDescriptions = setFromLispTree(tree.child(i++));
        Formula formula = Formula.fromString(tree.child(i++).value);
        EntrySource source = EntrySource.parseSourceDesc(tree.child(i++).value);
        double popularity = Double.parseDouble(tree.child(i++).value);
        Double.parseDouble(tree.child(i++).value);
        Map<String, Double> alignmentScores = featureMapFromLispTree(tree.child(i++));
        Set<String> types = setFromLispTree(tree.child(i++));
        return new LexicalEntry.UnaryLexicalEntry(
            textDescription, normalizedTextDesc, fbDescriptions, formula, source,
            popularity, alignmentScores, types);
      } else if (tree.child(0).value.equals("binary")) {
        String textDescription = tree.child(i++).value;
        String normalizedTextDesc = tree.child(i++).value;
        Set<String> fbDescriptions = setFromLispTree(tree.child(i++));
        Formula formula = Formula.fromString(tree.child(i++).value);
        EntrySource source = EntrySource.parseSourceDesc(tree.child(i++).value);
        double popularity = Double.parseDouble(tree.child(i++).value);
        Double.parseDouble(tree.child(i++).value); // this is computed in the constructor so need not save it
        String expectedType1 = tree.child(i++).value;
        String expectedType2 = tree.child(i++).value;
        String unitId = tree.child(i++).value;
        String unitDescription = tree.child(i++).value;
        Map<String, Double> alignmentScores = featureMapFromLispTree(tree.child(i++));
        String fullLexeme = tree.child(i++).value;
        return new LexicalEntry.BinaryLexicalEntry(
            textDescription, normalizedTextDesc, fbDescriptions, formula, source,
            popularity, expectedType1, expectedType2, unitId, unitDescription, alignmentScores, fullLexeme);
      } else {
        throw new RuntimeException("Invalid: " + tree);
      }
    }

    public static String emptyIfNull(String s) { return s == null ? "" : s; }

    public static LispTree entryToLispTree(LexicalEntry rawEntry) {
      LispTree result = LispTree.proto.newList();
      if (rawEntry instanceof LexicalEntry.EntityLexicalEntry) {
        LexicalEntry.EntityLexicalEntry entry = (LexicalEntry.EntityLexicalEntry) rawEntry;
        result.addChild("entity");

        result.addChild(entry.textDescription);
        result.addChild(entry.normalizedTextDesc);
        result.addChild(setToLispTree(entry.fbDescriptions));
        result.addChild(entry.formula.toString());
        result.addChild(entry.source.toString());
        result.addChild("" + entry.popularity);
        result.addChild("" + entry.distance);
        result.addChild(setToLispTree(entry.types));
        result.addChild(counterToLispTree(entry.entityFeatures));
      } else if (rawEntry instanceof LexicalEntry.UnaryLexicalEntry) {
        LexicalEntry.UnaryLexicalEntry entry = (LexicalEntry.UnaryLexicalEntry) rawEntry;
        result.addChild("unary");

        result.addChild(entry.textDescription);
        result.addChild(entry.normalizedTextDesc);
        result.addChild(setToLispTree(entry.fbDescriptions));
        result.addChild(entry.formula.toString());
        result.addChild(entry.source.toString());
        result.addChild("" + entry.popularity);
        result.addChild("" + entry.distance);
        result.addChild(featureMapToLispTree(entry.alignmentScores));
        result.addChild(setToLispTree(entry.types));
      } else if (rawEntry instanceof LexicalEntry.BinaryLexicalEntry) {
        LexicalEntry.BinaryLexicalEntry entry = (LexicalEntry.BinaryLexicalEntry) rawEntry;
        result.addChild("binary");

        result.addChild(entry.textDescription);
        result.addChild(entry.normalizedTextDesc);
        result.addChild(setToLispTree(entry.fbDescriptions));
        result.addChild(entry.formula.toString());
        result.addChild(entry.source.toString());
        result.addChild("" + entry.popularity);
        result.addChild("" + entry.distance);
        result.addChild(entry.expectedType1);
        result.addChild(entry.expectedType2);
        result.addChild(emptyIfNull(entry.unitId));
        result.addChild(emptyIfNull(entry.unitDescription));
        result.addChild(featureMapToLispTree(entry.alignmentScores));
        result.addChild(entry.fullLexeme);
      }
      return result;
    }
  }
}
