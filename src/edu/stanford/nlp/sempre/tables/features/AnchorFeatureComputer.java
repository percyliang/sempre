package edu.stanford.nlp.sempre.tables.features;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.StringNormalizationUtils;
import edu.stanford.nlp.sempre.tables.TableKnowledgeGraph;
import edu.stanford.nlp.sempre.tables.TableCell;
import edu.stanford.nlp.sempre.tables.TableColumn;
import fig.basic.LogInfo;

public class AnchorFeatureComputer implements FeatureComputer {

  @Override
  public void extractLocal(Example ex, Derivation deriv) {
    if (!(FeatureExtractor.containsDomain("anchored-entity"))) return;
    if (!(deriv.rule.sem instanceof FuzzyMatchFn)) return;
    FuzzyMatchFn sem = (FuzzyMatchFn) deriv.rule.sem;
    if (sem.getMatchAny() || sem.getMode() != FuzzyMatchFn.FuzzyMatchFnMode.ENTITY) return;
    String phrase = ((StringValue) ((ValueFormula<?>) deriv.child(0).formula).value).value;
    NameValue predicate = (NameValue) ((ValueFormula<?>) deriv.formula).value;
    TableKnowledgeGraph graph = (TableKnowledgeGraph) ex.context.graph;
    extractMatchingFeatures(graph, deriv, phrase, predicate);
  }

  private void extractMatchingFeatures(TableKnowledgeGraph graph,
      Derivation deriv, String phrase, NameValue predicate) {
    String predicateString = graph.getOriginalString(predicate);
    //LogInfo.logs("%s -> %s = %s", phrase, predicate, predicateString);
    predicateString = StringNormalizationUtils.simpleNormalize(predicateString).toLowerCase();
    if (predicateString.equals(phrase)) {
      deriv.addFeature("a-e", "exact");
      //LogInfo.logs("%s %s exact", phrase, predicateString);
    } else if (predicateString.startsWith(phrase + " ")) {
      deriv.addFeature("a-e", "prefix");
      //LogInfo.logs("%s %s prefix", phrase, predicateString);
    } else if (predicateString.endsWith(" " + phrase)) {
      deriv.addFeature("a-e", "suffix");
      //LogInfo.logs("%s %s suffix", phrase, predicateString);
    } else if (predicateString.contains(" " + phrase + " ")){
      deriv.addFeature("a-e", "substring");
      //LogInfo.logs("%s %s substring", phrase, predicateString);
    } else {
      deriv.addFeature("a-e", "other");
      //LogInfo.logs("%s %s other", phrase, predicateString);
    }
    // Does the phrase match other cells?
    Set<String> matches = new HashSet<>();
    for (TableColumn column : graph.columns) {
      for (TableCell cell : column.children) {
        String s = StringNormalizationUtils.simpleNormalize(cell.properties.originalString).toLowerCase();
        if (s.contains(phrase) && !cell.properties.id.equals(predicate.id)) {
          matches.add(s);
        }
      }
    }
    //LogInfo.logs(">> %s", matches);
    if (matches.size() == 0) {
      deriv.addFeature("a-e", "unique");
    } else if (matches.size() < 3) {
      deriv.addFeature("a-e", "multiple;" + matches.size());
    } else {
      deriv.addFeature("a-e", "multiple;>=3");
    }
  }

}
