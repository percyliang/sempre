package edu.stanford.nlp.sempre.vis;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Things that should go into Fig or similar.
 *
 * @author Roy Frostig
 */
public final class Utils {
  private Utils() {}

  public static Map<String, Double> elementwiseProduct(Map<String, Double> a, Map<String, Double> b) {
    Map<String, Double> m = new HashMap<String, Double>();
    for (Map.Entry<String, Double> entry : a.entrySet()) {
      Double bBox = b.get(entry.getKey());
      double bVal = (bBox == null) ? 0.0d : bBox;
      m.put(entry.getKey(), entry.getValue() * bVal);
    }
    return m;
  }

  public static Map<String, Double> linearComb(double aFactor, double bFactor,
                                               Map<String, Double> a, Map<String, Double> b) {
    Map<String, Double> m = new HashMap<String, Double>();
    for (Map.Entry<String, Double> entry : a.entrySet()) {
      Double bBox = b.get(entry.getKey());
      double bVal = (bBox == null) ? 0.0d : bBox;
      m.put(entry.getKey(), aFactor * entry.getValue() + bFactor * bVal);
    }
    return m;
  }

  public static Map<String, Double> scale(double factor, Map<String, Double> x) {
    return linearComb(factor, 0.0d, x, new HashMap<String, Double>());
  }

  public static String readLineHard(BufferedReader in) {
    try {
      return in.readLine();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void closeHard(Closeable c) {
    try {
      c.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static int[] resize(int[] arr, int n) {
    int[] next = new int[n];
    int len = Math.min(arr.length, n);
    for (int i = 0; i < len; i++)
      next[i] = arr[i];
    return next;
  }

  public static <T> Map<T, Integer> indicesOf(Iterable<T> xs) {
    Map<T, Integer> m = new HashMap<T, Integer>();
    int i = 0;
    for (T x : xs) {
      if (!m.containsKey(x))
        m.put(x, i);
      i++;
    }
    return m;
  }

  /**
   * Simple generator-like iterable that provides objects of type T until the
   * first null value.  You only implement `computeNext()`.
   */
  public static abstract class SimpleGenerator<T> implements Iterable<T> {
    /** Return null when done. */
    abstract protected T computeNext();

    public Iterator<T> iterator() {
      return new Iterator<T>() {
        private T buf = null;

        @Override
        public boolean hasNext() {
          // Either the item is already buffered (so we keep it
          // buffered), or we are buffering one, or there is truly
          // nothing to buffer.
          buf = next();
          return buf != null;
        }

        @Override
        public T next() {
          // If we have an item buffered, return it and clear buffer.
          if (buf != null) {
            T tmp = buf;
            buf = null;
            return tmp;
          }

          // Otherwise, actually give us a fresh next item.
          return computeNext();
        }

        @Override
        public void remove() {
          throw new UnsupportedOperationException();
        }
      };
    }
  }
}
