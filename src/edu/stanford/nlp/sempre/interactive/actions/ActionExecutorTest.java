package edu.stanford.nlp.sempre.interactive.actions;

import static org.testng.AssertJUnit.assertEquals;

import java.util.*;
import java.util.function.Predicate;

import fig.basic.*;
import edu.stanford.nlp.sempre.*;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Test the ActionExecutor
 * @author Sida Wang
 */
public class ActionExecutorTest {
  ActionExecutor executor = new ActionExecutor();

  protected static void runFormula(ActionExecutor executor, String formula, ContextValue context, Predicate<FlatWorld> checker) {
    LogInfo.begin_track("formula: %s", formula);
    Executor.Response response = executor.execute(Formulas.fromLispTree(LispTree.proto.parseFromString(formula)), context);
    
    NaiveKnowledgeGraph graph = (NaiveKnowledgeGraph)context.graph;
    String wallString = ((StringValue)graph.triples.get(0).e1).value;
    String jsonStr = ((StringValue)response.value).value;
    LogInfo.logs("Start:\t%s", wallString);
    LogInfo.logs("Result:\t%s", jsonStr);
    LogInfo.end_track();
    
    if (checker != null) {
      if (!checker.test(FlatWorld.fromContext("BlocksWorld", getContext(jsonStr)))) {
        Assert.fail(jsonStr);
      }
    }
  }

  private static ContextValue getContext(String blocks) {
    // a hack to pass in the world state without much change to the code
    String strigify2 = Json.writeValueAsStringHard(blocks); // some parsing issue inside lisptree parser
    return ContextValue.fromString(String.format("(context (graph NaiveKnowledgeGraph ((string \"%s\") (name b) (name c))))", strigify2));
  }
  
  public Predicate<FlatWorld> selectedSize(int n) {
    return x -> {LogInfo.logs("Got %d, expected %d", x.selected().size(), n); return x.selected().size()==n;};
  }
  
  @Test public void testJoin() {
    String defaultBlocks = "[[1,1,1,\"Green\",[]],[1,2,1,\"Blue\",[]],[2,2,1,\"Red\",[]],[3,2,2,\"Yellow\",[]]]";
    ContextValue context = getContext(defaultBlocks);
    LogInfo.begin_track("testJoin");

    runFormula(executor, "(: select *)", context, selectedSize(4));
    runFormula(executor, "(: select (or (color red) (color green)))", context, selectedSize(2));
    runFormula(executor, "(: select (or (row (number 1)) (row (number 2))))", context, x -> x.selected().size() == 3);
    runFormula(executor, "(: select (col ((reverse row) (color red))))", context, null); // has same col as the row of color red
    runFormula(executor, "(: select (color ((reverse color) (row 3))))", context, null); // color of the color of cubes in row 3
    runFormula(executor, "(: select (color ((reverse color) (color ((reverse color) (color red))))))", context,
        x -> x.selected.iterator().next().get("color").equals("red"));
    runFormula(executor, "(: select (and (row 1) (not (color green))))", context,
        x -> x.selected.iterator().next().get("color").equals("blue"));
    LogInfo.end_track();

  }
  @Test public void testMerge() {
    String defaultBlocks = "[[1,1,1,\"Green\",[]],[1,2,1,\"Blue\",[]],[2,2,1,\"Red\",[]],[2,2,2,\"Yellow\",[]]]";
    ContextValue context = getContext(defaultBlocks);
    LogInfo.begin_track("testMerge");
    runFormula(executor, "(: select (or (color red) (color blue)))", context, selectedSize(2));
    runFormula(executor, "(: select (and (color red) (color blue)))", context, selectedSize(0));
    runFormula(executor, "(: select (not (color red)))", context, selectedSize(3));
    runFormula(executor, "(: select (not *))", context, selectedSize(0));
    LogInfo.end_track();
  }
  @Test public void testBasicActions() {
    String defaultBlocks = "[[1,1,1,\"Green\",[]],[1,2,1,\"Blue\",[]],[2,2,1,\"Red\",[]],[2,2,3,\"Yellow\",[]]]";
    ContextValue context = getContext(defaultBlocks);
    LogInfo.begin_track("testBasicActions");
    runFormula(executor, "(:s (: select *) (: remove) (: remove))", context, x -> x.allitems.size() == 0);
    runFormula(executor, "(:s (: select (row (number 1))) (: add red top) (: add red top))", context, x -> x.allitems.size() == 8);
    runFormula(executor, "(:scope * (: remove))", context, x -> x.allitems.size() == 0);
    runFormula(executor, "(:s (: select (or (color red) (color orange))) (: remove))", context, x -> x.allitems.size() == 3);
    runFormula(executor, "(:scope (or (color red) (color orange)) (: remove))", context, x -> x.allitems.size() == 3);
    runFormula(executor, "(:scope (or (color red) (color orange)) (:rep (number 5) (: add red top)))",
       context, x -> x.allitems.size() == 9);
    runFormula(executor, "(:scope (or (color red) (color blue)) (:rep (number 5) (:s (: move left) (: move right) (: move left))))",
        context, null);
    runFormula(executor, "(:scope (or (color red) (color blue)) (: update color red))",
        context, null);
    LogInfo.end_track();
  }
  
}