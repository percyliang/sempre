package edu.stanford.nlp.sempre.interactive.actions;

import static org.testng.AssertJUnit.assertEquals;

import java.util.*;
import java.util.function.Predicate;

import fig.basic.*;
import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.Parser.Spec;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.testng.collections.Lists;

/**
 * Test the parser, and some floating examples
 * @author Sida Wang
 */
public class BlocksParsingTest {
  Predicate<Example> contains(String formula) {
    Formula answer = Formulas.fromLispTree(LispTree.proto.parseFromString(formula));
    return e -> e.predDerivations.stream().anyMatch(d -> d.formula.equals(answer));
  }
  Predicate<Example> moreThan(int count) {
    return e -> e.predDerivations.size() > count;
  }
  
  private static Spec defaultSpec() {
    FloatingParser.opts.defaultIsFloating = true;
    ActionExecutor.opts.convertNumberValues  = true;
    ActionExecutor.opts.printStackTrace = true;
    ActionExecutor.opts.FlatWorldType = "BlocksWorld";
    Grammar.opts.inPaths = Lists.newArrayList("./shrdlurn/blocksworld.grammar");
    Grammar.opts.useApplyFn = "interactive.ApplyFn";
    Grammar.opts.binarizeRules = false;
    
    ActionExecutor executor = new ActionExecutor();
    ActionExecutor.opts.FlatWorldType = "BlocksWorld";
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
    b.setUtterance(beamUtt);
    b.setContext(context);
    Example ex = b.createExample();
    ex.preprocess();
    
    Spec defSpec = defaultSpec();
    Parser parser = new BeamParser(defSpec);
    ParserState state = parser.parse(new Params(), ex, false);
    LogInfo.end_track();
    
    // Add the floating parser and check?
    if (checker != null) {
      if (!checker.test(ex)) {
        Assert.fail(beamUtt);
      }
    }
  }

  private static ContextValue getContext(String blocks) {
    // a hack to pass in the world state without much change to the code
    String strigify2 = Json.writeValueAsStringHard(blocks); // some parsing issue inside lisptree parser
    return ContextValue.fromString(String.format("(context (graph NaiveKnowledgeGraph ((string \"%s\") (name b) (name c))))", strigify2));
  }
  
  @Test public void basicTest() {
    String defaultBlocks = "[[1,1,1,\"Green\",[]],[1,2,1,\"Blue\",[]],[2,2,1,\"Red\",[]],[3,2,2,\"Yellow\",[]]]";
    ContextValue context = getContext(defaultBlocks);
    LogInfo.begin_track("testJoin");

    parse("select all", "select all", context, contains("(:for * (: select))"));
    parse("select has color red", "red blocks", context, contains("(:for (color red) (: select))"));
    parse("add red top", "add some to top of red blocks", context, contains("(: add red top)"));
    parse("for has color red [ remove ]", "remove red blocks", context, contains("(:for (color red) (: remove))"));
    parse("repeat 3 [add red]", "add 3 red", context, contains("(:loop (number 3) (: add red top))"));
    parse("for has color red [ add yellow top ]", "add red to top of yellow", context, moreThan(0));
    parse("select has row 3", "select row 3", context, moreThan(0));
    parse("select has color red or has color green", "select red and green", context, contains("(:for (or (color red) (color green)) (: select))"));
    parse("remove has color red ; remove has color blue", "remove red then remove blue", context, moreThan(0));
    LogInfo.end_track();
  }
}