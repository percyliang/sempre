package edu.stanford.nlp.sempre;

import fig.basic.LogInfo;
import fig.basic.MapUtils;
import fig.basic.SetUtils;
import fig.basic.StopWatch;

import java.lang.reflect.Array;
import java.util.*;

/** @author Roy Frostig */
public abstract class ParserState {
  // Modes:
  // 1) Bool: just check if cells (cat, start, end) are reachable (to prune chart)
  // 2) Full: compute everything
  public enum Mode { bool, full };

  private final Mode mode;
  private final Parser parser;
  private final Params params;
  private final Example ex;
  private final ParserState coarseState; // Used to prune (optional)
  final int numTokens;

  // Dynamic programming chart
  // cell (start, end, category) -> list of derivations (sorted by
  // decreasing score) [beam]
  final Map<String, List<Derivation>>[][] chart;

  private boolean execAllowed;

  // -- Some informational stats --
  // Number of milliseconds to parse this sentence
  protected long time;
  // Maximum number of derivations in any chart cell prior to pruning.
  protected int maxCellSize;
  // Description of that cell (for debugging)
  protected String maxCellDescription;
  // Did any hypotheses fall off the beam?
  protected boolean fallOffBeam;
  protected int totalDerivsInChart=0;

  // Parser timing
  StopWatch watch;

  //whether to create derivations based on subparts
  private boolean filterDerivationsBySubparts = false;
  //subpart descriptions
  private Set<String> subParts = null;

  @SuppressWarnings({"unchecked"})
  public ParserState(Mode mode,
      Parser parser,
      Params params,
      Example ex,
      ParserState coarseState) {
    this.mode = mode;
    this.parser = parser;
    this.params = params;
    this.ex = ex;
    this.coarseState = coarseState;
    this.numTokens = ex.numTokens();
    this.execAllowed = true;

    // Initialize the chart.
    this.chart = (HashMap<String, List<Derivation>>[][])
        Array.newInstance(
            HashMap.class,
            numTokens, numTokens + 1);
    for (int start = 0; start < numTokens; start++) {
      for (int end = start + 1; end <= numTokens; end++) {
        chart[start][end] = new HashMap<String, List<Derivation>>();
      }
    }
  }
  
  public void clearChart() {
    for (int start = 0; start < numTokens; start++) {
      for (int end = start + 1; end <= numTokens; end++) {
        chart[start][end].clear();
      }
    }
  }

  public Mode getMode() { return mode; }
  public Example getExample() { return ex; }
  public ParserState getCoarseState() { return coarseState; }
  public Params getModelParams() { return params; }
  public long getParseTime() { return time; }
  public Map<String, List<Derivation>>[][] getChart() { return chart; }

  public List<Derivation> getPredDerivations() {
    final List<Derivation> empty = Collections.emptyList();
    if (numTokens == 0)
      return empty;
    List<Derivation> result = chart[0][numTokens].get(Rule.rootCat);
    return result == null ? empty : result;
  }

  // Subclass entry point.
  protected abstract void build(int start, int end);

  public void infer() {
    if (numTokens == 0)
      return;

    watch = new StopWatch();
    watch.start();
    if (parser.verbose(2))
      LogInfo.begin_track("ParserState.infer");

    for (Derivation deriv : gatherTokenAndPhraseDerivations()) {
      featurizeAndScoreDerivation(deriv);
      addToChart(deriv);
    }

    // Recurse
    for (int len = 1; len <= numTokens; len++)
      for (int i = 0; i + len <= numTokens; i++)
        build(i, i + len);

    if (parser.verbose(2))
      LogInfo.end_track();
    watch.stop();
    time = watch.getCurrTimeLong();
  }

  int getBeamSize() {
    return ex.beamSize != -1 ? ex.beamSize : parser.getDefaultBeamSize();
  }

  void addToChart(Derivation deriv) {
    if(filterDerivationsBySubparts && !isValidSubpartDerivation(deriv))
      return;
    MapUtils.addToList(chart[deriv.start][deriv.end], deriv.cat, deriv);
    totalDerivsInChart++;
  }

  @SuppressWarnings({"unchecked"})
  // TODO: hash the formulas rather than the strings.
  private boolean isValidSubpartDerivation(Derivation deriv) {
    if(deriv.formula==null)
      return true;
    if(deriv.formula instanceof PrimitiveFormula) {
      ValueFormula<Value> valueFormula = (ValueFormula<Value>) deriv.formula;
      if(valueFormula.value instanceof StringValue)
        return true;
    }

    if("(type fb:type.text)".equals(deriv.type.toString()))
      return true;
    boolean valid = subParts.contains(deriv.formula.toString()); 
    if(parser.verbose(2))
      LogInfo.logs("ParserState.isValidSubpart: formula=%s, valid=%s",deriv.formula,valid);
    return valid;
  }

  void setExecAllowed(boolean execAllowed) {
    this.execAllowed = execAllowed;
  }

  protected void featurizeAndScoreDerivation(Derivation deriv) {
    if (parser.verbose(deriv.isRoot(numTokens) ? 2 : 3))
      LogInfo.logs(
          "featurizeAndScore %s %s: %s, %s",
          deriv.cat, ex.spanString(deriv.start, deriv.end), deriv,deriv.rule);

    // Compute features
    parser.extractor.extractLocal(ex, deriv);

    // Compute score
    deriv.computeScoreLocal(params);

    if (parser.verbose(deriv.isRoot(numTokens) ? 2 : 3))
      LogInfo.logs("featurizeAndScore score=%.4f", deriv.score);
  }

  // -- Coarse state pruning --

  // Remove any (cat, start, end) which isn't reachable from the
  // (Rule.rootCat, 0, numTokens)
  public void keepTopDownReachable() {
    if (numTokens == 0) return;

    Set<String> reachable = new HashSet<String>();
    collectReachable(reachable, Rule.rootCat, 0, numTokens);

    // Remove all derivations associated with (cat, start, end) that aren't reachable.
    for (int start = 0; start < numTokens; start++) {
      for (int end = start + 1; end <= numTokens; end++) {
        List<String> toRemoveCats = new LinkedList<String>();
        for (String cat : chart[start][end].keySet()) {
          String key = catStartEndKey(cat, start, end);
          if (!reachable.contains(key)) {
            toRemoveCats.add(cat);
          }
        }
        Collections.sort(toRemoveCats);
        for (String cat : toRemoveCats) {
          if(parser.verbose(1)) {
            LogInfo.logs("Pruning chart %s(%s,%s)",cat,start,end);
          }
          chart[start][end].remove(cat);
        }
      }
    }
  }
  private void collectReachable(Set<String> reachable, String cat, int start, int end) {
    String key = catStartEndKey(cat, start, end);
    if (reachable.contains(key)) return;

    if (!chart[start][end].containsKey(cat)) {
      // This should only happen for the root when there are no parses.
      return;
    }

    reachable.add(key);
    for (Derivation deriv : chart[start][end].get(cat)) {
      for (Derivation subderiv : deriv.children) {
        collectReachable(reachable, subderiv.cat, subderiv.start, subderiv.end);
      }
    }
  }
  private String catStartEndKey(String cat, int start, int end) {
    return cat + ":" + start + ":" + end;
  }

  // For pruning with the coarse state
  protected boolean coarseAllows(Trie node, int start, int end) {
    if (coarseState == null) return true;
    return SetUtils.intersects(
        node.cats,
        coarseState.getChart()[start][end].keySet());
  }
  protected boolean coarseAllows(String cat, int start, int end) {
    if (coarseState == null) return true;
    return coarseState.getChart()[start][end].containsKey(cat);
  }

  // -- Initialization --

  public List<Derivation> gatherTokenAndPhraseDerivations() {
    List<Derivation> derivs = new ArrayList<Derivation>();
    Example ex = getExample();
    int numTokens = ex.numTokens();

    final List<Derivation> empty = Collections.emptyList();

    // All tokens (length 1)
    for (int i = 0; i < numTokens; i++) {
      derivs.add(
          new Derivation.Builder()
          .cat(Rule.tokenCat).start(i).end(i + 1)
          .rule(Rule.nullRule)
          .children(empty)
          .withStringFormulaFrom(ex.token(i))
          .createDerivation());

      // Lemmatized version
      derivs.add(
          new Derivation.Builder()
          .cat(Rule.lemmaTokenCat).start(i).end(i + 1)
          .rule(Rule.nullRule)
          .children(empty)
          .withStringFormulaFrom(ex.lemmaToken(i))
          .createDerivation());
    }

    // All phrases (any length)
    for (int i = 0; i < numTokens; i++) {
      for (int j = i + 1; j <= numTokens; j++) {
        derivs.add(
            new Derivation.Builder()
            .cat(Rule.phraseCat).start(i).end(j)
            .rule(Rule.nullRule)
            .children(empty)
            .withStringFormulaFrom(ex.phrase(i, j))
            .createDerivation());

        // Lemmatized version
        derivs.add(
            new Derivation.Builder()
            .cat(Rule.lemmaPhraseCat).start(i).end(j)
            .rule(Rule.nullRule)
            .children(empty)
            .withStringFormulaFrom(ex.lemmaPhrase(i, j))
            .createDerivation());
      }
    }
    return derivs;
  }

  // -- Evaluation --

  public void setEvaluation() {
    LogInfo.begin_track_printAll("ParserState.setEvaluation");

    final Example ex = getExample();
    final Evaluation eval = new Evaluation();
    final boolean parsed = ex.predDerivations.size() > 0;
    eval.add("parsed", parsed);
    eval.add("numTokens", numTokens);
    eval.add("maxCellSize", maxCellDescription, maxCellSize);
    eval.add("fallOffBeam", fallOffBeam);

    if (getCoarseState() != null)
      eval.add("coarseParseTime", getCoarseState().getParseTime());
    eval.add("parseTime", getParseTime());
    eval.add("totalDerivs", totalDerivsInChart);

    ex.setParseEvaluation(eval);
    LogInfo.end_track();
  }

  /**
   * Generate fomrula subparts and only allow derivations with these formulas
   * TODO: move this logic to FormulaGenerationInfo.  ParserState is generic and
   * shouldn't depend on FormulaGenerationInfo.
   * @param fgInfos
   */
  public void setFormulaSubparts(List<FormulaGenerationInfo> fgInfos) {
    LogInfo.begin_track("Setting formulas subparts");
    filterDerivationsBySubparts=true;
    subParts = new HashSet<String>();
    for(FormulaGenerationInfo fgInfo: fgInfos) {
      Formula f = fgInfo.generateFormula();
      subParts.addAll(Formulas.extractSubparts(f));
    }
    LogInfo.logs("Number of subparts=%s",subParts.size());
    LogInfo.end_track();
  }
}
