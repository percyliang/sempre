package edu.stanford.nlp.sempre.interactive;

import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;

import edu.stanford.nlp.sempre.Builder;
import edu.stanford.nlp.sempre.ContextValue;
import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.Example;
import edu.stanford.nlp.sempre.Formula;
import edu.stanford.nlp.sempre.Formulas;
import edu.stanford.nlp.sempre.Master;
import edu.stanford.nlp.sempre.Params;
import edu.stanford.nlp.sempre.Parser;
import edu.stanford.nlp.sempre.ParserState;
import edu.stanford.nlp.sempre.Rule;
import edu.stanford.nlp.sempre.RuleSource;
import edu.stanford.nlp.sempre.Session;
import fig.basic.IOUtils;
import fig.basic.LispTree;
import fig.basic.LogInfo;
import fig.basic.Option;
import fig.basic.Ref;

/**
 * An InteractiveMaster supports interactive commands, and grammar induction
 * methods.
 */
public class InteractiveMaster extends Master {
  public static class Options {
    @Option(gloss = "Write out new grammar rules")
    public String intOutputPath;
    @Option(gloss = "each session gets a different model with its own parameters")
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
    
    @Option(gloss = "allow regular commands specified in Master")
    public boolean allowRegularCommands = false;
  }

  public static Options opts = new Options();

  public InteractiveMaster(Builder builder) {
    super(builder);
  }

  @Override
  protected void printHelp() {
    // interactive commands
    LogInfo.log("Interactive commands");
    LogInfo.log(
        "  (:def head [[body1,bodyformula1],[body2,bodyformula2]]): provide a definition for the original utterance");
    LogInfo.log("  (:q |utterance|): provide a definition for the original utterance");
    LogInfo.log("  (:accept |formula1| |formula2|): accept any derivation with those corresponding formula");
    LogInfo.log("  (:reject |formula1| |formula2|): reject any derivations with those corresponding formula");
    LogInfo.log("Main commands:");
    super.printHelp();
  }

  @Override
  public void runServer() {
    InteractiveServer server = new InteractiveServer(this);
    server.run();
  }

  @Override
  public Response processQuery(Session session, String line) {
    LogInfo.begin_track("InteractiveMaster.handleQuery");
    LogInfo.logs("session %s", session.id);
    LogInfo.logs("query %s", line);
    line = line.trim();
    Response response = new Response();
    if (line.startsWith("(:"))
      handleCommand(session, line, response);
    else if (line.startsWith("(") && opts.allowRegularCommands || session.id.equals("stdin"))
      super.processQuery(session, line);
    else
      handleCommand(session, String.format("(:q \"%s\")", line), response);
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
        return;
      }

      builder.parser.parse(builder.params, ex, false);

      stats.size(ex.predDerivations != null ? ex.predDerivations.size() : 0);
      stats.status(InteractiveUtils.getParseStatus(ex));

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
            .map(t -> Formulas.fromLispTree(LispTree.proto.parseFromString(t.value))).collect(Collectors.toList());
      } catch (Exception e) {
        e.printStackTrace();
        response.lines.add("cannot accept formula: ");
      }

      Example ex = exampleFromUtterance(utt, session);
      response.ex = ex;

      // Parse!
      ((InteractiveBeamParser)builder.parser).parseWithoutExecuting(builder.params, ex, false);

      int rank = -1;
      Derivation match = null;
      for (int i = 0; i < ex.predDerivations.size(); i++) {
        Derivation derivi = ex.predDerivations.get(i);
        if (targetFormulas.contains(derivi.formula)) {
          rank = i;
          match = derivi;
          break;
        }
      }
      if (rank == -1) {
        stats.error("unable to match on accept");
      }
      stats.rank(rank);
      stats.status(InteractiveUtils.getParseStatus(ex));
      stats.size(ex.predDerivations.size());

      stats.put("formulas.size", targetFormulas.size());
      stats.put("len_formula", targetFormulas.get(0).toLispTree().toString().length());
      stats.put("len_utterance", ex.utterance.length());

      if (match != null) {
        if (session.isWritingCitation()) {
          InteractiveUtils.cite(match, ex);
        }
        // ex.setTargetValue(match.value); // this is just for logging, not
        // actually used for learning
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
          inducedRules.addAll(induceRulesHelper(command, head, jsonDef, builder.parser, builder.params, session,
              new Ref<Response>(response)));
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
              InteractiveUtils.addRuleInteractive(rule, builder.parser);
            }
            stats.put("total_rules", ((InteractiveBeamParser)builder.parser).allRules.size());
            stats.put("total_unicat", ((InteractiveBeamParser)builder.parser).interactiveCatUnaryRules.size());
          }
          // TODO : should not have to parse again, I guess just set the formula
          // or something
          // builder.parser.parse(builder.params, refExHead.value, false);
          // write out the grammar
          if (session.isWritingGrammar()) {
            PrintWriter out = IOUtils
                .openOutAppendHard(Paths.get(InteractiveMaster.opts.intOutputPath, "grammar.log.json").toString());
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
      PrintWriter out = IOUtils
          .openOutAppendHard(Paths.get(InteractiveMaster.opts.intOutputPath + "grammar.final.json").toString());
      for (Rule rule : builder.grammar.getRules()) {
        out.println(rule.toJson());
      }
      out.close();
      LogInfo.logs("Done printing and overriding grammar and parameters...");
    } else if (command.equals(":context")) {
      if (tree.children.size() == 1) {
        LogInfo.logs("%s", session.context);
      } else {
        session.context = ContextValue
            .fromString(String.format("(context (graph NaiveKnowledgeGraph ((string \"%s\") (name b) (name c))))",
                tree.children.get(1).toString()));
        response.stats.put("context_length", tree.children.get(1).toString().length());
      }
    } else {
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
    
    InteractiveBeamParserState state = ((InteractiveBeamParser)parser).parseWithoutExecuting(params, exHead, false);
    
    if (GrammarInducer.getParseStatus(exHead) == GrammarInducer.ParseStatus.Core)
      throw BadInteractionException.headIsCore(head);

    LogInfo.logs("num anchored: %d", state.chartList.size());
    List<String> bodyList = InteractiveUtils.utterancefromJson(jsonDef, false);
    LogInfo.logs("bodyutterances:\n %s", String.join("\t", bodyList));

    Derivation bodyDeriv = InteractiveUtils
        .combine(InteractiveUtils.derivsfromJson(jsonDef, parser, params, refResponse));
    if (refResponse != null) {
      refResponse.value.ex = exHead;
    }

    List<Rule> inducedRules = new ArrayList<>();
    GrammarInducer grammarInducer = new GrammarInducer(exHead.getTokens(), bodyDeriv, state.chartList);
    inducedRules.addAll(grammarInducer.getRules());

    for (Rule rule : inducedRules) {
      rule.source = new RuleSource(session.id, head, bodyList);
    }

    if (opts.useAligner && bodyList.size() == 1) {
      List<Rule> alignedRules = DefinitionAligner.getRules(exHead.getTokens(),
          InteractiveUtils.utterancefromJson(jsonDef, true), bodyDeriv, state.chartList);
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
    if (tokens.size() > 10)
      return true;
    if (tokens.size() == 0)
      return true;
    return tokens.stream().anyMatch(s -> s.length() > 15);
  }

  private boolean utteranceAllowed(Example ex, Response response) {
    if (ex.utterance.length() > opts.maxChars) {
      response.lines.add(String.format("refused to execute: too many characters in one command (current: %d, max: %d)",
          ex.utterance.length(), opts.maxChars));
      return false;
    }
    long approxSeq = ex.getLemmaTokens().stream().filter(s -> s.contains(";")).count();
    if (approxSeq >= opts.maxSequence) {
      response.lines.add(String.format(
          "refused to execute: too many steps in one command -- "
              + "consider defining some of steps as one single step.  (current: %d, max: %d)",
          approxSeq, opts.maxSequence));
      return false;
    }
    return true;
  }

}
