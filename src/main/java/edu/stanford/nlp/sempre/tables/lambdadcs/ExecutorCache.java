package edu.stanford.nlp.sempre.tables.lambdadcs;

import java.util.*;

import fig.basic.*;

/**
 * Cache the executed values of an executor.
 *
 * The cache should be cleared once the parser finishes each example.
 * The metakey Object specifies the current example.
 * When the metakey is changed, the cache is cleared.
 *
 * @author ppasupat
 */
public final class ExecutorCache {
  public static class Options {
    @Option(gloss = "maximum number of values to retain")
    public int maxCacheSize = 1000000;
    @Option(gloss = "minimum number of values evicted before garbage collection")
    public int cacheGCThreshold = 10000000;
    @Option public int verbose = 0;
  }
  public static Options opts = new Options();

  // Default cache
  public static final ExecutorCache singleton = new ExecutorCache();

  private Object currentMetakey = null;
  private final Map<Object, Object> cache;
  private int accumSize = 0;

  public ExecutorCache() {
    cache = new HashMap<>();
  }

  public synchronized Object get(Object metakey, Object key) {
    if (currentMetakey != metakey) {
      clearCache(metakey);
    }
    Object value = cache.get(key);
    if (opts.verbose >= 1)
      LogInfo.logs("[GET =>] %s => %s", key, value);
    return value;
  }

  public synchronized void put(Object metakey, Object key, Object value) {
    if (currentMetakey != metakey) {
      clearCache(metakey);
    }
    if (opts.verbose >= 1)
      LogInfo.logs("[<= PUT] %s <= %s", key, value);
    if (cache.size() < opts.maxCacheSize)
      cache.put(key, value);
  }

  public void clearCache(Object metakey) {
    accumSize += cache.size();
    cache.clear();
    // Garbage collect
    if (accumSize >= opts.cacheGCThreshold) {
      System.gc();
      accumSize = 0;
    }
    if (opts.verbose >= 1)
      LogInfo.logs("[clearCache] metakey = %s", metakey);
    currentMetakey = metakey;
  }

}
