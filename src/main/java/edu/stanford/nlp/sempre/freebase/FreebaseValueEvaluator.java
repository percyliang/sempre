package edu.stanford.nlp.sempre.freebase;

import fig.basic.*;
import java.util.List;
import edu.stanford.nlp.sempre.*;

/**
 * Used to evaluate Freebase question answering.
 * Denotation is a list of entities.
 * Nothing in here is specific to Freebase, but this is not really meant to be
 * a general-purpose class.
 *
 * @author Percy Liang
 */
public class FreebaseValueEvaluator implements ValueEvaluator {
  public static class Options {
    @Option(gloss = "When evaluating lists, compute F1 rather than exact match") public boolean useF1 = true;
  }
  public static final Options opts = new Options();

  public double getCompatibility(Value target, Value pred) {
    double f1 = getF1(target, pred);
    return opts.useF1 ? f1 : (f1 == 1 ? 1 : 0);
  }

  // Compute F1 score between two lists (partial match).
  // this is target, that is predicted.
  private double getF1(Value target, Value pred) {
    List<Value> targetList = ((ListValue) target).values;
    if (!(pred instanceof ListValue)) return 0;
    List<Value> predList = ((ListValue) pred).values;

    if (targetList.size() == 0 && predList.size() == 0)
      return 1;
    if (targetList.size() == 0 || predList.size() == 0)
      return 0;

    double precision = 0;
    for (Value v2 : predList) {  // For every predicted value...
      double score = 0;
      for (Value v1 : targetList)
        score = Math.max(score, getItemCompatibility(v1, v2));
      precision += score;
    }
    precision /= predList.size();
    assert precision >= 0 && precision <= 1 : precision;

    double recall = 0;
    for (Value v1 : targetList) {  // For every true value...
      double score = 0;
      for (Value v2 : predList)
        score = Math.max(score, getItemCompatibility(v1, v2));
      recall += score;
    }
    recall /= targetList.size();
    assert recall >= 0 && recall <= 1 : recall;

    if (precision + recall == 0) return 0;

    double f1 = 2 * precision * recall / (precision + recall);
    assert f1 >= 0 && f1 <= 1 : f1;

    return f1;
  }

  // Compare one element of the list.
  public double getItemCompatibility(Value target, Value pred) {
    if (target instanceof DescriptionValue) {
      // Just has to match the description
      if (pred instanceof NameValue)
        return ((DescriptionValue) target).value.equals(((NameValue) pred).description) ? 1 : 0;
      return 0;
    }

    if (pred instanceof ErrorValue) return 0;  // Never award points for error
    if (pred == null) {
      LogInfo.warning("Predicted value is null!");
      return 0;
    }
    if (target.getClass() != pred.getClass()) return 0;

    if (target instanceof DateValue) {
      DateValue targetDate = (DateValue) target;
      DateValue predDate = (DateValue) pred;
      // Only comparing the year right now!  This is crude.
      boolean match = (targetDate.year == predDate.year);
      return match ? 1 : 0;
    }

    return target.equals(pred) ? 1 : 0;
  }
}
