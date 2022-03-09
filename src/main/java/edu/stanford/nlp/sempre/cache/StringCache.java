package edu.stanford.nlp.sempre.cache;

/**
 * Stores a Map<String, String>, which is synchronized with disk.
 *
 * @author Percy Liang
 */
public interface StringCache {
  String get(String key);
  void put(String key, String value);
}

