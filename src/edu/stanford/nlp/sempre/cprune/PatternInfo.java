package edu.stanford.nlp.sempre.cprune;

import java.util.*;
import edu.stanford.nlp.sempre.*;

public class PatternInfo {
  Set<String> baseCategories = new HashSet<String>(Arrays.asList("$Unary", "$Binary", "$Entity", "$Property"));

  public static String removePropertyPredicates(String formula) {
    formula = RegexReplaceManager.replace(formula, "fb:cell\\.cell\\.[_0-9a-z]+", "@PPT");
    formula = formula.replace("(reverse @PPT)", "@PPT");

    while (formula.contains("@PPT")) {
      int begin = formula.indexOf("(@PPT");
      if (begin == -1) {
        formula = formula.replace("@PPT", "");
        ;
        break;
      }
      int count = 1;
      for (int i = begin + 1; i < formula.length(); i++) {
        if (formula.charAt(i) == '(') {
          count += 1;
        } else if (formula.charAt(i) == ')') {
          count -= 1;
        }
        if (count == 0) {
          int end = i;
          formula = formula.substring(0, begin) + formula.substring(begin + 6, end) + formula.substring(end + 1, formula.length());
          break;
        }
        if (i == formula.length() - 1) {
          System.out.println(formula);
        }
      }
    }
    return formula;
  }

  public static String convertToPattern(Derivation deriv) {
    String formula = deriv.formula.toString();

    // These can interfere with (number 1)
    formula = formula.replace("argmax (number 1) (number 1)", "argmax");
    formula = formula.replace("argmin (number 1) (number 1)", "argmin");

    formula = removePropertyPredicates(formula);

    formula = formula.replace("fb:type.object.type fb:type.row", "@type @row");

    formula = RegexReplaceManager.replace(formula, "!fb:[._0-9a-z]+", "(reverse $0)").replace("reverse !fb", "reverse fb");
    formula = formula.replace("fb:row.row.index", "(reverse (lambda x ((reverse @index) (var x))))");
    formula = formula.replace("fb:row.row.next", "@next");
    formula = RegexReplaceManager.replace(formula, "\\(lambda [a-z]", "\\(lambda x");
    formula = RegexReplaceManager.replace(formula, "\\(var [a-z]\\)", "\\(var x\\)");

    formula = RegexReplaceManager.replace(formula, "fb:row\\.row\\.[_0-9a-z]+", "\\$Binary");
    formula = RegexReplaceManager.replace(formula, "fb:cell\\.cell\\.[_0-9a-z]+", "\\$Property");
    formula = RegexReplaceManager.replace(formula, "fb:cell_[_0-9a-z]+\\.[_0-9a-z]+", "\\$Entity");
    formula = RegexReplaceManager.replace(formula, "\\(number [0-9]+[.]?[0-9]+\\)", "\\$Entity");
    formula = RegexReplaceManager.replace(formula, "\\(number [0-9]+\\)", "\\$Entity");
    formula = RegexReplaceManager.replace(formula, "\\(date [-]?[0-9]+ [-]?[0-9]+ [-]?[0-9]+\\)", "\\$Entity");

    formula = RegexReplaceManager.replace(formula, "[ ]+", " ");
    formula = formula.replace("reverse", "@R");
    formula = RegexReplaceManager.replace(formula, "(<=|>=|>|<)", "@compare");
    return formula;
  }

  public static String convertToIndexedPattern(Derivation deriv) {
    String formula = deriv.formula.toString();

    // These can interfere with (number 1)
    formula = formula.replace("argmax (number 1) (number 1)", "argmax");
    formula = formula.replace("argmin (number 1) (number 1)", "argmin");

    formula = removePropertyPredicates(formula);
    formula = CustomGrammar.getIndexedSymbolicFormula(deriv, formula);

    formula = formula.replace("fb:type.object.type fb:type.row", "@type @row");

    formula = RegexReplaceManager.replace(formula, "!fb:[._0-9a-z]+", "(reverse $0)").replace("reverse !fb", "reverse fb");
    formula = formula.replace("fb:row.row.index", "(reverse (lambda x ((reverse @index) (var x))))");
    formula = formula.replace("fb:row.row.next", "@next");
    formula = RegexReplaceManager.replace(formula, "\\(lambda [a-z]", "\\(lambda x");
    formula = RegexReplaceManager.replace(formula, "\\(var [a-z]\\)", "\\(var x\\)");
    formula = RegexReplaceManager.replace(formula, "[ ]+", " ");
    formula = formula.replace("reverse", "@R");
    formula = RegexReplaceManager.replace(formula, "(<=|>=|>|<)", "@compare");
    return formula;
  }
}
