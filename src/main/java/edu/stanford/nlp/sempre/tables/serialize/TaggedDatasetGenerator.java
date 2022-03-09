package edu.stanford.nlp.sempre.tables.serialize;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.TableKnowledgeGraph;
import fig.basic.*;
import fig.exec.Execution;

/**
 * Generate TSV files containing CoreNLP tags of the datasets.
 *
 * Field descriptions:
 * - id:          unique ID of the example
 * - utterance:   the question in its original format
 * - context:     the table used to answer the question
 * - targetValue: the answer in its original format, possibly a `||`-separated list
 * - tokens:      the question, tokenized
 * - lemmaTokens: the question, tokenized and lemmatized
 * - posTags:     the part of speech tag of each token
 * - nerTags:     the name entity tag of each token
 * - nerValues:   if the NER tag is numerical or temporal, the value of that
 *                NER span will be listed here
 * - targetCanon: the answer, canonicalized
 * - targetCanonType: type of the canonicalized answer (number, date, or string)
 *
 * @author ppasupat
 */
public class TaggedDatasetGenerator extends TSVGenerator implements Runnable {

  public static void main(String[] args) {
    Execution.run(args, "TaggedDatasetGeneratorMain", new TaggedDatasetGenerator(),
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
      String filename = Execution.getFile("tagged-" + group + ".tsv");
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
    "id", "utterance", "context", "targetValue",
    "tokens", "lemmaTokens", "posTags", "nerTags", "nerValues",
    "targetCanon", "targetCanonType",
  };

  @Override
  protected void dump(String... stuff) {
    assert stuff.length == FIELDS.length;
    super.dump(stuff);
  }

  private void dumpExample(Example ex, LispTree tree) {
    String[] fields = new String[FIELDS.length];
    // Get original information from the LispTree
    for (int i = 1; i < tree.children.size(); i++) {
      LispTree arg = tree.child(i);
      String label = arg.child(0).value;
      if ("id".equals(label)) {
        fields[0] = serialize(arg.child(1).value);
      } else if ("utterance".equals(label)) {
        fields[1] = serialize(arg.child(1).value);
      } else if ("targetValue".equals(label) || "targetValues".equals(label)) {
        if (arg.children.size() != 2)
          throw new RuntimeException("Expect one target value");
        fields[3] = serialize(Values.fromLispTree(arg.child(1)));
      }
    }
    // Other information come from Example
    fields[2] = serialize(((TableKnowledgeGraph) ex.context.graph).filename.replace("lib/data/WikiTableQuestions/", ""));
    fields[4] = serialize(ex.languageInfo.tokens);
    fields[5] = serialize(ex.languageInfo.lemmaTokens);
    fields[6] = serialize(ex.languageInfo.posTags);
    fields[7] = serialize(ex.languageInfo.nerTags);
    fields[8] = serialize(ex.languageInfo.nerValues);
    // Information from target value
    fields[9] = serialize(ex.targetValue);
    Value targetValue = ex.targetValue;
    if (targetValue instanceof ListValue)
      targetValue = ((ListValue) targetValue).values.get(0);
    if (targetValue instanceof NumberValue)
      fields[10] = "number";
    else if (targetValue instanceof DateValue)
      fields[10] = "date";
    else
      fields[10] = "string";
    dump(fields);
  }
}
