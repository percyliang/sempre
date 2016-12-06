package edu.stanford.nlp.sempre.geo880;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.StringNormalizationUtils;
import fig.basic.LogInfo;

import java.util.List;

/**
 * This is only used because the data does not mention when a city is in the usa, but
 * the kg returns usa, and we want to use exact match, so we add this logic here.
 * Created by joberant on 03/12/2016.
 */
public class Geo880ValueEvaluator implements ValueEvaluator {

  public double getCompatibility(Value target, Value pred) {
    List<Value> targetList = ((ListValue) target).values;
    if (!(pred instanceof ListValue)) return 0;
    List<Value> predList = ((ListValue) pred).values;

    // In geo880, if we return that something is contained in a state, there is no need to return fb:country.usa
    Value toDelete = null;
    if (predList.size() > 1 && predList.get(0) instanceof NameValue) {
      for (Value v: predList) {
        String id = ((NameValue) v).id;
        if (id.equals("fb:country.usa")) {
          toDelete = v;
          break;
        }
      }
    }
    if (toDelete != null) {
      predList.remove(toDelete);
    }

    if (targetList.size() != predList.size()) return 0;

    for (Value targetValue : targetList) {
      boolean found = false;
      for (Value predValue : predList) {
        if (getItemCompatibility(targetValue, predValue)) {
          found = true;
          break;
        }
      }
      if (!found) return 0;
    }
    return 1;
  }

  // ============================================================
  // Item Compatibility
  // ============================================================

  // Compare one element of the list.
  protected boolean getItemCompatibility(Value target, Value pred) {
    if (pred instanceof ErrorValue) return false;  // Never award points for error
    if (pred == null) {
      LogInfo.warning("Predicted value is null!");
      return false;
    }

    if (target instanceof DescriptionValue) {
      String targetText = ((DescriptionValue) target).value;
      if (pred instanceof NameValue) {
        // Just has to match the description
        String predText = ((NameValue) pred).description;
        if (predText == null) predText = "";
        return targetText.equals(predText);
      }
    } else if (target instanceof NumberValue) {
      NumberValue targetNumber = (NumberValue) target;
      if (pred instanceof NumberValue) {
        return compareNumberValues(targetNumber, (NumberValue) pred);
      }
    }

    return target.equals(pred);
  }

  protected boolean compareNumberValues(NumberValue target, NumberValue pred) {
    return Math.abs(target.value - pred.value) < 1e-6;
  }

}
