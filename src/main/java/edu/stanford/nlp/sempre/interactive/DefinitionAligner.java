package edu.stanford.nlp.sempre.interactive;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.Rule;
import edu.stanford.nlp.sempre.interactive.GrammarInducer.ParseStatus;
import fig.basic.LogInfo;
import fig.basic.Option;

/**
 * Takes the definition and the head, then induce rules through alignment
 * 
 * @author sidaw
 */

public class DefinitionAligner {
  public static class Options {
    @Option(gloss = "categories that can serve as rules")
    public Set<String> alignedCats = new HashSet<String>();
    @Option(gloss = "phrase size")
    public int phraseSize = 2;
    @Option(gloss = "max length difference")
    public int maxLengthDifference = 3;
    @Option(gloss = "max set exclusion length")
    public int maxSetExclusionLength = 2;
    @Option(gloss = "max exact exclusion length")
    public int maxExactExclusionLength = 5;
    @Option(gloss = "window size")
    public int windowSize = 1;

    @Option(gloss = "strategies")
    public Set<Strategies> strategies = Sets.newHashSet(Strategies.SetExclusion, Strategies.ExactExclusion);
    @Option(gloss = "maximum matches")
    public int maxMatches = 3;
    @Option(gloss = "verbose")
    public int verbose = 0;

  }

  public enum Strategies {
    SetExclusion, ExactExclusion, cmdSet
  };

  public static Options opts = new Options();

  public class Match {
    @Override
    public String toString() {
      return "Match [deriv=" + deriv + ", start=" + start + ", end=" + end + "]";
    }

    public Match(Derivation def, int start, int end) {
      deriv = def;
      this.start = start;
      this.end = end;
      deriv.grammarInfo.start = start;
      deriv.grammarInfo.end = end;
    }

    Derivation deriv;
    int start;
    int end;
  }

  List<String> headTokens;
  List<String> defTokens;

  public static List<Rule> getRules(List<String> head, List<String> def, Derivation deriv, List<Derivation> chartList) {
    if (opts.verbose > 0)
      LogInfo.logs("DefinitionAligner.chartList: %s", chartList);

    DefinitionAligner aligner = new DefinitionAligner(head, def, deriv, chartList);

    List<Rule> allAlignedRules = Lists.newArrayList();
    if (opts.verbose > 0)
      LogInfo.logs("DefinitionAligner.allMatches.size(): %d", aligner.allMatches.size());

    for (int i = 0; i < aligner.allMatches.size() && i <= opts.maxMatches; i++) {
      Match match = aligner.allMatches.get(i);

      List<Derivation> filteredList = chartList.stream()
          .filter(d -> d.start >= match.deriv.start && d.end <= match.deriv.end).collect(Collectors.toList());

      // filter out core
      List<Derivation> currentParses = chartList.stream().filter(d -> {
        if (opts.verbose > 1)
          LogInfo.logs("DefinitionAligner.chartList.d: %s", d);
        return (d.start == match.start && d.end == match.end);
      }).collect(Collectors.toList());

      if (opts.verbose > 1)
        LogInfo.logs("DefinitionAligner.Match: %s", match);
      if (opts.verbose > 1)
        LogInfo.logs("DefinitionAligner.currentParses: %s", currentParses);

      if (GrammarInducer.getParseStatus(currentParses) != ParseStatus.Core) {
        if (opts.verbose > 1)
          LogInfo.logs("DefinitionAligner.NotCore: %s", currentParses);
        GrammarInducer grammarInducer = new GrammarInducer(head, match.deriv, filteredList);
        allAlignedRules.addAll(grammarInducer.getRules());
      }
    }
    return allAlignedRules;
  }

  public List<Match> allMatches = new ArrayList<>();
  private Map<String, List<Derivation>> chartMap;

  public DefinitionAligner(List<String> headTokens, List<String> defTokens, Derivation def,
      List<Derivation> chartList) {
    this.headTokens = headTokens;
    this.defTokens = defTokens;
    this.chartMap = GrammarInducer.makeChartMap(chartList);
    if (opts.verbose > 0)
      LogInfo.logs("DefinitionAligner: head '%s' as body: '%s'", headTokens, defTokens);
    if (Math.abs(headTokens.size() - defTokens.size()) >= 4)
      return;
    recursiveMatch(def);
  }

  void recursiveMatch(Derivation def) {
    // LogInfo.logs("Considering (%d,%d): %s", def.start, def.end, def);
    for (int start = 0; start < headTokens.size(); start++) {
      for (int end = headTokens.size(); end > start; end--) {
        // LogInfo.logs("Testing (%d,%d)", start, end);
        if (end == headTokens.size() && start == 0)
          continue;
        if (isMatch(def, start, end)) {
          if (opts.verbose > 0)
            LogInfo.logs("Matched head(%d,%d)=%s with deriv(%d,%d)=%s: %s", start, end, headTokens.subList(start, end),
                def.start, def.end, defTokens.subList(def.start, def.end), def);
          allMatches.add(new Match(def, start, end));
          return;
        }
      }
    }

    for (Derivation d : def.children) {
      recursiveMatch(d);
    }
  }

  boolean isMatch(Derivation def, int start, int end) {
    if (def.start == -1 || def.end == -1)
      return false;
    if (chartMap.containsKey(GrammarInducer.catFormulaKey(def)))
      return false;
    if (opts.verbose > 0)
      LogInfo.logs("checkingLengths (%d, %d) - (%d, %d)", start, end, def.start, def.end);
    if (Math.abs((end - start) - (def.end - def.start)) >= opts.maxLengthDifference)
      return false;
    if (opts.strategies.contains(Strategies.ExactExclusion) && exactExclusion(def, start, end))
      return true;
    if (opts.strategies.contains(Strategies.SetExclusion) && setExclusion(def, start, end))
      return true;
    if (opts.strategies.contains(Strategies.cmdSet) && cmdSet(def, start, end))
      return true;

    return false;
  }

  private boolean setExclusion(Derivation def, int start, int end) {
    // the span under consideration does not match anythign
    if (end - start > opts.maxSetExclusionLength)
      return false;
    if (!headTokens.subList(start, end).stream().noneMatch(t -> defTokens.contains(t)))
      return false;
    if (!defTokens.subList(def.start, def.end).stream().noneMatch(t -> headTokens.contains(t)))
      return false;

    // everything before and afterwards are accounted for
    if (!headTokens.subList(0, start).stream().allMatch(t -> defTokens.contains(t)))
      return false;
    if (!headTokens.subList(end, headTokens.size()).stream().allMatch(t -> defTokens.contains(t)))
      return false;
    return true;
  }

  private List<String> window(int lower, int upper, List<String> list) {
    List<String> ret = new ArrayList<>();
    for (int i = lower; i < upper; i++) {
      if (i < 0 || i >= list.size())
        ret.add("(*)");
      else
        ret.add(list.get(i));
    }
    return ret;
  }

  private boolean exactExclusion(Derivation def, int start, int end) {
    if (opts.verbose > 0)
      LogInfo.log("In exactExclusion");
    if (end - start > opts.maxExactExclusionLength)
      return false;

    boolean prefixEq = window(start - opts.windowSize, start, headTokens)
        .equals(window(def.start - opts.windowSize, def.start, defTokens));
    boolean sufixEq = window(end, end + opts.windowSize, headTokens)
        .equals(window(def.end, def.end + opts.windowSize, defTokens));
    if (opts.verbose > 0)
      LogInfo.logs("%b : %b", prefixEq, sufixEq);
    if (opts.verbose > 0)
      LogInfo.logs("(%d,%d)-head(%d,%d): %b %b %s %s", def.start, def.end, start, end, prefixEq, sufixEq,
          window(end, end + opts.windowSize, headTokens), window(def.end, def.end + opts.windowSize, defTokens));
    if (!prefixEq || !sufixEq)
      return false;
    if (headTokens.subList(start, end).equals(defTokens.subList(def.start, def.end)))
      return false;

    return true;
  }

  // exact match plus big
  private boolean cmdSet(Derivation def, int start, int end) {
    if (opts.verbose > 0)
      LogInfo.log("In exactPlusBig");
    // match only beginning and end
    boolean cmdSet = (end == headTokens.size()) && (start > 0) && def.end == defTokens.size() && def.start > 0;
    if (cmdSet && headTokens.subList(0, start).equals(defTokens.subList(0, start)))
      return true;

    return false;
  }

}
