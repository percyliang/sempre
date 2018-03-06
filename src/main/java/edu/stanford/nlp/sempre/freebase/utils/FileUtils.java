package edu.stanford.nlp.sempre.freebase.utils;

import com.google.common.base.Joiner;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.sempre.DateValue;
import edu.stanford.nlp.sempre.DescriptionValue;
import edu.stanford.nlp.sempre.Json;
import edu.stanford.nlp.sempre.NameValue;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.StringUtils;
import fig.basic.LispTree;
import fig.basic.LogInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Utilities for files
 * @author jonathanberant
 */
public final class FileUtils {
  private FileUtils() { }

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
      if (i % 1000000 == 0)
        LogInfo.logs("Uploaing line %s: %s", i, line);
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

  public static BiMap<String, Integer> loadString2IntegerBiMap(String file, String delimiter) throws IOException {

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

  public static BiMap<Integer, Integer> loadIntegerToIntegerBiMap(String file) throws IOException {

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

  public static Map<String, Integer> loadString2IntegerMap(String file) throws IOException {

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

  public static BiMap<String, Integer> loadString2IntegerBiMap(String file) throws IOException {
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

  //input: tab separated file with |utterance| |gold| |predicted|
  //output: prediction file for codalab
  public static void generatePredictionFile(String inFile, String outFile) throws IOException {

    PrintWriter writer = IOUtils.getPrintWriter(outFile);
    for (String line : IOUtils.readLines(inFile)) {
      String[] tokens = line.split("\\t");
      if (tokens.length == 0)
        continue;
      if (tokens.length < 2)
        throw new RuntimeException("Illegal line: " + line);
      String utterance = tokens[0];
      // get gold
      List<String> goldDescriptions = new ArrayList<>();
      LispTree goldTree = LispTree.proto.parseFromString(tokens[1]);
      for (int i = 1; i < goldTree.children.size(); ++i) {
        DescriptionValue dValue = (DescriptionValue) DescriptionValue.fromString(goldTree.child(i).toString());
        goldDescriptions.add(dValue.value);
      }
      // get predicted
      List<String> predictedDescriptions = new ArrayList<>();
      if (tokens.length > 2) {
        LispTree predictedTree = LispTree.proto.parseFromString(tokens[2]);
        for (int i = 1; i < predictedTree.children.size(); ++i) {
          if (predictedTree.child(i).child(0).value.equals("name")) {
            NameValue nValue = (NameValue) NameValue.fromString(predictedTree.child(i).toString());
            predictedDescriptions.add(nValue.description);
          } else if (predictedTree.child(i).child(0).value.equals("date")) {
            DateValue dateValue = (DateValue) DateValue.fromString(predictedTree.child(i).toString());
            predictedDescriptions.add(dateValue.toString());
          } else
            throw new RuntimeException("Can not support this value: " + line);
        }
      }
      writer.println(Joiner.on('\t').join(utterance,
              Json.writeValueAsStringHard(goldDescriptions),
              Json.writeValueAsStringHard(predictedDescriptions)));
    }
    writer.close();
  }

  public static void main(String[] args) {
    try {
      generatePredictionFile(args[0], args[1]);
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }
}
