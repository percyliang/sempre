package edu.stanford.nlp.sempre;

import fig.basic.IOUtils;
import fig.basic.LispTree;
import fig.basic.LogInfo;
import fig.basic.MapUtils;

import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.testng.collections.Lists;

import com.google.common.collect.ImmutableList;

import edu.stanford.nlp.sempre.interactive.ApplyFn;
import edu.stanford.nlp.sempre.interactive.GrammarInducer;

/**
 * @author sidaw
 */
public final class InteractiveUtils {
  private InteractiveUtils() { }

  public static Derivation stripDerivation(Derivation deriv) {
    while (deriv.rule.sem instanceof IdentityFn) {
      deriv = deriv.child(0);
    }
    return deriv;
  }
 
  // parse the definition, match with the chart of origEx, and add new rules to grammar
  public static List<Rule> induceGrammar(String head, List<Object> body, String sessionId, Parser parser, Params params) {
    Derivation bodyDeriv =  new Derivation.Builder().createDerivation();
    
    // string together the body definition
    boolean initial = true;
    for (Object obj : body) {
      List<String> pair = (List<String>)obj;
      String utt = pair.get(0);
      String formula = pair.get(1);
      
      Example.Builder b = new Example.Builder();
      b.setId("session:" + sessionId);
      b.setUtterance(utt);
      Example ex = b.createExample();
      ex.preprocess();
      parser.parse(params, ex, false);
      
      boolean found = false;
      for (Derivation d : ex.predDerivations) {
        if (d.formula.toString().equals(formula)) {
          found = true;
          if (initial) {
            bodyDeriv = stripDerivation(d);
            initial = false;
          }
          else
            bodyDeriv = combine(bodyDeriv, stripDerivation(d));
          break;
        }
      }
      if (!found) LogInfo.errors("Definition fails, matching formula not found: %s", formula);
      
      // small cheat to make testing easier
      if (!found && formula.equals("?") && ex.predDerivations.size() > 0)
        bodyDeriv = stripDerivation(ex.predDerivations.get(0));
    }
    
    Example.Builder b = new Example.Builder();
    b.setId("session:" + sessionId);
    b.setUtterance(head);
    Example exHead = b.createExample();
    exHead.preprocess();
    BeamFloatingParserState state = (BeamFloatingParserState)parser.parse(params, exHead, false);
    LogInfo.logs("target deriv: %s", bodyDeriv.toLispTree());
    LogInfo.logs("anchored elements: %s", state.chartList);
    GrammarInducer grammarInducer = new GrammarInducer(exHead, bodyDeriv, state.chartList);
    
//    PrintWriter out = IOUtils.openOutAppendHard(Paths.get(opts.newGrammarPath, sessionId + ".definition").toString());
//    // deftree.addChild(oldEx.utterance);
//    LispTree deftree = LispTree.proto.newList("definition", body.toString());
//    Example oldEx = new Example.Builder()
//        .setId(exHead.id)
//        .setUtterance(exHead.utterance)
//        .setTargetFormula(bodyDeriv.formula)
//        .createExample();
//    LispTree treewithdef = oldEx.toLispTree(false).addChild(deftree);
//    out.println(treewithdef.toString());
//    out.flush();
//    out.close();

    return grammarInducer.getRules();
  }


  public static synchronized void addRuleInteractive(Rule rule, Parser parser) {
    LogInfo.logs("addRuleInteractive: %s", rule);
    parser.grammar.addRule(rule);

    if (parser instanceof BeamParser || parser instanceof BeamFloatingParser) {
      parser.addRule(rule);
    }
  }
  

  // utilities for grammar induction
  static final Formula combineFormula =  Formulas.fromLispTree(LispTree.proto.parseFromString("(lambda a1 (lambda a2 (:s (var a1) (var a2))))"));
  static Rule combineRule() {
    return new Rule("$Action", Lists.newArrayList("$Action", "$Action"), new ApplyFn(combineFormula));
  }
  static Derivation combine(Derivation d1, Derivation d2) {
    Formula f = Formulas.lambdaApply((LambdaFormula)combineFormula, d1.getFormula());
    f = Formulas.lambdaApply((LambdaFormula)f, d2.getFormula());
    List<Derivation> children = Lists.newArrayList(d1, d2);
    Derivation res = new Derivation.Builder()
        .withCallable(new SemanticFn.CallInfo("$Action", -1, -1, combineRule(), ImmutableList.copyOf(children)))
        .formula(f)
        .createDerivation();
    return res;
  }
}
