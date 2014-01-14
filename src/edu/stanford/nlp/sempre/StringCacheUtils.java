package edu.stanford.nlp.sempre;

public class StringCacheUtils {
  // description could be
  //   Local path:
  //   Remote path: jacko:4000:/u/nlp/...
  public static StringCache create(String description) {
    if (description != null && description.indexOf(':') != -1) {
      String[] tokens = description.split(":", 3);
      if (tokens.length != 3)
        throw new RuntimeException("Invalid format (not server:port:path): " + description);
      RemoteStringCache cache = new RemoteStringCache(tokens[2], tokens[0], Integer.parseInt(tokens[1]));
      return cache;
    }

    // Local
    return new FileStringCache(description);
  }
}