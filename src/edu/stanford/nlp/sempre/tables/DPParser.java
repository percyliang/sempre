package edu.stanford.nlp.sempre.tables;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import fig.basic.*;

/**
 * A DPParser parses utterances like a FloatingParser,
 * but the dynamic programming states are derivations instead of formulas.
 *
 * DPParser makes 2 passes:
 * - Pass 1: Find the set of denotations that lead to the correct value.
 * - Pass 2: Use regular beam search (from FloatingParser), but only restrict
 *   the formulas to the ones found in Pass 1.
 *
 * @author ppasupat
 */
public class DPParser extends FloatingParser {
  public static class Options {
    @Option(gloss = "Use the targetValue at test time") public boolean cheat = false;
    @Option(gloss = "During training, combine the derivation list from FloatingParser")
    public boolean combineFromFloatingParser = false;
    @Option(gloss = "Random object for shuffling the derivation list")
    public Random shuffleRandom = new Random(1);
    @Option(gloss = "Custom maximum depth for DPParser (default = FloatingParser's maxDepth)")
    public int dpParserMaxDepth = -1;
    @Option(gloss = "Custom beam size for DPParser (default = FloatingParser's beamSize)")
    public int dpParserBeamSize = -1;
    @Option(gloss = "During the first pass, try add floating derivation to the cell with the lowest depth first")
    public boolean collapseFirstPass = false;
    @Option(gloss = "Use all pruners regardless of correctness")
    public boolean useAllPruners = false;
    @Option(gloss = "Collpase repeated formulas during the 2nd pass (even when the ingredients are different)")
    public boolean collapseRepeatedFormulas = false;
  }
  public static Options opts = new Options();

  public DPParser(Spec spec) {
    super(spec);
  }

  @Override
  public ParserState newParserState(Params params, Example ex, boolean computeExpectedCounts) {
    // Test time (not cheated) --> use FloatingParser
    if (!computeExpectedCounts && !opts.cheat)
      return super.newParserState(params, ex, computeExpectedCounts);
    // Training time (regardless of cheating) --> use mixture
    if (computeExpectedCounts && opts.combineFromFloatingParser)
      return new DPParserState(this, params, ex, computeExpectedCounts,
          super.newParserState(params, ex, computeExpectedCounts));
    // Cheated test OR training without mixture --> use DPParser
    return new DPParserState(this, params, ex, computeExpectedCounts);
  }

}

/**
 * Actual parsing logic.
 */
class DPParserState extends ParserState {

  private final DerivationPruner pruner;
  private final int maxDepth, beamSize;
  private final ParserState backoffParserState;

  public DPParserState(DPParser parser, Params params, Example ex, boolean computeExpectedCounts) {
    this(parser, params, ex, computeExpectedCounts, null);
  }

  public DPParserState(DPParser parser, Params params, Example ex, boolean computeExpectedCounts, ParserState backoff) {
    super(parser, params, ex, computeExpectedCounts);
    pruner = new DerivationPruner(this);
    maxDepth = DPParser.opts.dpParserMaxDepth > 0 ? DPParser.opts.dpParserMaxDepth : FloatingParser.opts.maxDepth;
    beamSize = DPParser.opts.dpParserBeamSize > 0 ? DPParser.opts.dpParserBeamSize : Parser.opts.beamSize;
    backoffParserState = backoff;
  }

  @Override
  protected int getBeamSize() { return beamSize; }

  protected void ensureExecuted(Derivation deriv) {
    deriv.ensureExecuted(parser.executor, ex.context);
    if (!deriv.isFeaturizedAndScored() && currentPass != ParsingPass.FIRST)
      featurizeAndScoreDerivation(deriv);
  }

  // ============================================================
  // Dynamic programming cells
  // ============================================================

  // Pass 1: Just try to reach the correct denotation
  //   state name => denotation => FirstPassData
  private final Map<String, Map<Value, Metadata>> firstPassCells = new HashMap<>();
  // Pass 2: Using results from Pass 1 to prune the possible formulas
  //   state name => denotation => SecondPassData
  private final Map<String, Map<Value, Metadata>> secondPassCells = new HashMap<>();

  enum ParsingPass { FIRST, SECOND, DONE };
  ParsingPass currentPass = ParsingPass.FIRST;

  private Map<String, Map<Value, Metadata>> getCellsForCurrentPass() {
    return currentPass == ParsingPass.FIRST ? firstPassCells : secondPassCells;
  }

  // ============================================================
  // DenotationIngredient
  // ============================================================

  // Represents a possible method for creating a particular denotation.
  class DenotationIngredient {
    public final Rule rule;
    public final Value denotation1, denotation2;
    private int hashCode;

    public DenotationIngredient() {
      this.rule = null;
      this.denotation1 = null;
      this.denotation2 = null;
      computeHashCode();
    }

    public DenotationIngredient(Rule rule, Derivation deriv1, Derivation deriv2) {
      this.rule = rule;
      if (deriv1 == null) {
        this.denotation1 = null;
      } else {
        ensureExecuted(deriv1);
        this.denotation1 = deriv1.value;
      }
      if (deriv2 == null) {
        this.denotation2 = null;
      } else {
        ensureExecuted(deriv1);
        this.denotation2 = deriv1.value;
      }
      computeHashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof DenotationIngredient)) return false;
      DenotationIngredient that = (DenotationIngredient) o;
      if (rule != that.rule) return false;    // Rules must be the same object
      if (denotation1 == null) {
        if (that.denotation1 != null) return false;
      } else {
        if (!denotation1.equals(that.denotation1)) return false;
      }
      if (denotation2 == null) {
        if (that.denotation2 != null) return false;
      } else {
        if (!denotation2.equals(that.denotation2)) return false;
      }
      return true;
    }

    private void computeHashCode() {
      hashCode = ((rule == null) ? 0 : rule.hashCode() * 1729)
          + ((denotation1 == null) ? 0 : denotation1.hashCode() * 42)
          + ((denotation2 == null) ? 0 : denotation2.hashCode());
    }

    @Override
    public int hashCode() {
      return hashCode;
    }

    @Override
    public String toString() {
      return "[" + rule + " | " + denotation1 + " | " + denotation2 + "]";
    }
  }

  private final Set<DenotationIngredient> allowedDenotationIngredients = new HashSet<>();

  // ============================================================
  // BackPointer
  // ============================================================

  // Back pointers for dynamic programming. Points to previous cells.
  class BackPointer {
    public final String cell;
    public final Value denotation;

    public BackPointer(String cell, Value denotation) {
      this.cell = cell;
      this.denotation = denotation;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof BackPointer)) return false;
      BackPointer that = (BackPointer) o;
      return cell.equals(that.cell) && denotation.equals(that.denotation);
    }

    @Override
    public int hashCode() {
      return cell.hashCode() * 100 + denotation.hashCode();
    }

    @Override
    public String toString() {
      return cell + " " + denotation;
    }
  }

  public BackPointer getBackPointer(String cell, Derivation child) {
    if (cell == null || child == null) return null;
    ensureExecuted(child);
    return new BackPointer(cell, child.value);
  }

  // ============================================================
  // Metadata
  // ============================================================

  // Stores derivations and other data
  class Metadata {
    public final Value denotation;
    // List of possible methods to create this denotation
    public Set<DenotationIngredient> possibleIngredients = new HashSet<>();
    // Backpointers for backtracking after the first pass
    public Set<BackPointer> backpointers = new HashSet<>();
    // All derivations containing formulas that execute to the denotation.
    // For the FIRST pass, this has only 1 formula.
    // For the SECOND pass, this will eventually be pruned to the beam size.
    public List<Derivation> derivations = new ArrayList<>();

    public Metadata(Value denotation) {
      this.denotation = denotation;
    }

    public void add(Derivation deriv, DenotationIngredient ingredient,
        BackPointer bp1, BackPointer bp2) {
      if (currentPass == ParsingPass.FIRST) {
        if (derivations.isEmpty()) {
          if (Parser.opts.verbose >= 3)
            LogInfo.logs("Metadata.add: %s %s", denotation, deriv);
          derivations.add(deriv);
        }
        if (Parser.opts.verbose >= 3) {
          LogInfo.logs("possibleIngredients.add: %s", ingredient);
        }
        possibleIngredients.add(ingredient);
        if (bp1 != null) backpointers.add(bp1);
        if (bp2 != null) backpointers.add(bp2);
      } else if (currentPass == ParsingPass.SECOND) {
        if (DPParser.opts.collapseRepeatedFormulas) {
          for (Derivation d : derivations) {
            if (d.formula.equals(deriv.formula)) return;
          }
        }
        derivations.add(deriv);
      }
    }
  }

  // ============================================================
  // Add to Chart
  // ============================================================

  private void addToChart(String name, Derivation deriv, DenotationIngredient ingredient,
      BackPointer bp1, BackPointer bp2) {
    if (Parser.opts.verbose >= 3)
      LogInfo.logs("addToChart %s %s: %s", name, deriv.value, deriv);
    ensureExecuted(deriv);
    Map<String, Map<Value, Metadata>> cells = getCellsForCurrentPass();
    Map<Value, Metadata> denotationToData = cells.get(name);
    if (denotationToData == null)
      cells.put(name, denotationToData = new HashMap<>());
    Metadata metadata = denotationToData.get(deriv.value);
    if (metadata == null)
      denotationToData.put(deriv.value, metadata = new Metadata(deriv.value));
    metadata.add(deriv, ingredient, bp1, bp2);
  }

  private String anchoredCell(String cat, int start, int end) {
    return cat + "[" + start + "," + end + "]";
  }

  private String floatingCell(String cat, int depth) {
    return cat + ":" + depth;
  }

  private int getDepth(String cell) {
    String[] tokens = cell.split(":");
    return tokens.length == 2 ? Integer.parseInt(tokens[1]) : 0;
  }

  // ============================================================
  // Apply Rule
  // ============================================================

  private void applyRule(Rule rule, int start, int end, int depth,
      String cell1, Derivation child1, String cell2, Derivation child2) {
    if (Parser.opts.verbose >= 5)
      LogInfo.logs("applyRule %s [%s:%s] depth=%s, %s %s", rule, start, end, depth, child1, child2);

    DenotationIngredient ingredient = new DenotationIngredient(rule, child1, child2);
    if (currentPass == ParsingPass.SECOND) {
      // Prune invalid ingredient
      if (!allowedDenotationIngredients.contains(ingredient)) return;
    }
    BackPointer bp1 = getBackPointer(cell1, child1), bp2 = getBackPointer(cell2, child2);

    List<Derivation> children;
    if (child1 == null)  // 0-ary
      children = Collections.emptyList();
    else if (child2 == null)  // 1-ary
      children = Collections.singletonList(child1);
    else {
      children = ListUtils.newList(child1, child2);
      // optionally: ensure that specific anchors are only used once per final derivation
      // TODO(ice): can we impose useAnchorsOnce on the first pass without dropping correct derivations?
      if (FloatingParser.opts.useAnchorsOnce && currentPass != ParsingPass.FIRST
          && FloatingRuleUtils.derivationAnchorsOverlap(child1, child2))
        return;
    }

    // Call the semantic function on the children and read the results
    DerivationStream results = rule.sem.call(ex,
        new SemanticFn.CallInfo(rule.lhs, start, end, rule, children));
    while (results.hasNext()) {
      Derivation newDeriv = results.next();
      if (pruner.isPruned(newDeriv)) continue;
      if (depth == -1) {
        // Anchored rule
        addToChart(anchoredCell(rule.lhs, start, end), newDeriv, ingredient, bp1, bp2);
        addToChart(floatingCell(rule.lhs, 0), newDeriv, ingredient, bp1, bp2);
      } else {
        // Floating rule
        if (DPParser.opts.collapseFirstPass && currentPass == ParsingPass.FIRST) {
          for (int lowerDepth = 0; lowerDepth <= depth; lowerDepth++) {
            Map<Value, Metadata> denotationToData = getCellsForCurrentPass().get(floatingCell(rule.lhs, lowerDepth));
            if (lowerDepth == depth || (denotationToData != null && denotationToData.containsKey(newDeriv.value))) {
              addToChart(floatingCell(rule.lhs, lowerDepth), newDeriv, ingredient, bp1, bp2);
              break;
            }
          }
        } else {
          addToChart(floatingCell(rule.lhs, depth), newDeriv, ingredient, bp1, bp2);
        }
      }
    }
  }

  private void applyAnchoredRule(Rule rule, int start, int end) {
    applyRule(rule, start, end, -1, null, null, null, null);
  }
  private void applyAnchoredRule(Rule rule, int start, int end,
      String cell1, Derivation child1) {
    applyRule(rule, start, end, -1, cell1, child1, null, null);
  }
  private void applyAnchoredRule(Rule rule, int start, int end,
      String cell1, Derivation child1, String cell2, Derivation child2) {
    applyRule(rule, start, end, -1, cell1, child1, cell2, child2);
  }

  private void applyFloatingRule(Rule rule, int depth) {
    applyRule(rule, -1, -1, depth, null, null, null, null);
  }
  private void applyFloatingRule(Rule rule, int depth,
      String cell1, Derivation child1) {
    applyRule(rule, -1, -1, depth, cell1, child1, null, null);
  }
  private void applyFloatingRule(Rule rule, int depth,
      String cell1, Derivation child1, String cell2, Derivation child2) {
    applyRule(rule, -1, -1, depth, cell1, child1, cell2, child2);
  }

  // ============================================================
  // Get rules and derivations
  // ============================================================

  private List<Derivation> getDerivations(Object cell) {
    Map<String, Map<Value, Metadata>> cells = getCellsForCurrentPass();
    Map<Value, Metadata> denotationToData = cells.get(cell);
    if (denotationToData == null) return Collections.emptyList();
    List<Derivation> derivations = new ArrayList<>();
    for (Metadata metadata : denotationToData.values()) {
      derivations.addAll(metadata.derivations);
    }
    return derivations;
  }

  // ============================================================
  // Build Anchored
  // ============================================================

  // Build derivations over span |start|, |end|.
  private void buildAnchored(int start, int end) {
    // Apply unary tokens on spans (rule $A (a))
    for (Rule rule : parser.grammar.getRules()) {
      if (!rule.isAnchored()) continue;
      if (rule.rhs.size() != 1 || rule.isCatUnary()) continue;
      boolean match = (end - start == 1) && ex.token(start).equals(rule.rhs.get(0));
      if (match)
        applyAnchoredRule(rule, start, end);
    }

    // Apply binaries on spans (rule $A ($B $C)), ...
    for (int mid = start + 1; mid < end; mid++) {
      for (Rule rule : parser.grammar.getRules()) {
        if (!rule.isAnchored()) continue;
        if (rule.rhs.size() != 2) continue;

        String rhs1 = rule.rhs.get(0);
        String rhs2 = rule.rhs.get(1);
        boolean match1 = (mid - start == 1) && ex.token(start).equals(rhs1);
        boolean match2 = (end - mid == 1) && ex.token(mid).equals(rhs2);

        if (!Rule.isCat(rhs1) && Rule.isCat(rhs2)) {  // token $Cat
          if (match1) {
            String cell = anchoredCell(rhs2, mid, end);
            List<Derivation> derivations = getDerivations(cell);
            for (Derivation deriv : derivations)
              applyAnchoredRule(rule, start, end, cell, deriv);
          }
        } else if (Rule.isCat(rhs1) && !Rule.isCat(rhs2)) {  // $Cat token
          if (match2) {
            String cell = anchoredCell(rhs1, start, mid);
            List<Derivation> derivations = getDerivations(cell);
            for (Derivation deriv : derivations)
              applyAnchoredRule(rule, start, end, cell, deriv);
          }
        } else if (!Rule.isCat(rhs1) && !Rule.isCat(rhs2)) {  // token token
          if (match1 && match2)
            applyAnchoredRule(rule, start, end);
        } else {  // $Cat $Cat
          String cell1 = anchoredCell(rhs1, start, mid);
          String cell2 = anchoredCell(rhs2, mid, end);
          List<Derivation> derivations1 = getDerivations(cell1);
          List<Derivation> derivations2 = getDerivations(cell2);
          for (Derivation deriv1 : derivations1)
            for (Derivation deriv2 : derivations2)
              applyAnchoredRule(rule, start, end, cell1, deriv1, cell2, deriv2);
        }
      }
    }

    // Apply unary categories on spans (rule $A ($B))
    // Important: do this in topologically sorted order and after all the binaries are done.
    for (Rule rule : parser.getCatUnaryRules()) {
      if (!rule.isAnchored()) continue;
      String cell = anchoredCell(rule.rhs.get(0), start, end);
      List<Derivation> derivations = getDerivations(cell);
      for (Derivation deriv : derivations) {
        applyAnchoredRule(rule, start, end, cell, deriv);
      }
    }
  }

  // ============================================================
  // Build Floating
  // ============================================================

  // Build floating derivations of exactly depth |depth|.
  private void buildFloating(int depth) {
    // Apply unary tokens on spans (rule $A (a))
    if (depth == 1) {
      for (Rule rule : parser.grammar.getRules()) {
        if (!rule.isFloating()) continue;
        if (rule.rhs.size() != 1 || rule.isCatUnary()) continue;
        applyFloatingRule(rule, depth);
      }
    }

    // Apply binaries on spans (rule $A ($B $C)), ...
    for (Rule rule : parser.grammar.getRules()) {
      if (!rule.isFloating()) continue;
      if (rule.rhs.size() != 2) continue;

      String rhs1 = rule.rhs.get(0);
      String rhs2 = rule.rhs.get(1);

      if (!Rule.isCat(rhs1) && !Rule.isCat(rhs2)) {  // token token
        if (depth == 1)
          applyFloatingRule(rule, depth);
      } else if (!Rule.isCat(rhs1) && Rule.isCat(rhs2)) {  // token $Cat
        String cell = floatingCell(rhs2, depth - 1);
        List<Derivation> derivations = getDerivations(cell);
        for (Derivation deriv : derivations)
          applyFloatingRule(rule, depth, cell, deriv);
      } else if (Rule.isCat(rhs1) && !Rule.isCat(rhs2)) {  // $Cat token
        String cell = floatingCell(rhs1, depth - 1);
        List<Derivation> derivations = getDerivations(cell);
        for (Derivation deriv : derivations)
          applyFloatingRule(rule, depth, cell, deriv);
      } else {  // $Cat $Cat
        if (FloatingParser.opts.useSizeInsteadOfDepth) {
          for (int depth1 = 0; depth1 < depth; depth1++) {
            int depth2 = depth - 1 - depth1;
            String cell1 = floatingCell(rhs1, depth1);
            String cell2 = floatingCell(rhs2, depth2);
            List<Derivation> derivations1 = getDerivations(cell1);
            List<Derivation> derivations2 = getDerivations(cell2);
            for (Derivation deriv1 : derivations1)
              for (Derivation deriv2 : derivations2)
                applyFloatingRule(rule, depth, cell1, deriv1, cell2, deriv2);
          }
        } else {
          for (int subDepth = 0; subDepth < depth; subDepth++) {  // depth-1 <=depth-1
            String cell1 = floatingCell(rhs1, depth - 1);
            String cell2 = floatingCell(rhs2, subDepth);
            List<Derivation> derivations1 = getDerivations(cell1);
            List<Derivation> derivations2 = getDerivations(cell2);
            for (Derivation deriv1 : derivations1)
              for (Derivation deriv2 : derivations2)
                applyFloatingRule(rule, depth, cell1, deriv1, cell2, deriv2);
          }
          for (int subDepth = 0; subDepth < depth - 1; subDepth++) {  // <depth-1 depth-1
            String cell1 = floatingCell(rhs1, subDepth);
            String cell2 = floatingCell(rhs2, depth - 1);
            List<Derivation> derivations1 = getDerivations(cell1);
            List<Derivation> derivations2 = getDerivations(cell2);
            for (Derivation deriv1 : derivations1)
              for (Derivation deriv2 : derivations2)
                applyFloatingRule(rule, depth, cell1, deriv1, cell2, deriv2);
          }
        }
      }
    }

    // Apply unary categories on spans (rule $A ($B))
    // Important: do this in topologically sorted order and after all the binaries are done.
    for (Rule rule : parser.getCatUnaryRules()) {
      if (!rule.isFloating()) continue;
      String cell = floatingCell(rule.rhs.get(0), depth - 1);
      List<Derivation> derivations = getDerivations(cell);
      for (Derivation deriv : derivations)
        applyFloatingRule(rule, depth, cell, deriv);
    }
  }

  // ============================================================
  // Infer (main entry)
  // ============================================================

  @Override public void infer() {
    LogInfo.begin_track("DPParser.infer()");
    // First pass
    LogInfo.begin_track("First pass");
    StopWatchSet.begin("DPParser.firstPass");
    currentPass = ParsingPass.FIRST;
    runParsingPass();
    collectPossibleIngredients();
    StopWatchSet.end();
    LogInfo.end_track();
    // Second pass
    LogInfo.begin_track("Second pass");
    StopWatchSet.begin("DPParser.secondPass");
    currentPass = ParsingPass.SECOND;
    runParsingPass();
    StopWatchSet.end();
    LogInfo.end_track();
    // Compile
    StopWatchSet.begin("DPParser.final");
    currentPass = ParsingPass.DONE;
    collectFinalDerivations();
    ensureExecuted();
    if (computeExpectedCounts) {
      expectedCounts = new HashMap<>();
      ParserState.computeExpectedCounts(predDerivations, expectedCounts);
    }
    StopWatchSet.end();
    LogInfo.end_track();
  }

  private void runParsingPass() {
    Set<String> categories = new HashSet<String>();
    for (Rule rule : parser.grammar.getRules())
      categories.add(rule.lhs);

    // Set the pruner
    if (currentPass == ParsingPass.FIRST && !DPParser.opts.useAllPruners)
      pruner.setCustomAllowedDomains(Arrays.asList(
          "emptyDenotation", "nonLambdaError", "badSuperlativeHead", "sameMerge", "mistypedMerge"));
    else
      pruner.setCustomAllowedDomains(null);

    // Base case ($TOKEN, $PHRASE, $LEMMA_PHRASE)
    // Denotations are StringValue
    for (Derivation deriv : gatherTokenAndPhraseDerivations()) {
      ensureExecuted(deriv);
      addToChart(anchoredCell(deriv.cat, deriv.start, deriv.end), deriv, new DenotationIngredient(), null, null);
    }

    // Build up anchored derivations
    int numTokens = ex.numTokens();
    for (int len = 1; len <= numTokens; len++) {
      for (int i = 0; i + len <= numTokens; i++)  {
        buildAnchored(i, i + len);
        for (String cat : categories) {
          pruneBeam(anchoredCell(cat, i, i + len));
        }
      }
    }

    // Build up floating derivations
    for (int depth = 1; depth <= maxDepth; depth++) {
      buildFloating(depth);
      for (String cat : categories) {
        pruneBeam(floatingCell(cat, depth));
      }
    }
  }

  // Prune to the beam size during Pass 2
  private void pruneBeam(String cell) {
    if (currentPass == ParsingPass.SECOND) {
      Map<String, Map<Value, Metadata>> cells = getCellsForCurrentPass();
      Map<Value, Metadata> denotationToData = cells.get(cell);
      if (denotationToData == null) return;
      for (Metadata metadata : denotationToData.values()) {
        pruneCell(cell, metadata.derivations);
      }
    }
  }

  private void collectPossibleIngredients() {
    if (Parser.opts.verbose >= 4)
      LogInfo.logs("DPParserState.collectPossibleIngredients()");
    Set<BackPointer> usedBps = new HashSet<>();
    collectPossibleIngredients(anchoredCell(Rule.rootCat, 0, numTokens), usedBps);
    for (int depth = 1; depth <= maxDepth; depth++)
      collectPossibleIngredients(floatingCell(Rule.rootCat, depth), usedBps);
    if (Parser.opts.verbose >= 5) {
      for (DenotationIngredient ingredient : allowedDenotationIngredients)
        LogInfo.logs("%s", ingredient);
    }
  }

  private void collectPossibleIngredients(String cell, Set<BackPointer> usedBps) {
    if (Parser.opts.verbose >= 4)
      LogInfo.logs("DPParserState.collectPossibleIngredients(%s)", cell);
    Map<Value, Metadata> denotationToMetadata = firstPassCells.get(cell);
    if (denotationToMetadata == null) return;
    for (Value denotation : denotationToMetadata.keySet()) {
      double compatibility = parser.valueEvaluator.getCompatibility(ex.targetValue, denotation);
      if (Parser.opts.verbose >= 2)
        LogInfo.logs("[%f] %s", compatibility, denotationToMetadata.get(denotation).derivations.get(0));
      if (compatibility != 1) continue;
      BackPointer bp = new BackPointer(cell, denotation);
      if (!usedBps.contains(bp))
        collectPossibleIngredients(bp, usedBps, 0);
    }
  }

  private void collectPossibleIngredients(BackPointer bp, Set<BackPointer> usedBps, int depth) {
    if (Parser.opts.verbose >= 4)
      LogInfo.logs("DPParserState.collectPossibleIngredients(%s)", bp);
    if (DPParser.opts.collapseFirstPass && depth + getDepth(bp.cell) > maxDepth) {
      if (Parser.opts.verbose >= 2)
        LogInfo.logs("Too deep: [%d] [%d] [%d] %s", depth, getDepth(bp.cell), maxDepth, bp.cell);
      return;
    }
    usedBps.add(bp);
    Map<Value, Metadata> denotationToMetadata = firstPassCells.get(bp.cell);
    if (denotationToMetadata == null) return;
    Metadata metadata = denotationToMetadata.get(bp.denotation);
    if (metadata == null) return;
    allowedDenotationIngredients.addAll(metadata.possibleIngredients);
    // Recurse
    for (BackPointer childBp : metadata.backpointers) {
      if (!usedBps.contains(childBp))
        collectPossibleIngredients(childBp, usedBps, depth + 1);
    }
  }

  // Collect final predicted derivations
  private void collectFinalDerivations() {
    predDerivations.addAll(getDerivations(anchoredCell(Rule.rootCat, 0, numTokens)));
    for (int depth = 1; depth <= maxDepth; depth++)
      predDerivations.addAll(getDerivations(floatingCell(Rule.rootCat, depth)));
    if (backoffParserState != null) {
      // Also combine derivations from the backoff parser state
      LogInfo.begin_track("Backoff ParserState");
      backoffParserState.infer();
      predDerivations.addAll(backoffParserState.predDerivations);
      // Prevent oracles from always being at the front.
      Collections.shuffle(predDerivations, DPParser.opts.shuffleRandom);
      LogInfo.end_track();
    }
  }

  // Collect the statistics and put them into the Evaluation object
  @Override
  protected void setEvaluation() {
    super.setEvaluation();
    // Number of cells
    countNumCells(firstPassCells, "firstPass");
    countNumCells(secondPassCells, "secondPass");
    // Number of possible ingredients
    evaluation.add("allowedIngredients", allowedDenotationIngredients.size());
  }

  private void countNumCells(Map<String, Map<Value, Metadata>> cells, String prefix) {
    int numAnchored = 0, numFloating = 0, numDenotations = 0,
        numErrorDenotations = 0, numUniqueErrorDenotations = 0, numDerivations = 0;
    Set<Value> uniqueDenotations = new HashSet<>();
    for (Map.Entry<String, Map<Value, Metadata>> entry : cells.entrySet()) {
      if (entry.getKey().contains(",")) numAnchored++; else numFloating++;
      for (Map.Entry<Value, Metadata> subentry : entry.getValue().entrySet()) {
        Value denotation = subentry.getKey();
        numDenotations++;
        uniqueDenotations.add(denotation);
        if (denotation instanceof ErrorValue)
          numErrorDenotations++;
        numDerivations += subentry.getValue().derivations.size();
      }
    }
    for (Value denotation : uniqueDenotations) {
      if (denotation instanceof ErrorValue)
        numUniqueErrorDenotations++;
    }
    evaluation.add(prefix + "Cells", cells.size());
    evaluation.add(prefix + "Anchored", numAnchored);
    evaluation.add(prefix + "Floating", numFloating);
    evaluation.add(prefix + "CellDenotations", numDenotations);
    evaluation.add(prefix + "ErrorDenotations", numErrorDenotations);
    evaluation.add(prefix + "UniqueDenotations", uniqueDenotations.size());
    evaluation.add(prefix + "UniqueErrorDenotations", numUniqueErrorDenotations);
    evaluation.add(prefix + "Derivations", numDerivations);
  }
}
