package edu.stanford.nlp.sempre;

import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.testng.collections.Lists;

import com.google.common.collect.ImmutableList;

import edu.stanford.nlp.sempre.interactive.BlockFn;
import edu.stanford.nlp.sempre.interactive.GrammarInducer;
import edu.stanford.nlp.sempre.interactive.actions.ActionFormula;
import fig.basic.IOUtils;
import fig.basic.LispTree;
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
  
  public static GrammarInducer getInducer(String head, String jsonDef, String sessionId, Parser parser, Params params) {
    return getInducer(head, jsonDef, sessionId, parser, params, ActionFormula.Mode.block);
  }
  
  public static List<Derivation> derivsfromJson(String jsonDef, Parser parser, Params params) {
    @SuppressWarnings("unchecked")
    List<Object> body = Json.readValueHard(jsonDef, List.class);
    // string together the body definition
    List<Derivation> allDerivs = new ArrayList<>();
    
    for (Object obj : body) {
      @SuppressWarnings("unchecked")
      List<String> pair = (List<String>)obj;
      String utt = pair.get(0);
      String formula = pair.get(1);
      
      if (formula.equals("()")) {
        LogInfo.error("Got empty formula");
        continue;
      }
      
      Example.Builder b = new Example.Builder();
      // b.setId("session:" + sessionId);
      b.setUtterance(utt);
      Example ex = b.createExample();
      ex.preprocess();

      // LogInfo.logs("Parsing definition: %s", ex.utterance);
      parser.parse(params, ex, false);
      
      boolean found = false;
      for (Derivation d : ex.predDerivations) {
        // LogInfo.logs("considering: %s", d.formula.toString());
        if (d.formula.equals(Formulas.fromLispTree(LispTree.proto.parseFromString(formula)))) {
          found = true;
          allDerivs.add(stripDerivation(d));
        }
      }
      if (!found && !formula.equals("?")) LogInfo.errors("matching formula not found: %s", formula);
      
      // just making testing easier, use top derivation when we formula is not given
      if (!found && (formula.equals("?") || formula==null) && ex.predDerivations.size() > 0)
        allDerivs.add(stripDerivation(ex.predDerivations.get(0)));
    }
    return allDerivs;
  }
  // parse the definition, match with the chart of origEx, and add new rules to grammar
  public static GrammarInducer getInducer(String head, String jsonDef, String sessionId, Parser parser, Params params, ActionFormula.Mode mode) {
    Derivation bodyDeriv = combine(derivsfromJson(jsonDef, parser, params), mode);
    
    Example.Builder b = new Example.Builder();
    b.setId("session:" + sessionId);
    b.setUtterance(head);
    Example exHead = b.createExample();
    exHead.preprocess();

    LogInfo.begin_track("Definition");
    LogInfo.logs("mode: %s", mode);
    LogInfo.logs("head: %s", exHead.utterance);
    BeamFloatingParserState state = (BeamFloatingParserState)parser.parse(params, exHead, true);
    LogInfo.logs("defderiv: %s", bodyDeriv.toLispTree());
    LogInfo.logs("anchored: %s", state.chartList);

    GrammarInducer grammarInducer = new GrammarInducer(exHead, bodyDeriv, state.chartList);
//  updates the value that the derivation is modified.
//    for (Derivation d : grammarInducer.getHead().predDerivations) {
//      d.value = null; // cuz ensureExecuted checks.
//      d.ensureExecuted(parser.executor, exHead.context);
//    }
//    parser.parse(params, exHead, false);
    LogInfo.end_track();;
    return grammarInducer;
  }
  
  public static void logRawDef(String utt, String jsonDef, String sessionId) {
    PrintWriter out = IOUtils.openOutAppendHard(Paths.get(Master.opts.newGrammarPath, sessionId + ".def").toString());
    // deftree.addChild(oldEx.utterance);
    LispTree deftree = LispTree.proto.newList(":def", utt);
    deftree.addChild(jsonDef);
    Example oldEx = new Example.Builder()
        .setId(sessionId)
        .setUtterance(utt)
        .createExample();
    LispTree treewithdef = oldEx.toLispTree(false).addChild(deftree);
    out.println(treewithdef.toString());
    out.flush();
    out.close();
  }
  
  public static synchronized void addRuleInteractive(Rule rule, Parser parser) {
    LogInfo.logs("addRuleInteractive: %s", rule);
    parser.grammar.addRule(rule);

    if (parser instanceof BeamParser || parser instanceof BeamFloatingParser) {
      parser.addRule(rule);
    }
  }
  
  static Rule blockRule(ActionFormula.Mode mode) {
    BlockFn b = new BlockFn(mode);
    b.init(LispTree.proto.parseFromString("(a block)"));
    return new Rule("$Action", Lists.newArrayList("$Action", "$Action"), b);
  }  
  public static Derivation combine(List<Derivation> children, ActionFormula.Mode mode) {
    // stop double blocking
    if (children.size() == 1) {
      ActionFormula.Mode cmode = ((ActionFormula)children.get(0).formula).mode;
      if (cmode == mode) {
        return children.get(0);
      }
    }
    Formula f = new ActionFormula(mode, 
        children.stream().map(d -> d.formula).collect(Collectors.toList()));
    Derivation res = new Derivation.Builder()
        .formula(f)
        .withCallable(new SemanticFn.CallInfo("$Action", -1, -1, blockRule(mode), ImmutableList.copyOf(children)))
        .createDerivation();
    return res;
  }

}
