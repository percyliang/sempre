package edu.stanford.nlp.sempre.interactive.blocks;

import fig.basic.*;
import java.util.*;

import com.beust.jcommander.internal.Lists;
import edu.stanford.nlp.sempre.*;

/**
 * Sida Wang
 */
public class RicherStacksWorldFeatureComputer implements FeatureComputer {
  private static final String PREFIX = "edu\\.stanford\\.nlp\\.sempre\\.cubeworld\\.RicherStacksWorld\\.";
  public static class Options {
    @Option(gloss = "Verbosity")
    public int verbose = 0;
    @Option(gloss = "the N in N-gram")
    public int ngramN = 3;
    @Option(gloss = "the number of wildcards to pad")
    public int numWild = 3;
    @Option(gloss = "decides if we pad ngrams")
    public boolean padNgram = false;
    @Option(gloss = "which categories to parameterize")
    public List<String> parameterizeCats = Lists.newArrayList("$NUM", "$Color");
    @Option(gloss = "prefixing indicating that the token corresponds to current derivation")
    public String anchorString = "!!";
  }
  public static Options opts = new Options();

  @Override public void extractLocal(Example ex, Derivation deriv) {
    addBranchFeatures(ex, deriv);
    addStatsFeatures(ex, deriv);
    addSubtreeFeatures(ex, deriv);
    addPredicateFeatures(ex, deriv);
  }

  private String unverbosify(String s) {
    return s.replaceAll(PREFIX, "").replaceAll("context:", "");
  }

  // only branchs, and not subtrees
  private List<String> getBranchFeatures(Formula f, String prefix, int depth) {
    List<String> feats = new ArrayList<String>();
    getBranchFeaturesRecurse(f, feats, prefix, depth);
    return feats;
  }

  private String lambdaVar(LambdaFormula lambdaFormula) {
    return lambdaFormula.var.substring(0, 1);
  }

  private void getBranchFeaturesRecurse(Formula f, List<String> feats, String prefix, int depth) {
    if (depth==0) { // base case
      feats.add(prefix);
      return;
    }
    String prefixn;
    if (f instanceof CallFormula) {
      CallFormula callFormula = (CallFormula) f;
      // getTreeFeatures(callFormula.func, feats, prefixn, depth-1);
      for (int i = 0; i < callFormula.args.size(); i++) {
        Formula argFormula = callFormula.args.get(i);
        prefixn = String.format("%s.%s-arg%d", prefix, unverbosify(callFormula.func.toString()), i); 
        getBranchFeaturesRecurse(argFormula, feats, prefixn, depth-1);
      }
    } else if (f instanceof JoinFormula) {
      JoinFormula joinFormula = (JoinFormula) f;
      prefixn = String.format("%s-join", prefix); 
      getBranchFeaturesRecurse(joinFormula.relation, feats, prefixn, depth-1);
      getBranchFeaturesRecurse(joinFormula.child, feats, prefixn, depth-1);
    } else if (f instanceof LambdaFormula) {
      LambdaFormula lambdaFormula = (LambdaFormula) f;
      prefixn = String.format("%s-lambda-%s", prefix, lambdaVar(lambdaFormula)); 
      getBranchFeaturesRecurse(lambdaFormula.body, feats, prefixn, depth-1);
    } else if (f instanceof ValueFormula<?>) {
      ValueFormula<?> valueFormula = (ValueFormula<?>) f;
      if (valueFormula.value instanceof NumberValue) {
        NumberValue numberValue = (NumberValue) valueFormula.value;
        feats.add(prefix + "-" + numberValue.unit);
        if (feats.size() == 0) {
          feats.add(prefix + "-" + numberValue.value);
        }
      }
    } else if (f instanceof VariableFormula) {
      VariableFormula variableFormula = (VariableFormula) f;
      feats.add(variableFormula.name.substring(0,1));
    } else throw new RuntimeException("Unhandled formula type: " + f.getClass().toString());
  }
  private String getSubtreeFeature(Formula f, int depth) {
    return getSubtreeFeature(f, depth, false);
  }
  private String getSubtreeFeature(Formula f, int depth, boolean anchored) {
    if (depth == 0) return "*";
    if (f instanceof CallFormula) {
      CallFormula callFormula = (CallFormula) f;
      String func = "(" + unverbosify(callFormula.func.toString());
      for (int i = 0; i < callFormula.args.size(); i++) {
        func += " " + getSubtreeFeature(callFormula.args.get(i), depth - 1, anchored) ;
      }
      return func + ")";
    } else if (f instanceof LambdaFormula) {
      LambdaFormula lambdaFormula = (LambdaFormula) f;
      return "lambda_"+ lambdaVar(lambdaFormula) + "." + getSubtreeFeature(lambdaFormula.body, depth - 1, anchored);
    } else if (f instanceof ValueFormula<?>) {
      ValueFormula<?> valueFormula = (ValueFormula<?>) f;
      if (valueFormula.value instanceof NumberValue) {
        NumberValue numberValue = (NumberValue) valueFormula.value;
        if (anchored)
          return numberValue.unit;
        else
          return numberValue.unit;
      }
      return valueFormula.toString();
    } else if (f instanceof VariableFormula) {
      VariableFormula variableFormula = (VariableFormula) f;
      return variableFormula.name;
    } else throw new RuntimeException("Unhandled formula type: " + f.getClass().toString());
  }

  private String getPredicateFeature(Formula f) {
    if (f instanceof CallFormula) {
      CallFormula callFormula = (CallFormula) f;
      return unverbosify(callFormula.func.toString());
    } else if (f instanceof LambdaFormula) {
      LambdaFormula lambdaFormula = (LambdaFormula) f;
      return "lambda_"+ lambdaVar(lambdaFormula) + "." + getPredicateFeature(lambdaFormula.body);
    } else if (f instanceof ValueFormula<?>) {
      ValueFormula<?> valueFormula = (ValueFormula<?>) f;
      if (valueFormula.value instanceof NumberValue) {
        NumberValue numberValue = (NumberValue) valueFormula.value;
        return numberValue.toString();
      }
      return valueFormula.toString();
    } else if (f instanceof VariableFormula) {
      VariableFormula variableFormula = (VariableFormula) f;
      return variableFormula.name;
    } else throw new RuntimeException("Unhandled formula type: " + f.getClass().toString());
  }
  
  private List<String> parametrizeAnchors(List<String> tokens, Derivation deriv) {
    if (deriv.start == -1) 
      return tokens;
    
    List<String> newTokens = new ArrayList<>();
    newTokens.addAll(tokens.subList(0, deriv.start));
    for (int i = deriv.start; i < deriv.end; i++) {
        newTokens.add(opts.anchorString);
    }
    newTokens.addAll(tokens.subList(deriv.end, tokens.size()));
    return newTokens;
  }
  private List<String> getAllNgrams(List<String> tokens, int n, Derivation deriv) {
    List<String> ngrams = new ArrayList<>();
    List<String> paddedTokens = new ArrayList<>();
    if (deriv.start == -1) // floating, just add everything 
      paddedTokens.addAll(tokens);
    else {
      paddedTokens.addAll(tokens.subList(Math.max(0, deriv.start-n+1), Math.min(tokens.size(), deriv.end+n-1)));
    }

    for (int i=0; i<paddedTokens.size()-n+1; i++) {
      List<String> current = new ArrayList<>(paddedTokens.subList(i, i+n));
      ngrams.add( current.toString() );
    }

    return ngrams;
  }

  private List<String> getAllSkipGrams(List<String> tokens, Derivation deriv) {
    List<String> ngrams = new ArrayList<>();
    List<String> paddedTokens = new ArrayList<>();
    if (tokens.size() < 3)
      return ngrams;

    if (deriv.start == -1) // floating, just add everything 
      paddedTokens.addAll(tokens);
    else
      paddedTokens.addAll(tokens.subList(Math.max(0, deriv.start-2), Math.min(tokens.size(), deriv.end+2)));

    for (int i=0; i<tokens.size()-2; i++) {
      ngrams.add( "[" + tokens.get(i).toString() + ", *, " + tokens.get(i+2) +"]");
    }
    return ngrams;
  }

  private void addBranchFeaturesHelper(Derivation deriv, String ngram, List<String> treeseq, String subname) {
    Map<String, Double> features = deriv.getAllFeatureVector();
    for (String head : treeseq) {
      String featname =  head  + "-" + ngram;
      if (!features.containsKey(FeatureVector.toFeature(subname, featname)))
        deriv.addFeature(subname, featname);
    }
  }

  private void addBranchFeatures(Example ex, Derivation deriv) {
    if (!FeatureExtractor.containsDomain("branch")) return;
    if (deriv.rule != Rule.nullRule) {
      String cat = ""; // deriv.rule.getLhs();
      for (int n=1; n<=opts.ngramN; n++) {
        for (String ngram : getAllNgrams(ex.languageInfo.tokens, n, null)) {
          addBranchFeaturesHelper(deriv, ngram, getBranchFeatures(deriv.formula, cat, 3), "branch");
          addBranchFeaturesHelper(deriv, ngram, getBranchFeatures(deriv.formula, cat, 4), "branch");
        }
      }
      for (String ngram : getAllSkipGrams(ex.languageInfo.tokens, deriv)) {
        addBranchFeaturesHelper(deriv, ngram, getBranchFeatures(deriv.formula, cat, 3), "branch");
        addBranchFeaturesHelper(deriv, ngram, getBranchFeatures(deriv.formula, cat, 4), "branch");
      }
    }
  }

  private void addSubtreeFeatures(Example ex, Derivation deriv) {
    if (!FeatureExtractor.containsDomain("subtree")) return;
    if (deriv.rule != Rule.nullRule) {
      List<String> anchorAbstractTokens = parametrizeAnchors(ex.languageInfo.tokens, deriv);
      // List<String> anchorAbstractTokens = ex.languageInfo.tokens;
      if (deriv.start!=-1) {
        for (int n=1; n<=opts.ngramN; n++) {
          for (String ngram : getAllNgrams(anchorAbstractTokens, n, deriv)) {
            // deriv.addFeature("anchored", getSubtreeFeature(deriv.formula, 3, true) + " :: " + ngram);
            deriv.addFeature("anchored", getSubtreeFeature(deriv.formula, 2, true) + " :: " + ngram);
            // deriv.addFeature("anchored", getSubtreeFeature(deriv.formula, 1, true) + " :: " + ngram);
          }
          for (String ngram : getAllSkipGrams(anchorAbstractTokens, deriv)) {
            deriv.addFeature("anchored", getSubtreeFeature(deriv.formula, 2, true) + " :: " + ngram);
          }
        }
      } else {
        for (int n=1; n<=opts.ngramN; n++) {
          for (String ngram : getAllNgrams(anchorAbstractTokens, n, deriv)) {
            deriv.addFeature("subtree", getSubtreeFeature(deriv.formula, 1) + " :: " + ngram);
            deriv.addFeature("subtree", getSubtreeFeature(deriv.formula, 2) + " :: " + ngram);
            deriv.addFeature("subtree", getSubtreeFeature(deriv.formula, 3) + " :: " + ngram);
          }
        }
        for (String ngram : getAllSkipGrams(anchorAbstractTokens, deriv)) {
          deriv.addFeature("subtree", getSubtreeFeature(deriv.formula, 1) + " :: " + ngram);
          deriv.addFeature("subtree", getSubtreeFeature(deriv.formula, 2) + " :: " + ngram);
          deriv.addFeature("subtree", getSubtreeFeature(deriv.formula, 3) + " :: " + ngram);
        }
      }
    }
  }

  private void addPredicateFeatures(Example ex, Derivation deriv) {
    if (!FeatureExtractor.containsDomain("predicate")) return;
    if (deriv.rule != Rule.nullRule && deriv.rule.isAnchored()) {
      for (int n=1; n<=opts.ngramN; n++) {
        for (String ngram : getAllNgrams(ex.languageInfo.tokens, n, deriv)) {
          deriv.addFeature("predicate", getPredicateFeature(deriv.formula) + " :: " + ngram);
        }
      }
    }
  }

  private void addStatsFeatures(Example ex, Derivation deriv) {
    if (!FeatureExtractor.containsDomain("stats")) return;
    if (deriv.rule != Rule.nullRule) {
      String cat = deriv.rule.getLhs();
      if (deriv.rule.isAnchored())
        deriv.addFeature("stats", "numAnchored");
      // deriv.addFeature("stats", cat);
      deriv.addFeature("stats", "anchored-" + cat + "-" + deriv.rule.isAnchored());
      deriv.addFeature("stats", "depth");
    }
  }
}
