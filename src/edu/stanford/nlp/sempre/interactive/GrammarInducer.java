package edu.stanford.nlp.sempre.interactive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.testng.collections.Sets;

import com.beust.jcommander.internal.Lists;
import com.google.common.base.Function;

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
 * Takes two examples, and induce some Rules
 *
 * @author Sida Wang
 */

public class GrammarInducer {
  public static class Options {
    @Option(gloss = "categories that can serve as rules")
    public Set<String> filteredCats = new HashSet<String>();
    @Option(gloss = "add bias")
    public int addBias = 1;
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
    if (def.grammarInfo.start == -1) {
      def.grammarInfo.start = 0;
      def.grammarInfo.end = headTokens.size();
    }
    
    // dont want weird cat unary rules with strange semantics
    chartList.removeIf(d -> d.start == 0 && d.end == headTokens.size());
    this.def = def;
    
    this.headTokens = headTokens;
    int numTokens = headTokens.size();

    this.matches = new ArrayList<>();
    addMatches(def, makeChartMap(chartList));
    Collections.reverse(this.matches);
    
    List<Derivation> bestPacking = bestPackingDP(this.matches, numTokens);
    
    HashMap<String, String> formulaToCat = new HashMap<>();
    bestPacking.forEach(d -> formulaToCat.put(catFormulaKey(d), varName(d)));
    
    LogInfo.logs("chartList.size = %d", chartList.size());
    LogInfo.log("Potential packings: " );
    this.matches.forEach(d -> LogInfo.logs("%f: %s\t", d.getScore(), d.formula));
    LogInfo.logs("BestPacking: %s", bestPacking);
    
    LogInfo.logs("formulaToCat: %s", formulaToCat);
    
    buildFormula(def, formulaToCat);
    

    inducedRules = new ArrayList<>(induceRules(bestPacking, def));
  }
    
  static Map<String, List<Derivation>> makeChartMap(List<Derivation> chartList) {
    Map<String, List<Derivation>> chartMap = new HashMap<>();
    for (Derivation d : chartList) {
      List<Derivation> derivs = chartMap.get(catFormulaKey(d));
      derivs = derivs!=null? derivs : new ArrayList<>();
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
      return cat.substring(0, cat.length()-1);
    else return cat;
  }
  
  //label the derivation tree with what it matches in chartList
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
    
    public String toString() {
      return this.score + ": " + this.packing.toString();
    }
  }
  
  
  // the maximum starting index of every match that ends on or before end
  private int blockingIndex(List<Derivation> matches, int end) {
    return matches.stream().filter(d -> d.end <= end).map(d -> d.start)
        .max( (s1, s2) -> s1.compareTo(s2)).orElse(Integer.MAX_VALUE/2);
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
      derivs = derivs!=null? derivs : new ArrayList<>();
      derivs.add(d);
      endsAtI[d.end] = derivs;
    }
    
    for (int i = 1; i<=length; i++) {
      // the new maximal either uses a derivation that ends at i, plus a previous maximal
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
      
      // or it's a previous bestEndsAtI[j] for  i-minLength+1 <= j < i        
      for (int j = blockingIndex(matches, i) + 1; j<i; j++) {
        // LogInfo.dbgs("BlockingIndex: %d, j=%d, i=%d", blockingIndex(matches, i), j, i);
        if (bestEndsAtI.get(j).score >= bestOverall.score)
          bestOverall = bestEndsAtI.get(j);
      }
      LogInfo.dbgs("maximalAtI[%d] = %f: %s, BlockingIndex: %d", i, bestOverall.score, bestOverall.packing, blockingIndex(matches, i));
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
  private void buildFormula(Derivation deriv, Map<String, String> replaceMap){
    // LogInfo.logs("BUILDING %s at (%d,%d) %s", deriv, deriv.start, deriv.end, catFormulaKey(deriv));
    if (replaceMap.containsKey(catFormulaKey(deriv))) {
      // LogInfo.logs("Found match %s, %s, %s", catFormulaKey(deriv), replaceMap, deriv);
      deriv.grammarInfo.formula = new VariableFormula(replaceMap.get(catFormulaKey(deriv)));
      return;
    }
    if (deriv.children.size() == 0) {
      deriv.grammarInfo.formula = deriv.formula;
    }

    for (Derivation c : deriv.children) {
      buildFormula(c, replaceMap);
      // deriv.grammarInfo.start = Math.min(deriv.grammarInfo.start, c.grammarInfo.start);
      // deriv.grammarInfo.end = Math.max(deriv.grammarInfo.end, c.grammarInfo.end);
    }
    Rule rule = deriv.rule;
    List<Derivation> args = deriv.children;

    // cant use the standard DerivationStream because formula is final
    if (rule.sem instanceof ApplyFn) {
      Formula f = Formulas.fromLispTree(((ApplyFn)rule.sem).formula.toLispTree());
      for (Derivation arg : args) {
        if (!(f instanceof LambdaFormula))
          throw new RuntimeException("Expected LambdaFormula, but got " + f);
        Formula after = renameBoundVars(f, new HashSet<>());
        // LogInfo.logs("renameBoundVar %s === %s", after, f);

        f = Formulas.lambdaApply((LambdaFormula)after, arg.grammarInfo.formula);
      }
      deriv.grammarInfo.formula = f;
    } else if (rule.sem instanceof IdentityFn) {
      deriv.grammarInfo.formula = args.get(0).grammarInfo.formula;
    } else if (rule.sem instanceof BlockFn) {
      deriv.grammarInfo.formula = new ActionFormula(((BlockFn)rule.sem).mode, 
          args.stream().map(d -> d.grammarInfo.formula).collect(Collectors.toList()));
    } else {
      deriv.grammarInfo.formula = deriv.formula;
    }
    
    // LogInfo.logs("BUILT %s for %s", deriv.grammarInfo.formula, deriv.formula);
    // LogInfo.log("built " + deriv.grammarInfo.formula);
  }

  private String newName(String s) {return s.endsWith("_")? s : s + "_";}
  private Formula renameBoundVars(Formula formula, Set<String> boundvars) {
    if (formula instanceof LambdaFormula) {
      LambdaFormula f = (LambdaFormula)formula;
      boundvars.add(f.var);
      return new LambdaFormula(newName(f.var), renameBoundVars(f.body, boundvars));
    } else {
      Formula after = formula.map(
          new Function<Formula, Formula>() {
            public Formula apply(Formula formula) {
              if (formula instanceof VariableFormula) {  // Replace variable
                String name = ((VariableFormula) formula).name;
                if (boundvars.contains(name)) return new VariableFormula(newName(name));
                else return formula;
              }
              return null;
            }
          });
      return after;
    }
  }
 
  private SemanticFn getSemantics(final Derivation def, List<Derivation> packings) {
    Formula baseFormula = def.grammarInfo.formula;
    if (packings.size() == 0) {
      SemanticFn constantFn = new ConstantFn();
      LispTree newTree = LispTree.proto.newList();
      newTree.addChild("ConstantFn");
      newTree.addChild(baseFormula.toLispTree());
      constantFn.init(newTree);
      return constantFn;
    }

    for (int i = packings.size() -1; i >= 0; i--) {
      baseFormula = new LambdaFormula( varName(packings.get(i)), Formulas.fromLispTree(baseFormula.toLispTree()));
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
      // LogInfo.logs("got (%d,%d):%s:%s", deriv.start, deriv.end, deriv.formula, deriv.cat);
      rhs.set(deriv.start, getNormalCat(deriv));
      for (int i = deriv.start + 1; i < deriv.end; i++) {
        rhs.set(i, null);
      }
    }
    return rhs.subList(def.grammarInfo.start, def.grammarInfo.end).stream().filter(s -> s!=null).collect(Collectors.toList());
  }

  public static enum ParseStatus {
    Nothing, // nothing at all parses in the utterance
    Float, // something parse
    Induced, // redefining known utterance
    Core; // define known utterance in core, should reject
  }

  public static ParseStatus getParseStatus(Example ex) {
    if (ex.predDerivations.size() > 0) {
      for (Derivation deriv : ex.predDerivations) {
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
