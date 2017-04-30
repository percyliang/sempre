package edu.stanford.nlp.sempre.tables.baseline;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.*;
import edu.stanford.nlp.sempre.tables.features.*;
import fig.basic.*;

/**
 * Compute features for BaselineParser
 *
 * @author ppasupat
 */
public class TableBaselineFeatureComputer implements FeatureComputer {
  public static class Options {
    @Option(gloss = "Verbosity") public int verbosity = 0;
  }
  public static Options opts = new Options();

  @Override
  public void extractLocal(Example ex, Derivation deriv) {
    if (!deriv.isRoot(ex.numTokens())) return;
    if (!FeatureExtractor.containsDomain("table-baseline")) return;
    List<PhraseInfo> phraseInfos = PhraseInfo.getPhraseInfos(ex);
    // Find the list of all entities mentioned in the question
    Set<String> mentionedEntities = new HashSet<>(), mentionedProperties = new HashSet<>();
    for (PhraseInfo phraseInfo : phraseInfos) {
      for (String s : phraseInfo.fuzzyMatchedPredicates) {
        // s is either an ENTITY or a BINARY
        SemType entityType = TableTypeSystem.getEntityTypeFromId(s);
        SemType propertyType = TableTypeSystem.getPropertyTypeFromId(s);
        if (entityType != null) mentionedEntities.add(s);
        if (propertyType != null) mentionedProperties.add(s);
      }
    }
    // Find the base cell(s)
    TableKnowledgeGraph graph = (TableKnowledgeGraph) ex.context.graph;
    List<Value> values = ((ListValue) deriv.value).values;
    if (opts.verbosity >= 2) LogInfo.logs("%s", values);
    if (values.get(0) instanceof NumberValue) {
      values = graph.joinSecond(TableTypeSystem.CELL_NUMBER_VALUE, values);
    } else if (values.get(0) instanceof DateValue) {
      values = graph.joinSecond(TableTypeSystem.CELL_DATE_VALUE, values);
    } else {
      values = new ArrayList<>(values);
    }
    if (opts.verbosity >= 2) LogInfo.logs("%s", values);
    List<String> predictedEntities = new ArrayList<>();
    for (Value value : values) {
      predictedEntities.add(((NameValue) value).id);
    }
    // Define features
    for (String predicted : predictedEntities) {
      String pProp = TableTypeSystem.getPropertyOfEntity(predicted);
      List<Integer> pRows = graph.getRowsOfCellId(predicted);
      if (opts.verbosity >= 2) LogInfo.logs("[p] %s %s %s", predicted, pProp, pRows);
      for (String mentioned : mentionedEntities) {
        String mProp = TableTypeSystem.getPropertyOfEntity(mentioned);
        List<Integer> mRows = graph.getRowsOfCellId(mentioned);
        if (opts.verbosity >= 2) LogInfo.logs("[m] %s %s %s", mentioned, mProp, mRows);
        // Same column as ENTITY + offset
        if (pProp != null && mProp != null && pProp.equals(mProp)) {
          defineAllFeatures(deriv, "same-column", phraseInfos);
          if (pRows != null && pRows.size() == 1 && mRows != null && mRows.size() == 1) {
            defineAllFeatures(deriv, "same-column;offset=" + (pRows.get(0) - mRows.get(0)), phraseInfos);
          }
        }
        // Same row as ENTITY
        if (mRows != null && pRows != null) {
          for (int pRow : pRows) {
            if (mRows.contains(pRow)) {
              defineAllFeatures(deriv, "same-row", phraseInfos);
              break;
            }
          }
        }
      }
      for (String mentioned : mentionedProperties) {
        // match column name BINARY
        if (opts.verbosity >= 2) LogInfo.logs("%s %s", pProp, mentioned);
        if (mentioned.equals(pProp)) {
          defineAllFeatures(deriv, "match-column-binary", phraseInfos);
        }
      }
      // Row index (first or last)
      if (pRows != null && pRows.contains(0))
        defineAllFeatures(deriv, "first-row", phraseInfos);
      if (pRows != null && pRows.contains(graph.numRows() - 1))
        defineAllFeatures(deriv, "last-row", phraseInfos);
    }

  }

  private void defineAllFeatures(Derivation deriv, String name, List<PhraseInfo> phraseInfos) {
    defineUnlexicalizedFeatures(deriv, name);
    defineLexicalizedFeatures(deriv, name, phraseInfos);
  }

  private void defineUnlexicalizedFeatures(Derivation deriv, String name) {
    deriv.addFeature("table-baseline", name);
  }

  private void defineLexicalizedFeatures(Derivation deriv, String name, List<PhraseInfo> phraseInfos) {
    for (PhraseInfo phraseInfo : phraseInfos) {
      deriv.addFeature("table-baseline", "phrase=" + phraseInfo.lemmaText + ";" + name);
    }
  }

}

