package edu.stanford.nlp.sempre.tables.lambdadcs;

import java.util.*;

import fig.basic.*;
import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.corenlp.CoreNLPAnalyzer;
import edu.stanford.nlp.sempre.tables.TableKnowledgeGraph;

import org.testng.annotations.Test;

/**
 * Test LambdaDCSExecutor: Execute a Formula on a given context graph.
 *
 * @author ppasupat
 */
public class LambdaDCSExecutorTest {

  // ============================================================
  // Value Checker (copied from SparqlExectutorTest)
  // ============================================================

  public static abstract class ValuesChecker {
    void checkValue(Value value) {
      if (value instanceof ListValue)
        checkList(new HashSet<>(((ListValue) value).values));
      else if (value instanceof PairListValue)
        checkPairList(new HashSet<>(((PairListValue) value).pairs));
      else
        throw new RuntimeException("The answer is not a ListValue or a PairListValue.");
    }
    void checkList(Collection<Value> values) {
      // Override this
      throw new RuntimeException("Got a ListValue; expected something else: " + values);
    }
    void checkPairList(Collection<Pair<Value, Value>> pairs) {
      // Override this
      throw new RuntimeException("Got a PairListValue; expected something else: " + pairs);
    }
  }

  public static ValuesChecker size(final int expectedNumResults) {
    return new ValuesChecker() {
      public void checkList(Collection<Value> values) {
        if (values.size() != expectedNumResults)
          throw new RuntimeException("Expected " + expectedNumResults + " results, but got " + values.size() + ": " + values);
      }
      public void checkPairList(Collection<Pair<Value, Value>> pairs) {
        if (pairs.size() != expectedNumResults)
          throw new RuntimeException("Expected " + expectedNumResults + " results, but got " + pairs.size() + ": " + pairs);
      }
    };
  }

  public static ValuesChecker sizeAtLeast(final int expectedNumResults) {
    return new ValuesChecker() {
      public void checkList(Collection<Value> values) {
        if (values.size() < expectedNumResults)
          throw new RuntimeException("Expected at least " + expectedNumResults + " results, but got " + values.size() + ": " + values);
      }
      public void checkPairList(Collection<Pair<Value, Value>> pairs) {
        if (pairs.size() < expectedNumResults)
          throw new RuntimeException("Expected at least " + expectedNumResults + " results, but got " + pairs.size() + ": " + pairs);
      }
    };
  }

  public static ValuesChecker matches(String expected) {
    final Value expectedValue = Value.fromString(expected);
    return new ValuesChecker() {
      public void checkList(Collection<Value> values) {
        if (values.size() != 1 || !(new ArrayList<>(values)).get(0).equals(expectedValue))
          throw new RuntimeException("Expected " + expectedValue + ", but got " + values);
      }
    };
  }

  public static ValuesChecker matchesAll(String... expected) {
    final List<Value> expectedValues = new ArrayList<>();
    for (String x : expected) expectedValues.add(Value.fromString(x));
    return new ValuesChecker() {
      public void checkList(Collection<Value> values) {
        if (values.size() != expectedValues.size() || !expectedValues.containsAll(values))
          throw new RuntimeException("Expected " + new ListValue(expectedValues) + ", but got " + values);
      }
    };
  }

  public static ValuesChecker regexMatches(final String expectedPattern) {
    return new ValuesChecker() {
      public void checkList(Collection<Value> values) {
        if (values.size() != 1 || !(new ArrayList<>(values)).get(0).toString().matches(expectedPattern))
          throw new RuntimeException("Expected " + expectedPattern + ", but got " + values);
      }
    };
  }

  // ============================================================
  // Processing
  // ============================================================

  LambdaDCSExecutor executor = new LambdaDCSExecutor();

  protected static void runFormula(LambdaDCSExecutor executor, String formula, KnowledgeGraph graph) {
    runFormula(executor, formula, graph, sizeAtLeast(0));
  }

  protected static void runFormula(LambdaDCSExecutor executor, String formula, KnowledgeGraph graph, ValuesChecker checker) {
    ContextValue context = new ContextValue(graph);
    LambdaDCSExecutor.opts.verbose = 5;
    LambdaDCSExecutor.opts.executeBinary = true;
    LogInfo.begin_track("formula: %s", formula);
    Executor.Response response = executor.execute(Formulas.fromLispTree(LispTree.proto.parseFromString(formula)), context);
    LogInfo.logs("RESULT: %s", response.value);
    LogInfo.end_track();
    if (checker != null)
      checker.checkValue(response.value);
  }

  protected static KnowledgeGraph getKnowledgeGraph(String name) {
    if ("simple".equals(name)) {
      return KnowledgeGraph.fromLispTree(LispTree.proto.parseFromString(
          "(graph NaiveKnowledgeGraph ((number 1) (number 2) (number 3)))"));
    } else if ("prez".equals(name)) {
      return KnowledgeGraph.fromLispTree(LispTree.proto.parseFromString(
          "(graph NaiveKnowledgeGraph " +
              "(fb:en.barack_obama fb:people.person.place_of_birth fb:en.honolulu)" +
              "(fb:en.barack_obama fb:people.person.profession fb:en.politician)" +
              "(fb:en.barack_obama fb:people.person.weight_kg (number 82))" +
              "(fb:en.george_w_bush fb:people.person.place_of_birth fb:en.new_haven)" +
              "(fb:en.george_w_bush fb:people.person.profession fb:en.politician)" +
              "(fb:en.george_w_bush fb:people.person.weight_kg (number 86))" +
              "(fb:en.bill_clinton fb:people.person.place_of_birth fb:en.hope_arkansas)" +
              "(fb:en.bill_clinton fb:people.person.profession fb:en.lawyer)" +
              "(fb:en.bill_clinton fb:people.person.profession fb:en.politician)" +
              "(fb:en.bill_clinton fb:people.person.weight_kg (number 100))" +
              "(fb:en.nicole_kidman fb:people.person.place_of_birth fb:en.honolulu)" +
              "(fb:en.nicole_kidman fb:people.person.profession fb:en.actor)" +
              "(fb:en.nicole_kidman fb:people.person.weight_kg (number 58))" +
              "(fb:en.morgan_freeman fb:people.person.place_of_birth fb:en.memphis)" +
              "(fb:en.morgan_freeman fb:people.person.profession fb:en.actor)" +
              "(fb:en.morgan_freeman fb:people.person.weight_kg (number 91))" +
              "(fb:en.ronald_reagan fb:people.person.place_of_birth fb:en.tampico)" +
              "(fb:en.ronald_reagan fb:people.person.profession fb:en.politician)" +
              "(fb:en.ronald_reagan fb:people.person.weight_kg (number 82))" +
              "(fb:en.honolulu fb:location.location.containedby fb:en.hawaii)" +
              "(fb:en.memphis fb:location.location.containedby fb:en.tennessee)" +
              "(fb:en.new_haven fb:location.location.containedby fb:en.connecticut)" +
              "(fb:en.hope_arkansas fb:location.location.containedby fb:en.arkansas)" +
              "(fb:en.tampico fb:location.location.containedby fb:en.illinois)" +
          ")"));
    } else if ("csv".equals(name)) {
      return TableKnowledgeGraph.fromFilename("tables/toy-examples/nikos_machlas.csv");
    } else if ("csv2".equals(name)) {
      return TableKnowledgeGraph.fromFilename("tables/toy-examples/204-495.tsv");
    } else if ("csv3".equals(name)) {
      return TableKnowledgeGraph.fromFilename("tables/toy-examples/203-839.tsv");
    }
    throw new RuntimeException("Unknown graph name: " + name);
  }

  // ============================================================
  // Actual Tests
  // ============================================================

  @Test(groups = "lambdaSimple") public void lambdaOnGraphDummyTest() {
    KnowledgeGraph graph = getKnowledgeGraph("simple");
    runFormula(executor, "(number 3)", graph, matches("(number 3)"));
  }

  @Test(groups = "lambdaPrez") public void lambdaOnGraphBasicTest() {
    KnowledgeGraph graph = getKnowledgeGraph("prez");
    runFormula(executor, "(fb:people.person.place_of_birth fb:en.honolulu)",
        graph, matchesAll("(name fb:en.barack_obama)", "(name fb:en.nicole_kidman)"));
    runFormula(executor, "(!fb:people.person.place_of_birth (fb:people.person.place_of_birth fb:en.honolulu))",
        graph, matches("(name fb:en.honolulu)"));
    runFormula(executor, "(!fb:people.person.place_of_birth fb:en.barack_obama)",
        graph, matches("(name fb:en.honolulu)"));
    runFormula(executor, "(and (fb:people.person.place_of_birth fb:en.honolulu) (fb:people.person.profession fb:en.actor))",
        graph, matches("(name fb:en.nicole_kidman)"));
    runFormula(executor, "(or (fb:people.person.place_of_birth fb:en.honolulu) (fb:people.person.profession fb:en.actor))",
        graph, matchesAll("(name fb:en.barack_obama)", "(name fb:en.nicole_kidman)", "(name fb:en.morgan_freeman)"));
    runFormula(executor, "(count (or (fb:people.person.place_of_birth fb:en.honolulu) (fb:people.person.profession fb:en.actor)))",
        graph, matches("(number 3)"));
  }

  @Test(groups = "lambdaPrez") public void lambdaOnGraphInfiniteTest() {
    KnowledgeGraph graph = getKnowledgeGraph("prez");
    runFormula(executor, "(!fb:people.person.place_of_birth *)",
        graph, size(5));
    runFormula(executor, "(and * (fb:people.person.place_of_birth fb:en.honolulu))",
        graph, matchesAll("(name fb:en.barack_obama)", "(name fb:en.nicole_kidman)"));
    runFormula(executor, "(max (!fb:people.person.weight_kg *))",
        graph, matches("(number 100)"));
    runFormula(executor, "(sum (!fb:people.person.weight_kg *))",
        graph, matches("(number 499)"));
    runFormula(executor, "(argmax 1 1 * fb:people.person.weight_kg)",
        graph, matches("(name fb:en.bill_clinton)"));
    runFormula(executor, "(fb:people.person.weight_kg (> (number 95)))",
        graph, matches("(name fb:en.bill_clinton)"));
    runFormula(executor, "(fb:people.person.weight_kg (!= (!fb:people.person.weight_kg fb:en.barack_obama)))",
        graph, size(4));
    runFormula(executor, "(fb:people.person.weight_kg ((reverse >) (number 82)))",
        graph, matchesAll("(name fb:en.barack_obama)", "(name fb:en.ronald_reagan)", "(name fb:en.nicole_kidman)"));
    runFormula(executor, "(fb:people.person.weight_kg (and (< (number 100)) (> (number 90))))",
        graph, matches("(name fb:en.morgan_freeman)"));
  }

  @Test(groups = "lambdaPrez") public void lambdaOnGraphLambdaTest() {
    KnowledgeGraph graph = getKnowledgeGraph("prez");
    runFormula(executor, "((lambda x (fb:people.person.place_of_birth (var x))) fb:en.honolulu)",
        graph, matchesAll("(name fb:en.barack_obama)", "(name fb:en.nicole_kidman)"));
    runFormula(executor, "(argmax 1 1 (fb:location.location.containedby *) (reverse (lambda x (count (fb:people.person.place_of_birth (var x))))))",
        graph, matches("(name fb:en.honolulu)"));
    runFormula(executor, "(and (!fb:people.person.place_of_birth *) ((reverse (lambda x (fb:people.person.place_of_birth (var x)))) fb:en.barack_obama))",
        graph, matches("(name fb:en.honolulu)"));
    runFormula(executor, "(and ((reverse (lambda x (fb:people.person.place_of_birth (var x)))) fb:en.barack_obama) (!fb:people.person.place_of_birth *))",
        graph, matches("(name fb:en.honolulu)"));
    runFormula(executor, "((reverse (lambda x (fb:people.person.place_of_birth (var x)))) fb:en.barack_obama)",
        graph, matches("(name fb:en.honolulu)"));
  }

  @Test(groups = "floating") public void lambdaOnGraphFloatingLambdaTest() {
    KnowledgeGraph graph = getKnowledgeGraph("prez");
    runFormula(executor, "(lambda x (fb:people.person.place_of_birth (var x)))", graph);
    runFormula(executor, "(lambda x ((reverse fb:people.person.place_of_birth) (var x)))", graph);
    runFormula(executor, "(lambda x (count ((reverse fb:people.person.place_of_birth) (var x))))", graph);
  }

  @Test(groups = "lambdaCSV") public void lambdaOnGraphCSVTest() {
    KnowledgeGraph graph = getKnowledgeGraph("csv");
    runFormula(executor, "(number 3)", graph, matches("(number 3)"));
    runFormula(executor, "(!fb:row.row.score (fb:row.row.opponent fb:cell_opponent.austria))",
        graph, matches("(name fb:cell_score.1_2)"));
    runFormula(executor, "(count (fb:row.row.result fb:cell_result.win))",
        graph, matches("(number 16)"));
    // Depending on tie-breaking, one of these will be correct
    try {
      // Return all top ties
      runFormula(executor, "(argmax 1 1 (!fb:row.row.opponent (fb:type.object.type fb:type.row)) "
          + "(reverse (lambda x (count (fb:row.row.opponent (var x))))))",
          graph, size(5));
    } catch (Exception e) {
      // Return only one item
      runFormula(executor, "(count (fb:row.row.opponent (argmax 1 1 (!fb:row.row.opponent (fb:type.object.type fb:type.row)) "
          + "(reverse (lambda x (count (fb:row.row.opponent (var x))))))))",
          graph, matches("(number 2)"));
    }
  }

  @Test(groups = "lambdaCSV2") public void lambdaOnGraphCSV2Test() {
    KnowledgeGraph graph = getKnowledgeGraph("csv2");
    runFormula(executor,
        "(and (!= (and (!= fb:cell_venue.away) fb:cell_venue.home)) ((reverse fb:row.row.opponent) (fb:row.row.index (- (number 2) (number 1)))))",
        graph, matches("(name fb:cell_opponent.derby_county)"));
  }

  @Test(groups = "lambdaCSV3") public void lambdaOnGraphCSV3Test() {
    LanguageAnalyzer.setSingleton(new CoreNLPAnalyzer());
    KnowledgeGraph graph = getKnowledgeGraph("csv3");
    runFormula(executor,
        "(count (fb:type.object.type fb:type.row))",
        graph, matches("(number 21)"));
    runFormula(executor,
        "(count (fb:row.row.opened (fb:cell.cell.date (< (date 1926 -1 -1)))))",
        graph, matches("(number 6)"));
    runFormula(executor,
        "(- (number 1926) (argmax (number 1) (number 1) ((reverse fb:cell.cell.number) "
        + "(or (or (or fb:cell_closed.1920 fb:cell_closed.1925) fb:cell_opened.1926) fb:cell_closed.1946)) "
        + "(reverse (lambda x (sum ((reverse fb:cell.cell.number) (fb:cell.cell.number (var x))))))))",
        graph, matches("(number 6)"));
  }
}
