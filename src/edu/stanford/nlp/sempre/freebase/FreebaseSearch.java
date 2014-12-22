package edu.stanford.nlp.sempre.freebase;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.cache.StringCache;
import edu.stanford.nlp.sempre.cache.StringCacheUtils;
import fig.basic.*;
import java.io.*;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.*;

/**
 * Takes a string (e.g., "obama") and asks the Freebase Search API.
 * Caches if necessary.
 * Outputs a set of entities.
 *
 * @author Percy Liang
 */
public class FreebaseSearch {
  public static class Entry {
    public Entry(String mid, String id, String name, double score) {
      this.mid = mid;
      this.id = id;
      this.name = name;
      this.score = score;
    }

    public final String mid;
    public final String id;
    public final String name;
    public final double score;

    public LispTree toLispTree() {
      LispTree tree = LispTree.proto.newList();
      tree.addChild(mid);
      tree.addChild(id == null ? "" : id);
      tree.addChild(name);
      tree.addChild(score + "");
      return tree;
    }

    public String toString() { return toLispTree().toString(); }
  }

  public static class Options {
    @Option(gloss = "Milliseconds to wait until opening connection times out")
    public int connectTimeoutMs = 1 * 60 * 1000;

    @Option(gloss = "Milliseconds to wait until reading connection times out")
    public int readTimeoutMs = 1 * 60 * 1000;

    @Option(gloss = "API key (needed to get more access)")
    public String apiKey;

    @Option(gloss = "Save results of Freebase API search")
    public String cachePath;
  }
  public static Options opts = new Options();

  private final StringCache cache;

  public class ServerResponse {
    public ServerResponse() { this.entries = new ArrayList<>(); this.error = null; }
    public ServerResponse(ErrorValue error) { this.entries = null; this.error = error; }
    public final List<Entry> entries;
    public final ErrorValue error;
    boolean cached;
    long timeMs;
  }

  public FreebaseSearch() {
    if (opts.cachePath != null)
      this.cache = StringCacheUtils.create(opts.cachePath);
    else
      this.cache = null;
  }

  @SuppressWarnings("unchecked")
  public ServerResponse lookup(String query) {
    StopWatch watch = new StopWatch();
    watch.start();
    String output = null;
    ServerResponse response = new ServerResponse();

    // First, try the cache.
    if (cache != null) {
      output = cache.get(query);
      if (output != null) response.cached = true;
    }

    // If got nothing, then need to hit the server.
    if (output == null) {
      try {
        // Setup the connection
        String url = String.format("https://www.googleapis.com/freebase/v1/search?query=%s", URLEncoder.encode(query, "UTF-8"));
        if (opts.apiKey != null) url += "&key=" + opts.apiKey;
        URLConnection conn = new URL(url).openConnection();
        conn.setConnectTimeout(opts.connectTimeoutMs);
        conn.setReadTimeout(opts.readTimeoutMs);
        InputStream in = conn.getInputStream();

        // Read the response
        StringBuilder buf = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = reader.readLine()) != null)
          buf.append(line);
        reader.close();

        // Put the result in the cache
        output = buf.toString();
        if (cache != null)
          cache.put(query, output);

      } catch (SocketTimeoutException e) {
        return new ServerResponse(ErrorValue.timeout);
      } catch (IOException e) {
        LogInfo.errors("Server exception: %s", e);
        if (e.toString().contains("HTTP response code: 408"))
          return new ServerResponse(ErrorValue.server408);
        if (e.toString().contains("HTTP response code: 500"))
          return new ServerResponse(ErrorValue.server500);
        throw new RuntimeException(e);  // Haven't seen this happen yet...
      }
    }

    // Parse the result
    Map<String, Object> results = Json.readMapHard(output);
    for (Object resultObj : (List) results.get("result")) {
      Map<String, Object> result = (Map<String, Object>) resultObj;
      String mid = (String) result.get("mid");
      String id = (String) result.get("id");
      mid = toRDF(mid);
      id = toRDF(id);
      String name = (String) result.get("name");
      double score = (double) result.get("score");
      response.entries.add(new Entry(mid, id, name, score));
    }

    watch.stop();
    response.timeMs = watch.getCurrTimeLong();
    LogInfo.logs("FreebaseSearch %s => %s results (cached=%s)", query, response.entries.size(), response.cached);
    return response;
  }

  // /en/barack_obama => fb:en.barack_obama
  private String toRDF(String s) {
    if (s == null) return s;
    return "fb:" + s.substring(1).replaceAll("/", ".");
  }

  public static void main(String[] args) {
    opts.cachePath = "FreebaseSearch.cache";
    String query = StrUtils.join(args, " ");
    query = "obama";
    FreebaseSearch search = new FreebaseSearch();
    LogInfo.logs("%s", search.lookup(query).entries);
  }
}
