package edu.stanford.nlp.sempre.cache;

import java.io.*;
import java.net.*;

import fig.basic.LogInfo;

/**
 * Cache backed by a remote service (see StringCacheServer).
 *
 * @author Percy Liang
 */
public class RemoteStringCache implements StringCache {
  public static final int NUM_TRIES = 5;

  private Socket socket;
  private PrintWriter out;
  private BufferedReader in;

  // Cache things locally.
  private FileStringCache local = new FileStringCache();

  public RemoteStringCache(String path, String host, int port) {
    try {
      LogInfo.begin_track("RemoteStringCache: connecting to %s:%s to access %s", host, port, path);
      this.socket = new Socket(host, port);
      this.out = new PrintWriter(socket.getOutputStream(), true);
      this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      String response = makeRequest("open", path, null);
      LogInfo.logs("Using cache path=%s, host=%s, port=%s", path, host, port);
      if (!response.equals("OK")) {
        throw new RuntimeException(response);
      }
      LogInfo.end_track();
    } catch (UnknownHostException e) {
      LogInfo.end_track();
      throw new RuntimeException(e);
    } catch (IOException e) {
      LogInfo.end_track();
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
      for (int i = 0; i < NUM_TRIES; i++) {
        try {
          String result = in.readLine();
          if (result.equals(StringCacheServer.nullString)) result = null;
          return result;
        } catch (NullPointerException e) {
          LogInfo.logs("RemoteStringCache.makeRequest(%s, %s, %s) failed", method, key, value);
        }
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
  }

  public void put(String key, String value) {
    local.put(key, value);
    makeRequest("put", key, value);
  }

  public int size() { return local.size(); }
}
