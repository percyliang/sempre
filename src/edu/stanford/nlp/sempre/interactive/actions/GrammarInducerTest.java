package edu.stanford.nlp.sempre.interactive.actions;

import static org.testng.AssertJUnit.assertEquals;

import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;

import fig.basic.*;
import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.Parser.Spec;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.testng.collections.Lists;

/**
 * Test the grammar induction
 * @author Sida Wang
 */
public class GrammarInducerTest {
  
  private static Spec defaultSpec() {
    FloatingParser.opts.defaultIsFloating = true;
    ActionExecutor.opts.convertNumberValues  = true;
    ActionExecutor.opts.printStackTrace = true;
    ActionExecutor.opts.FlatWorldType = "BlocksWorld";
    Grammar.opts.inPaths = Lists.newArrayList("./shrdlurn/blocksworld.grammar");
    Grammar.opts.useApplyFn = "interactive.ApplyFn";
    Grammar.opts.binarizeRules = false;

    ActionExecutor executor = new ActionExecutor();
    FeatureExtractor extractor = new FeatureExtractor(executor);
    FeatureExtractor.opts.featureDomains.add("rule");
    ValueEvaluator valueEvaluator = new ExactValueEvaluator();
    Grammar grammar = new Grammar();
    grammar.read();
    grammar.write();
    return new Parser.Spec(grammar, extractor, executor, valueEvaluator);
  }

  protected static void induceHelper(String head, List<Object> body) {
    Spec defSpec = defaultSpec();
    Parser parser = new BeamFloatingParser(defSpec);
    
    List<Rule> induced = InteractiveUtils.induceGrammar(head, body, "test",  parser, new Params());
    for (Rule rule : induced)
      LogInfo.logs("Induced: %s", rule);
  }
  
  protected static void induce(String head, String jsonDef) {
     List<Object> body = Json.readValueHard(jsonDef, List.class);
     induceHelper(head, body);
  }
  
  @Test public void basicTest() {
    LogInfo.begin_track("test Grammar");
    induce("add red twice","[[\"add red top\",\"(: add red top)\"],[\"add red top\",\"(: add red top)\"]]");
    induce("add red thrice", "[[\"[add red top] 3 times\",\"(:loop (number 3) (: add red top))\"]]");
    induce("add a red block to the left", "[[\"add red left\",\"(: add red left)\"]]");
    induce("remove card", "[[\"remove has color red\",\"(:foreach (color red) (: remove))\"]]");
    induce("delete card", "[[\"remove has color red\",\"(:foreach (color red) (: remove))\"]]");
    induce("remove the leftmost red block", "[[\"remove very left of has color red\",\"?\"]]");
    induce("add red to both sides", "[[\"add red left\",\"(: add red left)\"],[\"add red right\",\"(: add red right)\"]]");
    LogInfo.end_track();
  }

}
