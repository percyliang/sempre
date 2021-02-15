package edu.stanford.nlp.sempre;

import com.google.common.collect.Sets;
import fig.basic.LogInfo;
import fig.basic.MapUtils;
import fig.basic.StopWatchSet;

import java.util.*;

/**
 * Contains methods for putting derivations on the chart and combining them
 * to add new derivations to the agenda
 * @author joberant
 */
abstract class AbstractReinforcementParserState extends ChartParserState {

  protected final ReinforcementParser parser;
  protected final CoarseParser coarseParser;
  protected CoarseParser.CoarseParserState coarseParserState;
  protected static final double EPSILON = 10e-20; // used to break ties between agenda items

  public AbstractReinforcementParserState(ReinforcementParser parser, Params params, Example ex, boolean computeExpectedCounts) {
    super(parser, params, ex, computeExpectedCounts);
    this.parser = parser;
    coarseParser = parser.coarseParser;
  }

  protected abstract void addToAgenda(DerivationStream derivationStream);

  protected boolean coarseAllows(String cat, int start, int end) {
    return coarseParserState == null || coarseParserState.coarseAllows(cat, start, end);
  }

  //don't add to a cell in the chart that is fill
  protected boolean addToBoundedChart(Derivation deriv) {

    List<Derivation> derivations = chart[deriv.start][deriv.end].get(deriv.cat);
    totalGeneratedDerivs++;
    if (Parser.opts.visualizeChartFilling) {
      chartFillingList.add(new CatSpan(deriv.start, deriv.end, deriv.cat));
    }
    if (derivations == null) {
      chart[deriv.start][deriv.end].put(deriv.cat,
              derivations = new ArrayList<>());
    }
    if (derivations.size() < getBeamSize()) {
      derivations.add(deriv);
      Collections.sort(derivations, Derivation.derivScoreComparator); // todo - perhaps can be removed
      return true;
    } else return false;
  }

  // for [start, end) we try to create [start, end + i) or [start - i, end) and add unary rules
  protected void combineWithChartDerivations(Derivation deriv) {
    expandDerivRightwards(deriv);
    expandDerivLeftwards(deriv);
    applyCatUnaryRules(deriv);
  }

  private void expandDerivRightwards(Derivation leftChild) {
    if (parser.verbose(6))
      LogInfo.begin_track("Expanding rightward");
    Map<String, List<Rule>> rhsCategoriesToRules = parser.leftToRightSiblingMap.get(leftChild.cat);
    if (rhsCategoriesToRules != null) {
      for (int i = 1; leftChild.end + i <= numTokens; ++i) {
        Set<String> intersection = Sets.intersection(rhsCategoriesToRules.keySet(), chart[leftChild.end][leftChild.end + i].keySet());

        for (String rhsCategory : intersection) {
          List<Rule> compatibleRules = rhsCategoriesToRules.get(rhsCategory);
          List<Derivation> rightChildren = chart[leftChild.end][leftChild.end + i].get(rhsCategory);
          generateParentDerivations(leftChild, rightChildren, true, compatibleRules);
        }
      }
      // handle terminals
      if (leftChild.end < numTokens)
        handleTerminalExpansion(leftChild, false, rhsCategoriesToRules);
    }
    if (parser.verbose(6))
      LogInfo.end_track();
  }

  private void expandDerivLeftwards(Derivation rightChild) {
    if (parser.verbose(5))
      LogInfo.begin_track("Expanding leftward");
    Map<String, List<Rule>> lhsCategorisToRules = parser.rightToLeftSiblingMap.get(rightChild.cat);
    if (lhsCategorisToRules != null) {
      for (int i = 1; rightChild.start - i >= 0; ++i) {
        Set<String> intersection = Sets.intersection(lhsCategorisToRules.keySet(), chart[rightChild.start - i][rightChild.start].keySet());

        for (String lhsCategory : intersection) {
          List<Rule> compatibleRules = lhsCategorisToRules.get(lhsCategory);
          List<Derivation> leftChildren = chart[rightChild.start - i][rightChild.start].get(lhsCategory);
          generateParentDerivations(rightChild, leftChildren, false, compatibleRules);
        }
      }
      // handle terminals
      if (rightChild.start > 0)
        handleTerminalExpansion(rightChild, true, lhsCategorisToRules);
    }
    if (parser.verbose(5))
      LogInfo.end_track();
  }

  private void generateParentDerivations(Derivation expandedDeriv, List<Derivation> otherDerivs,
                                         boolean expandedLeftChild, List<Rule> compatibleRules) {

    for (Derivation otherDeriv : otherDerivs) {
      Derivation leftChild, rightChild;
      if (expandedLeftChild) {
        leftChild = expandedDeriv;
        rightChild = otherDeriv;
      } else {
        leftChild = otherDeriv;
        rightChild = expandedDeriv;
      }
      List<Derivation> children = new ArrayList<>();
      children.add(leftChild);
      children.add(rightChild);
      for (Rule rule : compatibleRules) {
        if (coarseAllows(rule.lhs, leftChild.start, rightChild.end)) {
          DerivationStream resDerivations = applyRule(leftChild.start, rightChild.end, rule, children);

          if (!resDerivations.hasNext())
            continue;
          addToAgenda(resDerivations);
        }
      }
    }
  }

  // returns the score of derivation computed
  private DerivationStream applyRule(int start, int end, Rule rule, List<Derivation> children) {
    try {
      if (Parser.opts.verbose >= 5)
        LogInfo.logs("applyRule %s %s %s %s", start, end, rule, children);
      StopWatchSet.begin(rule.getSemRepn()); // measuring time
      StopWatchSet.begin(rule.toString());
      DerivationStream results = rule.sem.call(ex,
              new SemanticFn.CallInfo(rule.lhs, start, end, rule, com.google.common.collect.ImmutableList.copyOf(children)));
      StopWatchSet.end();
      StopWatchSet.end();
      return results;
    } catch (Exception e) {
      LogInfo.errors("Composition failed: rule = %s, children = %s", rule, children);
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  private void applyCatUnaryRules(Derivation deriv) {
    if (parser.verbose(4))
      LogInfo.begin_track("Category unary rules");
    for (Rule rule : parser.catUnaryRules) {
      if (!coarseAllows(rule.lhs, deriv.start, deriv.end))
        continue;
      if (deriv.cat.equals(rule.rhs.get(0))) {
        DerivationStream resDerivations = applyRule(deriv.start, deriv.end, rule, Collections.singletonList(deriv));
        addToAgenda(resDerivations);
      }
    }
    if (parser.verbose(4))
      LogInfo.end_track();
  }

  public List<DerivationStream> gatherRhsTerminalsDerivations() {
    List<DerivationStream> derivs = new ArrayList<>();
    final List<Derivation> empty = Collections.emptyList();

    for (int i = 0; i < numTokens; i++) {
      for (int j = i + 1; j <= numTokens; j++) {
        for (Rule rule : MapUtils.get(parser.terminalsToRulesList, phrases[i][j], Collections.<Rule>emptyList())) {
          if (!coarseAllows(rule.lhs, i, j))
            continue;
          derivs.add(applyRule(i, j, rule, empty));
        }
      }
    }
    return derivs;
  }

  // rules where one word is a terminal and the other is a non-terminal
  private void handleTerminalExpansion(Derivation child, boolean before, Map<String, List<Rule>> categoriesToRules) {

    String phrase = before ? phrases[child.start - 1][child.start] : phrases[child.end][child.end + 1];
    int start = before ? child.start - 1 : child.start;
    int end = before ? child.end : child.end + 1;

    if (categoriesToRules.containsKey(phrase)) {
      List<Derivation> children = new ArrayList<>();
      children.add(child);
      for (Rule rule : categoriesToRules.get(phrase)) {
        if (coarseAllows(rule.lhs, start, end)) {
          DerivationStream resDerivations = applyRule(start, end, rule, children);
          if (!resDerivations.hasNext())
            continue;
          addToAgenda(resDerivations);
        }
      }
    }
  }
}
