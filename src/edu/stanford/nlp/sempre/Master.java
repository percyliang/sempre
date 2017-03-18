package edu.stanford.nlp.sempre;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

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
public class Master {
  public static class Options {
    @Option(gloss = "Execute these commands before starting")
    public List<String> scriptPaths = Lists.newArrayList();
    @Option(gloss = "Execute these commands before starting (after scriptPaths)")
    public List<String> commands = Lists.newArrayList();
    @Option(gloss = "Write a log of this session to this path")
    public String logPath;

    @Option(gloss = "Print help on startup")
    public boolean printHelp = true;

    @Option(gloss = "Number of exchanges to keep in the context")
    public int contextMaxExchanges = 0;

    @Option(gloss = "Online update weights on new examples.")
    public boolean onlineLearnExamples = true;
    @Option(gloss = "Write out new examples to this directory")
    public String newExamplesPath;
    @Option(gloss = "Write out new parameters to this directory")
    public String newParamsPath;

    // Interactive stuff
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

  public class Response {
    // Example that was parsed, if any.
    Example ex;

    // Which derivation we're selecting to show
    int candidateIndex = -1;

    // Detailed information
    Map<String, Object> stats = new LinkedHashMap<>();
    List<String> lines = new ArrayList<>();

    public String getFormulaAnswer() {
      if (ex.getPredDerivations().size() == 0 )
        return "(no answer)";
      else if (candidateIndex == -1)
        return "(not selected)";
      else {
        Derivation deriv = getDerivation();
        return deriv.getFormula() + " => " + deriv.getValue();
      }
    }
    public String getAnswer() {
      if (ex.getPredDerivations().size() == 0)
        return "(no answer)";
      else if (candidateIndex == -1)
        return "(not selected)";
      else {
        Derivation deriv = getDerivation();
        deriv.ensureExecuted(builder.executor, ex.context);
        return deriv.getValue().toString();
      }
    }
    public List<String> getLines() { return lines; }
    public Example getExample() { return ex; }
    public int getCandidateIndex() { return candidateIndex; }

    public Derivation getDerivation() {
      return ex.getPredDerivations().get(candidateIndex);
    }
  }

  private Builder builder;
  private Learner learner;
  private HashMap<String, Session> sessions = new LinkedHashMap<>();

  public Master(Builder builder) {
    this.builder = builder;
    this.learner = new Learner(builder.parser, builder.params, new Dataset());
  }

  public Params getParams() { return builder.params; }

  // Return the unique session identified by session id |id|.
  // Create a new session if one doesn't exist.
  public Session getSession(String id) {
    Session session = sessions.get(id);
    if (session == null) {
      session = new Session(id);

      if (opts.independentSessions) {
        session.useIndependentLearner(builder);
      }

      for (String path : opts.scriptPaths)
        processScript(session, path);
      for (String command : opts.commands)
        processQuery(session, command);
      if (id != null)
        sessions.put(id, session);
    }
    if (opts.independentSessions)
      builder.params = session.params;
    return session;
  }

  void printHelp() {
    LogInfo.log("Enter an utterance to parse or one of the following commands:");
    LogInfo.log("  (help): show this help message");
    LogInfo.log("  (status): prints out status of the system");
    LogInfo.log("  (get |option|): get a command-line option (e.g., (get Parser.verbose))");
    LogInfo.log("  (set |option| |value|): set a command-line option (e.g., (set Parser.verbose 5))");
    LogInfo.log("  (reload): reload the grammar/parameters");
    LogInfo.log("  (grammar): prints out the grammar");
    LogInfo.log("  (params [|file|]): dumps all the model parameters");
    LogInfo.log("  (select |candidate index|): show information about the |index|-th candidate of the last utterance.");
    LogInfo.log("  (accept |candidate index|): record the |index|-th candidate as the correct answer for the last utterance.");
    LogInfo.log("  (answer |answer|): record |answer| as the correct answer for the last utterance (e.g., (answer (list (number 3)))).");
    LogInfo.log("  (rule |lhs| (|rhs_1| ... |rhs_k|) |sem|): adds a rule to the grammar (e.g., (rule $Number ($TOKEN) (NumberFn)))");
    LogInfo.log("  (type |logical form|): perform type inference (e.g., (type (number 3)))");
    LogInfo.log("  (execute |logical form|): execute the logical form (e.g., (execute (call + (number 3) (number 4))))");
    LogInfo.log("  (def |key| |value|): define a macro to replace |key| with |value| in all commands (e.g., (def type fb:type.object type)))");
    LogInfo.log("  (context [(user |user|) (date |date|) (exchange |exchange|) (graph |graph|)]): prints out or set the context");
    // interactive commands, starting with the :
    LogInfo.log("  (:uttdef head [[body,bodyformula],[]]): provide a definition for the original utterance");
    LogInfo.log("  (:autocomplete |prefix|): provide a definition for the original utterance");
    LogInfo.log("  (:action |utternace|): commandline utility for performing an action on the world");
    LogInfo.log("Press Ctrl-D to exit.");
  }

  public void runInteractivePrompt() {
    Session session = getSession("stdin");

    if (opts.printHelp)
      printHelp();
    try {
      ConsoleReader reader = new ConsoleReader();
      reader.setPrompt("> ");
      String line;
      while ((line = reader.readLine()) != null) {
        int indent = LogInfo.getIndLevel();
        try {
          processQuery(session, line);
        } catch (Throwable t) {
          while (LogInfo.getIndLevel() > indent)
            LogInfo.end_track();
          t.printStackTrace();
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // Read LispTrees from |scriptPath| and process each of them.
  public void processScript(Session session, String scriptPath) {
    Iterator<LispTree> it = LispTree.proto.parseFromFile(scriptPath);
    while (it.hasNext()) {
      LispTree tree = it.next();
      processQuery(session, tree.toString());
    }
  }

  // Process user's input |line|
  // Currently, synchronize a very crude level.
  // In the future, refine this.
  // Currently need the synchronization because of writing to stdout.
  public Response processQuery(Session session, String line) {
    line = line.trim();
    Response response = new Response();

    if (!line.startsWith("(:") && opts.onlyInteractive)
      response.lines.add("command disabled for security: " + line);

    // Capture log output and put it into response.
    // Hack: modifying a static variable to capture the logging.
    // Make sure we're synchronized!
    // StringWriter stringOut = new StringWriter();
    // LogInfo.setFileOut(new PrintWriter(stringOut));

    if (line.startsWith("("))
      handleCommand(session, line, response);
    else
      handleUtterance(session, line, response);

    // Clean up
    // for (String outLine : stringOut.toString().split("\n"))
    //  response.lines.add(outLine);

    // Log interaction to disk
    if (!Strings.isNullOrEmpty(opts.logPath)) {
      PrintWriter out;
      out = IOUtils.openOutAppendHard(
          Paths.get(opts.logPath, session.id + ".log").toString());
      out.println(
          Joiner.on("\t").join(
              Lists.newArrayList(
                  "date=" + new Date().toString(),
                  "sessionId=" + session.id,
                  "remote=" + session.remoteHost,
                  "format=" + session.format,
                  "query=" + line,
                  "response=" + summaryString(response))));
      out.close();
    }

    return response;
  }

  String summaryString(Response response) {
    try {
    if (response.getExample() != null)
      return response.getFormulaAnswer();
    if (response.getLines().size() > 0)
      return response.getLines().get(0);
    } catch (Exception e) {
      return null;
    }
    return null;
  }

  private void handleUtterance(Session session, String query, Response response) {
    session.updateContext();

    // Create example
    Example.Builder b = new Example.Builder();
    b.setId("session:" + session.id);
    b.setUtterance(query);
    b.setContext(session.context);
    Example ex = b.createExample();

    ex.preprocess();

    // Parse!
    ParserState state;
    state = builder.parser.parse(builder.params, ex, false);

    response.ex = ex;
    ex.logWithoutContext();
    if (ex.predDerivations.size() > 0) {
      response.candidateIndex = 0;
      printDerivation(response.getDerivation());
    }
    session.updateContext(ex, opts.contextMaxExchanges);
  }

  private void printDerivation(Derivation deriv) {
    // Print features
    HashMap<String, Double> featureVector = new HashMap<>();
    deriv.incrementAllFeatureVector(1, featureVector);
    FeatureVector.logFeatureWeights("Pred", featureVector, builder.params);

    // Print choices
    Map<String, Integer> choices = new LinkedHashMap<>();
    deriv.incrementAllChoices(1, choices);
    FeatureVector.logChoices("Pred", choices);

    // Print denotation
    LogInfo.begin_track("Top formula");
    LogInfo.logs("%s", deriv.formula);
    LogInfo.end_track();
    if (deriv.value != null) {
      LogInfo.begin_track("Top value");
      deriv.value.log();
      LogInfo.end_track();
    }
  }

  void handleCommand(Session session, String line, Response response) {
    LispTree tree = LispTree.proto.parseFromString(line);
    tree = builder.grammar.applyMacros(tree);

    String command = tree.child(0).value;

    if (command == null || command.equals("help")) {
      printHelp();
    } else if (command.equals("status")) {
      LogInfo.begin_track("%d sessions", sessions.size());
      for (Session otherSession : sessions.values())
        LogInfo.log(otherSession + (session == otherSession ? " *" : ""));
      LogInfo.end_track();
      StopWatchSet.logStats();
    } else if (command.equals("reload")) {
      builder.build();
    } else if (command.equals("grammar")) {
      for (Rule rule : builder.grammar.rules)
        LogInfo.logs("%s", rule.toLispTree());
    } else if (command.equals("params")) {
      if (tree.children.size() == 1) {
        builder.params.write(LogInfo.stdout);
        if (LogInfo.getFileOut() != null)
          builder.params.write(LogInfo.getFileOut());
      } else {
        builder.params.write(tree.child(1).value);
      }
    } else if (command.equals("get")) {
      if (tree.children.size() != 2) {
        LogInfo.log("Invalid usage: (get |option|)");
        return;
      }
      String option = tree.child(1).value;
      LogInfo.logs("%s", getOptionsParser().getValue(option));
    } else if (command.equals("set")) {
      if (tree.children.size() != 3) {
        LogInfo.log("Invalid usage: (set |option| |value|)");
        return;
      }
      String option = tree.child(1).value;
      String value = tree.child(2).value;
      if (!getOptionsParser().parse(new String[] {"-" + option, value}))
        LogInfo.log("Unknown option: " + option);
    } else if (command.equals("select") || command.equals("accept") ||
        command.equals("s") || command.equals("a")) {
      // Select an answer
      if (tree.children.size() != 2) {
        LogInfo.logs("Invalid usage: (%s |candidate index|)", command);
        return;
      }

      Example ex = session.getLastExample();
      if (ex == null) {
        LogInfo.log("No examples - please enter a query first.");
        return;
      }
      int index = Integer.parseInt(tree.child(1).value);
      if (index < 0 || index >= ex.predDerivations.size()) {
        LogInfo.log("Candidate index out of range: " + index);
        return;
      }

      response.ex = ex;
      response.candidateIndex = index;
      session.updateContextWithNewAnswer(ex, response.getDerivation());
      printDerivation(response.getDerivation());

      // Add a training example.  While the user selects a particular derivation, there are three ways to interpret this signal:
      // 1. This is the correct derivation (Derivation).
      // 2. This is the correct logical form (Formula).
      // 3. This is the correct denotation (Value).
      // Currently:
      // - Parameters based on the denotation.
      // - Grammar rules are induced based on the denotation.
      // We always save the logical form and the denotation (but not the entire
      // derivation) in the example.
      if (command.equals("accept") || command.equals("a")) {
        ex.setTargetFormula(response.getDerivation().getFormula());
        ex.setTargetValue(response.getDerivation().getValue());
        ex.setContext(session.getContextExcludingLast());
        addNewExample(ex);
      }
    } else if (command.equals("answer")) {
      if (tree.children.size() != 2) {
        LogInfo.log("Missing answer.");
      }

      // Set the target value.
      Example ex = session.getLastExample();
      if (ex == null) {
        LogInfo.log("Please enter a query first.");
        return;
      }
      ex.setTargetValue(Values.fromLispTree(tree.child(1)));
      addNewExample(ex);
    } else if (command.equals("rule")) {
      int n = builder.grammar.rules.size();
      builder.grammar.addStatement(tree.toString());
      for (int i = n; i < builder.grammar.rules.size(); i++)
        LogInfo.logs("Added %s", builder.grammar.rules.get(i));
      // Need to update the parser given that the grammar has changed.
      builder.parser = null;
      builder.buildUnspecified();
    } else if (command.equals("type")) {
      LogInfo.logs("%s", TypeInference.inferType(Formulas.fromLispTree(tree.child(1))));
    } else if (command.equals("execute")) {
      Example ex = session.getLastExample();
      ContextValue context = (ex != null ? ex.context : session.context);
      Executor.Response execResponse = builder.executor.execute(Formulas.fromLispTree(tree.child(1)), context);
      LogInfo.logs("%s", execResponse.value);
    } else if (command.equals("def")) {
      builder.grammar.interpretMacroDef(tree);
    } else if (command.equals("context")) {
      if (tree.children.size() == 1) {
        LogInfo.logs("%s", session.context);
      } else {
        session.context = new ContextValue(tree);
      }
    } else if (command.equals("loadgraph")) {
      if (tree.children.size() != 2 || !tree.child(1).isLeaf())
        throw new RuntimeException("Invalid argument: argument should be a file path");
      KnowledgeGraph graph = NaiveKnowledgeGraph.fromFile(tree.child(1).value);
      session.context = new ContextValue(session.context.user, session.context.date,
        session.context.exchanges, graph);
    }

    // Start of interactive commands
    else if (command.equals(":q")) {
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

    } else if (command.equals(":qdebug")) {
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
            PrintWriter out = IOUtils.openOutAppendHard(Paths.get(Master.opts.intOutputPath, "grammar.log.json").toString());
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
      builder.params.write(Paths.get(Master.opts.intOutputPath, "params.params").toString());
      PrintWriter out = IOUtils.openOutAppendHard(Paths.get(Master.opts.intOutputPath + "grammar.final.json").toString());
      for (Rule rule : builder.grammar.rules) {
        out.println(rule.toJson());
      }
      out.close();
      LogInfo.logs("Done printing and overriding grammar and parameters...");
    } else if (command.equals(":action")) {
      // test code for mutating worlds, updates the context
      String query = tree.children.get(1).value;
      this.handleUtterance(session, query, response);
      LogInfo.logs("%s : %s", query, response.getAnswer());
      String blocks = ((StringValue)Values.fromString(response.getAnswer())).value;
      String strigify2 = Json.writeValueAsStringHard(blocks); // some parsing issue inside lisptree parser
      session.context = ContextValue.fromString(String.format("(context (graph NaiveKnowledgeGraph ((string \"%s\") (name b) (name c))))", strigify2));
    } else if (command.equals(":context")) {
      if (tree.children.size() == 1) {
        LogInfo.logs("%s", session.context);
      } else {
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

  private Example exampleFromUtterance(String utt, Session session) {
    Example.Builder b = new Example.Builder();
    b.setId(session.id);
    b.setUtterance(utt);
    b.setContext(session.context);
    Example ex = b.createExample();
    ex.preprocess();
    return ex;
  }

  void addNewExample(Example origEx) {
    // Create the new example, but only add relevant information.
    Example ex = new Example.Builder()
        .setId(origEx.id)
        .setUtterance(origEx.utterance)
        .setContext(origEx.context)
        .setTargetFormula(origEx.targetFormula)
        .setTargetValue(origEx.targetValue)
        .createExample();

    if (!Strings.isNullOrEmpty(opts.newExamplesPath)) {
      LogInfo.begin_track("Adding new example");
      Dataset.appendExampleToFile(opts.newExamplesPath, ex);
      LogInfo.end_track();
    }

    if (opts.onlineLearnExamples) {
      LogInfo.begin_track("Updating parameters");
      learner.onlineLearnExample(origEx);
      if (!Strings.isNullOrEmpty(opts.newParamsPath))
        builder.params.write(opts.newParamsPath);
      LogInfo.end_track();
    }
  }

  public static OptionsParser getOptionsParser() {
    OptionsParser parser = new OptionsParser();
    // Dynamically figure out which options we need to load
    // To specify this:
    //   java -Dmodules=core,freebase
    List<String> modules = Arrays.asList(System.getProperty("modules", "core").split(","));

    // All options are assumed to be of the form <class>opts.
    // Read the module-classes.txt file, which specifies which classes are
    // associated with each module.
    List<Object> args = new ArrayList<Object>();
    for (String line : IOUtils.readLinesHard("module-classes.txt")) {

      // Example: core edu.stanford.nlp.sempre.Grammar
      String[] tokens = line.split(" ");
      if (tokens.length != 2) throw new RuntimeException("Invalid: " + line);
      String module = tokens[0];
      String className = tokens[1];
      if (!modules.contains(tokens[0])) continue;

      // Group (e.g., Grammar)
      String[] classNameTokens = className.split("\\.");
      String group = classNameTokens[classNameTokens.length - 1];

      // Object (e.g., Grammar.opts)
      Object opts = null;
      try {
        for (Field field : Class.forName(className).getDeclaredFields()) {
          if (!"opts".equals(field.getName())) continue;
          opts = field.get(null);
        }
      } catch (Throwable t) {
        System.out.println("Problem processing: " + line);
        throw new RuntimeException(t);
      }

      if (opts != null) {
        args.add(group);
        args.add(opts);
      }
    }

    parser.registerAll(args.toArray(new Object[0]));
    return parser;
  }
}
