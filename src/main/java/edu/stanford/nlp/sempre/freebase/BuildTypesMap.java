package edu.stanford.nlp.sempre.freebase;

import fig.basic.IOUtils;
import fig.basic.MapUtils;
import fig.basic.StrUtils;
import fig.basic.LogInfo;
import fig.basic.Option;
import fig.exec.Execution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Input: canonical Freebase dump
 *   fb:en.arnold_schwarzenegger     fb:type.object.type     fb:people.person.
 *   ...
 * Output: map from ids to comma-separated list of types
 *   fb:en.arnold_schwarzenegger     fb:people.person, fb:government.politician
 *
 * @author Percy Liang
 */
public class BuildTypesMap implements Runnable {
  @Option public int maxInputLines = Integer.MAX_VALUE;
  @Option(required = true, gloss = "Input") public String inPath;
  @Option(required = true, gloss = "Output") public String outPath;
  @Option(gloss = "keep only fb:en.*") public boolean keepOnlyEnIds = true;

  Map<String, List<String>> types = new LinkedHashMap<String, List<String>>();

  public void run() {
    LogInfo.begin_track("Reading %s", inPath);
    try {
      BufferedReader in = IOUtils.openIn(inPath);
      String line;
      int numLines = 0;
      while ((line = in.readLine()) != null) {
        String[] triple = Utils.parseTriple(line);
        if (++numLines % 100000000 == 0) LogInfo.logs("%d lines", numLines);
        if (triple == null) continue;
        if (!triple[1].equals("fb:type.object.type")) continue;
        if (keepOnlyEnIds && !triple[0].startsWith("fb:en.")) continue;
        MapUtils.addToList(types, triple[0], triple[2]);
      }
      in.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    LogInfo.end_track();

    LogInfo.begin_track("Writing to %s", outPath);
    PrintWriter out = IOUtils.openOutHard(outPath);
    for (Map.Entry<String, List<String>> e : types.entrySet()) {
      out.println(e.getKey() + "\t" + StrUtils.join(e.getValue(), ","));
    }
    out.close();
    LogInfo.end_track();
  }

  public static void main(String[] args) {
    Execution.run(args, new BuildTypesMap());
  }
}
