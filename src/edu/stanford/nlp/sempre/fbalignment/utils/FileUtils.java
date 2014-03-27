package edu.stanford.nlp.sempre.fbalignment.utils;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.StringUtils;
import fig.basic.LogInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Utilities for files
 * @author jonathanberant
 */
public class FileUtils {

  /**
   * Upload a set of string where each line is an element
   *
   * @throws IOException
   */
  public static Set<String> loadSet(String file) throws IOException {

    Set<String> res = new TreeSet<String>();
    BufferedReader reader = IOUtils.getBufferedFileReader(file);
    String line;
    while ((line = reader.readLine()) != null) {
      res.add(line);
    }
    reader.close();
    return res;
  }

  public static Map<String, String> loadStringToStringMap(String file) throws IOException {

    Map<String, String> res = new HashMap<String, String>();
    BufferedReader reader = IOUtils.getBufferedFileReader(file);
    String line;
    int i = 0;
    while ((line = reader.readLine()) != null) {
      String[] tokens = line.split("\t");
      res.put(tokens[0], tokens[1]);
      i++;
      if(i % 1000000 == 0)
        LogInfo.logs("Uploaing line %s: %s",i,line);
    }
    reader.close();
    return res;
  }

  public static Map<Integer, Double> loadIntToDoubleMap(String file) throws IOException {

    Map<Integer, Double> res = new HashMap<Integer, Double>();
    BufferedReader reader = IOUtils.getBufferedFileReader(file);
    String line;
    while ((line = reader.readLine()) != null) {
      String[] tokens = line.split("\t");
      res.put(Integer.parseInt(tokens[0]), Double.parseDouble(tokens[1]));
    }
    reader.close();
    return res;
  }

  public static Map<String, String> loadStringToStringMap(String file, int keyColumn, int valueColumn) throws IOException {

    Map<String, String> res = new HashMap<String, String>();
    BufferedReader reader = IOUtils.getBufferedFileReader(file);
    String line;
    int i = 0;
    while ((line = reader.readLine()) != null) {
      String[] tokens = line.split("\t");
      res.put(tokens[keyColumn], tokens[valueColumn]);
      i++;
      if (i % 1000000 == 0)
        LogInfo.log("Number of lines uploaded: " + i);
    }
    reader.close();
    return res;
  }

  public static BiMap<String, String> loadStringToStringBiMap(String file, int from, int to) throws IOException {

    BiMap<String, String> res = HashBiMap.create();
    BufferedReader reader = IOUtils.getBufferedFileReader(file);
    String line;
    while ((line = reader.readLine()) != null) {
      String[] tokens = line.split("\t");
      if (res.containsKey(tokens[from]))
        throw new RuntimeException("Map already contains key: " + tokens[from]);
      if (res.inverse().containsKey(tokens[to]))
        throw new RuntimeException("Map already contains value: " + tokens[to]);
      res.put(tokens[from], tokens[to]);
    }
    reader.close();
    return res;
  }

  public static Set<String> loadSetFromTabDelimitedFile(String file, int column) throws IOException {

    Set<String> res = new HashSet<String>();
    BufferedReader reader = IOUtils.getBufferedFileReader(file);
    String line;
    int i = 0;
    while ((line = reader.readLine()) != null) {
      String[] tokens = line.split("\t");
      res.add(tokens[column]);
      i++;
      if (i % 1000000 == 0) {
        LogInfo.log("Number of lines: " + i);
      }
    }

    reader.readLine();
    return res;

  }

  public static BiMap<String, Integer> loadString2IntegerBiMap(String file, String delimiter) throws IOException, NumberFormatException {

    BiMap<String, Integer> res = HashBiMap.create();
    BufferedReader reader = IOUtils.getBufferedFileReader(file);
    String line;
    while ((line = reader.readLine()) != null) {

      String[] tokens = line.split(delimiter);
      res.put(tokens[0], Integer.parseInt(tokens[1]));

    }
    reader.close();
    return res;
  }

  public static BiMap<Integer, Integer> loadIntegerToIntegerBiMap(String file) throws IOException, NumberFormatException {

    BiMap<Integer, Integer> res = HashBiMap.create();
    BufferedReader reader = IOUtils.getBufferedFileReader(file);
    String line;
    while ((line = reader.readLine()) != null) {

      String[] tokens = line.split("\t");
      res.put(Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1]));

    }
    reader.close();
    return res;
  }

  public static Map<String, Integer> loadString2IntegerMap(String file) throws IOException, NumberFormatException {

    Map<String, Integer> res = new HashMap<String, Integer>();
    BufferedReader reader = IOUtils.getBufferedFileReader(file);
    String line;
    while ((line = reader.readLine()) != null) {

      String[] tokens = line.split("\t");
      res.put(tokens[0], Integer.parseInt(tokens[1]));

    }
    reader.close();
    return res;
  }

  public static BiMap<String, Integer> loadString2IntegerBiMap(String file) throws IOException, NumberFormatException {
    return loadString2IntegerBiMap(file, "\t");
  }

  public static Counter<String> loadStringCounter(String filename) {

    Counter<String> res = new ClassicCounter<String>();
    for (String line : ObjectBank.getLineIterator(filename)) {

      String[] tokens = line.split("\t");
      res.incrementCount(tokens[0], Double.parseDouble(tokens[1]));

    }
    return res;
  }

  public static void ridDuplicates(String inFile, String outFile) throws IOException {

    Set<String> inSet = loadSet(inFile);
    PrintWriter writer = IOUtils.getPrintWriter(outFile);
    for (String str : inSet) {
      writer.println(str);
    }
    writer.close();
  }

  public static String omitPunct(String str) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < str.length(); ++i) {
      if (!StringUtils.isPunct((new Character(str.charAt(i))).toString())) {
        sb.append(str.charAt(i));
      }
    }
    return sb.toString();
  }
}
