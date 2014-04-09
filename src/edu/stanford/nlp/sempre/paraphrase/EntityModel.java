package edu.stanford.nlp.sempre.paraphrase;

import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.Example;
import edu.stanford.nlp.sempre.Params;
import edu.stanford.nlp.sempre.Parser;
import fig.basic.*;

/**
 * Provides a distribution over entity instances p(e,c,s|x)
 * @author jonathanberant
 *
 */
public abstract class EntityModel {
  
  public static class Options {
    @Option public int verbose = 3;
  }
  public static Options opts = new Options();
  
  final Parser parser;
  Params params;
  final ContextModel contextMapper;

  public EntityModel(Parser parser, ContextModel contextMapper) {
    this.parser = parser;
    this.contextMapper = contextMapper;
  }
  
  public abstract TDoubleMap<EntityInstance> calcEntityInstanceDist(Example ex);
}

class HeuristicEntityModel extends EntityModel {

  public HeuristicEntityModel(Parser parser,ContextModel contextMapper) {
    super(parser,contextMapper);
    params = new Params(); //no params in this model
  }

  @Override
  public TDoubleMap<EntityInstance> calcEntityInstanceDist(Example ex) {
    
    //First parse
    StopWatchSet.begin("Parser.parse");
    parser.parse(params, ex, false); 
    StopWatchSet.end();
    //now compute distribution
    if(ex.getPredDerivations().size()==0)
      return new TDoubleMap<EntityInstance>();

    LogInfo.begin_track("Generating entity distribtuion");
    Pair<Context,Interval> bestPair = null;
    Derivation bestDeriv = null;

    for(Derivation currDeriv: ex.getPredDerivations()) {
      Pair<Context,Interval> currPair = ParaphraseUtils.extractContextIntervalPair(currDeriv, ex);
      if(bestPair==null || firstBetter(currPair,currDeriv,bestPair,bestDeriv,ex)) {
        if(opts.verbose>=3 && bestDeriv != null) 
          LogInfo.logs("ParaphraseLearner: replacing context %s with formula %s for context %s with formula %s",bestPair,bestDeriv.formula,currPair,currDeriv.formula);
        bestPair = currPair;
        bestDeriv = currDeriv;
      }
    }

    TDoubleMap<EntityInstance> res = new TDoubleMap<EntityInstance>();
    Context bestContext = bestPair.getFirst();
    Interval bestInterval = bestPair.getSecond();
    EntityInstance entityInstance = new EntityInstance(bestContext,
        ex.languageInfo.lemmaPhrase(bestInterval.start, bestInterval.end),
        bestInterval, bestDeriv.formula, bestDeriv.getAllFeatureVector(),bestDeriv.type);

    if(opts.verbose>=3)
      LogInfo.logs("ParaphraseLearner: generated entity instance=%s",entityInstance);
    res.put(entityInstance, 1.0);
    LogInfo.end_track();
    return res;
    
    
  }
  
  private boolean firstBetter(Pair<Context, Interval> currPair,
      Derivation currDeriv, Pair<Context, Interval> bestPair,
      Derivation bestDeriv, Example ex) {

    if(contextMapper.containsKey(currPair.getFirst()) && 
        !contextMapper.containsKey(bestPair.getFirst()))
      return true;
    if(currPair.getSecond().properSuperset(bestPair.getSecond()))
      return true;
    if(currDeriv.getAllFeatureVector("basicStats :: entity.popularity") >
    bestDeriv.getAllFeatureVector("basicStats :: entity.popularity"))
      return true;
    if(currDeriv.getAllFeatureVector("basicStats :: entity.popularity") <
    bestDeriv.getAllFeatureVector("basicStats :: entity.popularity"))
      return false;
    if(currDeriv.getAllFeatureVector("tokensDistance :: entity.equal") >
    bestDeriv.getAllFeatureVector("tokensDistance :: entity.equal"))
      return true;
    return false;
  }
  
  
  
}
