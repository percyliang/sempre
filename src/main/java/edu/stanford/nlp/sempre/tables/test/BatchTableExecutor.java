package edu.stanford.nlp.sempre.tables.test;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.TableKnowledgeGraph;
import edu.stanford.nlp.sempre.tables.TableValueEvaluator;
import edu.stanford.nlp.sempre.tables.lambdadcs.LambdaDCSExecutor;
import fig.basic.*;
import fig.exec.Execution;

/**
 * Execute the specified logical forms on the specified WikiTableQuestions context.
 *
 * @author ppasupat
 */
public class BatchTableExecutor implements Runnable {
  public static class Options {
    @Option(gloss = "TSV file containing table contexts and logical forms")
    public String batchInput;
    @Option(gloss = "Datasets for mapping example IDs to contexts")
    public List<String> batchDatasets = Arrays.asList("lib/data/tables/data/training.examples");
  }
  public static Options opts = new Options();

  public static void main(String[] args) {
    Execution.run(args, "BatchTableExecutorMain", new BatchTableExecutor(), Master.getOptionsParser());
  }

  @Override
  public void run() {
    if (opts.batchInput == null || opts.batchInput.isEmpty()) {
      LogInfo.logs("*******************************************************************************");
      LogInfo.logs("USAGE: ./run @mode=tables @class=execute -batchInput <filename>");
      LogInfo.logs("");
      LogInfo.logs("Input file format: Each line has something like");
      LogInfo.logs("  nt-218    [tab]   (count (fb:type.object.type fb:type.row))");
      LogInfo.logs("or");
      LogInfo.logs("  csv/204-csv/23.csv    [tab]   (count (fb:type.object.type fb:type.row))");
      LogInfo.logs("");
      LogInfo.logs("Results will also be printed to state/execs/___.exec/denotations.tsv");
      LogInfo.logs("Output format:");
      LogInfo.logs("  nt-218    [tab]   (count (fb:type.object.type fb:type.row))   [tab]   (list (number 10))   [tab]   false");
      LogInfo.logs("where the last column indicates whether the answer is consistent with the target answer");
      LogInfo.logs("(only available when the first column is nt-___)");
      LogInfo.logs("*******************************************************************************");
      System.exit(1);
    }
    LambdaDCSExecutor executor = new LambdaDCSExecutor();
    ValueEvaluator evaluator = new TableValueEvaluator();
    try {
      BufferedReader reader = IOUtils.openIn(opts.batchInput);
      PrintWriter output = IOUtils.openOut(Execution.getFile("denotations.tsv"));
      String line;
      while ((line = reader.readLine()) != null) {
        String[] tokens = line.split("\t");
        String answer;
        try {
          Formula formula = Formula.fromString(tokens[1]);
          if (tokens[0].startsWith("csv")) {
            TableKnowledgeGraph graph = TableKnowledgeGraph.fromFilename(tokens[0]);
            ContextValue context = new ContextValue(graph);
            Value denotation = executor.execute(formula, context).value;
            if (denotation instanceof ListValue)
              denotation = addOriginalStrings((ListValue) denotation, graph);
            answer = denotation.toString();
          } else {
            Example ex = exIdToExample(tokens[0]);
            Value denotation = executor.execute(formula, ex.context).value;
            if (denotation instanceof ListValue)
              denotation = addOriginalStrings((ListValue) denotation, (TableKnowledgeGraph) ex.context.graph);
            answer = denotation.toString();
            boolean correct = evaluator.getCompatibility(ex.targetValue, denotation) == 1.;
            answer = denotation.toString() + "\t" + correct;
          }
        } catch (Exception e) {
          answer = "ERROR: " + e;
        }
        System.out.printf("%s\t%s\t%s\n", tokens[0], tokens[1], answer);
        output.printf("%s\t%s\t%s\n", tokens[0], tokens[1], answer);
      }
      reader.close();
      output.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private Map<String, Object> exIdToExampleMap;

  private Example exIdToExample(String exId) {
    if (exIdToExampleMap == null) {
      exIdToExampleMap = new HashMap<>();
      try {
        for (String filename : opts.batchDatasets) {
          BufferedReader reader = IOUtils.openIn(filename);
          String line;
          while ((line = reader.readLine()) != null) {
            LispTree tree = LispTree.proto.parseFromString(line);
            if (!"id".equals(tree.child(1).child(0).value))
              throw new RuntimeException("Malformed example: " + line);
            exIdToExampleMap.put(tree.child(1).child(1).value, tree);
          }
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    Object obj = exIdToExampleMap.get(exId);
    if (obj == null) return null;
    Example ex;
    if (obj instanceof LispTree) {
      ex = Example.fromLispTree((LispTree) obj, exId);
      ex.preprocess();
      exIdToExampleMap.put(exId, ex);
    } else {
      ex = (Example) obj;
    }
    return ex;
  }

  ListValue addOriginalStrings(ListValue answers, TableKnowledgeGraph graph) {
    List<Value> values = new ArrayList<>();
    for (Value value : answers.values) {
      if (value instanceof NameValue) {
        NameValue name = (NameValue) value;
        if (name.description == null)
          value = new NameValue(name.id, graph.getOriginalString(((NameValue) value).id));
      }
      values.add(value);
    }
    return new ListValue(values);
  }

}
