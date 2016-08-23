package edu.stanford.nlp.sempre.interactive;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Function;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.Grammar.Options;
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
    public int maxRulesPerExample = 3;
  }

  public static Options opts = new Options();

  public static enum DefStatus {
    Cover, // some (including all) covers in the definiendum is accounted for
    NoCover, // cover is empty after checking with definition, so nothing would generalize
    NoParse, // definition does not parse, should we look for partials?
  }

  // this depends on the chart!
  public static enum ParseStatus {
    Nothing, // nothing at all parses in the utterance
    Float, // something parse
    Induced, // redefining known utterance
    Core; // define known utterance in core, should reject
  }

  public DefStatus defStatus;
  public ParseStatus parseStatus;

  List<Rule> inducedRules;

  Map<String, List<Derivation>>[][] chart;
  int numTokens;
  List<String> tokens;
  String id;

  Derivation defderiv;


  // induce rule is possible,
  // otherwise set the correct status
  public GrammarInducer(Example origEx, Example defEx) {
    id = origEx.id;
    chart = origEx.chart;
    numTokens = origEx.numTokens();
    tokens = origEx.getTokens();
    inducedRules = new ArrayList<>();
    defStatus = DefStatus.NoParse;
    parseStatus = getParseStatus(origEx);

    if (defEx.predDerivations.size() == 0) {
      return;
    }

    Derivation deriv;
    if (defEx.NBestInd == -1) {
      deriv = defEx.predDerivations.get(0);
    } else
      deriv = defEx.predDerivations.get(defEx.NBestInd);

    while (deriv.rule.isCatUnary()) deriv = deriv.child(0);
    List<Derivation> covers = getSimpleCover(deriv);

    if (covers.size() == 0) {
      defStatus = DefStatus.NoCover;
    } else {
      defStatus = DefStatus.Cover;
    }

    inducedRules = induceRules(deriv, covers);
  }

  public List<Rule> getRules() {
    return inducedRules;
  }

  private List<Rule> induceRules(Derivation deriv, List<Derivation> covers) {
    List<Rule> inducedRules = new ArrayList<>();

    List<String> RHS = getRHS(tokens, covers);
    SemanticFn sem = getSemantics(deriv, covers);
    String cat = getTopCat(deriv);
    Rule inducedRule = new Rule(cat, RHS, sem);
    inducedRule.addInfo(id, 1.0);
    inducedRule.addInfo(defStatus.toString(), 1.0);
    inducedRule.addInfo(parseStatus.toString(), 1.0);
    inducedRule.addInfo("induced", 1.0);
    if (!inducedRule.isCatUnary()) {
      inducedRules.add(inducedRule);
    }

    return inducedRules;
  }

  private String getTopCat(Derivation def) {
    return def.getCat();
  }

  // replace the sub derivation under each def by just the category
  private SemanticFn getSemantics(final Derivation def, List<Derivation> covers) {
    if (covers == null || covers.size() == 0) return new ConstantFn(def.formula);

    Function<Formula, Formula> replaceCover = new Function<Formula, Formula>() {
      @Override
      public Formula apply(Formula formula) {
        // there is a bias here when we do not go for one to one correspondence.
        // perhaps replace ones with larger cover first
        for (int i = 0; i < covers.size(); i++) {
          if (formula.equals(covers.get(i).formula))
            return new VariableFormula(covers.get(i).getCat() + i);
        }
        return null;
      }
    };
    Formula baseFormula = Formulas.fromLispTree(def.formula.toLispTree()).map(replaceCover);
    for (int i = covers.size() -1; i >= 0; i--) {
      baseFormula = new LambdaFormula(covers.get(i).getCat() + i, Formulas.fromLispTree(baseFormula.toLispTree()));
    }
    SemanticFn applyFn = new ApplyFn();
    LispTree newTree = LispTree.proto.newList();
    newTree.addChild("interactive.ApplyFn");
    newTree.addChild(baseFormula.toLispTree());
    applyFn.init(newTree);
    return applyFn;
  }

  private List<String> getRHS(List<String> tokens, List<Derivation> covers) {
    List<String> rhs = new ArrayList<>();
    int start = 0;
    for (Derivation deriv : covers) {
      if (deriv.start > start) {
        // leftover tokens
        rhs.addAll(tokens.subList(start, deriv.start));
      }
      rhs.add(deriv.getCat());
      start = deriv.end;
    }
    if (start < tokens.size()) // leftover tokens
      rhs.addAll(tokens.subList(start,tokens.size()));
    return rhs;
  }

  // find a list of sub derivation that produces a maximum cover that match the target
  // use dynamic programming
  // For now, just get the maximum cover greedily, from left to right
  // Issues: exact match not handled, and numbers went to lemma token
  private List<Derivation> getGreedyCover(Derivation definition) {
    List<Derivation> coveredDerivs = new ArrayList<>();
    int currentMax = 0;
    for (int start = 0; start < numTokens;) {
      Derivation currentDeriv = null;
      for (int end = start + 1; end <= numTokens; end++) {
        boolean matchedCat = false;
        for (String cat : Parser.opts.trackedCats) {
          LogInfo.dbgs("Checking...%s on %d-%d:%d, matched: %s", cat, start, end, currentMax, matchedCat);

          if (matchedCat) break;

          if (chart[start][end] == null || !chart[start][end].keySet().contains(cat))
            continue; // do not match random stuff, and take the first match

          for (Derivation deriv : chart[start][end].get(cat)) {
            LogInfo.dbgs("Real (%d, %d):(%d, %d) : %s", deriv.start, deriv.end, start, end, deriv);
            List<Derivation> matches = new ArrayList<>();
            LogInfo.dbgs("deriv %s: def %s", deriv, definition);
            getMatches(definition, deriv, matches);
            // do nothing when ==0: no match; >=1: too many matches
            if (matches.size() >= 1) {
                currentMax = end;
                currentDeriv = deriv;
                matchedCat = true;
                break;
            }
          }
        }
      }

      if (currentMax > start) {
        LogInfo.dbgs("GrammarInducer.added (%d, %d): %s", currentDeriv.start, currentDeriv.end, currentDeriv.rule.getLhs());
        coveredDerivs.add(currentDeriv);
        start = currentMax;
      } else start++;
    }
    LogInfo.dbgs("GrammarInducer.coveredDerivs: %s", coveredDerivs);
    return coveredDerivs;
  }

  private List<Derivation> getSimpleCover(Derivation definition) {
    List<Derivation> coveredDerivs = new ArrayList<>();
    int currentMax = 0;
    for (int start = 0; start < numTokens;) {
      Derivation currentDeriv = null;
      boolean matchedCat = false;
      for (int end = start + 1; end <= numTokens; end++) {
        if (matchedCat) break;
        for (String cat : Parser.opts.trackedCats) {
          if (matchedCat) break;
          if (chart[start][end] == null || !chart[start][end].keySet().contains(cat))
            continue; // do not match random stuff, and take the first match

          for (Derivation deriv : chart[start][end].get(cat)) {
            LogInfo.dbgs("Real (%d, %d):(%d, %d) : %s", deriv.start, deriv.end, start, end, deriv);

            List<Derivation> matches = new ArrayList<>();
            LogInfo.dbgs("deriv %s: def %s", deriv, definition);
            getMatches(definition, deriv, matches);
            // do nothing when ==0: no match; >=1: too many matches
            if (matches.size() >= 1) {
                currentMax = end;
                currentDeriv = deriv;
                matchedCat = true;
                break;
            }
          }
        }
      }

      if (currentMax > start) {
        LogInfo.dbgs("GrammarInducer.added (%d, %d): %s", currentDeriv.start, currentDeriv.end, currentDeriv.rule.getLhs());
        coveredDerivs.add(currentDeriv);
        start = currentMax;
      } else start++;
    }
    LogInfo.dbgs("GrammarInducer.coveredDerivs: %s", coveredDerivs);
    return coveredDerivs;
  }





  boolean nothingParses() {
    return false;
  }

  private boolean formulaEqual(Derivation parent, Derivation child) {
    return parent.formula.equals(child.formula);
  }
  // check if this derivation contains another one
  public boolean getMatches(Derivation parent, Derivation child, List<Derivation> matches) {
    if (formulaEqual(parent, child)) {
      matches.add(parent);
      return true;
    }
    if (parent.children == null) return false;

    boolean matched = false;
    for (Derivation deriv : parent.children) {
      if (getMatches(deriv, child, matches)) {
        matched = true;
      }
    }
    return matched;
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
