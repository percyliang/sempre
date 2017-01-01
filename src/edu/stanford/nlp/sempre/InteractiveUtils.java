package edu.stanford.nlp.sempre;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.testng.collections.Lists;

import com.google.common.collect.ImmutableList;

import edu.stanford.nlp.sempre.interactive.BlockFn;
import edu.stanford.nlp.sempre.interactive.GrammarInducer;
import edu.stanford.nlp.sempre.interactive.actions.ActionFormula;
import fig.basic.LogInfo;

/**
 * Utilities for grammar induction
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
  public static GrammarInducer getInducer(String head, List<Object> body, String sessionId, Parser parser, Params params) {
    
    // string together the body definition
    List<Derivation> allDerivs = new ArrayList<>();
    
    for (Object obj : body) {
      @SuppressWarnings("unchecked")
      List<String> pair = (List<String>)obj;
      String utt = pair.get(0);
      String formula = pair.get(1);
      
      Example.Builder b = new Example.Builder();
      b.setId("session:" + sessionId);
      b.setUtterance(utt);
      Example ex = b.createExample();
      ex.preprocess();

      LogInfo.logs("Parsing definition: %s", ex.utterance);
      parser.parse(params, ex, false);
      
      boolean found = false;
      for (Derivation d : ex.predDerivations) {
        LogInfo.logs("considering: %s", d.formula.toString());
        if (d.formula.toString().equals(formula)) {
          found = true;
          allDerivs.add(stripDerivation(d));
        }
      }
      if (!found) LogInfo.errors("Definition fails, matching formula not found: %s", formula);
      
      // just some hacks to make testing easier, use top derivation when we formula is not given
      if (!found && (formula.equals("?") || formula==null) && ex.predDerivations.size() > 0)
        allDerivs.add(stripDerivation(ex.predDerivations.get(0)));
    }
    Derivation bodyDeriv;
    if (allDerivs.size() > 1)
      bodyDeriv = combineList(allDerivs, ActionFormula.Mode.block);
    else
      bodyDeriv = allDerivs.get(0);
    
    Example.Builder b = new Example.Builder();
    b.setId("session:" + sessionId);
    b.setUtterance(head);
    Example exHead = b.createExample();
    exHead.preprocess();
    
    LogInfo.logs("Parsing head: %s", exHead.utterance);
    BeamFloatingParserState state = (BeamFloatingParserState)parser.parse(params, exHead, false);
    LogInfo.logs("target deriv: %s", bodyDeriv.toLispTree());
    LogInfo.logs("anchored elements: %s", state.chartList);
    GrammarInducer grammarInducer = new GrammarInducer(exHead, bodyDeriv, state.chartList);
    return grammarInducer;
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
  }
  
  public static synchronized void addRuleInteractive(Rule rule, Parser parser) {
    LogInfo.logs("addRuleInteractive: %s", rule);
    parser.grammar.addRule(rule);

    if (parser instanceof BeamParser || parser instanceof BeamFloatingParser) {
      parser.addRule(rule);
    }
  }
  
  static Rule blockRule() {
    return new Rule("$Action", Lists.newArrayList("$Action", "$Action"), new BlockFn());
  }  
  static Derivation combineList(List<Derivation> children, ActionFormula.Mode mode) {
    Formula f = new ActionFormula(mode, 
        children.stream().map(d -> d.formula).collect(Collectors.toList()));
    Derivation res = new Derivation.Builder()
        .formula(f)
        .withCallable(new SemanticFn.CallInfo("$Action", -1, -1, blockRule(), ImmutableList.copyOf(children)))
        .createDerivation();
    return res;
  }

}
