package edu.stanford.nlp.sempre.paraphrase;

import edu.stanford.nlp.sempre.fbalignment.utils.CollectionUtils;
import edu.stanford.nlp.sempre.fbalignment.utils.DoubleContainer;
import edu.stanford.nlp.sempre.fbalignment.utils.MathUtils;
import edu.stanford.nlp.sempre.paraphrase.paralex.ParalexRules;
import fig.basic.LogInfo;
import fig.basic.Option;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Given a context from a question c_x (x is the question) provides a 
 * distribution over contexts p(c|c_x)
 * @author jonathanberant
 *
 */
public abstract class ContextSimilarityModel {

  public static class Options {
    @Option public String bowMode="average_jaccard";
  }
  public static Options opts = new Options();

  public abstract  Map<Context,Double> getContextDist(Set<Context> candidates, Context questionContext);
}

/**
 * All probability mass is on a single context that has highest similarity according to some similarity metric
 * @author jonathanberant
 *
 */
class BowSimilarityModel extends ContextSimilarityModel {

  private static BowSimilarityComputer simComputer = getInstance();

  private static BowSimilarityComputer getInstance() {
    if(opts.bowMode.equals("average_jaccard"))
      return new AverageJaccardSimilarityComputer();
    else if(opts.bowMode.equals("jaccard"))
      return new JaccardSimilarityComputer();
    throw new RuntimeException("bow similarity computer mode illegal" + opts.bowMode);
  }

  public Map<Context,Double> getContextDist(Set<Context> candidates, Context questionContext) {
    if(candidates.size()==0)
      throw new RuntimeException("There must be at least one context candidate");

    Context mostSimilartContext = null;
    double similarity = 0.0;
    for(Context candidate: candidates) {

      double currSimilarity = simComputer.similarity(questionContext,candidate);
      if(mostSimilartContext==null || currSimilarity > similarity) {
        mostSimilartContext = candidate;
        similarity = currSimilarity;
      }
    }
    LogInfo.logs("ContextSimilarityModel.findBestContext: bestContext=%s, similarity=%s",mostSimilartContext,similarity);
    Map<Context,Double> res = new HashMap<>();
    res.put(mostSimilartContext, 1.0);
    return res;
  }
}

/**
 * All probability mass is on a single context that is equal to the target question if one exists
 * @author jonathanberant
 *
 */
class IdentityModel extends ContextSimilarityModel {

  public Map<Context,Double> getContextDist(Set<Context> candidates, Context questionContext) {
    if(candidates.size()==0)
      throw new RuntimeException("There must be at least one context candidate");

    Map<Context,Double> res = new HashMap<>();

    for(Context candidate: candidates) {
      if(candidate.equals(questionContext)) {
        res.put(candidate, 1.0);
        break;
      }
    }
    LogInfo.logs("ContextIdentityModel.getContextDist: context dist=%s",res);
    return res;
  }
}

/**
 * Given a two contexts returns a match if a transformation rule maps one context to the other
 * @author jonathanberant
 *
 */
class WholeContextTransformationModel extends ContextSimilarityModel {

  private ParalexRules rules = new ParalexRules();

  @Override
  public Map<Context, Double> getContextDist(Set<Context> candidates,
      Context questionContext) {
    Map<Context,DoubleContainer> res = new HashMap<Context, DoubleContainer>();

    for(Context candidate: candidates) {
      if(candidate.equals(questionContext)) {
        LogInfo.logs("RuleTransformationModel: Exact match",questionContext,candidate);
        res.clear();
        res.put(candidate, new DoubleContainer(1.0));
        break;
      }
      double matchCount = rules.match(questionContext,candidate).value();
      if(matchCount>0.0) {
        LogInfo.logs("RuleTransformationModel: question=%s, match=%s, score=%s",questionContext,candidate,matchCount);
        res.put(candidate, new DoubleContainer(matchCount));
      }
    }
    MathUtils.normalizeDoubleMap(res);
    return CollectionUtils.doubleContainerToDoubleMap(res);
  }
}

abstract class BowSimilarityComputer {
  public abstract double similarity(Context c1, Context c2);
}

class JaccardSimilarityComputer extends BowSimilarityComputer {
  @Override
  public double similarity(Context c1, Context c2) {
    Set<String> tokens1 = new HashSet<>();
    tokens1.addAll(c1.getLhsTokens());
    tokens1.addAll(c1.getRhsTokens());
    Set<String> tokens2 = new HashSet<>();
    tokens2.addAll(c2.getLhsTokens());
    tokens2.addAll(c2.getRhsTokens());
    return MathUtils.jaccard(tokens1, tokens2);
  }
}

class AverageJaccardSimilarityComputer extends BowSimilarityComputer {
  @Override
  public double similarity(Context c1, Context c2) {
    double leftJaccard = MathUtils.jaccard(c1.getLhsTokens(), c2.getLhsTokens());
    double rightJaccard = MathUtils.jaccard(c1.getRhsTokens(), c2.getRhsTokens());
    return (leftJaccard+rightJaccard) / 2;
  }
}
