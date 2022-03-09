package edu.stanford.nlp.sempre.freebase;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import edu.stanford.nlp.sempre.Formula;
import edu.stanford.nlp.sempre.Formulas;
import fig.basic.*;
import fig.exec.Execution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Input: canonicalized Freebase ttl file. Input: example files. Output: subset
 * of the ttl file that only involves the referenced properties.
 *
 * @author Percy Liang
 */
public class FilterFreebase implements Runnable {
  @Option(required = true, gloss = "Canonicalized Freebase dump")
  public String inPath;
  @Option public int maxInputLines = Integer.MAX_VALUE;
  @Option(gloss = "Examples files (keep properties that show up in these files)")
  public List<String> examplesPaths = new ArrayList<String>();

  @Option(gloss = "Keep only type entries involving these")
  public List<String> keepTypesPaths = new ArrayList<String>();
  @Option(gloss = "Keep these properties")
  public List<String> keepPropertiesPaths = new ArrayList<String>();
  @Option(gloss = "Ignore these properties")
  public List<String> notKeepPropertiesPaths = new ArrayList<String>();

  @Option(gloss = "Schema properties to keep")
  public HashSet<String> schemaProperties = new HashSet<String>(
      ImmutableList.of(
          "fb:type.property.schema",
          "fb:type.property.unit",
          "fb:type.property.expected_type",
          "fb:type.property.reverse_property",
          "fb:freebase.type_hints.mediator",
          "fb:freebase.type_hints.included_types"
      ));

  @Option(gloss = "General properties that we should keep")
  public HashSet<String> generalProperties = new HashSet<String>(
      ImmutableList.of(
          "fb:type.object.type",
          "fb:type.object.name",
          "fb:measurement_unit.dated_integer.number",
          "fb:measurement_unit.dated_integer.year"
      ));

  // Set this if we want to make a small Freebase.
  @Option(gloss = "If true, keep general properties only for entities seen with the other keepProperties (uses much more memory, but results in smaller output)")
  public boolean keepGeneralPropertiesOnlyForSeenEntities = false;

  @Option public boolean keepAllProperties = false;

  // Keep only type assertions involving these types.
  // If empty, don't filter.
  Set<String> keepTypes = new LinkedHashSet<String>();

  // These are the properties for which we should keep all entity pairs.  Derived from many sources.
  // Should never be empty.
  Set<String> keepProperties = new LinkedHashSet<String>();

  // Entities that we saw (only needed if we need to use them to filter general properties later).
  Set<String> seenEntities = new HashSet<String>();

  // Fill out |keepProperties|
  private void readKeep() {
    LogInfo.begin_track("readKeep");

    // Always keep schema
    keepProperties.addAll(schemaProperties);

    // General properties to keep
    if (!keepGeneralPropertiesOnlyForSeenEntities)
      keepProperties.addAll(generalProperties);

    // Keep properties mentioned in examples
    for (String path : examplesPaths) {
      LogInfo.logs("Reading %s", path);
      Iterator<LispTree> it = LispTree.proto.parseFromFile(path);
      while (it.hasNext()) {
        LispTree tree = it.next();
        if (!"example".equals(tree.child(0).value))
          throw new RuntimeException("Bad: " + tree);
        for (int i = 1; i < tree.children.size(); i++) {
          if ("targetFormula".equals(tree.child(i).child(0).value)) {
            Formula formula = Formulas.fromLispTree(tree.child(i).child(1));
            keepProperties.addAll(Formulas.extractAtomicFreebaseElements(formula));
          }
        }
      }
    }

    // Keep types
    for (String path : keepTypesPaths)
      for (String type : IOUtils.readLinesHard(path))
        keepTypes.add(type);

    // Keep and not keep properties
    for (String path : keepPropertiesPaths)
      for (String property : IOUtils.readLinesHard(path))
        keepProperties.add(property);
    for (String path : notKeepPropertiesPaths)
      for (String property : IOUtils.readLinesHard(path))
        keepProperties.remove(property);

    PrintWriter out = IOUtils.openOutHard(Execution.getFile("keepProperties"));
    for (String property : keepProperties)
      out.println(property);
    out.close();
    LogInfo.logs("Keeping %s properties", keepProperties.size());
    LogInfo.end_track();
  }

  private void filterTuples() {
    LogInfo.begin_track("filterTuples");
    TDoubleMap<String> propertyCounts = new TDoubleMap<String>();

    PrintWriter out = IOUtils.openOutHard(Execution.getFile("0.ttl"));
    out.println(Utils.ttlPrefix);

    try {
      BufferedReader in = IOUtils.openIn(inPath);
      String line;
      int numInputLines = 0;
      int numOutputLines = 0;
      while (numInputLines < maxInputLines && (line = in.readLine()) != null) {
        numInputLines++;
        if (numInputLines % 10000000 == 0)
          LogInfo.logs("filterTuples: Read %s lines, written %d lines", numInputLines, numOutputLines);
        String[] tokens = Utils.parseTriple(line);
        if (tokens == null) continue;
        String arg1 = tokens[0];
        String property = tokens[1];
        String arg2 = tokens[2];
        if (!keepAllProperties && !keepProperties.contains(property)) continue;

        if (keepGeneralPropertiesOnlyForSeenEntities) {
          seenEntities.add(arg1);
          seenEntities.add(arg2);
        }

        // Additional filtering of characters that Virtuoso can't index (we would need to be escape these).
        if (Utils.isUrl(arg2)) continue;
        if (Utils.identifierContainsStrangeCharacters(arg1) || Utils.identifierContainsStrangeCharacters(arg2))
          continue;

        Utils.writeTriple(out, arg1, property, arg2);

        propertyCounts.incr(property, 1);
        numOutputLines++;
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    // Make a second pass to only output general properties.
    if (keepGeneralPropertiesOnlyForSeenEntities) {
      LogInfo.begin_track("Second pass to output general properties for the %d seen entities", seenEntities.size());
      try {
        BufferedReader in = IOUtils.openIn(inPath);
        String line;
        int numInputLines = 0;
        int numOutputLines = 0;
        while (numInputLines < maxInputLines && (line = in.readLine()) != null) {
          numInputLines++;
          if (numInputLines % 10000000 == 0)
            LogInfo.logs("filterTuples: Read %s lines, written %d lines", numInputLines, numOutputLines);
          String[] tokens = Utils.parseTriple(line);
          if (tokens == null) continue;
          String arg1 = tokens[0];
          String property = tokens[1];
          String arg2 = tokens[2];
          if (!generalProperties.contains(property)) continue;
          if (!seenEntities.contains(arg1)) continue;

          // Only keep types that matter
          if (keepTypes.size() != 0 && property.equals("fb:type.object.type") && !keepTypes.contains(arg2)) continue;

          Utils.writeTriple(out, arg1, property, arg2);

          numOutputLines++;
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      LogInfo.end_track();
    }

    out.close();

    // Output property statistics
    PrintWriter propertyCountsOut = IOUtils.openOutHard(Execution.getFile("propertyCounts"));
    List<TDoubleMap<String>.Entry> entries = Lists.newArrayList(propertyCounts.entrySet());
    Collections.sort(entries, propertyCounts.entryValueComparator());
    for (TDoubleMap<String>.Entry e : entries) {
      propertyCountsOut.println(e.getKey() + "\t" + e.getValue());
    }
    propertyCountsOut.close();

    LogInfo.end_track();
  }

  public void run() {
    readKeep();
    filterTuples();
  }

  public static void main(String[] args) {
    Execution.run(args, new FilterFreebase());
  }
}
