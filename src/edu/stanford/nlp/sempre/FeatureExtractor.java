package edu.stanford.nlp.sempre;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import fig.basic.Option;
import fig.basic.StopWatchSet;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * A FeatureExtractor specifies a mapping from derivations to feature vectors.
 * There are two ways to add features to a Derivation:
 *  1) Add FeatureExtractor classes, which are called on each sub-Derivation.
 *  2) Use SemanticFn, which are called whenever they are called.
 *
 * @author Percy Liang
 */
public class FeatureExtractor {
  // Global place to specify features.
  public static class Options {
    @Option(gloss = "Set of feature domains to include")
    public Set<String> featureDomains = Sets.newHashSet();

    @Option(gloss = "Disable denotation features")
    public boolean disableDenotationFeatures = false;

    @Option(gloss = "Use all possible features, regardless of what featureDomains says")
    public boolean useAllFeatures = false;
    @Option(gloss = "Whether to conjoin all lemmas with binaries or each lemma")
    public boolean conjoinAllLemmas = false;
  }

  private Executor executor;
  private DerivOpCountFeatureExtractor opCountExtractor = new DerivOpCountFeatureExtractor();

  public FeatureExtractor(Executor executor) {
    this.executor = executor;
  }

  public static Options opts = new Options();

  public static boolean containsDomain(String domain) {
    if (opts.disableDenotationFeatures && domain.equals("denotation")) return false;
    return opts.useAllFeatures || opts.featureDomains.contains(domain);
  }

  // This function is called on every sub-Derivation, so we should extract only
  // features which depend in some way on |deriv|, not just on its children.
  public void extractLocal(Example ex, Derivation deriv) {
    StopWatchSet.begin("FeatureExtractor.extractLocal");
    extractRuleFeatures(ex, deriv);
    extractDenotationFeatures(ex, deriv);
    extractWhTypeFeatures(ex, deriv);
    opCountExtractor.extractLocal(ex, deriv);
    conjoinLemmaAndBinary(ex, deriv);
    StopWatchSet.end();
  }

  // Add an indicator for each rule that's applied.
  void extractRuleFeatures(Example ex, Derivation deriv) {
    if (!containsDomain("rule")) return;
    if (deriv.rule != Rule.nullRule) {
      deriv.addFeature("rule", "fire");
      deriv.addFeature("rule", deriv.rule.toString());
    }
  }

  // Extract features on the denotation of the logical form produced.
  void extractDenotationFeatures(Example ex, Derivation deriv) {
    if (!containsDomain("denotation")) return;
    if (!deriv.isRoot(ex.numTokens())) return;

    deriv.ensureExecuted(executor);

    if (deriv.value instanceof ErrorValue) {
      deriv.addFeature("denotation", "error");
      return;
    }

    if (!(deriv.value instanceof ListValue))
      throw new RuntimeException("Derivation value is not a list: " + deriv.value);

    ListValue list = (ListValue) deriv.value;

    if (Formulas.isCountFormula(deriv.formula)) {
      // TODO: clean this up
      if (list.values.size() != 1) {
        throw new RuntimeException(
            "Evaluation of count formula " + deriv.formula + " has size " + list.values.size());
      }
      int count = (int)((NumberValue)list.values.get(0)).value;
      deriv.addFeature("denotation", "count-size" + (count == 0 ? "=0" : ">0"));
    } else {
      int size = list.values.size();
      deriv.addFeature("denotation", "size" + (size < 3 ? "=" + size : ">=" + 3));
    }
  }

  // Conjunction of wh and type
  void extractWhTypeFeatures(Example ex, Derivation deriv) {
    if (!containsDomain("whType")) return;
    if (!deriv.isRoot(ex.numTokens())) return;

    if (ex.posTag(0).startsWith("W")) {
      deriv.addFeature("whType",
          "token0=" + ex.token(0) + "," +
              "type=" + FreebaseInfo.getSingleton().coarseType(deriv.type.toString()));
    }
  }

  void conjoinLemmaAndBinary(Example ex, Derivation deriv) {
    if(!containsDomain("lemmaAndBinaries")) return;
    if (!deriv.isRoot(ex.numTokens())) return;

    List<String> nonEntityLemmas = new LinkedList<String>();
    extractNonEntityLemmas(ex,deriv,nonEntityLemmas);  
    List<String> binaries = extractBinaries(deriv.formula);
    if(opts.conjoinAllLemmas) {
      deriv.addFeature("lemmaAndBinaries", "nonEntitylemmas="+Joiner.on('_').join(nonEntityLemmas)+
          ",binaries="+Joiner.on('_').join(binaries));
    }
    else {
      String binariesStr = Joiner.on('_').join(binaries);
      for(String nonEntityLemma: nonEntityLemmas) {
        deriv.addFeature("lemmaAndBinaries", "nonEntitylemmas="+nonEntityLemma+
            ",binaries="+binariesStr);
      }
    }
  }
//TODO clean this
  private void extractNonEntityLemmas(Example ex, Derivation deriv,
      List<String> nonEntityLemmas) {

  
    if(deriv.children.size()==0) {//base case this means it is a word that should be appended
      for(int i = deriv.start; i < deriv.end; i++){
        String pos = ex.languageInfo.posTags.get(i);
        if((pos.startsWith("N") || pos.startsWith("V") || pos.startsWith("W") || pos.startsWith("A") || pos.equals("IN")) 
            && !ex.languageInfo.lemmaTokens.get(i).equals("be"))
          nonEntityLemmas.add(ex.languageInfo.lemmaTokens.get(i));
      }
    }
    else { //recursion
      for(Derivation child: deriv.children) {
        if(child.rule.lhs == null || !child.rule.lhs.equals("$Entity")) {
          extractNonEntityLemmas(ex, child, nonEntityLemmas);
        }
        else if(child.rule.lhs.equals("$Entity")) {
          nonEntityLemmas.add("E");
        }
      }
    }
  }
//TODO clean this
  private List<String> extractBinaries(Formula formula) {
    List<String> res = new LinkedList<String>();
    Set<String> atomicElements = Formulas.extractAtomicFreebaseElements(formula);
    for(String atomicElement: atomicElements) {
      if(atomicElement.split("\\.").length==3 && !atomicElement.equals("fb:type.object.type"))
        res.add(atomicElement);
    }
    return res;
  }
}
