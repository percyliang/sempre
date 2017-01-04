package edu.stanford.nlp.sempre.interactive.actions;

import fig.basic.*;
import java.util.*;

import com.beust.jcommander.internal.Lists;
import edu.stanford.nlp.sempre.*;

/**
 * Sida Wang
 * Feature computer for the BeamFloatingParser
 * TODOs:
 * - control what categories to abstract out
 * - efficiency improvement, right now use all members of the cross product
 */
public class BeamFloatingFeatureComputer implements FeatureComputer {
  public static class Options {
    @Option(gloss = "Verbosity")
    public int verbose = 0;

    @Option(gloss = "the N in N-gram")
    public int ngramN = 3;

    @Option(gloss = "which categories to parameterize")
    public List<String> parameterizeCats = Lists.newArrayList("$Number", "$Dates");

    @Option(gloss = "prefixing indicating that the token corresponds to current derivation")
    public String anchorString = "!!";
  }
  public static Options opts = new Options();

  @Override public void extractLocal(Example ex, Derivation deriv) {
    addStatsFeatures(ex, deriv);
    addSubtreeFeatures(ex, deriv);
  }

  private String unverbosify(String s) {
    return s;
  }

  private String lambdaVar(LambdaFormula lambdaFormula) {
    return lambdaFormula.var.substring(0, 1);
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
        func += " " + getSubtreeFeature(callFormula.args.get(i), depth - 1, anchored);
      }
      return func + ")";
    } else if (f instanceof ActionFormula) {
      ActionFormula actionFormula = (ActionFormula) f;
      String func = "(" + actionFormula.mode;
      for (int i = 0; i < actionFormula.args.size(); i++) {
        func += " " + getSubtreeFeature(actionFormula.args.get(i), depth - 1, anchored);
      }
      return func + ")";
    } else if (f instanceof JoinFormula) {
      JoinFormula joinFormula = (JoinFormula) f;
      return "(" + getSubtreeFeature(joinFormula.relation, depth-1, anchored)+ "," +  getSubtreeFeature(joinFormula.child, depth-1, anchored)+")";
    } else if (f instanceof LambdaFormula) {
      LambdaFormula lambdaFormula = (LambdaFormula) f;
      return "lambda "+ lambdaVar(lambdaFormula) + "." + getSubtreeFeature(lambdaFormula.body, depth - 1, anchored);
    } else if (f instanceof ValueFormula<?>) {
      ValueFormula<?> valueFormula = (ValueFormula<?>) f;
      if (valueFormula.value instanceof NumberValue) {
        NumberValue numberValue = (NumberValue) valueFormula.value;
        return numberValue.unit;
      }
      return valueFormula.toString();
    } else if (f instanceof VariableFormula) {
      VariableFormula variableFormula = (VariableFormula) f;
      return variableFormula.name;
    } else if (f instanceof ReverseFormula) {
      ReverseFormula reverseFormula = (ReverseFormula) f;
      return getSubtreeFeature(reverseFormula.child, depth, anchored);
    } else if (f instanceof MergeFormula) {
      MergeFormula mergeFormula = (MergeFormula) f;
      return "(" + mergeFormula.mode + " " + getSubtreeFeature(mergeFormula.child1, depth-1, anchored) + "," + getSubtreeFeature(mergeFormula.child2, depth-1, anchored) + ")";
    } else if (f instanceof NotFormula) {
      NotFormula notFormula = (NotFormula) f;
      return "(not " + getSubtreeFeature(notFormula.child, depth-1, anchored) + ")";
    } else {
      // throw new RuntimeException("Unhandled formula type: " + f.getClass().toString());
      LogInfo.warning("Unhandled formula type: " + f.getClass().toString());
      return f.toString();
    }
  }

  // function to abstract out ALL anchored stuff in the utterance.
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
  
  private void processUtterance(Derivation deriv, List<String> tokens) {
    if (deriv == null || deriv.start == -1) 
      return;
    if (deriv.rule.isAnchored()) {
      for (int i = deriv.start; i<deriv.end; i++)
        tokens.set(i, opts.anchorString);
    }
    for (Derivation child : deriv.children)
      processUtterance(child, tokens);
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

  private List<String> breakUtterance(List<String> currentTokens, Derivation deriv) {
    
  
    // List<String> span = currentTokens.subList(deriv.start, deriv.end);
    List<String> span = currentTokens;
    
    List<String> retval = new ArrayList<String>();
    String current = "";
    for (String tok : span) {
      if (tok.equals(opts.anchorString) && current.length()>0) {
        retval.add(current);
        current = "";
      } else {
        current += " " + tok;
      }
    }
    
    if (current.length()>0) {
      retval.add(current);
    }
    return retval;
  }
  private void addSubtreeFeatures(Example ex, Derivation deriv) {
    if (!FeatureExtractor.containsDomain("bff")) return;
    if (deriv.rule != Rule.nullRule) {
      if (!deriv.allAnchored()) {
        List<String> anchorAbstractTokens = Lists.newArrayList(ex.languageInfo.tokens);
        processUtterance(deriv, anchorAbstractTokens);
        //List<String> anchorAbstractTokens = ex.languageInfo.tokens;
        
        for (String part : breakUtterance(anchorAbstractTokens, deriv)) {
          deriv.addFeature("bff", getSubtreeFeature(deriv.formula, 2) + " :: " + part);
          deriv.addFeature("bff", getSubtreeFeature(deriv.formula, 3) + " :: " + part);
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
