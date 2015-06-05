package edu.stanford.nlp.sempre.tables.alignment;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import fig.basic.Pair;

class DoubleMap {
  private Map<Pair<String, String>, Double> m;

  public DoubleMap() {
    m = new HashMap<>();
  }

  public double get(Pair<String, String> k) {
    Double v = m.get(k);
    return v == null ? 0.0 : v;
  }
  public double get(String k1, String k2) { return get(new Pair<>(k1, k2)); }

  public void put(Pair<String, String> k, double v) {
    m.put(k, v);
  }
  public void put(String k1, String k2, double v) { put(new Pair<>(k1, k2), v); }

  public void incr(Pair<String, String> k, double v) {
    Double oldV = m.get(k);
    m.put(k, (oldV == null ? 0.0 : oldV) + v);
  }
  public void incr(String k1, String k2, double v) { incr(new Pair<>(k1, k2), v); }

  public void reverseKeys() {
    Map<Pair<String, String>, Double> newM = new HashMap<>();
    for (Map.Entry<Pair<String, String>, Double> entry : m.entrySet()) {
      newM.put(entry.getKey().reverse(), entry.getValue());
    }
    m = newM;
  }

  public DoubleMap getReverseKeys() {
    DoubleMap newM = new DoubleMap();
    for (Map.Entry<Pair<String, String>, Double> entry : m.entrySet()) {
      newM.put(entry.getKey().reverse(), entry.getValue());
    }
    return newM;
  }

  public Set<Map.Entry<Pair<String, String>, Double>> entrySet() {
    return m.entrySet();
  }

  public static DoubleMap product(DoubleMap dm1, DoubleMap dm2) {
    DoubleMap dmp = new DoubleMap();
    for (Map.Entry<Pair<String, String>, Double> entry : dm1.entrySet()) {
      double v1 = entry.getValue(), v2 = dm2.get(entry.getKey());
      if (v1 * v2 > 0) dmp.put(entry.getKey(), v1 * v2);
    }
    return dmp;
  }

  // A DoubleMap that returns the same value always.
  static class ConstantDoubleMap extends DoubleMap {
    private final double value;

    public ConstantDoubleMap(double value) {
      this.value = value;
    }

    public double get(Pair<String, String> k) { return value; }
    public double get(String k1, String k2) { return value; }
    public void put(Pair<String, String> k, double v) { throw new RuntimeException("cannot put"); }
    public void put(String k1, String k2, double v) { throw new RuntimeException("cannot put"); }
    public void incr(Pair<String, String> k, double v) { throw new RuntimeException("cannot incr"); }
    public void incr(String k1, String k2, double v) { throw new RuntimeException("cannot incr"); }
    public void reverseKeys() { }
    public DoubleMap getReverseKeys() { return this; }
    public Set<Map.Entry<Pair<String, String>, Double>> entrySet() { throw new RuntimeException("cannot entrySet"); }
  }
}
