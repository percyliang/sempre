package edu.stanford.nlp.sempre;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import fig.basic.LispTree;
import fig.basic.Pair;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A rule specifies how to take a right hand of terminals and non-terminals.
 *
 * @author Percy Liang
 */
public class Rule {
  public static Rule nullRule = new Rule(null, null, null);

  // Categories begin with $.
  public static boolean isCat(String item) { return item.charAt(0) == '$'; }

  public static final String rootCat = "$ROOT";
  public static final String tokenCat = "$TOKEN";
  public static final String phraseCat = "$PHRASE";  // Sequence of tokens
  public static final String lemmaTokenCat = "$LEMMA_TOKEN";  // Lemmatized version
  public static final String lemmaPhraseCat = "$LEMMA_PHRASE";  // Lemmatized version
  public static final List<String> specialCats = Lists.newArrayList(rootCat, tokenCat, phraseCat, lemmaTokenCat, lemmaPhraseCat);
  public final String lhs;  // Left-hand side: category.
  public final List<String> rhs;  // Right-hand side: sequence of categories (have $ prefix) and tokens.
  public final SemanticFn sem;  // Takes derivations corresponding to RHS categories and produces a set of derivations corresponding to LHS.
  public List<Pair<String, Double>> info;  // Extra info
  public RuleSource source = null; // for tracking where the rule comes from when they are induced

  // Cache the semanticRepn
  public String getSemRepn() {
    if (semRepn == null) semRepn = sem.getClass().getSimpleName();
    return semRepn;
  }
  private String semRepn = null;

  public Rule(String lhs,
              List<String> rhs,
              SemanticFn sem) {
    this.lhs = lhs;
    this.rhs = rhs;
    this.sem = sem;
  }

  @Override
  public String toString() {
    if (stringRepn == null) {
      String semStr = sem == null? "NullSemanticFn" : sem.toString();
      //int maxLength = 100;
      //if (semStr.length() > maxLength)
      //  semStr = String.format("%s...(%d total)", semStr.substring(0,maxLength), semStr.length());
      stringRepn = lhs + " -> " + (rhs == null ? "" : Joiner.on(' ').join(rhs)) + " " + semStr;
    }
    return stringRepn;
  }
  private String stringRepn;  // Cache toString()

  // Get/set info
  public void addInfo(String key, double value) {
    if (info == null) info = Lists.newArrayList();
    info.add(Pair.newPair(key, value));
  }
  public Rule setInfo(Rule rule) { this.info = rule.info; return this; }

  // Accessors
  public SemanticFn getSem() { return sem; }
  public String getLhs() { return lhs; }

  // Return whether rule has form A -> B (both LHS and RHS contain one category).
  public boolean isCatUnary() { return rhs.size() == 1 && isCat(rhs.get(0)); }

  // Return if all RHS tokens are terminals
  public boolean isRhsTerminals() {
    for (int i = 0; i < rhs.size(); ++i) {
      if (isCat(rhs.get(i)))
        return false;
    }
    return true;
  }

  // Return the number of categories on the RHS
  public int numRhsCats() {
    int ret = 0;
    for (int i = 0; i < rhs.size(); ++i) {
      if (isCat(rhs.get(i)))
        ret++;
    }
    return ret;
  }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("rule");
    tree.addChild(lhs);
    tree.addChild(LispTree.proto.newList(rhs));
    tree.addChild(sem.toLispTree());
    if (info != null) {
      for (Pair<String, Double> p : info)
        tree.addChild(LispTree.proto.newList(p.getFirst(), "" + p.getSecond()));
    }
    if (source != null)
      tree.addChild(source.toJson());
    return tree;
  }

  /* Extract tag info */
  public double getInfoTag(String infoTag) {
    if (info != null) {
      for (Pair<String, Double> p : info) {
        if (p.getFirst().equals(infoTag)) return p.getSecond();
      }
    }
    return -1.0;
  }

  public boolean isFloating() {
    double f = getInfoTag("floating");
    double a = getInfoTag("anchored");
    if (f == 1.0)
      return true;
    else if (f == 0.0)
      return false;
    else
      return a == 1.0 ? false : FloatingParser.opts.defaultIsFloating;
  }

  public boolean isAnchored() {
    double f = getInfoTag("floating");
    double a = getInfoTag("anchored");
    if (a == 1.0)
      return true;
    else if (a == 0.0)
      return false;
    else
      return f == 1.0 ? false : !FloatingParser.opts.defaultIsFloating;
  }

  public boolean isInduced() {
    double a = getInfoTag("induced");
    if (a == 1.0) return true;
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Rule)) return false;
    return ((Rule)o).toString().equals(this.toString());
  }
  @Override
  public int hashCode() {
    return this.toString().hashCode();
  }

  public String toJson() {
    Map<String, Object> jsonMap = new LinkedHashMap<>();
    jsonMap.put("lhs", lhs);
    jsonMap.put("rhs", rhs);
    if (source != null) {
      jsonMap.put("source", source);
    }
    if (info != null) {
      for (Pair<String, Double> p : info)
        jsonMap.put(p.getFirst(), p.getSecond());
    }
    jsonMap.put("sem", sem.toString());
    return Json.writeValueAsStringHard(jsonMap);
  }
}
