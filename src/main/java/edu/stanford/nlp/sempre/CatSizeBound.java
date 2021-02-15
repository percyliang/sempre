package edu.stanford.nlp.sempre;

import java.util.*;

import fig.basic.*;

/**
 * Given the maximum formula size in a floating grammar, compute the maximum size
 * that each floating grammar category can have.
 *
 * For example, if the grammar looks like this:
 *   $ROOT -> $A | $A $B
 *   $A -> $C $A
 *   $B -> $C
 *   $C -> $D $B | nothing
 *   ...
 * and the maximum formula size (for $ROOT) is 10, then the maximum formula sizes for
 * $A, $B, $C and $D are 9, 9, 8, and 7, respectively.
 *
 * The bound is = maxFormulaSize - (shortest distance from $ROOT to cat)
 *
 * @author ppasupat
 */
public class CatSizeBound {
  public static class Options {
    @Option(gloss = "verbosity") public int verbose = 0;
  }
  public static Options opts = new Options();

  private final int maxFormulaSize;
  private final Map<String, Integer> bound = new HashMap<>();

  public CatSizeBound(int maxFormulaSize, Grammar grammar) {
    this(maxFormulaSize, grammar.getRules());
  }

  public CatSizeBound(int maxFormulaSize, List<Rule> rules) {
    this.maxFormulaSize = maxFormulaSize;
    if (!FloatingParser.opts.useSizeInsteadOfDepth) {
      LogInfo.warnings("Currently CatSizeBound is usable only when useSizeInsteadOfDepth = true.");
      return;
    }
    // Construct graph
    Map<String, Set<String>> graph = new HashMap<>();
    for (Rule rule : rules) {
      if (!Rule.isCat(rule.lhs))
        throw new RuntimeException("Non-cat found in LHS of rule " + rule);
      for (String rhsCat : rule.rhs) {
        if (Rule.isCat(rhsCat))
          MapUtils.addToSet(graph, rule.lhs, rhsCat);
      }
    }
    // Breadth first search
    bound.put(Rule.rootCat, maxFormulaSize);
    Queue<String> queue = new ArrayDeque<>();
    queue.add(Rule.rootCat);
    while (!queue.isEmpty()) {
      String cat = queue.remove();
      if (!graph.containsKey(cat)) continue;
      for (String rhsCat : graph.get(cat)) {
        if (bound.containsKey(rhsCat)) continue;
        bound.put(rhsCat, bound.get(cat) - 1);
        queue.add(rhsCat);
      }
    }
    if (opts.verbose >= 1) {
      LogInfo.begin_track("CatSizeBound: distances");
      for (Map.Entry<String, Integer> entry : bound.entrySet())
        LogInfo.logs("%25s : %2d", entry.getKey(), entry.getValue());
      LogInfo.end_track();
    }
  }

  public int getBound(String cat) {
    return bound.getOrDefault(cat, maxFormulaSize);
  }

}
