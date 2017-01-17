package edu.stanford.nlp.sempre;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.testng.collections.Lists;

import com.google.common.collect.ImmutableList;

import edu.stanford.nlp.sempre.Master.Options;
import edu.stanford.nlp.sempre.interactive.ActionFormula;
import edu.stanford.nlp.sempre.interactive.BlockFn;
import edu.stanford.nlp.sempre.interactive.DefinitionAligner;
import edu.stanford.nlp.sempre.interactive.FlatWorld;
import edu.stanford.nlp.sempre.interactive.DefinitionAligner.Match;
import edu.stanford.nlp.sempre.interactive.GrammarInducer;
import fig.basic.IOUtils;
import fig.basic.LispTree;
import fig.basic.LogInfo;
import fig.basic.Option;
import fig.basic.Ref;

/**
 * Utilities for grammar induction
 * @author sidaw
 */
public final class ILUtils {
  public static class Options {
    @Option(gloss = "Execute these commands before starting")
    public String JSONLogPath = "./int-output/json/";

    @Option(gloss = "path to store new command logs")
    public String commandLog;
    
    @Option(gloss = "read and run these commands on startup")
    public List<String> commandInputs;

    @Option(gloss = "use the best formula when no match or not provided")
    public boolean useBestFormula = false;
  }
  public static Options opts = new Options();
  private ILUtils() { }

  // dont spam my log when reading things in the beginning...
  public static boolean fakeLog = false;
  private static Consumer<String> writer(String path) {
    if (fakeLog) {
      return s -> LogInfo.log(s);
    } else
      return s -> {
        PrintWriter out = IOUtils.openOutAppendHard(path);
        out.write(s);
        out.close();
      };
  }
  
  public static Derivation stripDerivation(Derivation deriv) {
    while (deriv.rule.sem instanceof IdentityFn) {
      deriv = deriv.child(0);
    }
    return deriv;
  }
  public static Derivation stripBlock(Derivation deriv) {
    LogInfo.logs("StripBlock %s %s %s", deriv, deriv.rule, deriv.cat);
    while ((deriv.rule.sem instanceof BlockFn || deriv.rule.sem instanceof IdentityFn)
        && deriv.children.size()==1) {
      deriv = deriv.child(0);
    }
    return deriv;
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
      if (!found && ex.predDerivations.size() > 0 && (formula.equals("?") || formula==null || opts.useBestFormula) )
        allDerivs.add(stripDerivation(ex.predDerivations.get(0)));
    }
    return allDerivs;
  }
  
  public static List<String> utterancefromJson(String jsonDef) {
    @SuppressWarnings("unchecked")
    List<Object> body = Json.readValueHard(jsonDef, List.class);
    // string together the body definition
    List<String> utts = new ArrayList<>();
    for (Object obj : body) {
      @SuppressWarnings("unchecked")
      List<String> pair = (List<String>)obj;
      String utt = pair.get(0);

      Example.Builder b = new Example.Builder();
      // b.setId("session:" + sessionId);
      b.setUtterance(utt);
      Example ex = b.createExample();
      ex.preprocess();
      
      utts.addAll(ex.getTokens());
      if (!utts.get(utts.size()-1).equals(";")) utts.add(";");
      
    }
    return utts;
  }
  // parse the definition, match with the chart of origEx, and add new rules to grammar
  public static List<Rule> induceRules(List<String> head, List<String> def, Derivation bodyDeriv, List<Derivation> chartList) {
    
    List<Rule> inducedRules = new ArrayList<>();
    GrammarInducer grammarInducer = new GrammarInducer(head, bodyDeriv, chartList);
    inducedRules.addAll(grammarInducer.getRules());
    inducedRules.addAll(DefinitionAligner.getRules(head, def, bodyDeriv, grammarInducer.matches));

    LogInfo.end_track();;
    return inducedRules;
  }
  
  public static List<Rule> induceRulesHelper(String command, String head, String jsonDef,
      Parser parser, Params params, String sessionId, Ref<Example> refEx) {
    ActionFormula.Mode blockmode = command.equals(":def")? ActionFormula.Mode.block : ActionFormula.Mode.blockr;
    
    Derivation bodyDeriv = ILUtils.combine(
        ILUtils.derivsfromJson(jsonDef, parser, params), blockmode);
    
    Example.Builder b = new Example.Builder();
    b.setId("session:" + sessionId);
    b.setUtterance(head);
    Example exHead = b.createExample();
    exHead.preprocess();
    
    LogInfo.begin_track("Definition");
    LogInfo.logs("mode: %s", blockmode);
    LogInfo.logs("head: %s", exHead.getTokens());
    LogInfo.logs("body: %s", ILUtils.utterancefromJson(jsonDef));
    LogInfo.logs("defderiv: %s", bodyDeriv.toLispTree());
    LogInfo.logs("bodyformula: %s", bodyDeriv.formula.toLispTree());
    
    BeamFloatingParserState state = (BeamFloatingParserState)parser.parse(params, exHead, true);
    LogInfo.logs("anchored: %s", state.chartList);
    LogInfo.logs("exHead: %s", exHead.getTokens());
    
    exHead.predDerivations = Lists.newArrayList(bodyDeriv);
    refEx.value = exHead;
    return ILUtils.induceRules(exHead.getTokens(), 
        ILUtils.utterancefromJson(jsonDef), bodyDeriv, state.chartList);
  }
  
//  public static void logRawDef(String utt, String jsonDef, String sessionId) {
//    PrintWriter out = IOUtils.openOutAppendHard(Paths.get(Master.opts.newGrammarPath, sessionId + ".def").toString());
//    // deftree.addChild(oldEx.utterance);
//    LispTree deftree = LispTree.proto.newList(":def", utt);
//    deftree.addChild(jsonDef);
//    Example oldEx = new Example.Builder()
//        .setId(sessionId)
//        .setUtterance(utt)
//        .createExample();
//    LispTree treewithdef = oldEx.toLispTree(false).addChild(deftree);
//    out.println(treewithdef.toString());
//    out.flush();
//    out.close();
//  }

  public static void logJSONExample(Example ex, String sessionId, String command) {
    Map<String, Object> jsonMap = new LinkedHashMap<>();
    jsonMap.put("time", LocalDateTime.now().toString());
    try {
      jsonMap.put("cmd", command);
      jsonMap.put("utterance", ex.utterance);
      jsonMap.put("sessionId", sessionId);           
      jsonMap.put("context", FlatWorld.fromContext("BlocksWorld", ex.context).toJSON());
      jsonMap.put("targetFormula", ex.targetFormula);
      
      if (ex.targetValue instanceof StringValue)
        jsonMap.put("targetValue", ((StringValue)ex.targetValue).toString());
      else
        jsonMap.put("targetValue", ex.targetValue);
      
      if (ex.predDerivations != null) {
        jsonMap.put("predDerivSize", ex.predDerivations.size());
      }
    } catch (Exception e) {
      jsonMap.put("exception", e.toString());
    } finally {
      String jsonStr = Json.prettyWriteValueAsStringHard(jsonMap) + "\n";
      writer(Paths.get(ILUtils.opts.JSONLogPath, sessionId + ".ex.json").toString()).accept(jsonStr);
    }
  }
  
  public static void logJSON(String sessionId, String log) {
    Map<String, Object> jsonMap = new LinkedHashMap<>();
    jsonMap.put("time", LocalDateTime.now().toString());
    jsonMap.put("id", sessionId);
    jsonMap.put("log", log);

    writer(Paths.get(ILUtils.opts.JSONLogPath, sessionId + ".log.json").toString())
    .accept(Json.writeValueAsStringHard(jsonMap) + "\n");

    writer(Paths.get(ILUtils.opts.commandLog).toString())
    .accept(Json.writeValueAsStringHard(jsonMap) + "\n");
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
      return children.get(0);
    }
    
    Formula f = new ActionFormula(mode, 
        children.stream().map(d -> d.formula).collect(Collectors.toList()));
    Derivation res = new Derivation.Builder()
        .formula(f)
        // setting start and end to -1 is important, which grammarInducer uses to check things
        .withCallable(new SemanticFn.CallInfo("$Action", -1, -1, blockRule(mode), ImmutableList.copyOf(children)))
        .createDerivation();
    return res;
  }
}
