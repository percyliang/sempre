package edu.stanford.nlp.sempre.paraphrase;

import edu.stanford.nlp.sempre.FeatureVector;
import edu.stanford.nlp.sempre.Params;
import fig.basic.LispTree;

public class FeatureSimilarity {

  public final double score;
  public final FeatureVector featureVector;
  public final String source;
  public final String target;

  public FeatureSimilarity(FeatureVector fv, String source, String target, Params params) {
    this.featureVector=fv;
    this.source=source;
    this.target=target;
    score = fv.dotProduct(params);
  }

  public FeatureSimilarity(FeatureVector fv, String source, String target, double score) {
    this.featureVector=fv;
    this.source=source;
    this.target=target;
    this.score=score;
  }

  public FeatureSimilarity copy() {
    FeatureVector newFv = new FeatureVector();
    newFv.add(featureVector);
    return new FeatureSimilarity(newFv, source, target, score);
  }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("similarity");
    tree.addChild(LispTree.proto.newList("source", source));
    tree.addChild(LispTree.proto.newList("target", target));
    tree.addChild(LispTree.proto.newList("sim_score", ""+score));
    return tree;
  }

  public void clear() {
    featureVector.clear();
  }
}
