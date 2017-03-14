package edu.stanford.nlp.sempre.tables.serialize;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.FuzzyMatchFn.FuzzyMatchFnMode;
import edu.stanford.nlp.sempre.tables.TableKnowledgeGraph;
import fig.basic.*;
import fig.exec.Execution;

/**
 * Generate TSV files containing information about fuzzy matched objects.
 *
 * @author ppasupat
 */
public class TaggedFuzzyGenerator extends TSVGenerator implements Runnable {

  public static void main(String[] args) {
    Execution.run(args, "TaggedFuzzyGeneratorMain", new TaggedFuzzyGenerator(),
        Master.getOptionsParser());
  }

  @Override
  public void run() {
    // Read dataset
    LogInfo.begin_track("Dataset.read");
    for (Pair<String, String> pathPair : Dataset.opts.inPaths) {
      String group = pathPair.getFirst();
      String path = pathPair.getSecond();
      // Open output file
      String filename = Execution.getFile("fuzzy-" + group + ".tsv");
      out = IOUtils.openOutHard(filename);
      dump(FIELDS);
      // Read LispTrees
      LogInfo.begin_track("Reading %s", path);
      int maxExamples = Dataset.getMaxExamplesForGroup(group);
      Iterator<LispTree> trees = LispTree.proto.parseFromFile(path);
      // Go through the examples
      int n = 0;
      while (n < maxExamples) {
        // Format: (example (id ...) (utterance ...) (targetFormula ...) (targetValue ...))
        LispTree tree = trees.next();
        if (tree == null) break;
        if (tree.children.size() < 2 || !"example".equals(tree.child(0).value)) {
          if ("metadata".equals(tree.child(0).value)) continue;
          throw new RuntimeException("Invalid example: " + tree);
        }
        Example ex = Example.fromLispTree(tree, path + ":" + n);
        ex.preprocess();
        LogInfo.logs("Example %s (%d): %s => %s", ex.id, n, ex.getTokens(), ex.targetValue);
        n++;
        dumpExample(ex, tree);
      }
      out.close();
      LogInfo.logs("Finished dumping to %s", filename);
      LogInfo.end_track();
    }
    LogInfo.end_track();
  }

  private static final String[] FIELDS = new String[] {
    "id", "type", "start", "end", "phrase", "fragment"
  };

  @Override
  protected void dump(String... stuff) {
    assert stuff.length == FIELDS.length;
    super.dump(stuff);
  }

  private void dumpExample(Example ex, LispTree tree) {
    TableKnowledgeGraph graph = (TableKnowledgeGraph) (ex.context.graph);
    int n = ex.numTokens();
    List<String> tokens = ex.getTokens();
    for (int i = 0; i < n; i++) {
      StringBuilder sb = new StringBuilder(ex.token(i));
      for (int j = i; j < n; j++) {
        String term = sb.toString();
        for (Formula formula : graph.getFuzzyMatchedFormulas(tokens, i, j + 1, FuzzyMatchFnMode.ENTITY)) {
          LogInfo.logs("Found ENT %s -> %s", term, formula);
          dump(ex.id, "ENT", "" + i, "" + (j + 1), term, formula.toString());
        }
        for (Formula formula : graph.getFuzzyMatchedFormulas(tokens, i, j + 1, FuzzyMatchFnMode.BINARY)) {
          LogInfo.logs("Found REL %s -> %s", term, formula);
          dump(ex.id, "REL", "" + i, "" + (j + 1), term, formula.toString());
        }
        if (j + 1 < n)
          sb.append(" ").append(ex.token(j + 1));
      }
    }
  }
  
}
