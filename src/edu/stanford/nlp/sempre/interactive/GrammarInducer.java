package edu.stanford.nlp.sempre.interactive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.testng.collections.Sets;

import com.beust.jcommander.internal.Lists;
import com.google.common.collect.ImmutableList;

import edu.stanford.nlp.sempre.ConstantFn;
import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.DerivationStream;
import edu.stanford.nlp.sempre.Example;
import edu.stanford.nlp.sempre.Formula;
import edu.stanford.nlp.sempre.Formulas;
import edu.stanford.nlp.sempre.IdentityFn;
import edu.stanford.nlp.sempre.LambdaFormula;
import edu.stanford.nlp.sempre.Master;
import edu.stanford.nlp.sempre.Rule;
import edu.stanford.nlp.sempre.SemanticFn;
import edu.stanford.nlp.sempre.VariableFormula;
import edu.stanford.nlp.sempre.interactive.actions.ActionFormula;
import fig.basic.LispTree;
import fig.basic.LogInfo;
import fig.basic.Option;
import fig.basic.StopWatchSet;

/**
 * Takes two examples, and induce some Rules
 *
 * @author Sida Wang
 */

public class GrammarInducer {
  public static class Options {
    @Option(gloss = "categories that can serve as rules")
    public Set<String> filteredCats = new HashSet<String>();
    public int maxRulesPerExample = 3;
  }

  public static Options opts = new Options();


  Map<String, List<Derivation>>[][] chart;
  List<Rule> inducedRules = null;
  int numTokens;
  List<String> tokens;
  String id;
  List<Derivation> chartList = Lists.newArrayList();
  
  // really just a return value
  Example head;

  // induce rule is possible,
  // otherwise set the correct status
  public GrammarInducer(Example origEx, Derivation def,  List<Derivation> chartList) {
    id = origEx.id;
    head = origEx;
    head.predDerivations = Lists.newArrayList(def);
    
    this.chartList = chartList;
    
    LogInfo.logs("got %d in chartList", this.chartList.size());
    numTokens = origEx.numTokens();
    tokens = origEx.getTokens();
    
    addMatches(def);
    buildFormula(def);
    def.grammarInfo.start = 0;
    def.grammarInfo.end = tokens.size();
    
    inducedRules = new ArrayList<>(induceRules(def));
  }
  
  // label the derivation tree with what it matches in chartList
  private void addMatches(Derivation deriv) {
    for (Derivation anchored : chartList) {
      if (deriv.formula.equals(anchored.formula) && deriv.cat.equals(anchored.cat)) {
        deriv.grammarInfo.matches.add(anchored);
        LogInfo.dbgs("Replaced %s <- %s", anchored, uniquePackingName(anchored));
        deriv.grammarInfo.formula = new VariableFormula(uniquePackingName(anchored));
        deriv.grammarInfo.start = anchored.start;
        deriv.grammarInfo.end = anchored.end;
        break; // just adding the first match, could do highest scoring
      }
    }
    for (Derivation d : deriv.children) {
      addMatches(d);
    }
  }
  
  // packings are Derivations in the chartList that also appeared in the definition
  private void collectPacking(Derivation deriv, Map<Integer, Derivation> packings) {
    if (deriv.grammarInfo.matches.size() > 0) {
      Derivation candidatePacking = deriv.grammarInfo.matches.get(0);
      Integer packingKey = candidatePacking.start;
      if(!packings.containsKey(packingKey))
        packings.put(packingKey, candidatePacking);
    } else {
      for (Derivation d : deriv.children) {
        collectPacking(d, packings);
      }
    }
  }
  
  public List<Rule> getRules() {
    return inducedRules;
  }
  
  private List<Rule> induceRules(Derivation defDeriv) {
    Map<Integer, Derivation> packingMap = new HashMap<>();
    collectPacking(defDeriv, packingMap);
    List<Derivation> packings = new ArrayList<>(packingMap.values());
    
    packings.sort((s,t) -> s.start < t.start? -1: 1);
    
    LogInfo.dbgs("packings: %s", packings);
    List<Rule> inducedRules = new ArrayList<>();
    List<String> RHS = getRHS(defDeriv, packings);
    SemanticFn sem = getSemantics(defDeriv, packings);
    String cat = getTopCat(defDeriv);
    Rule inducedRule = new Rule(cat, RHS, sem);
    inducedRule.addInfo(id, 1.0);
    inducedRule.addInfo("induced", 1.0);
    inducedRule.addInfo("anchored", 1.0);
    if (!inducedRule.isCatUnary()) {
      inducedRules.add(inducedRule);
    }
    return inducedRules;
  }

  private String getTopCat(Derivation def) {
    return def.getCat();
  }

  // populate grammarInfo.formula, replacing everything that can be replaced
  private void buildFormula(Derivation deriv){
    //LogInfo.log("building " + deriv.grammarInfo.formula);
    if (deriv.grammarInfo.formula != null) return;
    if (deriv.grammarInfo.formula instanceof VariableFormula) {
      return;
    }
    if (deriv.children.size() == 0) {
      deriv.grammarInfo.formula = deriv.formula;
    }
    
    for (Derivation c : deriv.children) {
      buildFormula(c);
      deriv.grammarInfo.start = Math.min(deriv.grammarInfo.start, c.grammarInfo.start);
      deriv.grammarInfo.end = Math.max(deriv.grammarInfo.end, c.grammarInfo.end);
    }
    Rule rule = deriv.rule;
    List<Derivation> args = deriv.children;
    
    // cant use the standard DerivationStream because formula is final
    if (rule.sem instanceof ApplyFn) {
      Formula f = Formulas.fromLispTree(((ApplyFn)rule.sem).formula.toLispTree());
      for (Derivation arg : args) {
        if (!(f instanceof LambdaFormula))
          throw new RuntimeException("Expected LambdaFormula, but got " + f);
        f = Formulas.lambdaApply((LambdaFormula)f, arg.grammarInfo.formula);
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

    //LogInfo.log("built " + deriv.grammarInfo.formula);
  }
  
  private String uniquePackingName(Derivation anchored) {
    return anchored.cat + anchored.start + "_" + anchored.end;
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
      baseFormula = new LambdaFormula( uniquePackingName(packings.get(i)), Formulas.fromLispTree(baseFormula.toLispTree()));
    }
    SemanticFn applyFn = new ApplyFn();
    LispTree newTree = LispTree.proto.newList();
    newTree.addChild("interactive.ApplyFn");
    newTree.addChild(baseFormula.toLispTree());
    applyFn.init(newTree);
    return applyFn;
  }

  private List<String> getRHS(Derivation def, List<Derivation> packings) {
    List<String> rhs = new ArrayList<>(tokens);
    for (Derivation deriv : packings) {
      // LogInfo.logs("got (%d,%d):%s:%s", deriv.start, deriv.end, deriv.formula, deriv.cat);
      rhs.set(deriv.start, deriv.cat);
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

  public Example getHead() {
    return head;
  }

}
