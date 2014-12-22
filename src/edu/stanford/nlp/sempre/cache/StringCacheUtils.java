package edu.stanford.nlp.sempre.cache;


public final class StringCacheUtils {
  private StringCacheUtils() { }
  // description could be
  //   Local path: ...
  //   Remote path: jacko:4000:/u/nlp/...
  public static StringCache create(String description) {
    // Remote
    if (description != null && description.indexOf(':') != -1) {
      String[] tokens = description.split(":", 3);
      if (tokens.length != 3)
        throw new RuntimeException("Invalid format (not server:port:path): " + description);
      RemoteStringCache cache = new RemoteStringCache(tokens[2], tokens[0], Integer.parseInt(tokens[1]));
      return cache;
    }

    // Local
    FileStringCache cache = new FileStringCache();
    if (description != null)
      cache.init(description);
    return cache;
  }
}
