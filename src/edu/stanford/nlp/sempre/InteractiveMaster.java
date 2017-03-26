package edu.stanford.nlp.sempre;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import edu.stanford.nlp.sempre.ActionFormula.Mode;
import edu.stanford.nlp.sempre.NaiveKnowledgeGraph.KnowledgeGraphTriple;
import edu.stanford.nlp.sempre.interactive.BadInteractionException;
import edu.stanford.nlp.sempre.interactive.DefinitionAligner;
import edu.stanford.nlp.sempre.interactive.GrammarInducer;
import edu.stanford.nlp.sempre.interactive.ILUtils;
import edu.stanford.nlp.sempre.interactive.QueryStats;
import edu.stanford.nlp.sempre.interactive.QueryStats.QueryType;
import fig.basic.*;
import jline.console.ConsoleReader;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A Master manages multiple sessions. Currently, they all share the same model,
 * but they need not in the future.
 */
public class InteractiveMaster extends Master {
  public static class Options {
    @Option(gloss = "Write out new grammar rules")
    public String intOutputPath;
    @Option(gloss = "make sessions independent")
    public boolean independentSessions = false;
    @Option(gloss = "number of utterances to return for autocomplete")
    public int autocompleteCount = 5;
    @Option(gloss = "only allow interactive commands")
    public boolean onlyInteractive = false;

    @Option(gloss = "try partial matches")
    public boolean useAligner = true;
    
    @Option(gloss = "use the best formula when no match or not provided")
    public int maxSequence = 20;
    @Option(gloss = "path to the citations")
    public int maxChars = 200;
  }
  public static Options opts = new Options();

  public InteractiveMaster(Builder builder) {
    super(builder);
  }

  @Override
  void printHelp() {
    super.printHelp();
    // interactive commands, starting with the :
    LogInfo.log("  (:def head [[body1,bodyformula1],[body2,bodyformula2]]): provide a definition for the original utterance");
    LogInfo.log("  (:q |utterance|): provide a definition for the original utterance");
    LogInfo.log("  (:accept |formula1| |formula2|): accept any derivation with those corresponding formula");
    LogInfo.log("  (:accept |formula1| |formula2|): accept any derivation with those corresponding formula");
    LogInfo.log("Press Ctrl-D to exit.");
  }

  @Override
  public Response processQuery(Session session, String line) {
    LogInfo.begin_track("InteractiveMaster.handleQuery");
    LogInfo.logs("session %s", session.id);
    LogInfo.logs("query %s", line);
    line = line.trim();
    Response response = new Response();

    handleCommand(session, line, response);

    LogInfo.end_track();
    return response;
  }

  void handleCommand(Session session, String line, Response response) {
    LispTree tree = LispTree.proto.parseFromString(line);
    tree = builder.grammar.applyMacros(tree);

    String command = tree.child(0).value;
    QueryStats stats = new QueryStats(response, command);
    // Start of interactive commands
    if (command.equals(":q")) {
      // Create example
      String utt = tree.children.get(1).value;
      Example ex = exampleFromUtterance(utt, session);

      if (!utteranceAllowed(ex, response)) {
        stats.error("utterance_too_expensive");
        // returns with size and error message
        // return; parse anyways, there is a time limit
      }

      builder.parser.parse(builder.params, ex, false);

      stats.size(ex.predDerivations!=null? ex.predDerivations.size() : 0);
      stats.status(ILUtils.getParseStatus(ex));

      LogInfo.logs("parse stats: %s", response.stats);
      response.ex = ex;
    } else if (command.equals(":qdbg")) {
      // Create example
      String utt = tree.children.get(1).value;
      Example ex = exampleFromUtterance(utt, session);

      builder.parser.parse(builder.params, ex, false);

      Derivation.opts.showCat = true;
      Derivation.opts.showRules = true;
      for (Derivation d : ex.predDerivations) {
        response.lines.add(d.toLispTree().toString());
      }
      Derivation.opts.showCat = false;
      Derivation.opts.showRules = false;
      response.ex = ex;
    } else if (command.equals(":reject")) {
      stats.put("rejectSize", tree.children.size());
    } else if (command.equals(":accept")) {
      String utt = tree.children.get(1).value;
      List<Formula> targetFormulas = new ArrayList<>();
      try {
        targetFormulas = tree.children.subList(2, tree.children.size()).stream()
            .map(t -> Formulas.fromLispTree(LispTree.proto.parseFromString(t.value)))
            .collect(Collectors.toList());
      } catch (Exception e) {
        e.printStackTrace();
        response.lines.add("cannot accept formula: ");
      }

      Example ex = exampleFromUtterance(utt, session);
      response.ex = ex;

      // Parse!
      ParserState state;
      state = builder.parser.parse(builder.params, ex, true);
      state.ensureExecuted();

      int rank = -1;
      Derivation match = null;
      for (int i = 0; i < ex.predDerivations.size(); i++) {
        Derivation derivi = ex.predDerivations.get(i);
        if (targetFormulas.contains(derivi.formula)) {
          rank = i; match = derivi; break;
        }
      }
      if (rank == -1) {
        stats.error("unable to match on accept");
      }
      stats.rank(rank);
      stats.status(ILUtils.getParseStatus(ex));
      stats.size(ex.predDerivations.size());

      stats.put("formulas.size", targetFormulas.size());
      stats.put("len_formula", targetFormulas.get(0).toLispTree().toString().length());
      stats.put("len_utterance", ex.utterance.length());

      if (match!=null) {
        if (session.isWritingCitation()) {
          ILUtils.cite(match, ex);
        }
        // ex.setTargetValue(match.value); // this is just for logging, not actually used for learning
        if (session.isLearning()) {
          LogInfo.begin_track("Updating parameters");
          learner.onlineLearnExampleByFormula(ex, targetFormulas);
          LogInfo.end_track();
        }
      }
    } else if (command.startsWith(":def")) {
      stats.put("type", "def"); // startsWith
      if (tree.children.size() == 3) {
        String head = tree.children.get(1).value;
        String jsonDef = tree.children.get(2).value;

        List<Rule> inducedRules = new ArrayList<>();
        stats.put("head_len", head.length());
        stats.put("json_len", jsonDef.length());
        try {
          inducedRules.addAll(induceRulesHelper(command, head, jsonDef,
              builder.parser, builder.params, session, new Ref<Response>(response)));
          stats.put("num_rules", inducedRules.size());
        } catch (BadInteractionException e) {
          stats.put("num_rules", 0);
          stats.error(e.getMessage());
          response.lines.add(e.getMessage());
          return;
        }
        if (inducedRules.size() > 0) {
          if (session.isLearning()) {
            for (Rule rule : inducedRules) {
              ILUtils.addRuleInteractive(rule, builder.parser);
            }
          }
          // TODO : should not have to parse again, I guess just set the formula or something
          // builder.parser.parse(builder.params, refExHead.value, false);
          // write out the grammar
          if (session.isWritingGrammar()) {
            PrintWriter out = IOUtils.openOutAppendHard(Paths.get(InteractiveMaster.opts.intOutputPath, "grammar.log.json").toString());
            for (Rule rule : inducedRules) {
              out.println(rule.toJson());
            }
            out.close();
          }
        } else {
          LogInfo.logs("No rule induced for head %s", head);
        }
      } else {
        LogInfo.logs("Invalid format for def");
      }
    } else if (command.equals(":printInfo")) {
      LogInfo.logs("Printing and overriding grammar and parameters...");
      builder.params.write(Paths.get(InteractiveMaster.opts.intOutputPath, "params.params").toString());
      PrintWriter out = IOUtils.openOutAppendHard(Paths.get(InteractiveMaster.opts.intOutputPath + "grammar.final.json").toString());
      for (Rule rule : builder.grammar.rules) {
        out.println(rule.toJson());
      }
      out.close();
      LogInfo.logs("Done printing and overriding grammar and parameters...");
    } else if (command.equals(":context")) {
      if (tree.children.size() == 1) {
        LogInfo.logs("%s", session.context);
      } else {
        //        KnowledgeGraphTriple triple = new KnowledgeGraphTriple(
        //            new StringValue(String.format("\"%s\"", tree.children.get(1).toString())),
        //            new NameValue("r"),
        //            new NameValue("e2")
        //            );
        //        session.context = new ContextValue(new NaiveKnowledgeGraph(Lists.newArrayList(triple)));
        session.context = ContextValue.fromString(
            String.format("(context (graph NaiveKnowledgeGraph ((string \"%s\") (name b) (name c))))",
                tree.children.get(1).toString()));
        if (session.isStatsing())
          response.stats.put("context-length", tree.children.get(1).toString().length());
      }
    }
    else {
      LogInfo.log("Invalid command: " + tree);
    }
  }

  private static Example exampleFromUtterance(String utt, Session session) {
    Example.Builder b = new Example.Builder();
    b.setId(session.id);
    b.setUtterance(utt);
    b.setContext(session.context);
    Example ex = b.createExample();
    ex.preprocess();
    return ex;
  }

  public static List<Rule> induceRulesHelper(String command, String head, String jsonDef, Parser parser, Params params,
      Session session, Ref<Response> refResponse) throws BadInteractionException {
    Example exHead = exampleFromUtterance(head, session);
    LogInfo.logs("head: %s", exHead.getTokens());
    
    if (exHead.getTokens() == null || exHead.getTokens().size() == 0)
      throw BadInteractionException.headIsEmpty(head);
    if (isNonsense(exHead))
      throw BadInteractionException.nonSenseDefinition(head);
    BeamFloatingParserState state = (BeamFloatingParserState) parser.parse(params, exHead, true);
    if (GrammarInducer.getParseStatus(exHead) == GrammarInducer.ParseStatus.Core)
      throw BadInteractionException.headIsCore(head);
    
    LogInfo.logs("num anchored: %d", state.chartList.size());
    List<String> bodyList = ILUtils.utterancefromJson(jsonDef, false);
    LogInfo.logs("bodyutterance:\n %s", String.join("\n",bodyList));

    Derivation bodyDeriv = ILUtils.combine(ILUtils.derivsfromJson(jsonDef, parser, params, refResponse));
    if (refResponse != null) {
      refResponse.value.ex = exHead;
    }

    List<Rule> inducedRules = new ArrayList<>();
    GrammarInducer grammarInducer = new GrammarInducer(exHead.getTokens(), bodyDeriv, state.chartList);
    inducedRules.addAll(grammarInducer.getRules());

    for (Rule rule : inducedRules) {
      rule.source = new RuleSource(session.id, head, bodyList);
    }

    LogInfo.logs("testing Aligner, %b, %d", opts.useAligner, bodyList.size());
    if (opts.useAligner && bodyList.size() == 1){
      LogInfo.logs("testing Aligner2");
      List<Rule> alignedRules = DefinitionAligner.getRules(exHead.getTokens(), 
          ILUtils.utterancefromJson(jsonDef, true), bodyDeriv, state.chartList);
      for (Rule rule : alignedRules) {
        rule.source = new RuleSource(session.id, head, bodyList);
        rule.source.align = true;
      }
      inducedRules.addAll(alignedRules);
    }

    exHead.predDerivations = Lists.newArrayList(bodyDeriv);
    return inducedRules;
  }

  private static boolean isNonsense(Example exHead) {
    List<String> tokens = exHead.getTokens();
    if (tokens.size() > 10) return true;
    if (tokens.size() == 0) return true;
    return tokens.stream().anyMatch(s -> s.length() > 15);
  }

  private boolean utteranceAllowed(Example ex, Response response) {
    if (ex.utterance.length() > opts.maxChars) {
      response.lines.add(String.format(
          "refused to execute: too many characters in one command (current: %d, max: %d)", ex.utterance.length(), opts.maxChars)
          );
      return false;
    }
    long approxSeq = ex.getLemmaTokens().stream().filter(s -> s.contains(";")).count();
    if (approxSeq >= opts.maxSequence) {
      response.lines.add(String.format(
          "refused to execute: too many steps in one command -- " +
              "consider defining some of steps as one single step.  (current: %d, max: %d)",
              approxSeq, opts.maxSequence)
          );
      return false;
    }
    return true;
  }

}
