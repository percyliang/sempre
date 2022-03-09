package edu.stanford.nlp.sempre.interactive.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.testng.annotations.Test;
import org.testng.asserts.Assertion;
import org.testng.asserts.SoftAssert;
import org.testng.collections.Lists;

import com.google.common.collect.Sets;

import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.ExactValueEvaluator;
import edu.stanford.nlp.sempre.Example;
import edu.stanford.nlp.sempre.FeatureExtractor;
import edu.stanford.nlp.sempre.FloatingParser;
import edu.stanford.nlp.sempre.Grammar;
import edu.stanford.nlp.sempre.Json;
import edu.stanford.nlp.sempre.LanguageAnalyzer;
import edu.stanford.nlp.sempre.Params;
import edu.stanford.nlp.sempre.Parser;
import edu.stanford.nlp.sempre.Parser.Spec;
import edu.stanford.nlp.sempre.ParserState;
import edu.stanford.nlp.sempre.Rule;
import edu.stanford.nlp.sempre.Session;
import edu.stanford.nlp.sempre.SimpleLexicon;
import edu.stanford.nlp.sempre.ValueEvaluator;
import edu.stanford.nlp.sempre.interactive.InteractiveBeamParser;
import edu.stanford.nlp.sempre.interactive.DALExecutor;
import edu.stanford.nlp.sempre.interactive.DefinitionAligner;
import edu.stanford.nlp.sempre.interactive.GrammarInducer;
import edu.stanford.nlp.sempre.interactive.InteractiveMaster;
import edu.stanford.nlp.sempre.interactive.InteractiveUtils;
import fig.basic.LogInfo;

/**
 * Test the grammar induction
 * 
 * @author sidaw
 */
public class GrammarInducerTest {
  Assertion hard = new Assertion();
  Assertion soft = new SoftAssert();

  private static Spec defaultSpec() {
    FloatingParser.opts.defaultIsFloating = true;
    DALExecutor.opts.convertNumberValues = true;
    DALExecutor.opts.printStackTrace = true;
    DALExecutor.opts.worldType = "VoxelWorld";

    Derivation.opts.showTypes = false;
    Derivation.opts.showRules = false;
    Derivation.opts.showCat = true;

    LanguageAnalyzer.opts.languageAnalyzer = "interactive.DALAnalyzer";
    Grammar.opts.inPaths = Lists.newArrayList("./interactive/voxelurn.grammar");
    Grammar.opts.useApplyFn = "interactive.ApplyFn";
    Grammar.opts.binarizeRules = false;
    
    SimpleLexicon.opts.inPaths = Lists.newArrayList("./interactive/csscolors.lexicon");

    FeatureExtractor.opts.featureComputers = Sets.newHashSet("interactive.DALFeatureComputer");
    FeatureExtractor.opts.featureDomains = Sets.newHashSet(":rule", ":stats", ":window");

    DefinitionAligner.opts.strategies = Sets.newHashSet(DefinitionAligner.Strategies.ExactExclusion);
    DefinitionAligner.opts.verbose = 2;
    GrammarInducer.opts.verbose = 2;
    InteractiveMaster.opts.useAligner = true;

    DALExecutor executor = new DALExecutor();

    FeatureExtractor extractor = new FeatureExtractor(executor);

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
      parser = new InteractiveBeamParser(defaultSpec());
      params = new Params();
      allRules = new ArrayList<>();
    }

    public void def(String head, String def) {
      List<Rule> induced = InteractiveMaster.induceRulesHelper(":def", head, def, parser, params,
          new Session("testsession"), null);
      allRules.addAll(induced);
      LogInfo.logs("Defining %s := %s, added %s", head, def, induced);
      induced.forEach(r -> InteractiveUtils.addRuleInteractive(r, parser));
    }

    public boolean match(String head, String def) {
      Example.Builder b = new Example.Builder();
      b.setUtterance(head);
      Example exHead = b.createExample();
      exHead.preprocess();

      // LogInfo.logs("Parsing definition: %s", ex.utterance);
      parser.parse(params, exHead, true);

      Derivation defDeriv = InteractiveUtils.combine(InteractiveUtils.derivsfromJson(def, parser, params, null));

      boolean found = false;
      int ind = 0;
      for (Derivation d : exHead.predDerivations) {
        // LogInfo.logs("considering: %s", d.formula.toString());
        LogInfo.logs("Comparing %s vs %s", InteractiveUtils.stripBlock(d).formula.toString(),
            InteractiveUtils.stripBlock(defDeriv).formula.toString());
        if (InteractiveUtils.stripBlock(d).formula.toString()
            .equals(InteractiveUtils.stripBlock(defDeriv).formula.toString())) {
          found = true;
          LogInfo.logs("found %s at %d", d.formula, ind);
        }
        ind++;
      }
      printAllRules();
      if (!found) {
        LogInfo.logs("Did not find %s among \n %s", defDeriv.formula, exHead.predDerivations);
      }
      return found;
    }

    public void accept(String head, String def) {
      LogInfo.begin_track("Accepting");
      Example.Builder b = new Example.Builder();
      b.setUtterance(head);
      Example exHead = b.createExample();
      exHead.preprocess();

      Derivation defDeriv = InteractiveUtils.combine(InteractiveUtils.derivsfromJson(def, parser, params, null));

      // LogInfo.logs("Parsing definition: %s", ex.utterance);
      parser.parse(params, exHead, true);

      for (Derivation deriv : exHead.predDerivations) {
        deriv.compatibility = defDeriv.formula.equals(deriv.formula) ? 1 : 0;
      }
      exHead.predDerivations.forEach(d -> LogInfo.logs("Compatibility %s : %f", d.formula, d.compatibility));

      HashMap<String, Double> counts = new HashMap<>();
      ParserState.computeExpectedCounts(exHead.predDerivations, counts);
      LogInfo.logs("Gradients: %s", counts);
      // LogInfo.logs("paramsbefore: %s", params.getWeights());
      params.update(counts);
      // LogInfo.logs("paramsafter: %s", params.getWeights());
      LogInfo.end_track();
    }

    public void printAllRules() {
      LogInfo.begin_track("Rules induced");
      allRules.forEach(r -> LogInfo.log(r));
      LogInfo.end_track();
    }
  }

  // tests simple substitutions
  @Test(groups = { "Interactive" })
  public void simpleTest() {
    LogInfo.begin_track("simpleTest");
    ParseTester T = new ParseTester();
    Assertion A = hard;

    T.def("add red twice", d("add red top", "add red top"));
    A.assertTrue(T.match("add blue twice", d("add blue top", "add blue top")));

    T.def("add red 3 times", d("repeat 3 [add red]"));
    A.assertTrue(T.match("add yellow 5 times", d("repeat 5 [add yellow]")));

    T.def("add red 3 ^ 2 times", d("repeat 3 {[repeat 3 [add red]]}"));
    A.assertTrue(T.match("add blue 2 ^ 2 times", d("repeat 2 {[repeat 2 [add blue]]}")));

    T.def("add a red block to the left", d("add red left"));
    A.assertTrue(T.match("add a yellow block to the right", d("add yellow right")));

    T.def("remove the leftmost red block", d("remove very left of has color red"));
    A.assertTrue(T.match("remove the leftmost yellow block", d("remove very left of has color yellow")));

    T.def("add red to both sides", d("add red left", "add red right"));
    T.def("add red to both sides", d("add red back", "add red front"));
    A.assertTrue(T.match("add green to both sides", d("add green left", "add green right")));
    A.assertTrue(T.match("add green to both sides", d("add green back", "add green front")));
    A.assertFalse(T.match("add green to both sides", d("add green left", "add green back")));

    T.def("select all but yellow", d("select not has color yellow"));
    A.assertTrue(T.match("select all but blue", d("select not has color blue")));

    T.def("add a yellow block on top of red blocks", d("select has color red", "add yellow top"));
    A.assertTrue(T.match("add a green block on top of blue blocks", d("select has color blue", "add green top")));

    T.def("add a yellow block on top of red blocks", d("select has color red; add yellow top"));
    A.assertTrue(T.match("add a green block on top of blue blocks", d("select has color blue ; add green top")));

    // T.printAllRules();
    // A.assertAll();

    LogInfo.end_track();
  }

  @Test(groups = { "Interactive" })
  public void actionTest() {
    LogInfo.begin_track("actionTest");
    ParseTester T = new ParseTester();
    Assertion A = hard;

    // by default, we prefer higher level abstractions
    T.def("add red twice", d("repeat 2 [add red]"));
    // T.def("select has color red twice", d("repeat 2 [select has color
    // red]"));
    A.assertTrue(T.match("add blue top twice", d("repeat 2 [add blue top]")));

    T.def("add red 3 times", d("repeat 3 [add red]"));
    A.assertTrue(T.match("add yellow top 5 times", d("repeat 5 [add yellow top]")));

    T.def("add red then add yellow left", d("add red; add yellow left"));
    A.assertTrue(T.match("remove has color red then add brown", d("remove has color red; add brown")));

    // T.printAllRules();
    // A.assertAll();

    LogInfo.end_track();
  }

  @Test(groups = { "Interactive" })
  public void notActionTest() {
    LogInfo.begin_track("notActionTest");
    ParseTester T = new ParseTester();
    Assertion A = hard;

    T.def("add red top twice", d("add red; add red top"));
    T.accept("add yellow top twice", d("add yellow; add yellow top"));
    // T.accept("add yellow bot twice", d("add yellow; add yellow bot"));
    // T.accept("add brown left twice", d("add brown; add brown left"));
    T.def("add red top twice", d("add red; add red top"));
    A.assertTrue(T.match("add blue right twice", d("add blue; add blue right")));

    // T.printAllRules();
    // A.assertAll();

    LogInfo.end_track();
  }

  @Test(groups = { "Interactive" })
  public void learnCatTest() {
    LogInfo.begin_track("test the learning via alignment");
    ParseTester T = new ParseTester();
    Assertion A = hard;

    T.def("add cardinal", d("add red"));
    A.assertTrue(T.match("select has color cardinal", d("select has color red")));

    T.def("move up", d("move top"));
    A.assertTrue(T.match("select up", d("select top")));
    A.assertTrue(T.match("add red up", d("add red top")));

    T.def("move it up", d("move top"));
    A.assertTrue(T.match("select up", d("select top")));
    A.assertTrue(T.match("add red up", d("add red top")));

    // do not redefine core test
    T.def("move it top", d("move it bot"));
    A.assertFalse(T.match("select top", d("select bot")));
    A.assertFalse(T.match("add red top", d("add red bot")));

    T.def("select the highest of has color red", d("select very top of has color red"));
    A.assertTrue(T.match("remove the highest of all", d("remove very top of all")));

    T.def("select the top most of has color red", d("select very top of has color red"));
    A.assertTrue(T.match("remove the bot most of all", d("remove very bot of all")));

    // T.printAllRules();
    // A.assertAll();

    LogInfo.end_track();
  }

  @Test(groups = { "Interactive" })
  public void cubeTest() {
    LogInfo.begin_track("cubeTest");
    ParseTester T = new ParseTester();
    Assertion A = hard;

    T.def("red stick of size 3", d("{repeat 3 [add red; select top]}"));
    T.def("red plate of size 3", d("{repeat 3 [red stick of size 3; select left]}"));
    T.def("red cube of size 3", d("{repeat 3 [red plate of size 3; select back]}"));

    A.assertTrue(T.match("blue stick of size 4", d("{repeat 4 [add blue; select top]}")));
    A.assertTrue(T.match("blue plate of size 4", d("{repeat 4 [blue stick of size 4; select left]}")));
    A.assertTrue(T.match("blue cube of size 5", d("{repeat 5 [blue plate of size 5; select back]}")));

    // T.printAllRules();
    // A.assertAll();

    LogInfo.end_track();
  }

  @Test(groups = { "Interactive" })
  public void rectTest() {
    LogInfo.begin_track("rectTest");
    ParseTester T = new ParseTester();
    Assertion A = hard;

    T.def("red stick of size 4", d("{repeat 4 [add red; select top]}"));
    T.def("red plate of size 3 by 4", d("{repeat 3 [red stick of size 4; select left]}"));
    T.def("red cube of size 2 by 3 by 4", d("{repeat 2 [red plate of size 3 by 4; select back]}"));

    A.assertTrue(T.match("red plate of size 1 by 2", d("{repeat 1 [red stick of size 2; select left]}")));
    A.assertTrue(T.match("red cube of size 1 by 2 by 3", d("{repeat 1 [red plate of size 2 by 3; select back]}")));

    // T.printAllRules();
    // A.assertAll();

    LogInfo.end_track();
  }

  @Test(groups = { "Interactive" })
  public void setsTest() {
    LogInfo.begin_track("setsTest");
    ParseTester T = new ParseTester();
    Assertion A = hard;

    T.def("remove those red blocks", d("remove has color red"));
    A.assertTrue(T.match("select those red blocks", d("select has color red")));

    T.def("remove the leftmost red block", d("remove very left of has color red"));
    A.assertTrue(T.match("select the leftmost red block", d("select very left of has color red")));

    // T.printAllRules();
    // A.assertAll();

    LogInfo.end_track();
  }

}
