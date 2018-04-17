package edu.stanford.nlp.sempre.freebase;

import fig.basic.IOUtils;
import fig.basic.LogInfo;
import fig.basic.MapUtils;
import fig.basic.Option;
import fig.exec.Execution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

/**
 * Input: raw RDF Freebase dump (ttl format) downloaded from the Freebase
 * website. Input: canonical id map file output by BuildCanonicalIdMap.
 * <p/>
 * Output: Freebase dump with all mids and ids standardized.
 *
 * @author Percy Liang
 */
public class CanonicalizeIds implements Runnable {
  @Option public int maxInputLines = Integer.MAX_VALUE;
  @Option(required = true) public String canonicalIdMapPath;
  @Option(required = true, gloss = "Raw Freebase triples")
  public String rawPath;
  @Option(required = true, gloss = "Canonicalized Freebase triples")
  public String canonicalizedPath;

  public void run() {
    Map<String, String> canonicalIdMap = Utils.readCanonicalIdMap(canonicalIdMapPath, maxInputLines);

    // Do conversion
    LogInfo.begin_track("Convert");
    PrintWriter out = IOUtils.openOutHard(canonicalizedPath);
    out.println(Utils.ttlPrefix);
    try {
      BufferedReader in = IOUtils.openIn(rawPath);
      String line;
      int numInputLines = 0;
      while (numInputLines < maxInputLines && (line = in.readLine()) != null) {
        numInputLines++;
        if (numInputLines % 10000000 == 0)
          LogInfo.logs("Read %s lines", numInputLines);
        String[] tokens = Utils.parseTriple(line);
        if (tokens == null) continue;
        String arg1 = tokens[0];
        String property = tokens[1];
        String arg2 = tokens[2];

        // Do some simple filtering
        if (!property.startsWith("fb:")) continue;
        if (property.contains(".."))
          continue;  // Freebase dumps started containing paths through CVTs, which we don't need
        if (property.equals("fb:type.type.instance"))
          continue; // Already have type.object.type, don't need reverse map explicitly
        if (arg2.startsWith("\"") && !(arg2.endsWith("@en") || arg2.contains("^^xsd:")))
          continue;  // Strings must be in English or xsd values (boolean, int, float, datetime)

        arg2 = Utils.quoteValues(arg2); // Fix numerical values

        // Convert everything to use canonical ids.
        arg1 = MapUtils.get(canonicalIdMap, arg1, arg1);
        property = MapUtils.get(canonicalIdMap, property, property);
        arg2 = MapUtils.get(canonicalIdMap, arg2, arg2);

        Utils.writeTriple(out, arg1, property, arg2);
      }
      in.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    out.close();
    LogInfo.end_track();
  }

  public static void main(String[] args) {
    Execution.run(args, new CanonicalizeIds());
  }
}
