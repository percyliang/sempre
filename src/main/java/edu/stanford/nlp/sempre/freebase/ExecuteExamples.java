package edu.stanford.nlp.sempre.freebase;

import edu.stanford.nlp.sempre.*;

import com.google.common.base.Function;
import fig.basic.LispTree;
import fig.basic.LogInfo;
import fig.basic.Option;
import fig.exec.Execution;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Executes all examples and reports any badness.
 * Was used to prepare the Free917 dataset.
 *
 * @author Percy Liang
 */
public class ExecuteExamples implements Runnable {
  @Option(required = true, gloss = "Input path to examples to canonicalize")
  public List<String> examplesPaths;

  SparqlExecutor executor;
  Map<Formula, Executor.Response> cache = new HashMap<Formula, Executor.Response>();

  private boolean queryReturnsResults(Formula formula) {
    // If counting, look inside to make sure the actual set is non-empty.
    if (formula instanceof AggregateFormula)
      formula = ((AggregateFormula) formula).child;

    Executor.Response response = cache.get(formula);
    if (response == null)
      cache.put(formula, response = executor.execute(formula, null));
    if (!(response.value instanceof ListValue) ||
        ((ListValue) response.value).values.size() == 0) {
      LogInfo.logs("BAD QUERY: %s => %s", formula, response.value);
      return false;
    } else {
      LogInfo.logs("GOOD QUERY: %s => %s", formula, response.value);
      return true;
    }
  }

  // Test each individual nested NameFormula.
  public Formula test(Formula formula) {
    return formula.map(
        new Function<Formula, Formula>() {
          public Formula apply(Formula formula) {
            String name = Formulas.getNameId(formula);
            if (name != null) {
              if (name.startsWith("!")) name = name.substring(1);
              queryReturnsResults(Formulas.newNameFormula(name));
            }
            return null;
          }
        });
  }

  public void run() {
    executor = new SparqlExecutor();
    for (String path : examplesPaths) {
      Iterator<LispTree> it = LispTree.proto.parseFromFile(path);
      while (it.hasNext()) {
        LispTree tree = it.next();
        if (!"example".equals(tree.child(0).value))
          throw new RuntimeException("Bad: " + tree);
        for (int i = 1; i < tree.children.size(); i++) {
          if ("targetFormula".equals(tree.child(i).child(0).value)) {
            Formula formula = Formulas.fromLispTree(tree.child(i).child(1));
            formula = test(formula);
            queryReturnsResults(formula);
          }
        }
      }
    }
  }

  public static void main(String[] args) {
    Execution.run(args, new ExecuteExamples(), "SparqlExecutor", SparqlExecutor.opts);
  }
}
