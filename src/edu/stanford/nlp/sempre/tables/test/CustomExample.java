package edu.stanford.nlp.sempre.tables.test;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import edu.stanford.nlp.sempre.CanonicalNames;
import edu.stanford.nlp.sempre.ContextValue;
import edu.stanford.nlp.sempre.Example;
import edu.stanford.nlp.sempre.Formulas;
import edu.stanford.nlp.sempre.LanguageInfo;
import edu.stanford.nlp.sempre.Values;
import edu.stanford.nlp.sempre.tables.StringNormalizationUtils;
import edu.stanford.nlp.sempre.tables.TableTypeSystem;
import fig.basic.*;
import fig.exec.Execution;

/**
 * Custom version of Example.
 *
 * - Allow additional keys "warning", "error", and "alternativeFormula"
 * - Allow shorthand annotation for targetFormula
 *
 * @author ppasupat
 */
public class CustomExample extends Example {
  public static class Options {
    // Format: "3-5,10,12-20"
    @Option(gloss = "Filter only these examples") public String filterExamples = null;
    @Option public boolean allowNoAnnotation = false;
  }
  public static Options opts = new Options();

  public List<String> warnings = new ArrayList<>();
  public List<String> errors = new ArrayList<>();

  public CustomExample(Example ex) {
    // Copy everything from ex
    super(ex.id, ex.utterance, ex.context, ex.targetFormula, ex.targetValue, ex.languageInfo);
  }

  static final Set<String> usefulTags =
      new HashSet<String>(Arrays.asList("id", "utterance", "targetFormula", "targetValue", "targetValues", "context"));

  /**
   * Convert LispTree to Example with additional tags
   */
  public static CustomExample fromLispTree(LispTree tree, String defaultId) {
    Builder b = new Builder().setId(defaultId);

    for (int i = 1; i < tree.children.size(); i++) {
      LispTree arg = tree.child(i);
      String label = arg.child(0).value;
      if ("id".equals(label)) {
        b.setId(arg.child(1).value);
      } else if ("utterance".equals(label)) {
        b.setUtterance(arg.child(1).value);
      } else if ("targetFormula".equals(label)) {
        LispTree canonicalized = canonicalizeFormula(arg.child(1));
        b.setTargetFormula(Formulas.fromLispTree(canonicalized));
      } else if ("targetValue".equals(label) || "targetValues".equals(label)) {
        if (arg.children.size() != 2)
          throw new RuntimeException("Expect one target value");
        b.setTargetValue(Values.fromLispTree(arg.child(1)));
      } else if ("context".equals(label)) {
        b.setContext(new ContextValue(arg));
      }
    }
    b.setLanguageInfo(new LanguageInfo());

    CustomExample ex = new CustomExample(b.createExample());
    boolean error = false;

    for (int i = 1; i < tree.children.size(); i++) {
      LispTree arg = tree.child(i);
      String label = arg.child(0).value;
      if ("warning".equals(label)) {
        ex.warnings.add(arg.child(1).value);
      } else if ("error".equals(label)) {
        error = true;
        ex.errors.add(arg.child(1).value);
      } else if ("alternativeFormula".equals(label)) {
        // Do nothing for now
      } else if (!usefulTags.contains(label)) {
        throw new RuntimeException("Invalid example argument: " + arg);
      }
    }

    // Check formula and error
    if (ex.targetFormula == null && !error && !opts.allowNoAnnotation)
      throw new RuntimeException("Either error or targetFormula must be present.");
    if (ex.targetFormula != null && error)
      throw new RuntimeException("Cannot use error when targetFormula is present.");

    return ex;
  }

  static final Map<String, String> formulaMacros;
  static {
    formulaMacros = new HashMap<>();
    formulaMacros.put("@type", CanonicalNames.TYPE);
    formulaMacros.put("@row", TableTypeSystem.ROW_TYPE);
    formulaMacros.put("@next", TableTypeSystem.ROW_NEXT_VALUE.id);
    formulaMacros.put("@!next", "!" + TableTypeSystem.ROW_NEXT_VALUE.id);
    formulaMacros.put("@index", TableTypeSystem.ROW_INDEX_VALUE.id);
    formulaMacros.put("@!index", "!" + TableTypeSystem.ROW_INDEX_VALUE.id);
    formulaMacros.put("@p.num", TableTypeSystem.CELL_NUMBER_VALUE.id);
    formulaMacros.put("@!p.num", "!" + TableTypeSystem.CELL_NUMBER_VALUE.id);
    formulaMacros.put("@p.date", TableTypeSystem.CELL_DATE_VALUE.id);
    formulaMacros.put("@!p.date", "!" + TableTypeSystem.CELL_DATE_VALUE.id);
    //formulaMacros.put("@p.first", TableTypeSystem.CELL_FIRST_VALUE.id);
    //formulaMacros.put("@!p.first", "!" + TableTypeSystem.CELL_FIRST_VALUE.id);
    formulaMacros.put("@p.num2", TableTypeSystem.CELL_SECOND_VALUE.id);
    formulaMacros.put("@!p.num2", "!" + TableTypeSystem.CELL_SECOND_VALUE.id);
  }

  static final Pattern regexProperty = Pattern.compile("c\\.(.*)");
  static final Pattern regexReversedProperty = Pattern.compile("!c\\.(.*)");
  static final Pattern regexEntity = Pattern.compile("c_(.*)\\.(.*)");

  /**
   * Return a new LispTree representing the canonicalized version of the original formula
   */
  public static LispTree canonicalizeFormula(LispTree orig) {
    if (orig.isLeaf()) {
      String value = orig.value;
      // 45 --> (number 45)
      if (StringNormalizationUtils.parseNumberStrict(value) != null)
        return LispTree.proto.newList("number", value);
      // value with "@" --> canonicalized name
      if (value.contains("@")) {
        String canonicalName = formulaMacros.get(value);
        if (canonicalName == null)
          throw new RuntimeException("Unrecognized macro: " + value);
        return LispTree.proto.newLeaf(canonicalName);
      }
      // c.xxx or c_xxx.yyy --> canonicalized name
      Matcher match;
      if ((match = regexProperty.matcher(value)).matches())
        return LispTree.proto.newLeaf(TableTypeSystem.getPropertyName(match.group(1)));
      if ((match = regexReversedProperty.matcher(value)).matches())
        return LispTree.proto.newLeaf("!" + TableTypeSystem.getPropertyName(match.group(1)));
      if ((match = regexEntity.matcher(value)).matches())
        return LispTree.proto.newLeaf(TableTypeSystem.getCellName(match.group(2), match.group(1)));
      if (value.contains("."))
        throw new RuntimeException("Unhandled '.': " + value);
      return orig;
    } else {
      LispTree answer = LispTree.proto.newList();
      // Handle special cases
      LispTree head = orig.child(0);
      if ("date".equals(head.value)) {
        for (LispTree child : orig.children) {
          answer.addChild(LispTree.proto.newLeaf(child.value));
        }
      } else {
        for (LispTree child : orig.children) {
          answer.addChild(canonicalizeFormula(child));
        }
      }
      return answer;
    }
  }

  // ============================================================
  // Read dataset
  // ============================================================

  interface ExampleProcessor {
    void run(CustomExample ex);
  }

  private static boolean checkFilterExamples(int n) {
    if (opts.filterExamples == null || opts.filterExamples.isEmpty()) return true;
    for (String range : opts.filterExamples.split(",")) {
      String[] tokens = range.split("-");
      if (tokens.length == 1)
        if (Integer.parseInt(tokens[0]) == n)
          return true;
      if (tokens.length == 2)
        if (Integer.parseInt(tokens[0]) <= n && Integer.parseInt(tokens[1]) >= n)
          return true;
    }
    return false;
  }

  public static List<CustomExample> getDataset(List<Pair<String, String>> pathPairs, ExampleProcessor processor) {
    LogInfo.begin_track_printAll("Dataset.read");
    Evaluation evaluation = new Evaluation();
    List<CustomExample> examples = new ArrayList<>();
    for (Pair<String, String> pathPair : pathPairs) {
      String group = pathPair.getFirst();
      String path = pathPair.getSecond();
      if (!group.equals("train")) continue;
      Execution.putOutput("group", group);

      LogInfo.begin_track("Reading %s", path);
      Iterator<LispTree> trees = LispTree.proto.parseFromFile(path);

      while (trees.hasNext()) {
        // Format: (example (id ...) (utterance ...) (targetFormula ...) (targetValue ...))
        LispTree tree = trees.next();
        if (!checkFilterExamples(examples.size())) { // Skip -- for debugging
          examples.add(null);
          continue;
        }
        LogInfo.begin_track("Reading Example %s", examples.size());
        if (tree.children.size() < 2 && !"example".equals(tree.child(0).value))
          throw new RuntimeException("Invalid example: " + tree);
        CustomExample ex = null;
        Execution.putOutput("example", examples.size());
        try {
          ex = CustomExample.fromLispTree(tree, path + ":" + examples.size());  // Specify a default id if it doesn't exist
          ex.preprocess();
          ex.log();
        } catch (Exception e) {
          StringWriter sw = new StringWriter();
          e.printStackTrace(new PrintWriter(sw));
          LogInfo.warnings("Example %s: CONTAINS ERROR! %s:\n%s", ex == null ? ex : ex.id, e, sw);
        }
        examples.add(ex);
        for (String warning : ex.warnings) LogInfo.logs("WARNING: %s", warning);
        for (String error : ex.errors) LogInfo.logs("ERROR: %s", error);
        if (processor != null) processor.run(ex);
        LogInfo.end_track();
        if (ex != null && ex.evaluation != null) {
          LogInfo.logs("Current: %s", ex.evaluation.summary());
          evaluation.add(ex.evaluation);
          LogInfo.logs("Cumulative(%s): %s", group, evaluation.summary());
        }
      }
      LogInfo.end_track();
      LogInfo.logs("Stats for %s: %s", group, evaluation.summary());
      evaluation.logStats(group);
      evaluation.putOutput(group);
    }
    LogInfo.end_track();
    return examples;
  }

  public static List<CustomExample> getDataset(List<Pair<String, String>> pathPairs) {
    return getDataset(pathPairs, null);
  }

}
