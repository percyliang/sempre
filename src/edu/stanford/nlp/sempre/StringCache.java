package edu.stanford.nlp.sempre;

import fig.basic.IOUtils;
import fig.basic.LogInfo;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;

/**
 * Stores a Map<String, String>, which is synchronized with disk.
 *
 * @author Percy Liang
 */
public interface StringCache {
  public String get(String key);
  public void put(String key, String value);
}

/**
 * Cache backed by a file.
 *
 * @author Percy Liang
 */
class FileStringCache implements StringCache {
  String path;
  LinkedHashMap<String, String> cache = new LinkedHashMap<String, String>();
  PrintWriter out;

  public FileStringCache(String path) {
    if (path == null) return;

    this.path = path;

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

    out = IOUtils.openOutAppendHard(path);
    LogInfo.logs("Using cache %s (%d entries)", path, cache.size());
  }

  public String get(String key) { return cache.get(key); }

  public void put(String key, String value) {
    assert key.indexOf('\t') == -1 : key;
    assert key.indexOf('\n') == -1 : key;
    assert value.indexOf('\n') == -1 : value;
    cache.put(key, value);
    if (out != null) {
      out.println(key + "\t" + value);
      out.flush();
    }
  }

  public int size() { return cache.size(); }
}

/**
 * Cache backed by a remote service (see StringCacheServer).
 *
 * @author Percy Liang
 */
class RemoteStringCache implements StringCache {
  private Socket socket;
  private PrintWriter out;
  private BufferedReader in;

  // Cache things locally.
  private FileStringCache local = new FileStringCache(null);

  public RemoteStringCache(String path, String host, int port) {
    try {
      this.socket = new Socket(host, port);
      this.out = new PrintWriter(socket.getOutputStream(), true);
      this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      makeRequest("open", path, null);
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public String makeRequest(String method, String key, String value) {
    try {
      if (value == null)
        out.println(method + "\t" + key);
      else
        out.println(method + "\t" + key + "\t" + value);
      out.flush();
      for(int i = 0; i < 5; ++i) {
        try {
          String result = in.readLine();
          if (result.equals(StringCacheServer.nullString)) result = null;
          return result;
        } catch(NullPointerException e) {}
      }
      throw new NullPointerException();
    } catch (SocketTimeoutException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public String get(String key) {
    // First check the local cache.
    String value = local.get(key);
    if (value == null)
      value = makeRequest("get", key, null);
    return value;
    //return makeRequest("get", key, null);
  }

  public void put(String key, String value) {
    local.put(key, value);
    makeRequest("put", key, value);
  }

  public int size() { return local.size(); }
}

class StringCacheUtils {
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
