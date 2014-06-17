package edu.stanford.nlp.sempre.paraphrase;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.fbalignment.utils.DoubleContainer;
import edu.stanford.nlp.sempre.fbalignment.utils.MathUtils;
import fig.basic.*;

import java.io.File;
import java.util.*;

/**
 * Maps a context to a distribution over logical forms
 * @author jonathanberant
 *
 */
public class ContextModel {

  public static class Options {
    @Option public int verbose = 3;
    @Option public String packageName = "edu.stanford.nlp.sempre.paraphrase";
    @Option public String contextModelName;
    @Option public double minCosine=0.75;
  }
  public static Options opts = new Options();

  @JsonProperty private Map<Context,Map<Formula,DoubleContainer>> contextToBinaryCounter;
  private boolean normalized=false;
  private FbFormulasInfo fbFormulasInfo;
  private ContextSimilarityModel transModel;

  public ContextModel() {
    contextToBinaryCounter = new HashMap<>();
    fbFormulasInfo = FbFormulasInfo.getSingleton();
    try {
      transModel = (ContextSimilarityModel) Class.forName(opts.packageName+"."+opts.contextModelName).newInstance();
    }
    catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
      throw new RuntimeException(e);
    }
  }

  @JsonCreator
  public ContextModel(@JsonProperty("contextToBinaryCounter") Map<Context,Map<Formula,DoubleContainer>> contextToBinaryCounter) {
    this.contextToBinaryCounter = contextToBinaryCounter;
    fbFormulasInfo = FbFormulasInfo.getSingleton();
    try {    
      transModel = (ContextSimilarityModel) Class.forName(opts.packageName+"."+opts.contextModelName).newInstance();
    }
    catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public void inc(Context c, Formula f, double value) {
    if(normalized)
      throw new RuntimeException("Can not increment a normalized context mapper");

    MapUtils.putIfAbsent(contextToBinaryCounter, c, new HashMap<Formula,DoubleContainer>());
    Map<Formula,DoubleContainer> formulaMap = contextToBinaryCounter.get(c); //guaranteed to exist

    MapUtils.putIfAbsent(formulaMap, f, new DoubleContainer(0.0));
    DoubleContainer currValue = formulaMap.get(f);
    currValue.inc(value);

  }

  public void normalize() {
    normalized = true;
    LogInfo.logs("ContextModel.normalize: number of contexts=%s",contextToBinaryCounter.size());

    for(Context context: contextToBinaryCounter.keySet()) {
      Map<Formula,DoubleContainer> formulaCounter = contextToBinaryCounter.get(context);
      MathUtils.normalizeDoubleMap(formulaCounter);
    }
  }

  public boolean containsKey(Context c) {
    return contextToBinaryCounter.containsKey(c);
  }

  public void map(Example ex) {

    Map<Formula,List<Pair<Context,Interval>>> formulaToContextAndInterval = new HashMap<>();
    //go over all derivations and check which one contains a correct formula
    for(Derivation deriv: ex.getPredDerivations()) {
      if(ex.targetValue.getCompatibility(deriv.getValue())>0.999) {
        Formula f = extractFormula(deriv.formula);
        processCurrDerivContext(formulaToContextAndInterval, ParaphraseUtils.extractContextIntervalPair(deriv, ex),f);
      }
    }

    for(Formula f: formulaToContextAndInterval.keySet()) {
      for(Pair<Context,Interval> pair: formulaToContextAndInterval.get(f)) {
        LogInfo.logs("ContextModel: context=%s, formula=%s",pair.getFirst(),f);        
        inc(pair.getFirst(), f, 1.0);
      }
    }
  }


  private void processCurrDerivContext(Map<Formula, List<Pair<Context, Interval>>> formulaToContextAndInterval, 
      Pair<Context,Interval> contextIntervalPair, Formula f) {

    Pair<Context,Interval> newPair = Pair.newPair(contextIntervalPair.getFirst(),contextIntervalPair.getSecond());
    List<Pair<Context,Interval>> existingformulaContexts = formulaToContextAndInterval.get(f);
    if(existingformulaContexts==null) {
      existingformulaContexts = new LinkedList<>();
      formulaToContextAndInterval.put(f, existingformulaContexts);
    }
    for(Pair<Context,Interval> existingFormulaContext: existingformulaContexts) {
      if(existingFormulaContext.getSecond().superset(newPair.getSecond())) {
        if(opts.verbose>=3) {
          LogInfo.logs("ContextModel: skipping context, context=%s, interval=%s, otherContext=%s, otherInterval=%s",newPair.getFirst(),
              newPair.getSecond(),existingFormulaContext.getFirst(),existingFormulaContext.getSecond());
        }
        return;
      }
    }
    existingformulaContexts.add(newPair);
  }

  private Formula extractFormula(Formula formula) {
    if(!(formula instanceof JoinFormula)) 
      throw new RuntimeException("ContextModel: logical form is not a join formula: " + formula);
    JoinFormula join = (JoinFormula) formula;
    return Formulas.fromLispTree(join.relation.toLispTree());
  }

  /**
   * Given an example and the context, compare the context to all contexts we have and find the best formula for it
   * then apply this formula on the entity
   * @param ex
   * @param entityInstancesScores
   */
  public List<Prediction> computePredictions(Example ex, TDoubleMap<EntityInstance> entityInstancesScores) {

    LogInfo.begin_track("ContextModel.computePredictions()");
    TDoubleMap<Formula> formulaScores = new TDoubleMap<>();
    //for every possible entity
    for(EntityInstance entityInstance: entityInstancesScores.keySet()) {

      //for every context
      Map<Context,Double> contextDist = transModel.getContextDist(contextToBinaryCounter.keySet(), entityInstance.context);
      for(Context currContext: contextDist.keySet()) {

        Map<Formula,DoubleContainer> contextFormulas = MapUtils.get(contextToBinaryCounter, currContext, new HashMap<Formula,DoubleContainer>());
        double contextProb = contextDist.get(currContext);
        LogInfo.logs("ContextModel: context=%s, context probability=%s, number of formulas=%s",currContext, contextProb,contextFormulas.size()); 

        //for each formula compute its probability
        calcFormulaProbsGivenContextAndEntity(entityInstance,entityInstancesScores.get(entityInstance, 0.0), contextProb, contextFormulas,formulaScores);
      }
    }
    //after computing everything we need to sort and return
    List<Prediction> res = new ArrayList<Prediction>();
    for(Formula f: formulaScores.keySet()) {
      res.add(new Prediction(f, formulaScores.get(f, 0.0)));
    }
    Collections.sort(res);
    if(opts.verbose>=3) {
      LogInfo.logs("ContextModel: number of type compatible formulas=%s",res.size());
      for(Prediction p: res)
        LogInfo.logs("ContextModel: prediction=%s, score=%s",p.formula, p.score);
    }
    LogInfo.end_track();
    return res;
  }

  /**
   * Formula probability is computed by using the model, zeroing relations that are not
   * compatible with the entity and re-normalizing
   */
  private void calcFormulaProbsGivenContextAndEntity(
      EntityInstance entityInstance, double entityProb, double contextProb, Map<Formula,DoubleContainer> contextRelations, TDoubleMap<Formula> formulaScores) {

    Formula[] relations = new Formula[contextRelations.size()];
    double[] scores = new double[contextRelations.size()];

    int i = 0;
    //zero non-compatible scores
    for(Formula currRelation: contextRelations.keySet()) {

      //see if semantic types match
      FbFormulasInfo.BinaryFormulaInfo bInfo = fbFormulasInfo.getBinaryInfo(currRelation);
      if(bInfo==null)
        throw new RuntimeException("No info for relation: " + currRelation);

      SemType entityType = entityInstance.semType;
      SemType binaryType = bInfo.getSemType();
      SemType type = binaryType.apply(entityType);
      boolean isValid = type.isValid();
      if(isValid) 
        LogInfo.logs("ContextModel: type compatible formula=%s", currRelation);
      else
        LogInfo.logs("ContextModel: type incompatible formula=%s, binrayType=%s", currRelation,binaryType);

      relations[i]=currRelation;
      scores[i++] = isValid ? contextRelations.get(currRelation).value() : 0.0;
    }
    //re-normalize
    NumUtils.normalize(scores);
    //populate
    for(int j = 0; j < relations.length; ++j) {
      if(scores[j]>0.0) {
        Formula jFormula = new JoinFormula(relations[j], entityInstance.formula); //create the full join formula from the context and the entity
        if(opts.verbose>=3)
          //here we integrate all 3 scores - entity score: p(c_x,s_x,e|x), context score: p(c|c_x), binary score: p(b|c,e)
          formulaScores.incr(jFormula, entityProb * contextProb * scores[j]);
      }
    }
  }

  public void log() {
    LogInfo.begin_track("Logging contexts");
    for(Context c: contextToBinaryCounter.keySet()) {
      for(Formula f: contextToBinaryCounter.get(c).keySet()) {
        LogInfo.log("ContextModel: context to formula:\t" + c + "\t"+ f + "\t" + contextToBinaryCounter.get(c).get(f).value());
      }
    }
    LogInfo.end_track();
  }

  /**
   * Generate a training set from the map by treating contexts c1 and c2 as paraphrases
   * if the formulas they are mapped with highest score is equal
   */
  public void generateTrainingSet(String outFile) {

    Map<Formula,List<Context>> formulaToContextMap = new HashMap<>();

    LogInfo.log("Convert map context-->formulas to map formula-->contexts");
    LogInfo.logs("number of contexts: %s",contextToBinaryCounter.size());
    for(Context c: contextToBinaryCounter.keySet()) {
      double topScore = 0.0;
      Set<Formula> bestFormulas = new HashSet<>();
      //collect the best formulas
      for(Formula f: contextToBinaryCounter.get(c).keySet()) {
        double currScore = contextToBinaryCounter.get(c).get(f).value();
        if(currScore-topScore>1e-10) { //if current formula is better than best so far
          bestFormulas.clear();
          bestFormulas.add(f);
          topScore = currScore;
        }
        else if(Math.abs(currScore)-topScore < 1e-10) { // if current formulas is as good as best so far
          bestFormulas.add(f);
        }
      }
      //add the best formulas
      for(Formula bestFormula: bestFormulas) {
        MapUtils.addToList(formulaToContextMap, bestFormula, c);
      }
    }
    LogInfo.logs("Number of formulas: %s",formulaToContextMap.size());

    //generate positive training examples
    generateExamples(formulaToContextMap, outFile);
  }

  private void generateExamples(Map<Formula,List<Context>> formulaToContextMap, String outFile) {
    List<ParaphraseExample> positives = new LinkedList<ParaphraseExample>();
    List<ParaphraseExample> negatives = new LinkedList<ParaphraseExample>();

    double posAvgCosine=0d, negAvgCosine=0d;
    int numOfNeg=0, numOfPos = 0;
    List<Context> seenContexts = new LinkedList<>();
    int numOfFormulas=0;
    for(Formula formula: formulaToContextMap.keySet()) {
      List<Context> contexts = formulaToContextMap.get(formula);
      //generate positives
      for(int i = 0; i < contexts.size()-1; ++i) {
        for(int j = i+1; j < contexts.size(); ++j) {
          String utter1 = contexts.get(i).toUtteranceString();
          String utter2 = contexts.get(j).toUtteranceString();
          double cosine = MathUtils.tokensCosine(Arrays.asList(utter1.split("\\s+")),Arrays.asList(utter2.split("\\s+")));
          posAvgCosine += cosine;
          if(cosine>opts.minCosine)
          positives.add(new ParaphraseExample(utter1,utter2,formula,new BooleanValue(true)));
        }
      }
      //generatve negatives
      for(Context seenContext: seenContexts) {
        for(Context currContext: contexts) {
          String utter1 = seenContext.toUtteranceString();
          String utter2 = currContext.toUtteranceString();
          double cosine = MathUtils.tokensCosine(Arrays.asList(utter1.split("\\s+")),Arrays.asList(utter2.split("\\s+")));
          negAvgCosine+=cosine;
          numOfNeg++;
          if(cosine>opts.minCosine && cosine<1)
            negatives.add(new ParaphraseExample(utter1,utter2,formula,new BooleanValue(false)));
        }
      }
      seenContexts.addAll(contexts);
      if(++numOfFormulas % 10 == 0)
        LogInfo.logs("Number of formulas=%s",numOfFormulas);
    }
    
    //assuming more negatives - Sample same number of negatives as positives
    ListUtils.randomPermute(negatives, new Random(1));
    negatives = negatives.subList(0, positives.size());

    //write with Json
    LogInfo.logs("ContextMolde.generateTrainingSet: avgPosCosine=%s, avgNegCosine=%s",(posAvgCosine/numOfPos),(negAvgCosine/numOfNeg));
    LogInfo.logs("ContextModel.generateTrainingSet: number of positives: %s",positives.size());
    LogInfo.logs("ContextModel.generateTrainingSet: number of negatives: %s",negatives.size());
    
    List<ParaphraseExample> dataset = new LinkedList<>(positives);
    dataset.addAll(negatives);
    ListUtils.randomPermute(dataset, new Random(1));
    LogInfo.logs("ContextModel.generateTrainingSet: number of examples: %s",dataset.size());
    Json.writeValueHard(new File(outFile), dataset);
  }
}
