package edu.stanford.nlp.sempre.interactive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.beust.jcommander.internal.Lists;
import com.google.common.base.Function;

import edu.stanford.nlp.sempre.ActionFormula;
import edu.stanford.nlp.sempre.ConstantFn;
import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.Example;
import edu.stanford.nlp.sempre.Formula;
import edu.stanford.nlp.sempre.Formulas;
import edu.stanford.nlp.sempre.IdentityFn;
import edu.stanford.nlp.sempre.LambdaFormula;
import edu.stanford.nlp.sempre.Rule;
import edu.stanford.nlp.sempre.SemanticFn;
import edu.stanford.nlp.sempre.VariableFormula;
import fig.basic.LispTree;
import fig.basic.LogInfo;
import fig.basic.Option;

/**
 * Takes two examples, and induce Rules
 *
 * @author sidaw
 */

public class GrammarInducer {
  public static class Options {
    @Option(gloss = "categories that can serve as rules")
    public Set<String> filteredCats = new HashSet<String>();
    @Option(gloss = "verbose")
    public int verbose = 0;
    @Option(gloss = "cats that never overlaps, and always save to replace")
    public List<String> simpleCats = Lists.newArrayList("$Color", "$Number", "$Direction");
    @Option(gloss = "use best packing")
    public boolean useBestPacking = true;
    @Option(gloss = "use simple packing")
    public boolean useSimplePacking = true;
    @Option(gloss = "maximum nonterminals in a rule")
    public long maxNonterminals = 4;
  }

  public static Options opts = new Options();

  private List<Rule> inducedRules = null;

  List<String> headTokens;
  String id;

  public List<Derivation> matches;
  Derivation def;

  // induce rule is possible,
  // otherwise set the correct status
  public GrammarInducer(List<String> headTokens, Derivation def, List<Derivation> chartList) {
    // grammarInfo start and end is used to indicate partial, when using aligner
    boolean allHead = false;
    if (def.grammarInfo.start == -1) {
      def.grammarInfo.start = 0;
      def.grammarInfo.end = headTokens.size();
      allHead = true;
    }

    // dont want weird cat unary rules with strange semantics
    if (headTokens == null || headTokens.isEmpty()) {
      throw new RuntimeException("The head is empty, refusing to define.");
    }
    chartList.removeIf(d -> d.start == def.grammarInfo.start && d.end == def.grammarInfo.end);
    this.def = def;

    this.headTokens = headTokens;
    int numTokens = headTokens.size();

    this.matches = new ArrayList<>();
    addMatches(def, makeChartMap(chartList));
    Collections.reverse(this.matches);

    inducedRules = new ArrayList<>();
    if (allHead && opts.useSimplePacking) {
      List<Derivation> filteredMatches = this.matches.stream().filter(d -> {
        return opts.simpleCats.contains(d.cat) && d.allAnchored() && d.end - d.start == 1;
      }).collect(Collectors.toList());

      List<Derivation> packing = new ArrayList<>();
      for (int i = 0; i <= headTokens.size(); i++) {
        for (Derivation d : filteredMatches) {
          if (d.start == i) {
            packing.add(d);
            break;
          }
        }
      }

      HashMap<String, String> formulaToCat = new HashMap<>();
      packing.forEach(d -> formulaToCat.put(catFormulaKey(d), varName(d)));
      buildFormula(def, formulaToCat);
      List<Rule> simpleInduced = induceRules(packing, def);
      for (Rule rule : simpleInduced) {
        rule.addInfo("simple_packing", 1.0);
        filterRule(rule);
      }

      if (opts.verbose > 1) {
        LogInfo.logs("Simple Packing", chartList.size());
        LogInfo.logs("chartList.size = %d", chartList.size());
        LogInfo.log("Potential packings: ");
        this.matches.forEach(d -> LogInfo.logs("%f: %s\t %s", d.getScore(), d.formula, d.allAnchored()));
        LogInfo.logs("packing: %s", packing);
        LogInfo.logs("formulaToCat: %s", formulaToCat);
      }
    }
    if (opts.useBestPacking) {
      List<Derivation> bestPacking = bestPackingDP(this.matches, numTokens);

      HashMap<String, String> formulaToCat = new HashMap<>();
      bestPacking.forEach(d -> formulaToCat.put(catFormulaKey(d), varName(d)));
      buildFormula(def, formulaToCat);
      for (Rule rule : induceRules(bestPacking, def)) {
        if (rule.rhs.stream().allMatch(s -> Rule.isCat(s)))
          continue;
        filterRule(rule);
      }

      if (opts.verbose > 1) {
        LogInfo.logs("chartList.size = %d", chartList.size());
        LogInfo.log("Potential packings: ");
        this.matches.forEach(d -> LogInfo.logs("%f: %s\t", d.getScore(), d.formula));
        LogInfo.logs("BestPacking: %s", bestPacking);
        LogInfo.logs("formulaToCat: %s", formulaToCat);
      }
    }

  }

  Set<String> RHSs = new HashSet<>();

  private void filterRule(Rule rule) {
    if (rule.isCatUnary()) {
      LogInfo.logs("GrammarInducer.filterRule: not allowing CatUnary rules %s", rule.toString());
      return;
    }
      
    if (RHSs.contains(rule.rhs.toString())) {
      LogInfo.logs("GrammarInducer.filterRule: already have %s", rule.toString());
      return;
    }
    int numNT = 0;
    for (String t : rule.rhs) {
      if (Rule.isCat(t)) numNT++;
    }
    
    if (numNT > GrammarInducer.opts.maxNonterminals ) {
      LogInfo.logs("GrammarInducer.filterRule: too many nontermnimals (max %d) %s", GrammarInducer.opts.maxNonterminals, rule.rhs.toString());
      return;
    }
    inducedRules.add(rule);
    RHSs.add(rule.rhs.toString());
  }

  static Map<String, List<Derivation>> makeChartMap(List<Derivation> chartList) {
    Map<String, List<Derivation>> chartMap = new HashMap<>();
    for (Derivation d : chartList) {
      List<Derivation> derivs = chartMap.get(catFormulaKey(d));
      derivs = derivs != null ? derivs : new ArrayList<>();
      derivs.add(d);
      chartMap.put(catFormulaKey(d), derivs);
    }
    return chartMap;
  }

  // this is used to test for matches, same cat, same formula
  // maybe cat needs to be more flexible
  static String catFormulaKey(Derivation d) {
    // return d.formula.toString();
    return getNormalCat(d) + "::" + d.formula.toString();
  }

  private String varName(Derivation anchored) {
    int s = def.grammarInfo.start;
    return getNormalCat(anchored) + (anchored.start - s) + "_" + (anchored.end - s);
  }

  static private String getNormalCat(Derivation def) {
    // return def.cat;
    String cat = def.getCat();
    if (cat.endsWith("s"))
      return cat.substring(0, cat.length() - 1);
    else
      return cat;
  }

  // label the derivation tree with what it matches in chartList
  private void addMatches(Derivation deriv, Map<String, List<Derivation>> chartMap) {
    String key = catFormulaKey(deriv);
    if (chartMap.containsKey(key)) {
      deriv.grammarInfo.matches.addAll(chartMap.get(key));
      deriv.grammarInfo.matched = true;
      matches.addAll(chartMap.get(key));
    }
    for (Derivation d : deriv.children) {
      addMatches(d, chartMap);
    }
  }

  class Packing {
    List<Derivation> packing;
    double score;

    public Packing(double score, List<Derivation> packing) {
      this.score = score;
      this.packing = packing;
    }

    @Override
    public String toString() {
      return this.score + ": " + this.packing.toString();
    }
  }

  // the maximum starting index of every match that ends on or before end
  private int blockingIndex(List<Derivation> matches, int end) {
    return matches.stream().filter(d -> d.end <= end).map(d -> d.start).max((s1, s2) -> s1.compareTo(s2))
        .orElse(Integer.MAX_VALUE / 2);
  }

  // start inclusive, end exclusive
  private List<Derivation> bestPackingDP(List<Derivation> matches, int length) {
    List<Packing> bestEndsAtI = new ArrayList<>(length + 1);
    List<Packing> maximalAtI = new ArrayList<>(length + 1);
    bestEndsAtI.add(new Packing(Double.NEGATIVE_INFINITY, new ArrayList<Derivation>()));
    maximalAtI.add(new Packing(0.0, new ArrayList<Derivation>()));

    @SuppressWarnings("unchecked")
    List<Derivation>[] endsAtI = new ArrayList[length + 1];

    for (Derivation d : matches) {
      List<Derivation> derivs = endsAtI[d.end];
      derivs = derivs != null ? derivs : new ArrayList<>();
      derivs.add(d);
      endsAtI[d.end] = derivs;
    }

    for (int i = 1; i <= length; i++) {
      // the new maximal either uses a derivation that ends at i, plus a
      // previous maximal
      Packing bestOverall = new Packing(Double.NEGATIVE_INFINITY, new ArrayList<>());
      Derivation bestDerivI = null;
      if (endsAtI[i] != null) {
        for (Derivation d : endsAtI[i]) {
          double score = d.getScore() + maximalAtI.get(d.start).score;
          if (score >= bestOverall.score) {
            bestOverall.score = score;
            bestDerivI = d;
          }
        }
        List<Derivation> bestpacking = new ArrayList<>(maximalAtI.get(bestDerivI.start).packing);
        bestpacking.add(bestDerivI);
        bestOverall.packing = bestpacking;
      }
      bestEndsAtI.add(i, bestOverall);

      // or it's a previous bestEndsAtI[j] for i-minLength+1 <= j < i
      for (int j = blockingIndex(matches, i) + 1; j < i; j++) {
        // LogInfo.dbgs("BlockingIndex: %d, j=%d, i=%d", blockingIndex(matches,
        // i), j, i);
        if (bestEndsAtI.get(j).score >= bestOverall.score)
          bestOverall = bestEndsAtI.get(j);
      }
      if (opts.verbose > 1)
        LogInfo.logs("maximalAtI[%d] = %f: %s, BlockingIndex: %d", i, bestOverall.score, bestOverall.packing,
            blockingIndex(matches, i));
      if (bestOverall.score > Double.NEGATIVE_INFINITY)
        maximalAtI.add(i, bestOverall);
      else {
        maximalAtI.add(i, new Packing(0, new ArrayList<>()));
      }
    }
    return maximalAtI.get(length).packing;
  }

  public List<Rule> getRules() {
    return inducedRules;
  }

  private List<Rule> induceRules(List<Derivation> packings, Derivation defDeriv) {
    List<String> RHS = getRHS(defDeriv, packings);
    SemanticFn sem = getSemantics(defDeriv, packings);
    String cat = getNormalCat(defDeriv);
    Rule inducedRule = new Rule(cat, RHS, sem);
    inducedRule.addInfo("induced", 1.0);
    inducedRule.addInfo("anchored", 1.0);
    List<Rule> inducedRules = new ArrayList<>();
    if (!inducedRule.isCatUnary()) {
      inducedRules.add(inducedRule);
    }
    return inducedRules;
  }

  // populate grammarInfo.formula, replacing everything that can be replaced
  private void buildFormula(Derivation deriv, Map<String, String> replaceMap) {
    // LogInfo.logs("BUILDING %s at (%d,%d) %s", deriv, deriv.start, deriv.end,
    // catFormulaKey(deriv));
    if (replaceMap.containsKey(catFormulaKey(deriv))) {
      // LogInfo.logs("Found match %s, %s, %s", catFormulaKey(deriv),
      // replaceMap, deriv);
      deriv.grammarInfo.formula = new VariableFormula(replaceMap.get(catFormulaKey(deriv)));
      return;
    }
    if (deriv.children.size() == 0) {
      deriv.grammarInfo.formula = deriv.formula;
    }

    for (Derivation c : deriv.children) {
      buildFormula(c, replaceMap);
      // deriv.grammarInfo.start = Math.min(deriv.grammarInfo.start,
      // c.grammarInfo.start);
      // deriv.grammarInfo.end = Math.max(deriv.grammarInfo.end,
      // c.grammarInfo.end);
    }
    Rule rule = deriv.rule;
    List<Derivation> args = deriv.children;

    // cant use the standard DerivationStream because formula is final
    if (rule == null || rule.sem == null) {
      deriv.grammarInfo.formula = deriv.formula;
    } else if (rule.sem instanceof ApplyFn) {
      Formula f = Formulas.fromLispTree(((ApplyFn) rule.sem).formula.toLispTree());
      for (Derivation arg : args) {
        if (!(f instanceof LambdaFormula))
          throw new RuntimeException("Expected LambdaFormula, but got " + f);
        Formula after = renameBoundVars(f, new HashSet<>());
        // LogInfo.logs("renameBoundVar %s === %s", after, f);
        f = Formulas.lambdaApply((LambdaFormula) after, arg.grammarInfo.formula);
      }
      deriv.grammarInfo.formula = f;
    } else if (rule.sem instanceof IdentityFn) {
      deriv.grammarInfo.formula = args.get(0).grammarInfo.formula;
    } else if (rule.sem instanceof BlockFn) {
      deriv.grammarInfo.formula = new ActionFormula(((BlockFn) rule.sem).mode,
          args.stream().map(d -> d.grammarInfo.formula).collect(Collectors.toList()));
    } else {
      deriv.grammarInfo.formula = deriv.formula;
    }
    // LogInfo.logs("BUILT %s for %s", deriv.grammarInfo.formula,
    // deriv.formula);
    // LogInfo.log("built " + deriv.grammarInfo.formula);
  }

  private String newName(String s) {
    return s.endsWith("_") ? s : s + "_";
  }

  private Formula renameBoundVars(Formula formula, Set<String> boundvars) {
    if (formula instanceof LambdaFormula) {
      LambdaFormula f = (LambdaFormula) formula;
      boundvars.add(f.var);
      return new LambdaFormula(newName(f.var), renameBoundVars(f.body, boundvars));
    } else {
      Formula after = formula.map(new Function<Formula, Formula>() {
        @Override
        public Formula apply(Formula formula) {
          if (formula instanceof VariableFormula) { // Replace variable
            String name = ((VariableFormula) formula).name;
            if (boundvars.contains(name))
              return new VariableFormula(newName(name));
            else
              return formula;
          }
          return null;
        }
      });
      return after;
    }
  }

  private SemanticFn getSemantics(final Derivation def, List<Derivation> packings) {
    Formula baseFormula = def.grammarInfo.formula;
    if (opts.verbose > 0)
      LogInfo.logs("getSemantics %s", baseFormula);
    if (packings.size() == 0) {
      SemanticFn constantFn = new ConstantFn();
      LispTree newTree = LispTree.proto.newList();
      newTree.addChild("ConstantFn");
      newTree.addChild(baseFormula.toLispTree());
      constantFn.init(newTree);
      return constantFn;
    }

    for (int i = packings.size() - 1; i >= 0; i--) {
      baseFormula = new LambdaFormula(varName(packings.get(i)), Formulas.fromLispTree(baseFormula.toLispTree()));
    }
    SemanticFn applyFn = new ApplyFn();
    LispTree newTree = LispTree.proto.newList();
    newTree.addChild("interactive.ApplyFn");
    newTree.addChild(baseFormula.toLispTree());
    applyFn.init(newTree);
    return applyFn;
  }

  private List<String> getRHS(Derivation def, List<Derivation> packings) {
    List<String> rhs = new ArrayList<>(headTokens);
    for (Derivation deriv : packings) {
      // LogInfo.logs("got (%d,%d):%s:%s", deriv.start, deriv.end,
      // deriv.formula, deriv.cat);
      rhs.set(deriv.start, getNormalCat(deriv));
      for (int i = deriv.start + 1; i < deriv.end; i++) {
        rhs.set(i, null);
      }
    }
    return rhs.subList(def.grammarInfo.start, def.grammarInfo.end).stream().filter(s -> s != null)
        .collect(Collectors.toList());
  }

  public static enum ParseStatus {
    Nothing, // nothing at all parses in the utterance
    /// Float, // something parse, no longer used.
    Induced, // redefining known utterance
    Core;

    public static ParseStatus fromString(String status) {
      for (ParseStatus c : ParseStatus.values())
        if (c.name().equalsIgnoreCase(status))
          return c;
      return null;
    } // define known utterance in core, should reject
  }

  public static ParseStatus getParseStatus(Example ex) {
    return getParseStatus(ex.predDerivations);
  }

  public static ParseStatus getParseStatus(List<Derivation> derivs) {
    if (derivs.size() > 0) {
      for (Derivation deriv : derivs) {
        if (deriv.allAnchored()) {
          return ParseStatus.Core;
        }
      }
      return ParseStatus.Induced;
    }
    // could check the chart here set partial, but no need for now
    return ParseStatus.Nothing;
  }
}
