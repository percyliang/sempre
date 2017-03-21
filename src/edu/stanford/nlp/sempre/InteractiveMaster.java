package edu.stanford.nlp.sempre;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import edu.stanford.nlp.sempre.NaiveKnowledgeGraph.KnowledgeGraphTriple;
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
    line = line.trim();
    Response response = new Response();

    if (!line.startsWith("(:") && opts.onlyInteractive) {
      response.lines.add("most commands are disabled for security" + line);
    } else {
      handleCommand(session, line, response);
    }
    return response;
  }

  void handleCommand(Session session, String line, Response response) {
    LispTree tree = LispTree.proto.parseFromString(line);
    tree = builder.grammar.applyMacros(tree);

    String command = tree.child(0).value;

    // Start of interactive commands
    if (command.equals(":q")) {
      // Create example
      String utt = tree.children.get(1).value;
      Example ex = exampleFromUtterance(utt, session);

      //if (approxSeq >= 8)
      //  response.lines.add("You are taking many actions in one step, consider defining some of steps as one single step.");
      ILUtils.sanitize(ex);

      builder.parser.parse(builder.params, ex, false);

      if (session.isStatsing()) {
        response.stats.put("type", "q");
        response.stats.put("size", ex.predDerivations!=null? ex.predDerivations.size() : 0);
        response.stats.put("status", ILUtils.getParseStatus(ex));
      }
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
      if (session.isStatsing()) {
        response.stats.put("type", "reject");
        response.stats.put("rejectsize", tree.children.size()-2);
      }
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


      //Derivation match = ex.predDerivations.stream()
      //    .filter(d -> d.formula.equals(targetFormulaFinal)).findFirst().orElse(null);

      int rank = -1;
      Derivation match = null;
      for (int i = 0; i < ex.predDerivations.size(); i++) {
        Derivation derivi = ex.predDerivations.get(i);
        if (targetFormulas.contains(derivi.formula)) {
          rank = i; match = derivi; break;
        }
      }

      if (session.isStatsing()) {
        response.stats.put("type", "accept");
        response.stats.put("rank", rank);
        response.stats.put("status", ILUtils.getParseStatus(ex));
        response.stats.put("size", ex.predDerivations.size());
        response.stats.put("formulas.size", targetFormulas.size());
        response.stats.put("rank", rank);

        response.stats.put("len_formula", targetFormulas.get(0).toLispTree().numNodes());
        response.stats.put("len_utterance", ex.getTokens().size());
      }

      if (match!=null) {
        LogInfo.logs(":accept successful: %s", response.stats);

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
      if (tree.children.size() == 3) {
        String head = tree.children.get(1).value;
        String jsonDef = tree.children.get(2).value;

        Ref<Example> refExHead = new Ref<>();
        List<Rule> inducedRules = ILUtils.induceRulesHelper(command, head, jsonDef,
            builder.parser, builder.params, session.id, refExHead);

        if (inducedRules.size() > 0) {
          if (session.isLearning()) {
            for (Rule rule : inducedRules) {
                ILUtils.addRuleInteractive(rule, builder.parser);
            }
          }
          // TODO : should not have to parse again, I guess just set the formula or something
          // builder.parser.parse(builder.params, refExHead.value, false);
          response.ex = refExHead.value;
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

        if (session.isStatsing()) {
          response.stats.put("type", "def");
          response.stats.put("numRules", inducedRules.size());
        }
      } else {
        LogInfo.logs("Invalid format for def");
      }
    } else if (command.equals(":admin")) {
      if (!tree.child(1).value.equals("withcrappysecurity")) return;
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
        KnowledgeGraphTriple triple = new KnowledgeGraphTriple(
            new StringValue(tree.children.get(1).toString()),
            new NameValue("r"),
            new NameValue("e2")
            );
        session.context = new ContextValue(new NaiveKnowledgeGraph(Lists.newArrayList(triple)));
//        session.context = ContextValue.fromString(
//            String.format("(context (graph NaiveKnowledgeGraph ((string \"%s\") (name b) (name c))))",
//                tree.children.get(1).toString()));
        if (session.isStatsing())
          response.stats.put("context-length", tree.children.get(1).toString().length());
      }
    }
    else {
      LogInfo.log("Invalid command: " + tree);
    }
  }

  private Example exampleFromUtterance(String utt, Session session) {
    Example.Builder b = new Example.Builder();
    b.setId(session.id);
    b.setUtterance(utt);
    b.setContext(session.context);
    Example ex = b.createExample();
    ex.preprocess();
    return ex;
  }
}
