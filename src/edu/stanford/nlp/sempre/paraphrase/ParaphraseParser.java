package edu.stanford.nlp.sempre.paraphrase;

import edu.stanford.nlp.sempre.BooleanValue;
import edu.stanford.nlp.sempre.Evaluation;
import edu.stanford.nlp.sempre.Executor;
import edu.stanford.nlp.sempre.FeatureVector;
import edu.stanford.nlp.sempre.FormulaGenerationInfo;
import edu.stanford.nlp.sempre.FormulaRetriever;
import edu.stanford.nlp.sempre.Params;

import fig.basic.Fmt;
import fig.basic.LogInfo;
import fig.basic.Option;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * Paraphrase parsing is done in the following way:
 * Given a question |q| - 
 * 1. Find candidate formulas |(r,e)| relevant for |q|
 * 2. Generate candidate questions |q'| for each |(r,e)|
 * 3. For every |q'| transfrom q to q'
 * 4. extract features from the transformation, r and e and using params score
 * 5. return the answer by executing the formula that has the highest score
 * @author jonathanberant
 *
 */
public class ParaphraseParser {

  public static class Options {
    @Option(gloss="Whether to use the vsm model") public boolean vsm=true;
    @Option(gloss="Whether to use the alignment model") public boolean alignment=true;
    @Option(gloss="Whether to use the alignment model") public boolean baseline=false;
    @Option(gloss="Size of beam") public int beamSize=2000;
  }
  public static Options opts = new Options();

  private final VectorSpaceModel vsmModel;
  private final Aligner aligner;
  private final FeatureSimilarityComputer fsComputer;

  private final QuestionGenerator qGenerator;
  private final Executor executor;
  private final FormulaRetriever fRetriever;
  private final ParaphraseFeatureExtractor fExtractor;

  public ParaphraseParser(Executor executor) throws IOException {
    qGenerator = new QuestionGenerator();
    fRetriever = new FormulaRetriever(false);
    fExtractor = new ParaphraseFeatureExtractor(executor);
    vsmModel=VectorSpaceModel.getSingleton();
    aligner=Aligner.getSingleton();
    fsComputer = FeatureSimilarityComputerFactory.getFeatureSimilarityComputer();
    this.executor=executor;
    if(opts.baseline && (opts.vsm ||
            opts.alignment))
      throw new RuntimeException("When using baseline can not use any other model");
  }

  public void parseQuestion(ParsingExample example, Params params) {

    String question = example.utterance;
    ensureTargetExecuted(example);
    LogInfo.begin_track("Generating candidate formulas");
    List<FormulaGenerationInfo> formulaGenerationInfos = fRetriever.retrieveFormulas(example);
    LogInfo.logs("Number of candidate formulas=%s",formulaGenerationInfos.size());

    LogInfo.end_track();

    LogInfo.begin_track("Generate questions and transform");

    int numOfGeneratedQuestions=0;
    for(FormulaGenerationInfo formulaGenerationInfo: formulaGenerationInfos) { // for every candidate formula

      LogInfo.begin_track("FormulaGenerationInfo=%s",formulaGenerationInfo);
      Set<String> generatedQuestions =
              qGenerator.getQuestionsForFgInfo(formulaGenerationInfo); //maybe generalize to arbitrary formula
      for(String generatedQuestion: generatedQuestions) { //for every generated question
        LogInfo.logs("ParaphraseParser.parseQuestion: genQue=%s",generatedQuestion);
        ParaphraseExample paraEx = new ParaphraseExample(question, generatedQuestion, new BooleanValue(true));

        if(opts.vsm)
          vsmModel.computeSimilarity(paraEx, params);
        if(opts.alignment)
          aligner.align(paraEx, params);
        if(opts.baseline)
          fsComputer.computeSimilarity(paraEx, params);


          ParaphraseDerivation paraphraseDerivation =
                  new ParaphraseDerivation(example.languageInfo,
                          paraEx,formulaGenerationInfo,fExtractor,params);
          example.addPrediction(paraphraseDerivation);
        numOfGeneratedQuestions++;
      }
      LogInfo.end_track();
    }
    LogInfo.logs("ParaphraseParser.parseQuestions: genQuestions=%s",numOfGeneratedQuestions);
    LogInfo.end_track();

    example.sortDerivations();
    example.predParaDeriv =
            new ArrayList<>(example.predParaDeriv.subList(0, Math.min(opts.beamSize, example.predParaDeriv.size())));
    LogInfo.logs("ParphraseParser.parseQuestion: Number of parphrase derivations=%s",example.predParaDeriv.size());
    setEvaluation(example,params);
  }

  private void ensureTargetExecuted(ParsingExample example) {
    if(example.targetValue==null && example.targetFormula!=null)
      example.targetValue = executor.execute(example.targetFormula).value;
  }

  /**
   * Assumes that praphrasederivations are scored and sorted
   */
  private void setEvaluation(ParsingExample ex, Params params) {
    final Evaluation eval = new Evaluation();
    int numCandidates = ex.predParaDeriv.size();
    LogInfo.begin_track_printAll("Parser.setEvaluation: %d candidates", numCandidates);

    // Each derivation has a compatibility score (in [0,1]) as well as a model probability.
    // Terminology:
    //   True (correct): compatibility = 1
    //   Partial: 0 < compatibility < 1
    //   Wrong: compatibility = 0

    List<ParaphraseDerivation> predictions = ex.predParaDeriv;

    // Make sure derivations are executed
    for (ParaphraseDerivation paraphraseDeriv : predictions) {
      paraphraseDeriv.ensureExecuted(executor);
    }

    // Did we get the answer correct?
    int correct_i = -1;  // Index of first correct derivation
    double maxCompatibility = 0.0;
    double[] compatibilities = null;
    if (ex.targetValue != null) {
      compatibilities = new double[numCandidates];
      for (int i = 0; i < numCandidates; i++) {
        ParaphraseDerivation proofDeriv = predictions.get(i);
        compatibilities[i] = proofDeriv.compatibility = ex.targetValue.getCompatibility(proofDeriv.value);

        // Must be fully compatible to count as correct.
        if (compatibilities[i] == 1 && correct_i == -1)
          correct_i = i;
        //record maximum compatibility for partial oracle
        maxCompatibility = Math.max(compatibilities[i], maxCompatibility);
      }
    }

    // Compute probabilities
    double[] probs = ParaphraseDerivation.getProbs(predictions, 1);
    for (int i = 0; i < numCandidates; i++) {
      predictions.get(i).prob = probs[i];
    }

//    List<Pair<Value, DoubleContainer>> valueList = computeValueList(ex.predParaDeriv);
//    evaluateValues(eval, ex, valueList);

    // Number of derivations which have the same top score
    int numTop = 0;
    double topMass = 0;
    if (ex.targetValue != null) {
      while (numTop < numCandidates &&
              compatibilities[numTop] > 0.0d &&
              Math.abs(predictions.get(numTop).score - predictions.get(0).score) < 1e-10) {
        topMass += probs[numTop];
        numTop++;
      }
    }
    double correct = 0;
    double partial_correct = 0;
    if (ex.targetValue != null) {
      for (int i = 0; i < numTop; i++) {
        if (compatibilities[i] == 1) correct += probs[i] / topMass;
        if (compatibilities[i] > 0)
          partial_correct += (compatibilities[i] * probs[i]) / topMass;
      }
    }

    // Print features (note this is only with respect to the first correct, is NOT the gradient).
    // Things are not printed if there is only partial compatability.
    if (correct_i != -1 && correct != 1) {
      ParaphraseDerivation trueDeriv = predictions.get(correct_i);
      ParaphraseDerivation predDeriv = predictions.get(0);
      HashMap<String, Double> featureDiff = new HashMap<>();
      trueDeriv.incrementAllFeatureVector(+1, featureDiff); //TODO if features will go out of proof this needs to change
      predDeriv.incrementAllFeatureVector(-1, featureDiff);
      String heading = String.format("TopTrue (%d) - Pred (%d) = Diff", correct_i, 0);
      FeatureVector.logFeatureWeights(heading, featureDiff, params);
    }

    // Fully correct
    for (int i = 0; i < predictions.size(); i++) {
      ParaphraseDerivation deriv = predictions.get(i);
      if (compatibilities != null && compatibilities[i] == 1) {
        LogInfo.logs(
                "True@%04d: %s [score=%s, prob=%s%s]", i, deriv.toString(),
                Fmt.D(deriv.score), Fmt.D(probs[i]), compatibilities != null ? ", comp=" + Fmt.D(compatibilities[i]) : "");
      }
    }
    // Partially correct
    for (int i = 0; i < predictions.size(); i++) {
      ParaphraseDerivation deriv = predictions.get(i);
      if (compatibilities != null && compatibilities[i] > 0 && compatibilities[i] < 1) {
        LogInfo.logs(
                "Part@%04d: %s [score=%s, prob=%s%s]", i, deriv.toString(),
                Fmt.D(deriv.score), Fmt.D(probs[i]), compatibilities != null ? ", comp=" + Fmt.D(compatibilities[i]) : "");
      }
    }
    // Anything that's predicted.
    for (int i = 0; i < predictions.size(); i++) {
      ParaphraseDerivation deriv = predictions.get(i);
      // Either print all predictions or this prediction is worse by some amount.
      boolean print;
      print = probs[i] >= probs[0] / 2 || i < 10;
      if (print) {
        LogInfo.logs(
                "Pred@%04d: %s [score=%s, prob=%s%s]", i, deriv.toString(),
                Fmt.D(deriv.score), Fmt.D(probs[i]), compatibilities != null ? ", comp=" + Fmt.D(compatibilities[i]) : "");
      }
    }

    eval.add("correct", correct);
    eval.add("oracle", correct_i != -1);
    eval.add("partCorrect", partial_correct);
    eval.add("partOracle", maxCompatibility);
    eval.add("numCandidates", numCandidates);  // From this parse
    if (predictions.size() > 0)
      eval.add("parsedNumCandidates", predictions.size());

    for (ParaphraseDerivation deriv : predictions) {
      if (deriv.executorStats != null)
        eval.add(deriv.executorStats);
    }
    // Finally, set all of these stats as the example's evaluation.
    ex.setEvaluation(eval);
    LogInfo.end_track();
  }
}
