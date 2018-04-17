package edu.stanford.nlp.sempre;

import java.util.*;
import fig.basic.*;

/**
 * Actually does the parsing.  Main method is infer(), whose job is to fill in
 *
 * @author Roy Frostig
 * @author Percy Liang
 */
public abstract class ParserState {
  public static class Options {
    @Option(gloss = "Use a custom distribution for computing expected counts")
    public CustomExpectedCount customExpectedCounts = CustomExpectedCount.NONE;
    @Option(gloss = "For customExpectedCounts = TOP, only update if good < bad + margin")
    public double contrastiveMargin = 1e6;    // default = always update
    @Option(gloss = "Whether to prune based on probability difference")
    public boolean pruneByProbDiff = false;
    @Option(gloss = "Difference in probability for pruning by prob diff")
    public double probDiffPruningThresh = 100;
    @Option(gloss = "Throw features away after scoring to save memory")
    public boolean throwFeaturesAway = false;
  }
  public static Options opts = new Options();

  public enum CustomExpectedCount { NONE, UNIFORM, TOP, TOPALT, RANDOM, }

  //// Input: specification of how to parse

  public final Parser parser;
  public final Params params;
  public final Example ex;
  public final boolean computeExpectedCounts;  // Whether we're learning

  //// Output

  public final List<Derivation> predDerivations = new ArrayList<Derivation>();
  public final Evaluation evaluation = new Evaluation();

  // If computeExpectedCounts is true (for learning), then fill this out.
  public Map<String, Double> expectedCounts;
  public double objectiveValue;

  // Statistics generated while parsing
  public final int numTokens;
  public long parseTime;  // Number of milliseconds to parse this example
  public int maxCellSize; // Maximum number of derivations in any chart cell prior to pruning.
  public String maxCellDescription; // Description of that cell (for debugging)
  public boolean fallOffBeam; // Did any hypotheses fall off the beam?
  public int totalGeneratedDerivs; // Total number of derivations produced
  public int numOfFeaturizedDerivs = 0; // Number of derivations featured

  public ParserState(Parser parser, Params params, Example ex, boolean computeExpectedCounts) {
    this.parser = parser;
    this.params = params;
    this.ex = ex;
    this.computeExpectedCounts = computeExpectedCounts;
    this.numTokens = ex.numTokens();
  }

  protected int getBeamSize() { return Parser.opts.beamSize; }

  // Main entry point.  Should set all the output variables.
  public abstract void infer();

  protected void featurizeAndScoreDerivation(Derivation deriv) {
    if (deriv.isFeaturizedAndScored()) {
      LogInfo.warnings("Derivation already featurized: %s", deriv);
      return;
    }

    // Compute features
    parser.extractor.extractLocal(ex, deriv);

    // Compute score
    deriv.computeScoreLocal(params);

    if (opts.throwFeaturesAway)
      deriv.clearFeatures();

    if (parser.verbose(5)) {
      LogInfo.logs("featurizeAndScoreDerivation(score=%s) %s %s: %s [rule: %s]",
          Fmt.D(deriv.score), deriv.cat, ex.spanString(deriv.start, deriv.end), deriv, deriv.rule);
    }
    numOfFeaturizedDerivs++;
  }

  /**
   * Prune down the number of derivations in |derivations| to the beam size.
   * Sort the beam by score.
   * Update beam statistics.
   */
  protected void pruneCell(String cellDescription, List<Derivation> derivations) {
    if (derivations == null) return;

    // Update stats about cell size.
    if (derivations.size() > maxCellSize) {
      maxCellSize = derivations.size();
      maxCellDescription = cellDescription;
      if (maxCellSize > 5000)
        LogInfo.logs("ParserState.pruneCell %s: maxCellSize = %s entries (not pruned yet)",
            maxCellDescription, maxCellSize);
    }

    // The extra code blocks in here that set |deriv.maxXBeamPosition|
    // are there to track, over the course of parsing, the lowest
    // position at which any of a derivation's constituents ever
    // placed on any of the relevant beams.

    // Max beam position (before sorting)
    int i = 0;
    for (Derivation deriv : derivations) {
      deriv.maxUnsortedBeamPosition = i;
      if (deriv.children != null) {
        for (Derivation child : deriv.children)
          deriv.maxUnsortedBeamPosition = Math.max(deriv.maxUnsortedBeamPosition, child.maxUnsortedBeamPosition);
      }
      if (deriv.preSortBeamPosition == -1) {
        // Need to be careful to only do this once since |pruneCell()|
        // might be called several times for the same beam and the
        // second time around we have already sorted once.
        deriv.preSortBeamPosition = i;
      }
      i++;
    }

    // Inject noise into the noise (to simulate sampling); ideally would add Gumbel noise
    if (Parser.opts.derivationScoreNoise > 0) {
      for (Derivation deriv : derivations)
        deriv.score += Parser.opts.derivationScoreRandom.nextDouble() * Parser.opts.derivationScoreNoise;
    }

    Derivation.sortByScore(derivations);

    // Print out information
    if (Parser.opts.verbose >= 3) {
      LogInfo.begin_track("ParserState.pruneCell(%s): %d derivations", cellDescription, derivations.size());
      for (Derivation deriv : derivations) {
        LogInfo.logs("%s(%s,%s): %s %s, [score=%s] allAnchored: %s", deriv.cat, deriv.start, deriv.end, deriv.formula,
            deriv.canonicalUtterance, deriv.score, deriv.allAnchored());
      }
      LogInfo.end_track();
    }

    // Max beam position (after sorting)
    i = 0;
    for (Derivation deriv : derivations) {
      deriv.maxBeamPosition = i;
      if (deriv.children != null) {
        for (Derivation child : deriv.children)
          deriv.maxBeamPosition = Math.max(deriv.maxBeamPosition, child.maxBeamPosition);
      }
      deriv.postSortBeamPosition = i;
      i++;
    }

    //prune all d_i s.t  p(d_1) > CONST \cdot p(d_i)
    if(ChartParserState.opts.pruneByProbDiff) {
      double highestScore = derivations.get(0).score;
      while (highestScore - derivations.get(derivations.size()-1).score > Math.log(opts.probDiffPruningThresh)) {
        derivations.remove(derivations.size() - 1);
        fallOffBeam = true;
      }
    }
    //prune by beam size
    else {
      // Keep only the top hypotheses
      int beamSize = getBeamSize();
      if (derivations.size() > beamSize && Parser.opts.verbose >= 1) {
        LogInfo.logs("ParserState.pruneCell %s: Pruning %d -> %d derivations", cellDescription, derivations.size(), beamSize);
      }
      while (derivations.size() > beamSize) {
        derivations.remove(derivations.size() - 1);
        fallOffBeam = true;
      }
    }
  }

  // -- Base case --
  public List<Derivation> gatherTokenAndPhraseDerivations() {
    List<Derivation> derivs = new ArrayList<>();

    // All tokens (length 1)
    for (int i = 0; i < numTokens; i++) {
      derivs.add(
              new Derivation.Builder()
                      .cat(Rule.tokenCat).start(i).end(i + 1)
                      .rule(Rule.nullRule)
                      .children(Derivation.emptyList)
                      .withStringFormulaFrom(ex.token(i))
                      .canonicalUtterance(ex.token(i))
                      .createDerivation());

      // Lemmatized version
      derivs.add(
              new Derivation.Builder()
                      .cat(Rule.lemmaTokenCat).start(i).end(i + 1)
                      .rule(Rule.nullRule)
                      .children(Derivation.emptyList)
                      .withStringFormulaFrom(ex.lemmaToken(i))
                      .canonicalUtterance(ex.token(i))
                      .createDerivation());
    }

    // All phrases (any length)
    for (int i = 0; i < numTokens; i++) {
      for (int j = i + 1; j <= numTokens; j++) {
        derivs.add(
                new Derivation.Builder()
                        .cat(Rule.phraseCat).start(i).end(j)
                        .rule(Rule.nullRule)
                        .children(Derivation.emptyList)
                        .withStringFormulaFrom(ex.phrase(i, j))
                        .canonicalUtterance(ex.phrase(i, j))
                        .createDerivation());

        // Lemmatized version
        derivs.add(
                new Derivation.Builder()
                        .cat(Rule.lemmaPhraseCat).start(i).end(j)
                        .rule(Rule.nullRule)
                        .children(Derivation.emptyList)
                        .withStringFormulaFrom(ex.lemmaPhrase(i, j))
                        .canonicalUtterance(ex.phrase(i, j))
                        .createDerivation());
      }
    }
    return derivs;
  }

  // Ensure that all the logical forms are executed and compatibilities are computed.
  public void ensureExecuted() {
    LogInfo.begin_track("Parser.ensureExecuted");
    // Execute predicted derivations to get value.
    for (Derivation deriv : predDerivations) {
      deriv.ensureExecuted(parser.executor, ex.context);
      if (ex.targetValue != null)
        deriv.compatibility = parser.valueEvaluator.getCompatibility(ex.targetValue, deriv.value);
      if (!computeExpectedCounts && Parser.opts.executeTopFormulaOnly) break;
    }
    LogInfo.end_track();
  }

  // Add statistics to |evaluation|.
  // Override if we have more statistics.
  protected void setEvaluation() {
    evaluation.add("numTokens", numTokens);
    evaluation.add("parseTime", parseTime);
    evaluation.add("maxCellSize", maxCellDescription, maxCellSize);
    evaluation.add("fallOffBeam", fallOffBeam);
    evaluation.add("totalDerivs", totalGeneratedDerivs);
    evaluation.add("numOfFeaturizedDerivs", numOfFeaturizedDerivs);
  }

  public static double compatibilityToReward(double compatibility) {
    if (Parser.opts.partialReward)
      return compatibility;
    return compatibility == 1 ? 1 : 0;  // All or nothing
  }

  /**
   * Fill |counts| with the gradient with respect to the derivations
   * according to a standard exponential family model over a finite set of derivations.
   * Assume that everything has been executed, and compatibility has been computed.
   */
  public static void computeExpectedCounts(List<Derivation> derivations, Map<String, Double> counts) {
    double[] trueScores;
    double[] predScores;

    int n = derivations.size();
    if (n == 0) return;

    trueScores = new double[n];
    predScores = new double[n];
    // For update schemas that choose one good and one bad candidate to update
    int[] goodAndBad = null;
    if (opts.customExpectedCounts == CustomExpectedCount.TOP || opts.customExpectedCounts == CustomExpectedCount.TOPALT) {
      goodAndBad = getTopDerivations(derivations);
      if (goodAndBad == null) return;
    } else if (opts.customExpectedCounts == CustomExpectedCount.RANDOM) {
      goodAndBad = getRandomDerivations(derivations);
      if (goodAndBad == null) return;
    }

    for (int i = 0; i < n; i++) {
      Derivation deriv = derivations.get(i);
      double logReward = Math.log(compatibilityToReward(deriv.compatibility));

      switch (opts.customExpectedCounts) {
        case NONE:
          trueScores[i] = deriv.score + logReward;
          predScores[i] = deriv.score;
          break;
        case UNIFORM:
          trueScores[i] = logReward;
          predScores[i] = 0;
          break;
        case TOP: case RANDOM:
          trueScores[i] = (i == goodAndBad[0]) ? 0 : Double.NEGATIVE_INFINITY;
          predScores[i] = (i == goodAndBad[1]) ? 0 : Double.NEGATIVE_INFINITY;
          break;
        case TOPALT:
          trueScores[i] = (i == goodAndBad[0]) ? 0 : Double.NEGATIVE_INFINITY;
          predScores[i] = (i == goodAndBad[0] || i == goodAndBad[1]) ? deriv.score : Double.NEGATIVE_INFINITY;
          break;
        default:
          throw new RuntimeException("Unknown customExpectedCounts: " + opts.customExpectedCounts);
      }
    }

    // Usually this happens when there are no derivations.
    if (!NumUtils.expNormalize(trueScores)) return;
    if (!NumUtils.expNormalize(predScores)) return;

    // Update parameters
    for (int i = 0; i < n; i++) {
      Derivation deriv = derivations.get(i);
      double incr = trueScores[i] - predScores[i];
      if (incr == 0) continue;
      deriv.incrementAllFeatureVector(incr, counts);
    }
  }

  private static int[] getTopDerivations(List<Derivation> derivations) {
    int chosenGood = -1, chosenBad = -1;
    double chosenGoodScore = Double.NEGATIVE_INFINITY, chosenBadScore = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < derivations.size(); i++) {
      Derivation deriv = derivations.get(i);
      if (deriv.compatibility == 1) {    // good
        if (deriv.score > chosenGoodScore) {
          chosenGood = i; chosenGoodScore = deriv.score;
        }
      } else {    // bad
        if (deriv.score > chosenBadScore) {
          chosenBad = i; chosenBadScore = deriv.score;
        }
      }
    }
    if (chosenGood == -1 || chosenBad == -1 || chosenGoodScore >= chosenBadScore + opts.contrastiveMargin)
      return null;
    return new int[] {chosenGood, chosenBad};
  }

  private static int[] getRandomDerivations(List<Derivation> derivations) {
    int chosenGood = -1, chosenBad = -1, numGoodSoFar = 0, numBadSoFar = 0;
    // Get a uniform random sample from the stream
    for (int i = 0; i < derivations.size(); i++) {
      Derivation deriv = derivations.get(i);
      if (deriv.compatibility == 1) {
        numGoodSoFar++;
        if (Math.random() <= 1.0 / numGoodSoFar) {
          chosenGood = i;
        }
      } else {    // bad
        numBadSoFar++;
        if (Math.random() <= 1.0 / numBadSoFar) {
          chosenBad = i;
        }
      }
    }
    return (chosenGood == -1 || chosenBad == -1) ? null : new int[] {chosenGood, chosenBad};
  }
}
