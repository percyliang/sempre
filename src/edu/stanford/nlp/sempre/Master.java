package edu.stanford.nlp.sempre;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import edu.stanford.nlp.sempre.fbalignment.lexicons.BinaryLexicon;
import edu.stanford.nlp.sempre.fbalignment.lexicons.EntityLexicon;
import edu.stanford.nlp.sempre.fbalignment.lexicons.UnaryLexicon;
import edu.stanford.nlp.sempre.fbalignment.lexicons.WordDistance;
import fig.basic.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

/**
 * A Master manages multiple sessions. Currently, they all share the same model,
 * but they need not in the future.
 */
public class Master {
  public static class Options {
    @Option(gloss = "Execute these commands before starting")
    public List<String> scriptPaths = Lists.newArrayList();
    @Option(gloss = "Write out new examples to this directory")
    public String newExamplesPath;
    @Option(gloss = "Write out input lines to this path") public String logPath;
    @Option(gloss = "Online update weights on new examples.")
    public boolean onlineLearnExamples;
  }

  public static Options opts = new Options();

  public class Response {
    // Example that was parsed, if any.
    Example ex;

    // Which derivation we're selecting to show
    int candidateIndex = -1;

    // Detailed information
    List<String> lines = new ArrayList<String>();

    public String getAnswer() {
      if (ex.getPredDerivations().size() == 0)
        return "(no answer)";
      else {
        Derivation deriv = getDerivation();
        deriv.ensureExecuted(builder.executor);
        return getDerivation().getValue().toString();
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
  private HashMap<String, Session> sessions = new LinkedHashMap<String, Session>();

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
      for (String path : opts.scriptPaths)
        processScript(session, path);
      sessions.put(id, session);
    }
    return session;
  }

  void printHelp() {
    LogInfo.log("Enter an utterance to parse or one of the following commands:");
    LogInfo.log("  (help): show this help message");
    LogInfo.log("  (status): prints out status of the system");
    LogInfo.log("  (set |option| |value|): set a command-line option (e.g., (set BeamParser.verbose 3))");
    LogInfo.log("  (reload): reload the grammar/parameters");
    LogInfo.log("  (grammar): prints out the grammar");
    LogInfo.log("  (params): dumps all the model parameters");
    LogInfo.log("  (select |candidate index|): show information about the |index|-th candidate of the last utterance.");
    LogInfo.log("  (accept |candidate index|): record the |index|-th candidate as the correct answer for the last utterance.");
    LogInfo.log("  (answer |answer|): record |answer| as the correct answer for the last utterance (e.g., (answer (list (number 3)))).");
    LogInfo.log("  (rule |lhs| (|rhs_1| ... |rhs_k|) |sem|): adds a rule to the grammar (e.g., (rule $Number ($TOKEN) (NumberFn)))");
    LogInfo.log("  (execute |logical form|): adds a rule to the grammar (e.g., (execute (call + (number 3) (number 4))))");
    LogInfo.log("  (def |key| |value|): define a macro to replace |key| with |value| in all commands (e.g., (def type fb:type.object type)))");
  }

  public void runInteractivePrompt() {
    Session session = getSession("stdin");

    printHelp();
    LogInfo.log("Press Ctrl-D to exit.");

    while (true) {
      LogInfo.stdout.print("> ");
      LogInfo.stdout.flush();
      String line;
      try {
        line = LogInfo.stdin.readLine();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      if (line == null) break;

      try {
        processLine(session, line);
      } catch (Throwable t) {
        t.printStackTrace();
      }
    }
  }

  // Read LispTrees from |scriptPath| and process each of them.
  public void processScript(Session session, String scriptPath) {
    Iterator<LispTree> it = LispTree.proto.parseFromFile(scriptPath);
    while (it.hasNext()) {
      LispTree tree = it.next();
      processLine(session, tree.toString());
    }
  }

  // Process user's input |line|
  // Currently, synchronize a very crude level.
  // In the future, refine this.
  // Currently need the synchronization because of writing to stdout.
  public synchronized Response processLine(Session session, String line) {
    Response response = new Response();

    line = line.trim();
    if (line.startsWith("("))
      handleCommand(session, line, response);
    else
      handleQuery(session, line, response);

    // Log interaction to disk
    if (!Strings.isNullOrEmpty(opts.logPath)) {
      PrintWriter out = IOUtils.openOutAppendHard(opts.logPath);
      out.println(
          Joiner.on("\t").join(
              Lists.newArrayList(
                  new Date().toString(),
                  session.id,
                  session.lastRemoteAddr != null ? session.lastRemoteAddr : "(none)",
                  line,
                  summaryString(response))));
      out.close();
    }

    return response;
  }

  String summaryString(Response response) {
    if (response.getExample() != null)
      return response.getAnswer();
    if (response.getLines().size() > 0)
      return response.getLines().get(0);
    return null;
  }

  private void handleQuery(Session session, String query, Response response) {
    session.lastQuery = query;

    Example.Builder b = new Example.Builder();

    b.setId("session:" + session.id);
    int slashIndex = query.indexOf('/');
    if (slashIndex != -1) {
      // if query = "where was obama born? /place_of_birth", that constrains
      // derivations to only ones whose formula contains place_of_birth.
      b.setDerivConstraint(new DerivationConstraint(query.substring(slashIndex + 1)));
      query = query.substring(0, slashIndex).trim();
    }
    b.setUtterance(query);

    Example ex = b.createExample();
    ex.preprocess();

    // Parse!
    builder.parser.parse(builder.params, ex);

    session.examples.add(ex);
    response.ex = ex;
    if (ex.predDerivations.size() > 0) {
      response.candidateIndex = 0;
      printDerivation(response.getDerivation());
    }
  }

  private void printDerivation(Derivation deriv) {
    // Print features
    HashMap<String, Double> featureVector = new HashMap<String, Double>();
    deriv.incrementAllFeatureVector(1, featureVector);
    FeatureVector.logFeatureWeights("Pred", featureVector, builder.params);

    // Print choices
    Map<String, Integer> choices = new LinkedHashMap<String, Integer>();
    deriv.incrementAllChoices(1, choices);
    FeatureVector.logChoices("Pred", choices);

    // Print denotation
    if (deriv.value != null) {
      LogInfo.begin_track("Top value");
      deriv.value.log();
      LogInfo.end_track();
    }
  }

  private void handleCommand(Session session, String line, Response response) {
    // Capture log output and put it into response.
    // Hack: modifying a static variable to capture the logging.
    // Make sure we're synchronized!
    StringWriter stringOut = new StringWriter();
    LogInfo.setFileOut(new PrintWriter(stringOut));

    handleCommandHelper(session, line, response);

    for (String outLine : stringOut.toString().split("\n"))
      response.lines.add(outLine);

    LogInfo.setFileOut(null);
  }

  LispTree applyMacros(Session session, LispTree tree) {
    if (tree.isLeaf()) {
      LispTree replacement = session.macros.get(tree.value);
      if (replacement != null) return replacement;
      return tree;
    }
    LispTree newTree = LispTree.proto.newList();
    for (LispTree child : tree.children)
      newTree.addChild(applyMacros(session, child));
    return newTree;
  }

  private void handleCommandHelper(Session session, String line, Response response) {
    LispTree tree = LispTree.proto.parseFromString(line);
    tree = applyMacros(session, tree);

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
      builder.params.write(LogInfo.stdout);
    } else if (command.equals("set")) {
      if (tree.children.size() != 3) {
        LogInfo.log("Invalid usage: (set |key| |value|)");
        return;
      }
      String key = tree.child(1).value;
      String value = tree.child(2).value;
      if (!getOptionsParser().parse(new String[]{"-" + key, value}))
        LogInfo.log("Unknown option: " + key);
    } else if (command.equals("select") || command.equals("accept")) {
      // Select an answer
      if (tree.children.size() != 2) {
        LogInfo.logs("Invalid usage: (%s |candidate index|)", command);
        return;
      }

      if (session.examples.size() == 0) {
        LogInfo.log("No examples - please enter a query first.");
        return;
      }

      Example ex = session.examples.get(session.examples.size() - 1);
      int index = Integer.parseInt(tree.child(1).value);
      if (index < 0 || index >= ex.predDerivations.size()) {
        LogInfo.log("Candidate index out of range: " + index);
        return;
      }

      response.ex = ex;
      response.candidateIndex = index;

      printDerivation(response.getDerivation());

      // Set logical form
      if (command.equals("accept")) {
        ex.setTargetFormula(response.getDerivation().getFormula());
        ex.setTargetValue(response.getDerivation().getValue());
        addNewExample(ex);
      }
    } else if (command.equals("answer")) {
      if (tree.children.size() != 2) {
        LogInfo.log("Missing answer.");
      }

      if (session.examples.size() == 0) {
        LogInfo.log("No examples - please enter a query first.");
        return;
      }

      // Set the target value.
      Example ex = session.examples.get(session.examples.size() - 1);
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
    } else if (command.equals("execute")) {
      Executor.Response execResponse = builder.executor.execute(Formulas.fromLispTree(tree.child(1)));
      LogInfo.logs("%s", execResponse.value);
    } else if (command.equals("def")) {
      if (tree.children.size() != 3 || !tree.child(1).isLeaf()) {
        LogInfo.log("Invalid usage: (def |name| |value|)");
        return;
      }
      session.macros.put(tree.child(1).value, tree.child(2));
    } else {
      LogInfo.log("Invalid command: " + tree);
    }
  }

  void addNewExample(Example origEx) {
    // Create the new example, but only add relevant information.
    Example ex = new Example.Builder()
        .setId(origEx.id)
        .setUtterance(origEx.utterance)
        .setTargetFormula(origEx.targetFormula)
        .setTargetValue(origEx.targetValue)
        .createExample();

    if (!Strings.isNullOrEmpty(opts.newExamplesPath)) {
      LogInfo.log("Added new example.");
      PrintWriter out = IOUtils.openOutAppendHard(opts.newExamplesPath);
      out.println(ex.toJson());
      out.close();
    }

    if (opts.onlineLearnExamples) {
      LogInfo.log("Updating parameters.");
      learner.onlineLearnExample(origEx);
    }
  }

  public static OptionsParser getOptionsParser() {
    OptionsParser parser = new OptionsParser();
    parser.registerAll(
        new Object[]{
            "Master", Master.opts,
            "Builder", Builder.opts,
            "Grammar", Grammar.opts,
            "Derivation", Derivation.opts,
            "Parser", Parser.opts,
            "BeamParser", BeamParser.opts,
            "SparqlExecutor", SparqlExecutor.opts,
            "Dataset", Dataset.opts,
            "Params", Params.opts,
            "Learner", Learner.opts,
            "SemanticFn", SemanticFn.opts,
            "LexiconFn", LexiconFn.opts,
            "SelectFn", SelectFn.opts,
            "MergeFn", MergeFn.opts,
            "JoinFn", JoinFn.opts,
            "DescriptionValue", DescriptionValue.opts,
            "FeatureExtractor", FeatureExtractor.opts,
            "LanguageInfo", LanguageInfo.opts,
            "EntityLexicon", EntityLexicon.opts,
            "BinaryLexicon", BinaryLexicon.opts,
            "UnaryLexicon", UnaryLexicon.opts,
            "FreebaseInfo", FreebaseInfo.opts,
            "BridgeFn", BridgeFn.opts,
            "WordDistance", WordDistance.opts,
        });
    return parser;
  }
}
