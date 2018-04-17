package edu.stanford.nlp.sempre.interactive.test;

import static org.testng.AssertJUnit.assertEquals;

import java.util.*;
import java.util.function.Predicate;

import fig.basic.*;
import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.Parser.Spec;
import edu.stanford.nlp.sempre.interactive.InteractiveBeamParser;
import edu.stanford.nlp.sempre.interactive.DALExecutor;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.testng.collections.Lists;

/**
 * Test the parser, and some floating examples
 * @author sidaw
 */
@Test(groups = { "InteractiveLearning" })
public class FloatingParsingTest {
  Predicate<Example> contains(String formula) {
    Formula answer = Formulas.fromLispTree(LispTree.proto.parseFromString(formula));
    return e -> e.predDerivations.stream().anyMatch(d -> d.formula.equals(answer));
  }
  Predicate<Example> moreThan(int count) {
    return e -> e.predDerivations.size() > count;
  }
  Predicate<Example> hasAll(String...substrings) {
    return new Predicate<Example>() {
      List<String> required = Lists.newArrayList(substrings);

      @Override
      public boolean test(Example e) {
        int match = 0;
        for (Derivation deriv : e.predDerivations) {
          String formula = deriv.formula.toString();
          if (required.stream().anyMatch(s -> formula.indexOf(s)!=-1)) {
            match ++;
            LogInfo.log("Got a match: " + formula);
          }
        }
        if (match == 0)
          throw new RuntimeException("Failed to match " + required.toString() + " : " + e.utterance);
        return true;
      }
    };
  }

  private static Spec defaultSpec() {
    FloatingParser.opts.defaultIsFloating = true;
    DALExecutor.opts.convertNumberValues  = true;
    DALExecutor.opts.printStackTrace = true;
    DALExecutor.opts.worldType = "VoxelWorld";
    Grammar.opts.inPaths = Lists.newArrayList("./interactive/voxelurn.grammar");
    Grammar.opts.useApplyFn = "interactive.ApplyFn";
    Grammar.opts.binarizeRules = false;

    DALExecutor executor = new DALExecutor();
    DALExecutor.opts.worldType = "BlocksWorld";
    FeatureExtractor extractor = new FeatureExtractor(executor);
    FeatureExtractor.opts.featureDomains.add("rule");
    ValueEvaluator valueEvaluator = new ExactValueEvaluator();
    Grammar grammar = new Grammar();
    grammar.read();
    grammar.write();
    return new Parser.Spec(grammar, extractor, executor, valueEvaluator);
  }

  protected static void parse(String beamUtt, String floatUtt, ContextValue context, Predicate<Example> checker) {
    LogInfo.begin_track("Cannonical: %s\t Float: %s", beamUtt, floatUtt);

    Example.Builder b = new Example.Builder();
    b.setId("session:test");
    b.setUtterance(floatUtt);
    b.setContext(context);
    Example ex = b.createExample();
    ex.preprocess();

    Spec defSpec = defaultSpec();
    Parser parser = new InteractiveBeamParser(defSpec);
    ParserState state = parser.parse(new Params(), ex, false);
    LogInfo.end_track();

    // Add the floating parser and check?
    if (checker != null) {
      if (!checker.test(ex)) {
        Assert.fail(floatUtt);
      }
    }
  }

  private static ContextValue getContext(String blocks) {
    // a hack to pass in the world state without much change to the code
    String strigify2 = Json.writeValueAsStringHard(blocks); // some parsing issue inside lisptree parser
    return ContextValue.fromString(String.format("(context (graph NaiveKnowledgeGraph ((string \"%s\") (name b) (name c))))", strigify2));
  }

  public void basicTest() {
    String defaultBlocks = "[[1,1,1,\"Green\",[]],[1,2,1,\"Blue\",[]],[2,2,1,\"Red\",[]],[3,2,2,\"Yellow\",[]]]";
    ContextValue context = getContext(defaultBlocks);
    LogInfo.begin_track("testJoin");

    parse("select all", "select all", context, contains("(: select *)"));
    // parse("select has color red", "red blocks", context, contains("(:for (color red) (: select))"));
    parse("select has color red", "red blocks", context, hasAll("(color red)", "(: select)", ":foreach"));
    parse("add red top", "add some to top of red blocks", context, contains("(: add red top)"));
    parse("for has color red [ remove ]", "remove red blocks", context, contains("(:foreach (color red) (: remove))"));
    parse("repeat 3 [add red]", "add red 3 times", context, contains("(:loop (number 3) (: add red))"));
    parse("for has color red [ add yellow top ]", "add red to top of yellow", context, moreThan(0));
    // parse("select has row 3", "select row 3", context, moreThan(0));
    // parse("select has color red or has color green", "select red and green", context, contains("(:for (or (color red) (color green)) (: select))"));
    // parse("select has color red or has color green", "select red or green", context, contains("(:for (or (color red) (color green)) (: select))"));
    parse("remove has color red ; remove has color blue", "remove red then remove blue", context, moreThan(0));
    LogInfo.end_track();
  }

  public void advanced() {
    String defaultBlocks = "[[1,1,1,\"Green\",[]],[1,2,1,\"Blue\",[]],[2,2,1,\"Red\",[]],[3,2,2,\"Yellow\",[]]]";
    ContextValue context = getContext(defaultBlocks);
    LogInfo.begin_track("testJoin");

    parse("repeat 4 [add yellow]", "add 4 yellow blocks", context, hasAll("(:loop", "(number 4)", "(color yellow)"));
    parse("repeat 4 [for has color red [ add yellow left ] ]", "put 4 yellow left of red", context, hasAll(":for", "red", "left"));
    parse("", "put 4 yellow to the left of red", context, hasAll(":foreach", "red", "left"));
    parse("", "select has color red or has color green",  context, hasAll("(color red)", "(color green)", "(: select)"));
    parse("", "add red then add green and then add yellow",  context, hasAll("(color red)", "(color green)", "(color yellow)"));
    parse("", "add red then add green and then add yellow",  context, hasAll("(: add", "(color red)", "(color green)", "(color yellow)"));
    parse("", "remove the very left yellow block", context, moreThan(0));
    parse("", "add red top then add yellow then add green", context, moreThan(0));
    parse("", "add 4 yellow to red or green", context, moreThan(0));
    // might be manageable with projectivity
    parse("", "add 4 yellow to the left of red or green", context, moreThan(0));
    parse("", "repeat 3 [delete top of all]", context, moreThan(0));
    parse("", "repeat 3 [delete top of all]", context, moreThan(0));
    // parse("", "add 3 red to left", context, hasAll("(:loop (number 3) (: add red left))"));
    // parse("repeat 5 [ add red left ]", "add 5 red left", context, hasAll("(:loop (number 5) (: add red left))"));
    LogInfo.end_track();
  }
  // things we won't handle
  public void outOfScope() {
    String defaultBlocks = "[[1,1,1,\"Green\",[]],[1,2,1,\"Blue\",[]],[2,2,1,\"Red\",[]],[3,2,2,\"Yellow\",[]]]";
    ContextValue context = getContext(defaultBlocks);
    LogInfo.begin_track("testJoin");

    parse("", "repeat 3 [ repeat 3 [delete very top of all] ]", context, moreThan(0));
    LogInfo.end_track();
  }
}
