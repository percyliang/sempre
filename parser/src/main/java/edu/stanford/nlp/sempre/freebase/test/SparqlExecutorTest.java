package edu.stanford.nlp.sempre.freebase.test;

import java.util.List;
import edu.stanford.nlp.sempre.Executor;
import edu.stanford.nlp.sempre.Formulas;
import edu.stanford.nlp.sempre.Value;
import edu.stanford.nlp.sempre.ListValue;
import edu.stanford.nlp.sempre.freebase.SparqlExecutor;
import fig.basic.LispTree;
import fig.basic.LogInfo;
import org.testng.annotations.Test;

/**
 * Test execution of Formulas on a SPARQL server.
 * @author Percy Liang
 */
public class SparqlExecutorTest {
  interface ValuesChecker {
    void checkValues(List<Value> values);
  }

  public static ValuesChecker size(final int expectedNumResults) {
    return new ValuesChecker() {
      public void checkValues(List<Value> values) {
        if (values.size() != expectedNumResults)
          throw new RuntimeException("Expected " + expectedNumResults + " results, but got " + values.size() + ": " + values);
      }
    };
  }

  public static ValuesChecker sizeAtLeast(final int expectedNumResults) {
    return new ValuesChecker() {
      public void checkValues(List<Value> values) {
        if (values.size() < expectedNumResults)
          throw new RuntimeException("Expected at least " + expectedNumResults + " results, but got " + values.size() + ": " + values);
      }
    };
  }

  public static ValuesChecker matches(String expected) {
    final Value expectedValue = Value.fromString(expected);
    return new ValuesChecker() {
      public void checkValues(List<Value> values) {
        if (values.size() != 1 || !values.get(0).equals(expectedValue))
          throw new RuntimeException("Expected " + expectedValue + ", but got " + values);
      }
    };
  }

  public static ValuesChecker regexMatches(final String expectedPattern) {
    return new ValuesChecker() {
      public void checkValues(List<Value> values) {
        if (values.size() != 1 || !values.get(0).toString().matches(expectedPattern))
          throw new RuntimeException("Expected " + expectedPattern + ", but got " + values);
      }
    };
  }

  protected static void runFormula(SparqlExecutor executor, String formula) {
    runFormula(executor, formula, sizeAtLeast(0));
  }

  protected static void runFormula(SparqlExecutor executor, String formula, ValuesChecker checker) {
    Executor.Response response = executor.execute(Formulas.fromLispTree(LispTree.proto.parseFromString(formula)), null);
    LogInfo.logs("RESULT: %s", response.value);
    checker.checkValues(((ListValue) response.value).values);
  }

  SparqlExecutor executor = new SparqlExecutor();

  public SparqlExecutorTest() {
    SparqlExecutor.opts.endpointUrl = System.getProperty("sparqlserver");
    // Hard-coding not ideal.
    if (SparqlExecutor.opts.endpointUrl == null)
      SparqlExecutor.opts.endpointUrl = "http://freebase.cloudapp.net:3093/sparql";
    SparqlExecutor.opts.verbose = 3;
  }

  @Test(groups = "sparql") public void sparqlTrivial() {
    runFormula(executor, "fb:en.barack_obama", size(1));
    runFormula(executor, "(number 3)", size(1));
  }

  @Test(groups = "sparql") public void sparqlJoin() {
    runFormula(executor, "(!fb:people.person.place_of_birth fb:en.barack_obama)", matches("(name fb:en.honolulu)"));  // place of birth of Obama
    runFormula(executor, "(!fb:people.person.date_of_birth fb:en.barack_obama)", matches("(date 1961 8 4)"));  // date of birth of Obama
    runFormula(executor, "(!fb:common.topic.alias fb:en.barack_obama)", sizeAtLeast(2));  // Names of Obama
    runFormula(executor, "(!fb:people.person.children fb:en.barack_obama)", size(2));  // children of Obama
    runFormula(executor, "(!fb:people.marriage.spouse (!fb:people.person.spouse_s fb:en.barack_obama))", size(2));  // spouse of Obama (will include Barack and Michelle)
  }

  @Test(groups = "sparql") public void sparqlIntersect() {
    runFormula(executor, "(and (fb:type.object.type fb:type.datetime) (!fb:people.person.date_of_birth fb:en.barack_obama))", size(1));  // date of birth of Obama
    runFormula(executor, "(and (fb:type.object.type fb:people.person) (!fb:people.person.parents fb:en.barack_obama))", size(2));  // parents of Obama
  }

  @Test(groups = "sparql") public void sparqlLambda() {
    runFormula(executor, "((lambda x (!fb:people.person.parents (var x))) fb:en.barack_obama)", size(2));  // parents of Obama
    runFormula(executor, "((lambda x (!fb:people.marriage.spouse (!fb:people.person.spouse_s (var x)))) fb:en.barack_obama)", size(2));  // spouse of Barack Obama (includes himself)
  }

  @Test(groups = "sparql") public void sparqlReverse() {
    runFormula(executor, "((reverse fb:people.person.parents) fb:en.barack_obama)", size(2));  // parents of Obama
    runFormula(executor, "((reverse (reverse fb:people.person.parents)) fb:en.barack_obama)", size(2));  // children of Obama
    runFormula(executor, "((reverse (lambda x (fb:people.person.parents (fb:people.person.parents (var x))))) fb:en.barack_obama)", size(4));  // grandparents of Obama
  }

  @Test(groups = "sparql") public void sparqlUnion() {
    runFormula(executor, "(or (!fb:people.person.children fb:en.barack_obama) (!fb:people.person.parents fb:en.barack_obama))", size(4));  // children or parents of Obama
    runFormula(executor, "(or fb:en.barack_obama fb:en.michelle_obama)", size(2));  // Barack and Michelle Obama

    // TODO(pliang): doesn't work
    // runFormula(executor, "(or (number 3) (number 5))", size(2));  // 3 or 5
  }

  @Test(groups = "sparql") public void sparqlNot() {
    runFormula(executor, "(and (!fb:people.person.parents fb:en.barack_obama) (not (fb:people.person.gender fb:en.male)))", size(1));  // parents of Obama who are not male
  }

  @Test(groups = "sparql") public void sparqlRelations() {
    runFormula(executor, "(and (fb:type.object.type fb:location.citytown) (fb:type.object.name (string \"Palo Alto\")))", size(8));  // cities called "Palo Alto"
    runFormula(executor, "(and (fb:type.object.type fb:location.us_state) (fb:type.object.name (STRSTARTS (string A))))", size(4));  // cities whose names begin with "A"
    runFormula(executor, "(and (!fb:people.person.parents fb:en.barack_obama) (!= fb:en.ann_dunham))", size(1));  // parents of Obama who are not Ann Dunham
    runFormula(executor, "(fb:geography.mountain.elevation (>= 8500))", size(4));  // mountains at least 8500 meters tall
    runFormula(executor, "(fb:people.person.height_meters (> 1.8))", sizeAtLeast(10));  // people over 1.8 meters tall

    // Dates are tricky
    runFormula(executor, "(and (!fb:people.person.children fb:en.barack_obama) (fb:people.person.date_of_birth (= (date 2001 -1 -1))))", size(1));  // children of Obama born in 2001
    runFormula(executor, "(and (!fb:people.person.children fb:en.barack_obama) (fb:people.person.date_of_birth (<= (date 2001 -1 -1))))", size(2));  // children of Obama born no later than 2001
    runFormula(executor, "(and (!fb:people.person.children fb:en.barack_obama) (fb:people.person.date_of_birth (>= (date 2001 -1 -1))))", size(1));  // children of Obama born no earlier than 2001
  }

  @Test(groups = "sparql") public void sparqlMark() {
    runFormula(executor, "(mark x (and (fb:type.object.type fb:people.person) (fb:people.person.place_of_birth (!fb:people.deceased_person.place_of_death (var x)))))", sizeAtLeast(10));  // people who were born in the place that they died
    runFormula(executor, "((lambda x (mark y (!fb:people.marriage.spouse (!fb:people.person.spouse_s (and (var x) (!= (var y))))))) fb:en.barack_obama)", size(1));  // spouse of Barack Obama
  }

  @Test(groups = "sparql") public void sparqlAggregate() {
    runFormula(executor, "(count (fb:type.object.type fb:location.us_state))", matches("(number 50)"));  // number of US states
    runFormula(executor, "(min (!fb:location.location.area (fb:type.object.type fb:location.us_state)))", matches("(number 3140 fb:en.square_kilometer)"));  // minimum area of all US states
    runFormula(executor, "(max (!fb:location.location.area (fb:type.object.type fb:location.us_state)))", matches("(number 1717850 fb:en.square_kilometer)"));  // maximum area of all US states
    runFormula(executor, "(sum (!fb:location.location.area (fb:type.object.type fb:location.us_state)))", regexMatches("\\(number .* fb:en.square_kilometer\\)"));  // total area of all US states
    runFormula(executor, "((reverse (lambda x (count (!fb:people.person.children (var x))))) (>= 50))", size(2));  // people with at least 50 children

    String border = "(lambda x (mark y (fb:location.location.adjoin_s (fb:location.adjoining_relationship.adjoins (and (var x) (!= (var y)))))))";
    runFormula(executor, "(and (fb:type.object.type fb:location.us_state) ((reverse (lambda x (count (and (fb:type.object.type fb:location.us_state) (" + border + " (var x)))))) (> 6)))", size(4));  // states bordering more than 6 states

    // TODO(pliang): known bug (bordering less than 2 states doesn't include Alaska and Hawaii - need to make things optional)
    // (execute (and (@type @state) ((reverse (lambda x (count (and (@type @state) (@border (var x)))))) (number 0)))) # doesn't work
  }

  @Test(groups = "sparql") public void sparqlSuperlative() {
    runFormula(executor, "(argmax 1 1 (fb:type.object.type fb:location.us_state) fb:location.location.area)", matches("(name fb:en.alaska)"));  // largest state
    runFormula(executor, "(argmax 1 3 (fb:type.object.type fb:location.us_state) fb:location.location.area)", size(3));  // 3 largest states
    runFormula(executor, "(argmax 3 1 (fb:type.object.type fb:location.us_state) fb:location.location.area)", matches("(name fb:en.california)"));  // 3rd largest state

    runFormula(executor, "(!fb:measurement_unit.dated_integer.number (argmax 1 1 (!fb:location.statistical_region.population fb:en.california) fb:measurement_unit.dated_integer.year))", size(1));  // (most recent) population of california
    runFormula(executor, "(!fb:measurement_unit.dated_integer.number (argmax 1 2 (!fb:location.statistical_region.population fb:en.california) fb:measurement_unit.dated_integer.year))", size(2));  // (most recent) two populations of california

    // TODO(pliang): this query seems to time out, figure out why.
    // runFormula(executor, "(argmax 1 1 (fb:type.object.type fb:location.us_state) (lambda x (!fb:measurement_unit.dated_integer.number (argmax 1 1 (!fb:location.statistical_region.population (var x)) fb:measurement_unit.dated_integer.year))))", matches("(name fb:en.california)"));

    runFormula(executor, "(argmax 1 1 (fb:type.object.type fb:location.us_state) (reverse (lambda x (count ((reverse (lambda y (fb:location.location.adjoin_s (fb:location.adjoining_relationship.adjoins (and (fb:type.object.type fb:location.us_state) (var y)))))) (var x))))))", size(2));  // states that borders the most states
  }

  // Arithmetic
  @Test(groups = "sparql") public void sparqlArithmetic() {
    runFormula(executor, "(+ (!fb:people.person.height_meters fb:en.barack_obama) (!fb:people.person.height_meters fb:en.michelle_obama))", regexMatches("\\(number 3.650 fb:en.meter\\)"));
    runFormula(executor, "(- (!fb:people.person.height_meters fb:en.barack_obama) (!fb:people.person.height_meters fb:en.michelle_obama))", regexMatches("\\(number 0.050 fb:en.meter\\)"));
    runFormula(executor, "(/ (!fb:people.person.height_meters fb:en.barack_obama) (!fb:people.person.height_meters fb:en.michelle_obama))", regexMatches("\\(number 1.028\\)"));
    runFormula(executor, "(+ (number 3) (number 8))", regexMatches("\\(number 11\\)"));
  }
}
