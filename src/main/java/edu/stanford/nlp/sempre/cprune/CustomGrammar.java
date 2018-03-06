package edu.stanford.nlp.sempre.cprune;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import fig.basic.*;

public class CustomGrammar extends Grammar {
  public static class Options {
    @Option(gloss = "Whether to decompose the templates into multiple rules")
    public boolean enableTemplateDecomposition = true;
  }

  public static Options opts = new Options();

  public static final Set<String> baseCategories = new HashSet<String>(Arrays.asList(
      Rule.tokenCat, Rule.phraseCat, Rule.lemmaTokenCat, Rule.lemmaPhraseCat,
      "$Unary", "$Binary", "$Entity", "$Property"));

  ArrayList<Rule> baseRules = new ArrayList<>();
  // symbolicFormulas => symbolicFormula ID
  Map<String, Integer> symbolicFormulas = new HashMap<>();
  // indexedSymbolicFormula => customRuleString
  Map<String, Set<String>> customRules = new HashMap<>();
  // customRuleString => Binarized rules
  Map<String, Set<Rule>> customBinarizedRules = new HashMap<>();

  public void init(Grammar initGrammar) {
    baseRules = new ArrayList<>();
    for (Rule rule : initGrammar.getRules()) {
      if (baseCategories.contains(rule.lhs)) {
        baseRules.add(rule);
      }
    }
    this.freshCatIndex = initGrammar.getFreshCatIndex();
  }

  public List<Rule> getRules(Collection<String> customRuleStrings) {
    Set<Rule> ruleSet = new LinkedHashSet<>();
    ruleSet.addAll(baseRules);
    for (String ruleString : customRuleStrings) {
      ruleSet.addAll(customBinarizedRules.get(ruleString));
    }
    return new ArrayList<Rule>(ruleSet);
  }

  public Set<String> addCustomRule(Derivation deriv, Example ex) {
    String indexedSymbolicFormula = getIndexedSymbolicFormula(deriv);
    if (customRules.containsKey(indexedSymbolicFormula)) {
      return customRules.get(indexedSymbolicFormula);
    }

    CPruneDerivInfo derivInfo = aggregateSymbols(deriv);
    Set<String> crossReferences = new HashSet<>();
    for (Symbol symbol : derivInfo.treeSymbols.values()) {
      if (symbol.frequency > 1) {
        crossReferences.add(symbol.formula);
      }
    }
    computeCustomRules(deriv, crossReferences);
    customRules.put(indexedSymbolicFormula, new HashSet<String>(derivInfo.customRuleStrings));

    LogInfo.begin_track("Add custom rules for formula: " + indexedSymbolicFormula);
    for (String customRuleString : derivInfo.customRuleStrings) {
      if (customBinarizedRules.containsKey(customRuleString)) {
        LogInfo.log("Custom rule exists: " + customRuleString);
        continue;
      }

      rules = new ArrayList<>();
      LispTree tree = LispTree.proto.parseFromString(customRuleString);
      interpretRule(tree);
      customBinarizedRules.put(customRuleString, new HashSet<Rule>(rules));

      // Debug
      LogInfo.begin_track("Add custom rule: " + customRuleString);
      for (Rule rule : rules) {
        LogInfo.log(rule.toString());
      }
      LogInfo.end_track();
    }
    LogInfo.end_track();

    // Debug
    System.out.println("consistent_lf\t" + ex.id + "\t" + deriv.formula.toString());

    return customRules.get(indexedSymbolicFormula);
  }

  public static String getIndexedSymbolicFormula(Derivation deriv) {
    return getIndexedSymbolicFormula(deriv, deriv.formula.toString());
  }

  /**
   * Replace symbols (e.g., fb:row.row.name) with placeholders (e.g., Binary#1).
   */
  public static String getIndexedSymbolicFormula(Derivation deriv, String formula) {
    CPruneDerivInfo derivInfo = aggregateSymbols(deriv);
    int index = 1;
    List<Symbol> symbolList = new ArrayList<>(derivInfo.treeSymbols.values());
    for (Symbol symbol : symbolList)
      symbol.computeIndex(formula);
    Collections.sort(symbolList);
    for (Symbol symbol : symbolList) {
      if (formula.equals(symbol.formula))
        formula = symbol.category + "#" + index;
      formula = safeReplace(formula, symbol.formula, symbol.category + "#" + index);
      index += 1;
    }
    return formula;
  }

  // ============================================================
  // Private methods
  // ============================================================

  private static String safeReplace(String formula, String target, String replacement) {
    // (argmin 1 1 ...) and (argmax 1 1 ...) are troublesome
    String before = formula, targetBefore = target;
    formula = formula.replace("(argmin (number 1) (number 1)", "(ARGMIN");
    formula = formula.replace("(argmax (number 1) (number 1)", "(ARGMAX");
    target = target.replace("(argmin (number 1) (number 1)", "(ARGMIN");
    target = target.replace("(argmax (number 1) (number 1)", "(ARGMAX");
    formula = formula.replace(target + ")", replacement + ")");
    formula = formula.replace(target + " ", replacement + " ");
    formula = formula.replace("(ARGMIN", "(argmin (number 1) (number 1)");
    formula = formula.replace("(ARGMAX", "(argmax (number 1) (number 1)");
    if (CollaborativePruner.opts.verbose >= 2)
      LogInfo.logs("REPLACE: [%s | %s] %s | %s", targetBefore, replacement, before, formula);
    return formula;
  }

  /**
   * Cache the symbols in deriv.tempState[cprune].treeSymbols
   */
  private static CPruneDerivInfo aggregateSymbols(Derivation deriv) {
    Map<String, Object> tempState = deriv.getTempState();
    if (tempState.containsKey("cprune")) {
      return (CPruneDerivInfo) tempState.get("cprune");
    }
    CPruneDerivInfo derivInfo = new CPruneDerivInfo();
    tempState.put("cprune", derivInfo);

    Map<String, Symbol> treeSymbols = new LinkedHashMap<>();
    derivInfo.treeSymbols = treeSymbols;
    if (baseCategories.contains(deriv.cat)) {
      String formula = deriv.formula.toString();
      treeSymbols.put(formula, new Symbol(deriv.cat, formula, 1));
    } else {
      for (Derivation child : deriv.children) {
        CPruneDerivInfo childInfo = aggregateSymbols(child);
        for (Symbol symbol : childInfo.treeSymbols.values()) {
          if (derivInfo.treeSymbols.containsKey(symbol.formula)) {
            treeSymbols.get(symbol.formula).frequency += symbol.frequency;
          } else {
            treeSymbols.put(symbol.formula, symbol);
          }
        }
      }
    }
    return derivInfo;
  }

  private CPruneDerivInfo computeCustomRules(Derivation deriv, Set<String> crossReferences) {
    CPruneDerivInfo derivInfo = (CPruneDerivInfo) deriv.getTempState().get("cprune");
    Map<String, Symbol> ruleSymbols = new LinkedHashMap<>();
    derivInfo.ruleSymbols = ruleSymbols;
    derivInfo.customRuleStrings = new ArrayList<>();
    String formula = deriv.formula.toString();

    if (baseCategories.contains(deriv.cat)) {
      // Leaf node induces no custom rule
      derivInfo.containsCrossReference = crossReferences.contains(formula);
      // Propagate the symbol of this derivation to the parent
      ruleSymbols.putAll(derivInfo.treeSymbols);
    } else {
      derivInfo.containsCrossReference = false;
      for (Derivation child : deriv.children) {
        CPruneDerivInfo childInfo = computeCustomRules(child, crossReferences);
        derivInfo.containsCrossReference = derivInfo.containsCrossReference || childInfo.containsCrossReference;
      }

      for (Derivation child : deriv.children) {
        CPruneDerivInfo childInfo = (CPruneDerivInfo) child.getTempState().get("cprune");
        ruleSymbols.putAll(childInfo.ruleSymbols);
        derivInfo.customRuleStrings.addAll(childInfo.customRuleStrings);
      }

      if (opts.enableTemplateDecomposition == false || derivInfo.containsCrossReference) {
        // If this node contains a cross reference
        if (deriv.isRootCat()) {
          // If this is the root node, then generate a custom rule
          derivInfo.customRuleStrings.add(getCustomRuleString(deriv, derivInfo));
        }
      } else {
        if (!deriv.cat.startsWith("$Intermediate")) {
          // Generate a custom rule for this node
          derivInfo.customRuleStrings.add(getCustomRuleString(deriv, derivInfo));

          // Propagate this derivation as a category to the parent
          ruleSymbols.clear();
          ruleSymbols.put(formula, new Symbol(hash(deriv), deriv.formula.toString(), 1));
        }
      }
    }
    return derivInfo;
  }

  private String getCustomRuleString(Derivation deriv, CPruneDerivInfo derivInfo) {
    String formula = deriv.formula.toString();
    List<Symbol> rhsSymbols = new ArrayList<>(derivInfo.ruleSymbols.values());
    for (Symbol symbol : rhsSymbols)
      symbol.computeIndex(formula);
    Collections.sort(rhsSymbols);

    String lhs = null;
    if (derivInfo.containsCrossReference)
      lhs = deriv.cat;
    else
      lhs = deriv.isRootCat() ? "$ROOT" : hash(deriv);

    LinkedList<String> rhsList = new LinkedList<>();
    int index = 1;
    for (Symbol symbol : rhsSymbols) {
      if (formula.equals(symbol.formula)) {
        formula = "(IdentityFn)";
      } else {
        formula = safeReplace(formula, symbol.formula, "(var s" + index + ")");
        formula = "(lambda s" + index + " " + formula + ")";
      }
      rhsList.addFirst(symbol.category);
      index += 1;
    }
    String rhs = null;
    if (rhsList.size() > 0) {
      rhs = "(" + String.join(" ", rhsList) + ")";
    } else {
      rhs = "(nothing)";
      formula = "(ConstantFn " + formula + ")";
    }
    return "(rule " + lhs + " " + rhs + " " + formula + ")";
  }

  private String hash(Derivation deriv) {
    if (baseCategories.contains(deriv.cat))
      return deriv.cat;

    String formula = getSymbolicFormula(deriv);
    if (!symbolicFormulas.containsKey(formula)) {
      symbolicFormulas.put(formula, symbolicFormulas.size() + 1);
      String hashString = "$Formula" + symbolicFormulas.get(formula);
      LogInfo.log("Add symbolic formula: " + hashString + " = " + formula + "  (" + deriv.cat + ")");
    }
    return "$Formula" + symbolicFormulas.get(formula);
  }

  private static String getSymbolicFormula(Derivation deriv) {
    CPruneDerivInfo derivInfo = aggregateSymbols(deriv);
    String formula = deriv.formula.toString();
    for (Symbol symbol : derivInfo.treeSymbols.values()) {
      if (formula.equals(symbol.formula))
        formula = symbol.category;
      formula = safeReplace(formula, symbol.formula, symbol.category);
    }
    return formula;
  }

}
