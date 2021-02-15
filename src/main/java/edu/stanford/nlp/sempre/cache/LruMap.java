package edu.stanford.nlp.sempre.cache;

import fig.basic.MemUsage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TODO(rf): Fig.MemUsage is buggy wrt computing LinkedHashMap byte
 * usage, so we do our own bookkeeping here, but we are doing so very
 * incompletely (i.e. only on put() and remove()).
 *
 * @author Roy Frostig
 */
public class LruMap<K, V> extends LinkedHashMap<K, V> {
  private final int cap;
  private final LruCallback<K, V> callback;
  private int bytes = 0;

  public LruMap(int capacity) {
    this(capacity, null);
  }

  public LruMap(int capacity, LruCallback<K, V> evictCallback) {
    // "As a general rule, the default load factor (.75) offers a good
    // tradeoff between time and space costs."
    // -- Java 8 API,
    //    http://docs.oracle.com/javase/8/docs/api/java/util/HashMap.html
    super(capacity, 0.75f, true); // Flag true for access-order.
    this.cap = capacity;
    this.callback = evictCallback;
  }

  public int getCapacity() {
    return cap;
  }

  public int getBytes() {
    return bytes;
  }

  @Override
  public V put(K key, V value) {
    boolean replacing = containsKey(key);
    V old = super.get(key);
    bytes += MemUsage.getBytes(value);
    if (replacing) {
      bytes -= MemUsage.getBytes(old);
    } else {
      bytes += MemUsage.getBytes(key);
    }
    return super.put(key, value);
  }

  @Override
  public V remove(Object key) {
    boolean decr = containsKey(key);
    V old = super.remove(key);
    if (decr) {
      bytes -= MemUsage.getBytes(key);
      bytes -= MemUsage.getBytes(old);
    }
    return old;
  }

  /**
   * Ignore the argument, iterate in access order and remove until
   * memory constraints are satisfied. Always return false, since
   * we did our own removal.
   */
  @Override
  protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
    while (getBytes() > cap && !isEmpty()) {
      Map.Entry<K, V> toRemove = null;
      for (Map.Entry<K, V> entry : entrySet()) {
        toRemove = entry;
        break;
      }
      remove(toRemove.getKey());
      if (callback != null)
        callback.onEvict(toRemove);
    }
    return false;
  }
}

