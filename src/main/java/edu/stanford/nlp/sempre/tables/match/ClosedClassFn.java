package edu.stanford.nlp.sempre.tables.match;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.*;
import fig.basic.LispTree;
import fig.basic.LogInfo;
import fig.basic.Option;

/**
 * Generate the closed class entities from the table, including:
 * - [generic] Generic common entities (e.g., null = empty cell)
 * - [column] If the number of unique entities in a column is <= the limit,
 *     and there is at least one repeated entity, generate all entities in the column.
 *
 * @author ppasupat
 */
public class ClosedClassFn extends SemanticFn {
  public static class Options {
    @Option(gloss = "verbosity") public int verbose = 0;
    @Option(gloss = "maximum number of unique entities in a column to be considered a closed class")
    public int maxNumClosedClassEntities = 3;
  }
  public static Options opts = new Options();

  public enum ClosedClassFnMode {
    GENERIC, COLUMN,
  }
  protected ClosedClassFnMode mode;

  public void init(LispTree tree) {
    super.init(tree);
    String value = tree.child(1).value;
    if ("generic".equals(value)) this.mode = ClosedClassFnMode.GENERIC;
    else if ("column".equals(value)) this.mode = ClosedClassFnMode.COLUMN;
    else throw new RuntimeException("Invalid argument: " + value);
  }

  @Override
  public DerivationStream call(Example ex, Callable c) {
    return new LazyClosedClassFnDerivs(ex, c, mode);
  }

  // ============================================================
  // Derivation
  // ============================================================

  public static class LazyClosedClassFnDerivs extends MultipleDerivationStream {
    final Example ex;
    final TableKnowledgeGraph graph;
    final Callable c;
    final ClosedClassFnMode mode;

    int index = 0;
    List<Formula> formulas;

    public LazyClosedClassFnDerivs(Example ex, Callable c, ClosedClassFnMode mode) {
      this.ex = ex;
      this.graph = (TableKnowledgeGraph) ex.context.graph;
      this.c = c;
      this.mode = mode;
    }

    @Override
    public Derivation createDerivation() {
      // Compute the formulas if not computed yet
      if (formulas == null) {
        switch (mode) {
          case GENERIC:
            formulas = new ArrayList<>(createGenericFormulas());
            break;
          case COLUMN:
            formulas = new ArrayList<>(createColumnFormulas());
            break;
          default:
            throw new RuntimeException("Invalid mode: " + mode);
        }
      }

      // Use the next formula to create a derivation
      if (index >= formulas.size()) return null;
      Formula formula = formulas.get(index++);
      SemType type = TypeInference.inferType(formula);

      return new Derivation.Builder().withCallable(c)
          .formula(formula).type(type).createDerivation();
    }

    protected Collection<Formula> createGenericFormulas() {
      List<Formula> formulas = new ArrayList<>();
      // Find out if the table has a null cell
      for (TableColumn column : graph.columns) {
        for (TableCell cell : column.children) {
          if (cell.properties.id.endsWith(".null")) {
            formulas.add(new ValueFormula<>(cell.properties.nameValue));
            break;
            
          }
        }
      }
      if (ClosedClassFn.opts.verbose >= 2) {
        LogInfo.begin_track("ClosedClassFn(generic):");
        for (Formula formula : formulas)
          LogInfo.logs("%s", formula);
        LogInfo.end_track();
      }
      return formulas;
    }

    protected Collection<Formula> createColumnFormulas() {
      Set<Formula> formulas = new HashSet<>();
      // Process the columns separately
      for (TableColumn column : graph.columns) {
        boolean hasRepeats = false;
        Set<Value> values = new HashSet<>();
        for (TableCell cell : column.children) {
          if (cell.properties.id.endsWith(".null")) continue;
          if (values.contains(cell.properties.nameValue))
            hasRepeats = true;
          else
            values.add(cell.properties.nameValue);
        }
        if (values.size() <= ClosedClassFn.opts.maxNumClosedClassEntities && hasRepeats) {
          for (Value value : values) {
            formulas.add(new ValueFormula<>(value));
          }
        }
      }
      if (ClosedClassFn.opts.verbose >= 2) {
        LogInfo.begin_track("ClosedClassFn(column):");
        for (Formula formula : formulas)
          LogInfo.logs("%s", formula);
        LogInfo.end_track();
      }
      return formulas;
    }

  }

}
