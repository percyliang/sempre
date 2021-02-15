package edu.stanford.nlp.sempre.cache;

import fig.basic.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cache backed by a file.
 *
 * @author Percy Liang
 */
public class FileStringCache implements StringCache, LruCallback<String, String> {
  public static class Options {
    @Option(gloss = "Cache capacity (in MB)")
    public int capacity = 35 * 1024;

    @Option(gloss = "Auto-flush cache to disk every N accesses")
    public int flushFrequency = Integer.MAX_VALUE;

    @Option(gloss = "Append mode instead of dump mode")
    public boolean appendMode = true;

    public int verbose = 0;
  }
  public static final Options opts = new Options();

  private String path;
  private PrintWriter out;

  private final LinkedHashMap<String, String> cache;
  private final StatFig keyStats = new StatFig();
  private final StatFig valStats = new StatFig();
  private int numTouches = 0;
  private int numEvictions = 0;
  private boolean readOnly;

  public FileStringCache() {
    int cap = opts.capacity;
    cap = (cap < 0) ? cap : (cap * 1024 * 1024);
    if (cap < 0) {
      cache = new LinkedHashMap<String, String>();
    } else {
      cache = new LruMap<String, String>(cap, this);
    }
  }

  public String getPath() { return path; }

  public void init(String path) { init(path, false); }
  public void init(String path, boolean readOnly) {
    if (this.path != null) throw new RuntimeException("Already initialized with " + this.path);
    this.path = path;
    this.readOnly = readOnly;

    // Read existing.
    if (new File(path).exists()) {
      try {
        BufferedReader in = IOUtils.openInHard(path);
        String line;
        while ((line = in.readLine()) != null) {
          String[] tokens = line.split("\t", 2);
          if (tokens.length != 2)
            throw new RuntimeException("Invalid line in cache file: " + line);
          cache.put(tokens[0], tokens[1]);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    LogInfo.logs("Using cache %s (%d entries)", path, cache.size());

    if (!readOnly && opts.appendMode)
      out = IOUtils.openOutAppendHard(path);
    flush();
  }

  private void flush() {
    if (readOnly)
      return;

    if (out != null) // Append mode
      return;

    if (path == null) // No file-backing
      return;

    if (opts.verbose >= 2) {
      LogInfo.begin_track("FileStringCache FLUSH (dump mode)");
      LogInfo.logs("Size: %d", size());
      if (cache instanceof LruMap)
        LogInfo.logs("Memory: %d", ((LruMap<String, String>) cache).getBytes());
      LogInfo.logs("Touches: %d", numTouches);
      LogInfo.logs("Evictions: %d", numEvictions);
      LogInfo.logs("Evicted keys: %s", keyStats);
      LogInfo.logs("Evicted values: %s", valStats);
      LogInfo.end_track();
    }

    PrintWriter dumpOut = IOUtils.openOutHard(this.path + ".tmp");
    for (Map.Entry<String, String> entry : cache.entrySet()) {
      dumpOut.println(entry.getKey() + "\t" + entry.getValue());
    }
    dumpOut.flush();
    dumpOut.close();
    try {
      Path src = FileSystems.getDefault().getPath(this.path + ".tmp");
      Path dst = FileSystems.getDefault().getPath(this.path);
      Files.move(src, dst,
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public String get(String key) { return cache.get(key); }

  public void put(String key, String value) {
    assert key.indexOf('\t') == -1 : key;
    assert key.indexOf('\n') == -1 : key;
    assert value.indexOf('\n') == -1 : value;
    if (opts.verbose >= 5) {
      logTrack("FileStringCache PUT (before)", key, value);
    }
    cache.put(key, value);
    if (out != null) { // Append mode
      out.println(key + "\t" + value);
      out.flush();
    }
    if (numTouches++ % opts.flushFrequency == 0)
      flush();
  }

  public int size() { return cache.size(); }

  @Override
  public void onEvict(Map.Entry<String, String> entry) {
    if (opts.verbose >= 5) {
      logTrack("FileStringCache EVICT (after)", entry.getKey(), entry.getValue());
    }
    numEvictions++;
    keyStats.add(entry.getKey(), entry.getKey().length());
    valStats.add(entry.getValue(), entry.getValue().length());
  }

  private void logTrack(String header, String key, String value) {
    LogInfo.begin_track(header);
    LogInfo.logs("Key size: %d (%d bytes)", key.length(), MemUsage.getBytes(key));
    LogInfo.logs("Val size: %d (%d bytes)", value.length(), MemUsage.getBytes(value));
    if (cache instanceof LruMap) {
      LogInfo.logs("Cache size: %d entries (%d bytes of %d)",
          cache.size(),
          ((LruMap) cache).getBytes(),
          ((LruMap) cache).getCapacity());
    }
    LogInfo.end_track();
  }

  // For tests
  @Deprecated
  public int getNumTouches() {
    return numTouches;
  }
}
