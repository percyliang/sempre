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
import org.testng.asserts.SoftAssert;
import org.testng.asserts.Assertion;
import org.testng.annotations.Test;
import org.testng.collections.Lists;

/**
 * Test the grammar induction
 * @author Sida Wang
 */
public class GrammarInducerTest {
  static Assertion A = new Assertion();
  // static Assertion A = new SoftAssert();
  
  private static Spec defaultSpec() {
    FloatingParser.opts.defaultIsFloating = true;
    ActionExecutor.opts.convertNumberValues  = true;
    ActionExecutor.opts.printStackTrace = true;
    ActionExecutor.opts.FlatWorldType = "BlocksWorld";
    LanguageAnalyzer.opts.languageAnalyzer = "interactive.actions.ActionLanguageAnalyzer";
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

  protected String d(String... defs) {
    List<List<String>> defList = new ArrayList<>();
    for (String def : defs) {
      defList.add(Lists.newArrayList(def, "?"));
    }
    return Json.writeValueAsStringHard(defList);
  }
  
  class ParseTester {
    Parser parser;
    Params params;
    List<Rule> allRules;
    public ParseTester() {
      parser = new BeamFloatingParser(defaultSpec());
      params = new Params();
      allRules = new ArrayList<>();
    }
    public void def(String head, String def) {
      List<Rule> induced = InteractiveUtils.getInducer(head, def, "testsession",  parser, new Params()).getRules();
      allRules.addAll(induced);
      induced.forEach(r -> InteractiveUtils.addRuleInteractive(r, parser));
      LogInfo.logs("Defining %s := %s, added %s", head, def, induced);
    }
    public boolean found(String head, String def) {
      Example.Builder b = new Example.Builder();
      b.setUtterance(head);
      Example exHead = b.createExample();
      exHead.preprocess();
      
      // LogInfo.logs("Parsing definition: %s", ex.utterance);
      parser.parse(params, exHead, false);
      
      Derivation defDeriv = InteractiveUtils.combine(InteractiveUtils.derivsfromJson(def, parser, params), ActionFormula.Mode.block);
      boolean found = false; 
      int ind = 0;
      for (Derivation d : exHead.predDerivations) {
        // LogInfo.logs("considering: %s", d.formula.toString());
        if (d.formula.equals(defDeriv.formula)) {
          found = true;
          LogInfo.logs("found %s at %d", d.formula, ind);
        }
        ind++;
      }
      printAllRules();
      return found;
    }
    
    public void printAllRules() {
      LogInfo.begin_track("Rules induced");
      allRules.forEach(r -> LogInfo.log(r));
      LogInfo.end_track();
    }
  }
  // tests simple substitutions
  @Test public void simpleTest() {
    LogInfo.begin_track("test simple substitutions");
    ParseTester T = new ParseTester();
    
    T.def("add red twice", d("add red top","add red top"));
    A.assertTrue(T.found("add blue twice", d("add blue top","add blue top")));
    
    T.def("add red 3 times", d("repeat 3 [add red top]"));
    A.assertTrue(T.found("add yellow 5 times", d("repeat 5 [add yellow top]")));
    
    T.def("add a red block to the left", d("add red left"));
    A.assertTrue(T.found("add a yellow block to the right", d("add yellow right")));
    
    T.def("remove the leftmost red block", d("remove very left of has color red"));
    A.assertTrue(T.found("remove the leftmost yellow block", d("remove very left of has color yellow")));
    
    T.def("add red to both sides", d("add red left", "add red right"));
    T.def("add red to both sides", d("add red back", "add red front"));
    A.assertTrue(T.found("add green to both sides", d("add green left", "add green right")));
    A.assertTrue(T.found("add green to both sides", d("add green back", "add green front")));
    A.assertFalse(T.found("add green to both sides", d("add green left", "add green back")));
    
    T.def("select all but yellow", d("select not has color yellow"));
    A.assertTrue(T.found("select all but yellow", d("select not has color yellow")));
    
    T.def("add a yellow block on top of red blocks", d("select has color red", "add yellow top"));
    A.assertTrue(T.found("add a green block on top of blue blocks", d("select has color blue", "add green top")));
    
    T.def("add a yellow block on top of red blocks", d("select has color red; add yellow top"));
    A.assertTrue(T.found("add a green block on top of blue blocks", d("select has color blue ; add green top")));
    
    //T.printAllRules();
    //A.assertAll();
   
    LogInfo.end_track();
  }
//  
//  @Test public void basicTest() {
//    LogInfo.begin_track("test Grammar");
//    induce("add red twice","[[\"add red top\",\"(: add red top)\"],[\"add red top\",\"(: add red top)\"]]");
//    induce("add red thrice", "[[\"[add red top] 3 times\",\"(:loop (number 3) (: add red top))\"]]");
//    induce("add a red block to the left", "[[\"add red left\",\"(: add red left)\"]]");
//    induce("remove card", "[[\"remove has color red\",\"(:foreach (color red) (: remove))\"]]");
//    induce("delete card", "[[\"remove has color red\",\"(:foreach (color red) (: remove))\"]]");
//    induce("remove the leftmost red block", "[[\"remove very left of has color red\",\"?\"]]");
//    induce("add red to both sides", "[[\"add red left\",\"(: add red left)\"],[\"add red right\",\"(: add red right)\"]]");
//    induce("add a yellow block next to brown", 
//        "[[\"select brown\",\"(:foreach (color brown) (: select))\"],[\"add yellow right\",\"(: add yellow right)\"]]");
//    induce("select all but yellow", 
//        "[[\"select not has color yellow\",\"?\"]]");
//    LogInfo.end_track();
//  }
//  
//  @Test public void learnSets() {
//    LogInfo.begin_track("test Grammar");
//    induce("remove those red blocks", "[[\"remove has color red\",\"(:foreach (color red) (: remove))\"]]");
//    induce("select card", "[[\"remove has color red\",\"(:foreach (color red) (: remove))\"]]");
//    induce("remove the leftmost red block", "[[\"remove very left of has color red\",\"?\"]]");
//    induce("add a red column of size 3",
//        "[[\"repeat 3 [ add red; select top of this]\",\"(:loop (number 3) (:s (: add red here) (:for (call adj top this) (: select))))\"]]");
//    LogInfo.end_track();
//  }
//  @Test public void learnLoop() {
//    LogInfo.begin_track("test Grammar");
//    induce("add a red column of size 3",
//        "[[\"repeat 3 [ add red; select top of this]\",\"(:loop (number 3) (:s (: add red here) (:for (call adj top this) (: select))))\"]]");
//    LogInfo.end_track();
//  }
}
