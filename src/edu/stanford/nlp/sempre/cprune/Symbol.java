package edu.stanford.nlp.sempre.cprune;

/**
 * Represents the leaf node of the parse tree.
 *
 * Any sub-derivation whose category is in CustomGrammar.baseCategories becomes a Symbol.
 */
public class Symbol implements Comparable<Symbol> {
  String category;
  String formula;
  Integer frequency;
  Integer index;

  public Symbol(String category, String formula, int frequency) {
    this.category = category;
    this.formula = formula;
    this.frequency = frequency;
  }

  public void computeIndex(String referenceString) {
    index = referenceString.indexOf(formula);
    if (index < 0) {
      index = Integer.MAX_VALUE;
    }
  }

  @Override
  public int compareTo(Symbol that) {
    return index.compareTo(that.index);
  }
}
