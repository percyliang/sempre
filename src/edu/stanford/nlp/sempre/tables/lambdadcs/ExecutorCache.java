package edu.stanford.nlp.sempre.tables.lambdadcs;

import java.util.*;

import fig.basic.*;

/**
 * Cache the executed values of LambdaDCSExecutor.
 *
 * @author ppasupat
 */
public final class ExecutorCache {
  public static class Options {
    @Option(gloss = "maximum number of entries to retain")
    public int maxCacheSize = 1;
  }
  public static Options opts = new Options();

  public static final ExecutorCache singleton = new ExecutorCache();

  private final Map<Object, Map<Object, Object>> cache;
  private final Map<Object, Long> lastUsed;
  private long timestamp = 0;

  private ExecutorCache() {
    cache = new HashMap<>();
    lastUsed = new HashMap<>();
  }

  public synchronized Object get(Object metakey, Object key) {
    Map<Object, Object> cacheForMetakey = cache.get(metakey);
    if (cacheForMetakey == null) return null;
    lastUsed.put(metakey, timestamp++);
    return cacheForMetakey.get(key);
  }

  public synchronized void put(Object metakey, Object key, Object value) {
    Map<Object, Object> cacheForMetakey = cache.get(metakey);
    if (cacheForMetakey == null) {
      while (cache.size() >= opts.maxCacheSize) evict();
      cache.put(metakey, cacheForMetakey = new HashMap<>());
    }
    cacheForMetakey.put(key, value);
  }

  private void evict() {
    long minLastUsed = Long.MAX_VALUE;
    Object minLastUsedMetakey = null;
    for (Object existingMetakey : cache.keySet()) {
      Long existingLastUsed = lastUsed.get(existingMetakey);
      if (existingLastUsed == null) existingLastUsed = 0L;
      if (existingLastUsed < minLastUsed) {
        minLastUsed = existingLastUsed;
        minLastUsedMetakey = existingMetakey;
      }
    }
    cache.remove(minLastUsedMetakey);
  }

}
