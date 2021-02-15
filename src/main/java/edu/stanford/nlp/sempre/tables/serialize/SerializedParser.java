package edu.stanford.nlp.sempre.tables.serialize;

import java.io.File;
import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.alter.TurkEquivalentClassInfo;
import fig.basic.LogInfo;
import fig.basic.Option;
import fig.basic.Pair;

/**
 * Parser used when loading serialized data.
 *
 * SerializedParser assumes that all candidate derivations were already computed in the dump file.
 * So the parser skips the parsing step and just load the candidates
 *
 * @author ppasupat
 */
public class SerializedParser extends Parser {
  public static class Options {
    // Must be a gzip file or a directory of gzip files
    @Option(gloss = "Path for formula annotation")
    public String annotationPath = null;
    // Skip the example if some criterion is met
    @Option(gloss = "(optional) Path for turk-info.tsv")
    public String turkInfoPath = null;
    @Option(gloss = "(If turkInfoPath is present) Maximum number of numClassesMatched")
    public int maxNumClassesMatched = 50;
    @Option(gloss = "(If turkInfoPath is present) Maximum number of numDerivsMatched")
    public int maxNumDerivsMatched = 50000;
  }
  public static Options opts = new Options();

  // Map from ID string to LazyLoadedExampleList and example index.
  protected Map<String, Pair<LazyLoadedExampleList, Integer>> idToSerializedIndex = null;
  // Map from ID string to TurkEquivalentClassInfo.
  protected Map<String, TurkEquivalentClassInfo> idToTurkInfo = null;

  public SerializedParser(Spec spec) {
    super(spec);
    if (opts.annotationPath != null)
      readSerializedFile(opts.annotationPath);
    if (opts.turkInfoPath != null) {
      LogInfo.begin_track("Reading Turk info from %s", opts.turkInfoPath);
      idToTurkInfo = TurkEquivalentClassInfo.fromFile(opts.turkInfoPath);
      LogInfo.end_track();
    }
  }

  // Don't do it.
  @Override protected void computeCatUnaryRules() {
    catUnaryRules = Collections.emptyList();
  };

  protected void readSerializedFile(String annotationPath) {
    idToSerializedIndex = new HashMap<>();
    SerializedDataset dataset = new SerializedDataset();
    if (new File(annotationPath).isDirectory()) {
      dataset.readDir(annotationPath);
    } else {
      dataset.read("annotated", annotationPath);
    }
    for (String group : dataset.groups()) {
      LazyLoadedExampleList examples = dataset.examples(group);
      List<String> ids = examples.getAllIds();
      for (int i = 0; i < ids.size(); i++)
        idToSerializedIndex.put(ids.get(i), new Pair<>(examples, i));
    }
  }

  @Override
  public ParserState newParserState(Params params, Example ex, boolean computeExpectedCounts) {
    return new SerializedParserState(this, params, ex, computeExpectedCounts);
  }

}

class SerializedParserState extends ParserState {

  public SerializedParserState(Parser parser, Params params, Example ex, boolean computeExpectedCounts) {
    super(parser, params, ex, computeExpectedCounts);
  }

  @Override
  public void infer() {
    SerializedParser parser = (SerializedParser) this.parser;
    if (parser.idToTurkInfo != null) {
      TurkEquivalentClassInfo info = parser.idToTurkInfo.get(ex.id);
      if (info != null) {
        if (info.numClassesMatched > SerializedParser.opts.maxNumClassesMatched) {
          LogInfo.logs("Skipped %s since numClassesMatched = %d > %d",
              ex.id, info.numClassesMatched, SerializedParser.opts.maxNumClassesMatched);
          if (computeExpectedCounts) expectedCounts = new HashMap<>();
          return;
        }
        if (info.numDerivsMatched > SerializedParser.opts.maxNumDerivsMatched) {
          LogInfo.logs("Skipped %s since numDerivsMatched = %d > %d",
              ex.id, info.numDerivsMatched, SerializedParser.opts.maxNumDerivsMatched);
          if (computeExpectedCounts) expectedCounts = new HashMap<>();
          return;
        }
      }
    }
    if (parser.idToSerializedIndex != null) {
      Pair<LazyLoadedExampleList, Integer> pair = parser.idToSerializedIndex.get(ex.id);
      if (pair != null) {
        Example annotatedEx = pair.getFirst().get(pair.getSecond());
        for (Derivation deriv : annotatedEx.predDerivations) {
          featurizeAndScoreDerivation(deriv);
          predDerivations.add(deriv);
        }
      }
    } else {
      // Assume that the example already has all derivations.
      if (ex.predDerivations == null)
        ex.predDerivations = new ArrayList<>();
      for (Derivation deriv : ex.predDerivations) {
        featurizeAndScoreDerivation(deriv);
        predDerivations.add(deriv);
      }
    }
    ensureExecuted();
    if (computeExpectedCounts) {
      expectedCounts = new HashMap<>();
      ParserState.computeExpectedCounts(predDerivations, expectedCounts);
    }
  }

}
