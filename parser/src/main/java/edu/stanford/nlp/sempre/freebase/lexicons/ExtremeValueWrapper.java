package edu.stanford.nlp.sempre.freebase.lexicons;

public abstract class ExtremeValueWrapper {
  public double distance;
  public abstract boolean add(double other);
}

class MinValueWrapper extends ExtremeValueWrapper {

  public MinValueWrapper(double max) {
    distance = max;
  }
  @Override
  public boolean add(double other) {
    if (other < distance) {
      distance = other;
      return true;
    }
    return false;
  }
}

class MaxValueWrapper extends ExtremeValueWrapper {

  public MaxValueWrapper(double min) {
    distance = min;
  }
  @Override
  public boolean add(double other) {
    if (other > distance) {
      distance = other;
      return true;
    }
    return false;
  }
}


