package edu.stanford.nlp.sempre.cprune;

import java.util.regex.Pattern;

import edu.stanford.nlp.sempre.Derivation;
import fig.basic.LogInfo;

public class FormulaPattern implements Comparable<FormulaPattern> {
  public String pattern;
  public Integer frequency;
  public Double score;

  public FormulaPattern(String pattern, Integer frequency) {
    this.pattern = pattern;
    this.frequency = frequency;
  }

  public Double complexity() {
    // Roughly the number of predicates
    return (double) (pattern.length() - pattern.replace("(@R", "***").replace("(", "").length());
  }

  @Override
  public String toString() {
    return "(" + pattern + ", " + frequency + ")";
  }

  @Override
  public int compareTo(FormulaPattern that) {
    if (this.frequency > that.frequency) {
      return -1;
    } else if (this.frequency < that.frequency) {
      return 1;
    } else {
      return this.complexity().compareTo(that.complexity());
    }
  }

  // ============================================================
  // Utilities
  // ============================================================

  private static Pattern reverseRelation = Pattern.compile("!(fb:[._a-z0-9]+)");
  private static Pattern varName = Pattern.compile("\\((lambda|var) [a-z0-9]+");
  private static Pattern compare = Pattern.compile("(<=|>=|>|<)");
  private static Pattern whitespace = Pattern.compile("\\s+");

  public static String convertToIndexedPattern(Derivation deriv) {
    String formula = deriv.formula.toString();

    // These can interfere with (number 1)
    formula = formula.replace("argmax (number 1) (number 1)", "argmax");
    formula = formula.replace("argmin (number 1) (number 1)", "argmin");

    formula = removePropertyPredicates(formula);
    formula = CustomGrammar.getIndexedSymbolicFormula(deriv, formula);

    formula = formula.replace("fb:type.object.type fb:type.row", "@type @row");
    formula = reverseRelation.matcher(formula).replaceAll("(reverse $1)");
    formula = formula.replace("fb:row.row.index", "(reverse (lambda x ((reverse @index) (var x))))");
    formula = formula.replace("fb:row.row.next", "@next");
    formula = varName.matcher(formula).replaceAll("($1 x");
    formula = formula.replace("reverse", "@R");
    formula = compare.matcher(formula).replaceAll("@compare");
    formula = whitespace.matcher(formula).replaceAll(" ");

    if (CollaborativePruner.opts.verbose >= 2)
      LogInfo.logs("PATTERN: %s -> %s", deriv.formula, formula);
    return formula;
  }

  private static Pattern cellProperty = Pattern.compile("!?fb:cell\\.cell\\.[_a-z0-9]+|\\(reverse fb:cell\\.cell\\.[_a-z0-9]+\\)");

  /**
   * Remove cell property relations (fb:cell.cell.*)
   */
  public static String removePropertyPredicates(String formula) {
    formula = cellProperty.matcher(formula).replaceAll("@PPT");
    while (formula.contains("@PPT")) {
      int begin = formula.indexOf("(@PPT");
      if (begin == -1) {
        formula = formula.replace("@PPT", "");
        break;
      }
      // Find the matching parenthesis
      int count = 1;
      for (int i = begin + 1; i < formula.length(); i++) {
        if (formula.charAt(i) == '(') {
          count++;
        } else if (formula.charAt(i) == ')') {
          count--;
          if (count == 0) {
            int end = i;
            formula = formula.substring(0, begin) + formula.substring(begin + 6, end) + formula.substring(end + 1, formula.length());
            break;
          }
        }
        if (i == formula.length() - 1) {
          LogInfo.fails("Unbalanced parentheses: %s", formula);
        }
      }
    }
    return formula;
  }

}
