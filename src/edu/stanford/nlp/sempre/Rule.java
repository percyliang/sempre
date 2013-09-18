package edu.stanford.nlp.sempre;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import fig.basic.LispTree;
import fig.basic.Pair;

import java.util.List;

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

  @JsonProperty String lhs;  // Left-hand side: category.
  @JsonProperty
  List<String> rhs;  // Right-hand side: sequence of categories (have $ prefix) and tokens.
  @JsonProperty
  SemanticFn sem;  // Takes derivations corresponding to RHS categories and produces a set of derivations corresponding to LHS.
  List<Pair<String, Double>> info;  // Extra info

  private String semRepn = null;
  public String getSemRepn() {
    if (semRepn == null) semRepn = sem.getClass().getSimpleName();
    return semRepn;
  }

  public Rule() {}

  @JsonCreator
  public Rule(@JsonProperty("lhs") String lhs,
      @JsonProperty("rhs") List<String> rhs,
      @JsonProperty("sem") SemanticFn sem) {
    this.lhs = lhs;
    this.rhs = rhs;
    this.sem = sem;
  }

  @Override
  public String toString() {
    if (stringRepn == null)
      stringRepn = lhs + " -> " + (rhs == null ? "" : Joiner.on(' ').join(rhs)) + " " + sem;
    return stringRepn;
  }
  private String stringRepn;  // Cache toString()

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Rule rule = (Rule) o;

    if (info != null ? !info.equals(rule.info) : rule.info != null) return false;
    if (lhs != null ? !lhs.equals(rule.lhs) : rule.lhs != null) return false;
    if (rhs != null ? !rhs.equals(rule.rhs) : rule.rhs != null) return false;
    if (sem != null ? !sem.equals(rule.sem) : rule.sem != null) return false;

    return true;
  }

  public void addInfo(String key, double value) {
    if (info == null) info = Lists.newArrayList();
    info.add(Pair.newPair(key, value));
  }

  // Accessors
  public SemanticFn getSem() { return sem; }

  // Return whether rule has form A -> B (both LHS and RHS contain one category).
  public boolean isCatUnary() { return rhs.size() == 1 && isCat(rhs.get(0)); }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("rule");
    tree.addChild(lhs);
    tree.addChild(LispTree.proto.newList(rhs));
    tree.addChild(sem.toLispTree());
    return tree;
  }

  public String getLhs() { 
    return lhs;
  }
}
