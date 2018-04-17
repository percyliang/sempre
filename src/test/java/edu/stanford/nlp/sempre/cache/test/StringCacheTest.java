package edu.stanford.nlp.sempre.cache.test;

import edu.stanford.nlp.sempre.cache.FileStringCache;
import fig.basic.IOUtils;
import fig.basic.MemUsage;

import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.AssertJUnit.assertEquals;

/**
 * @author Roy Frostig
 */
public class StringCacheTest {
  private int numLines(BufferedReader in) throws IOException {
    int n = 0;
    while (in.readLine() != null) n++;
    return n;
  }

  /**
   * Check that we flush at the given frequency
   * @throws IOException
   */
  @SuppressWarnings("deprecation")
  @Test(groups = "fs")
  public void testFlush() throws IOException {
    FileStringCache.opts.appendMode = false;
    FileStringCache.opts.capacity = 1;
    FileStringCache.opts.flushFrequency = 10;
    FileStringCache.opts.verbose = 5;

    final String fs = "StringCacheTest-cache.tmp";
    final Path fsPath = FileSystems.getDefault().getPath(fs);

    Files.deleteIfExists(fsPath);
    FileStringCache cache = new FileStringCache();
    cache.init(fs);

    for (int i = 0; i <= 100; i++) {
      String key = "key:" + i;
      String val = "val:" + i;
      cache.put(key, val);
      assertEquals(cache.getNumTouches(), i + 1);
      if (i > 0 && i % 10 == 0) {
        int lines = numLines(IOUtils.openInHard(fs));
        System.out.println("!!! " + lines + " = " + i);
        assertEquals(lines, i + 1);
      }
    }
    Files.deleteIfExists(fsPath);
  }

  /**
   * Check that we really do evict at the capacity.
   * @throws IOException
   */
  @Test(groups = "fs")
  public void testEvict() throws IOException {
    FileStringCache.opts.appendMode = false;
    FileStringCache.opts.capacity = 10;
    FileStringCache.opts.flushFrequency = 10;
    FileStringCache.opts.verbose = 5;

    final String fs = "StringCacheTest-cache.tmp";
    final Path fsPath = FileSystems.getDefault().getPath(fs);

    Files.deleteIfExists(fsPath);
    FileStringCache cache = new FileStringCache();
    cache.init(fs);

    // Make ~20 MB of string data
    String junk20MB = "junk";
    while (MemUsage.getBytes(junk20MB) <= 20 * 1024 * 1024)
      junk20MB += junk20MB;

    // Make ~1 MB of string data
    String junk1MB = "junk";
    while (MemUsage.getBytes(junk1MB) <= 1024 * 1024)
      junk1MB += junk1MB;

    // Add something small, to be evicted
    cache.put(junk1MB + "1", "test1");
    assertEquals(1, cache.size());
    assert cache.get(junk1MB + "1").equals("test1");

    // Add something small, shouldn't evict anything
    cache.put(junk1MB + "2", "test2");
    assertEquals(2, cache.size());
    assert cache.get(junk1MB + "1").equals("test1");
    assert cache.get(junk1MB + "2").equals("test2");

    // Add something big, to saturate cache and kick out the small entries.
    // Also kicks itself out.
    cache.put(junk20MB + "1", "big1");
    assertEquals(0, cache.size());
    assertEquals(cache.get(junk20MB + "1"), null);

    // Do that again.
    cache.put(junk20MB + "2", "big2");
    assertEquals(0, cache.size());
    assertEquals(cache.get(junk20MB + "2"), null);

    Files.deleteIfExists(fsPath);
  }
}
