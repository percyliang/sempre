package edu.stanford.nlp.sempre.freebase.utils;

import java.util.HashMap;
import java.util.Map;

public final class CollectionUtils {
  private CollectionUtils() { }

  public static <K, V> Map<K, V> arraysToMap(K[] keys, V[] values) {
    if (keys.length != values.length)
      throw new RuntimeException("Lenght of keys: " + keys.length + ", length of values: " + values.length);
    Map<K, V> res = new HashMap<>();
    for (int i =  0; i < keys.length; ++i) {
      res.put(keys[i], values[i]);
    }
    return res;
  }

  public static <K> Map<K, Double> doubleContainerToDoubleMap(Map<K, DoubleContainer> map) {
    Map<K, Double> res = new HashMap<>();
    for (K key : map.keySet())
      res.put(key, map.get(key).value());
    return res;
  }
}
