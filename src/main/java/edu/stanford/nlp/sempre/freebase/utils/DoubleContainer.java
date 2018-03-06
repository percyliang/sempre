package edu.stanford.nlp.sempre.freebase.utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import fig.basic.Fmt;

public class DoubleContainer implements Comparable<DoubleContainer> {

  @JsonProperty private double value;

  @JsonCreator
  public DoubleContainer(@JsonProperty("count") double count) { this.value = count; }

  public void inc() { value++; }

  public void dec() { value--; }

  public void inc(double n) { value += n; }

  public void dec(double n) { value -= n; }

  public void set(double n) { value = n; }

  public double value() { return value; }

  public String toString() { return "" + Fmt.D(value); }

  @Override
  public int compareTo(DoubleContainer o) {
    if (value < o.value) return -1;
    if (value > o.value) return 1;
    return 0;
  }
}
