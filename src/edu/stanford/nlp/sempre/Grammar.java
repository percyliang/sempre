package edu.stanford.nlp.sempre;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import fig.basic.*;
import fig.exec.Execution;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;

/**
 * The grammar is a set of rules of the form:
 *   (rule lhsCat (rhs ... rhs) semanticFn (key value) ... (key value))

 * Some programming-language-esque features are supported to make life easier.
 * All grammar-file variables should start with '@' (both loop variables and global definitions).
 * Includes (reads in commands from the given file):
 *   (include base.grammar)
 * Control flow:
 *   (when (and tag1 (not tag2)) ...)
 * Macro definitions:
 *   (def @type fb:type.object.type)
 * Iteration:
 *   (for @x (a an the) (rule $DT (@x) (IdentityFn)))
 *
 * @author Percy Liang
 */
public class Grammar {
  public static class Options {
    @Option public List<String> inPaths = new ArrayList<>();
    @Option(gloss = "Variables which are used to interpret the grammar file")
    public List<String> tags = new ArrayList<>();
    @Option public boolean binarizeRules = true;
  }

  public static Options opts = new Options();

  // All the rules in the grammar.  Each parser can read these and transform
  // them however the parser wishes.
  // This contains binarized rules
  ArrayList<Rule> rules = new ArrayList<>();
  public List<Rule> getRules() { return rules; }

  Map<String, LispTree> macros = new HashMap<>();  // Map from macro name to its replacement value

  // Verbatim copy of all the lines read, so we can preserve the grammar file.
  List<String> statements = new ArrayList<>();

  public void read() {
    LogInfo.begin_track("Grammar.read");
    read(opts.inPaths);
    LogInfo.logs("%s rules", rules.size());
    LogInfo.end_track();
  }

  public void read(String path) { read(Collections.singletonList(path)); }
  public void read(List<String> paths) {
    for (String path : paths)
      readOnePath(path, Sets.newHashSet(opts.tags));
    verifyValid();
  }

  private void verifyValid() {
    // Make sure that all the categories which are used are actually defined.
    Set<String> defined = new HashSet<>();
    defined.add(Rule.tokenCat);
    defined.add(Rule.phraseCat);
    defined.add(Rule.lemmaTokenCat);
    defined.add(Rule.lemmaPhraseCat);
    for (Rule rule : rules)
      defined.add(rule.lhs);

    // Make sure every non-terminal is defined
    for (Rule rule : rules) {
      for (String item : rule.rhs) {
        if (Rule.isCat(item) && !defined.contains(item)) {
          LogInfo.warnings("Category not defined in the grammar: %s; used in rule: %s", item, rule);
        }
      }
    }
  }

  /**
   * @param contextPath Path relative to which grammar includes and
   * such are expanded.
   */
  public void addStatement(String stmt, String contextPath, Set<String> tags) {
    statements.add(stmt);
    interpret(contextPath, LispTree.proto.parseFromString(stmt), Sets.newHashSet(Iterables.concat(tags, opts.tags)));
  }

  public void addStatement(String stmt, String contextPath) {
    Set<String> s = Collections.emptySet();
    addStatement(stmt, null, s);
  }

  public void addStatement(String stmt) {
    addStatement(stmt, null);
  }

  // Replace all leaves of LispTree with value in macros if exists
  private static LispTree applyMacros(Map<String, LispTree> macros, LispTree tree) {
    if (tree.isLeaf()) {
      LispTree replacement = macros.get(tree.value);
      if (replacement != null) return replacement;
      return tree;
    }
    LispTree newTree = LispTree.proto.newList();
    for (LispTree child : tree.children)
      newTree.addChild(applyMacros(macros, child));
    return newTree;
  }

  // Apply the macro substitutions to |tree|.
  public LispTree applyMacros(LispTree tree) {
    return applyMacros(this.macros, tree);
  }

  private void readOnePath(String path, Set<String> tags) {
    // Save raw lines
    if (statements.size() > 0) statements.add("");
    statements.add("####### " + path);
    for (String line : IOUtils.readLinesHard(path))
      statements.add(line);

    Iterator<LispTree> trees = LispTree.proto.parseFromFile(path);
    while (trees.hasNext())
      interpret(path, trees.next(), tags);
  }

  public void write() {
    String path = Execution.getFile("grammar");
    if (path == null) return;
    PrintWriter out = IOUtils.openOutHard(path);
    for (String statement : statements)
      out.println(statement);
    out.close();

    out = IOUtils.openOutHard(Execution.getFile("processed-grammar"));
    for (Rule rule : rules)
      out.println(rule.toLispTree().toString());
    out.close();
  }

  private void interpret(String path, LispTree tree, Set<String> tags) {
    if (tree.isLeaf())
      throw new RuntimeException("Expected list, got " + tree);

    try {
      String command = tree.child(0).value;
      if ("rule".equals(command)) {
        interpretRule(tree);
      } else if ("include".equals(command)) {
        if (path == null) {
          throw new RuntimeException(
              "Grammar include statement given without context path");
        }
        for (int i = 1; i < tree.children.size(); i++)
          readOnePath(new File(path).getParent() + "/" + tree.child(i).value, tags);
      } else if ("when".equals(command)) {
        if (interpretBoolean(tree.child(1), tags)) {
          for (int i = 2; i < tree.children.size(); i++)
            interpret(path, tree.child(i), tags);
        }
      } else if ("def".equals(command)) {
        interpretMacroDef(tree);
      } else if ("for".equals(command)) {
        interpretFor(path, tree, tags);
      } else {
        throw new RuntimeException("Invalid command: " + command);
      }
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException("Error on " + tree + ": " + e);
    }
  }

  private boolean interpretBoolean(LispTree tree, Set<String> tags) {
    if (tree.isLeaf())
      return tags.contains(tree.value);
    if ("not".equals(tree.child(0).value))
      return !interpretBoolean(tree.child(1), tags);
    if ("and".equals(tree.child(0).value))
      return interpretBoolean(tree.child(1), tags) && interpretBoolean(tree.child(2), tags);
    if ("or".equals(tree.child(0).value))
      return interpretBoolean(tree.child(1), tags) || interpretBoolean(tree.child(2), tags);
    throw new RuntimeException("Expected a single tag, but got: " + tree);
  }

  public void interpretMacroDef(LispTree tree) {
    if (tree.children.size() != 3 || !tree.child(1).isLeaf()) {
      throw new RuntimeException("Invalid usage: (def |name| |value|)");
    }
    String var = tree.child(1).value;
    checkIsValidVar(var);
    macros.put(var, applyMacros(tree.child(2)));
  }

  public void interpretFor(String path, LispTree tree, Set<String> tags) {
    if (tree.children.size() <= 3 || !tree.child(1).isLeaf()) {
      throw new RuntimeException("Invalid usage: (for |var| (|value| ... |value|) |statement| ...)");
    }

    String var = tree.child(1).value;
    checkIsValidVar(var);
    List<LispTree> values = applyMacros(tree.child(2)).children;
    LispTree old = macros.get(var);
    for (LispTree value : values) {
      macros.put(var, value);
      for (int j = 3; j < tree.children.size(); j++)
        interpret(path, tree.child(j), tags);
    }
    if (old == null) macros.remove(var);
    else macros.put(var, old);
  }

  private void interpretRule(LispTree tree) {
    if (tree.children.size() < 4)
      throw new RuntimeException("Invalid rule: " + tree);

    // (rule lhs rhs semantics (key value) ... (key value))
    tree = applyMacros(tree);

    // Parse LHS
    if (!tree.child(1).isLeaf())
      throw new RuntimeException("Invalid LHS: " + tree.child(1));
    String lhs = tree.child(1).value;

    // Parse RHS
    List<String> rhs = Lists.newArrayList();
    List<Boolean> isOptionals = new ArrayList<>();
    LispTree rhsTree = tree.child(2);
    if (rhsTree.isLeaf())
      throw new RuntimeException("RHS needs to be list, but got: " + rhsTree);
    for (int i = 0; i < rhsTree.children.size(); i++) {
      LispTree child = rhsTree.child(i);
      boolean isOptional = false;
      if (child.isLeaf()) {  // $PHRASE
        rhs.add(child.value);
      } else {
        // e.g., ($PHRASE optional)
        // First item is the token/category; the rest of the items
        // specify parameters (currently, only parameter is optional).
        rhs.add(child.child(0).value);
        for (int j = 1; j < child.children.size(); j++)
          if (child.child(j).value.equals("optional"))
            isOptional = true;
      }
      isOptionals.add(isOptional);
    }

    // Parse semantic function
    SemanticFn sem = parseSemanticFn(tree.child(3));

    Rule rule = new Rule(lhs, rhs, sem);

    // Parse extra info
    for (int i = 4; i < tree.children.size(); i++) {
      LispTree item = tree.child(i);
      if (!item.isLeaf() && item.children.size() != 2)
        throw new RuntimeException("Invalid key-value pair: " + item);
      try {
        rule.addInfo(item.child(0).value, Double.parseDouble(item.child(1).value));
      } catch (NumberFormatException e) {
        throw new RuntimeException("Invalid key-value pair: " + item);
      }
    }

    addRule(rule, isOptionals);
  }

  public synchronized boolean addRule(Rule rule) {
    List<Boolean> isOptionals = new ArrayList<>();
    for (String rhs : rule.rhs) isOptionals.add(false);
    return addRule(rule, isOptionals);
  }

  // Add a rule to the grammar.
  public synchronized boolean addRule(Rule rule, List<Boolean> isOptionals) {
    rules.addAll(binarizeRule(rule, isOptionals));
    return true;
  }

  // Generate intermediate categories for binarization.
  private int freshCatIndex = 0;
  private String generateFreshCat() {
    freshCatIndex++;
    return "$Intermediate" + freshCatIndex;
    // return "$I" + freshCatIndex + ":" + content;
  }

  // Create multiple versions of this rule if there are optional RHS.
  // Restriction: must be able to split the RHS into two halves, each of
  // which contains at most one non-optional category.
  // Recall that only the non-optional categories on the RHS are arguments into the SemanticFn.
  // Example: stop? $A stop $Stop? $B stop $Stop?
  private List<Rule> binarizeRule(Rule rule, List<Boolean> isOptionals) {
    List<Rule> newRules = new ArrayList<>();

    // Special case: JoinFn with an arg0Fn but with multiple non-optional categories.
    // In this case, we need to use arg0Fn on just the first such category, and
    // then use function application on the rest.
    // Old: (rule $A (a ($Z optional) $B $C $D) (JoinFn (arg0 (lambda b (lambda c (lambda d ...))))))
    // New rules to binarize ($B is the first non-optional category):
    // 1. (rule $I1 (a ($Z optional) $B) (JoinFn (arg0 (lambda b (lambda c (lambda d ...))))))
    // 2. (rule $A ($I1 $C $D) (JoinFn forward betaReduce))
    if (rule.sem instanceof JoinFn && ((JoinFn) rule.sem).getArg0Fn() != null) {
      // Find the first non-optional category
      int i = 0;
      while (i < rule.rhs.size() && !(Rule.isCat(rule.rhs.get(i)) && !isOptionals.get(i)))
        i++;
      // Find the next non-optional category
      int j = i + 1;
      while (j < rule.rhs.size() && !(Rule.isCat(rule.rhs.get(j)) && !isOptionals.get(j)))
        j++;
      // If one exists, then we have to invoke special binarization
      if (j < rule.rhs.size()) {
        // Create an intermediate category
        String intCat = generateFreshCat();

        // Add rule 1
        List<String> rhs1 = new ArrayList<>(rule.rhs.subList(0, i + 1));
        newRules.addAll(binarizeRule(new Rule(intCat, rhs1, rule.sem).setInfo(rule), isOptionals.subList(0, i + 1)));

        // Add rule 2
        List<String> rhs2 = new ArrayList<>();
        rhs2.add(intCat);
        rhs2.addAll(rule.rhs.subList(i + 1, rule.rhs.size()));
        SemanticFn forwardBetaReduce = new JoinFn();
        forwardBetaReduce.init(LispTree.proto.parseFromString("(JoinFn forward betaReduce)"));
        newRules.addAll(binarizeRule(new Rule(rule.lhs, rhs2, forwardBetaReduce).setInfo(rule), isOptionals.subList(i, isOptionals.size())));

        return newRules;
      }
    }

    // Don't binarize: do same as before
    if (!opts.binarizeRules) {
      if (isOptionals.contains(true))
        throw new RuntimeException("Can't have optionals if don't binarize: " + rule + " " + isOptionals);
      newRules.add(rule);
      return newRules;
    }

    if (!isOptionals.contains(false))
      throw new RuntimeException("Can't have all RHS items be optional: " + rule + " " + isOptionals);

    // Unaries: don't need to binarize
    if (rule.rhs.size() == 1) {
      newRules.add(rule);
      return newRules;
    }

    // Stores the current RHS that we're building up.
    List<String> newRhs = new ArrayList<>();
    List<Boolean> newIsOptional = new ArrayList<>();
    List<Boolean> isRequiredCat = new ArrayList<>();  // These are the arguments to the semantic function

    // Left-binarize.
    assert rule.rhs.size() >= 2;
    boolean appliedRuleSem = false;
    for (int i = 0; i < rule.rhs.size(); i++) {
      newRhs.add(rule.rhs.get(i));
      newIsOptional.add(isOptionals.get(i));
      isRequiredCat.add(!isOptionals.get(i) && Rule.isCat(rule.rhs.get(i)));

      // Aim is to create rules with two RHS required categories
      // (binarized rules + tokens which don't cost anything).
      // Sometimes semantic functions take more than one argument.
      // Note: we assume they just left-binarize.
      if (newRhs.size() < 2) // This should only happen in the beginning
        continue;

      if (isRequiredCat.get(0) && isRequiredCat.get(1))
        appliedRuleSem = true;

      boolean atEnd = (i == rule.rhs.size() - 1);
      // TODO(pliang): currently, too many intermediate categories are created.  Remove
      // to make flatter grammars (will generate fewer derivations).
      String lhs = atEnd && appliedRuleSem ? rule.lhs : generateFreshCat();

      // Create rule with newRhs possibly excluding the optionals (there should be at most 2)
      assert (newRhs.size() == 2);
      boolean allCanBeOptional = false;
      for (int b0 = 0; b0 < 2; b0++) {  // Whether to include newRhs.get(0)
        if (b0 == 0 && !newIsOptional.get(0)) continue;
        for (int b1 = 0; b1 < 2; b1++) {  // Whether to include newRhs.get(1)
          if (b1 == 0 && !newIsOptional.get(1)) continue;

          List<String> rhs = Lists.newArrayList();
          SemanticFn sem;

          if (b0 == 1) rhs.add(newRhs.get(0));
          if (b1 == 1) rhs.add(newRhs.get(1));

          if (isRequiredCat.get(0) && isRequiredCat.get(1))
            sem = rule.sem;
          else if (b0 == 1 && Rule.isCat(rhs.get(0)) && isRequiredCat.get(1))
            sem = new SelectFn(1);
          else if (isRequiredCat.get(0) || isRequiredCat.get(1))
            sem = new SelectFn(0);
          else
            sem = new ConstantFn(Formula.nullFormula);

          // We can't allow empty RHS, but if we need it, just mark it as all
          // can be optional.
          if (rhs.size() > 0)
            newRules.add(new Rule(lhs, rhs, sem).setInfo(rule));
          else
            allCanBeOptional = true;
        }
      }
      boolean req = isRequiredCat.get(0) || isRequiredCat.get(1);

      // Replace with new category.
      newRhs.clear(); newRhs.add(lhs);
      newIsOptional.clear(); newIsOptional.add(allCanBeOptional);
      isRequiredCat.clear(); isRequiredCat.add(req);
    }

    assert newRhs.size() == 1;
    assert !newIsOptional.get(0);

    // Final unary rule if needed
    if (!appliedRuleSem)
      newRules.add(new Rule(rule.lhs, Lists.newArrayList(newRhs), rule.sem).setInfo(rule));

    // LogInfo.begin_track("binarize %s", rule);
    // for (Rule r : newRules) LogInfo.logs("%s", r);
    // LogInfo.end_track();

    return newRules;
  }

  // Examples of |tree|
  //   (ConstantFn null)
  //   (NumberFn)
  private SemanticFn parseSemanticFn(LispTree tree) {
    // Syntactic sugar: foo => (ConstantFn foo)
    if (tree.isLeaf()) {
      LispTree newTree = LispTree.proto.newList();
      newTree.addChild("ConstantFn");
      newTree.addChild(tree.value);
      tree = newTree;
    }

    String name = tree.child(0).value;

    // Syntactic sugar: (lambda x (var x)) => (JoinFn betaReduce forward (arg0 (lambda x (var x))))
    if (name.equals("lambda")) {
      LispTree newTree = LispTree.proto.newList();
      newTree.addChild("JoinFn");
      newTree.addChild("betaReduce");
      newTree.addChild("forward");
      newTree.addChild(LispTree.proto.newList("arg0", tree));
      tree = newTree;
      name = tree.child(0).value;
    }

    // For backward compatibility: SemanticFn which have moved.
    if (name.equals("LexiconFn") || name.equals("BridgeFn"))
      name = "freebase." + name;

    SemanticFn fn;
    fn = (SemanticFn) Utils.newInstanceHard(SempreUtils.resolveClassName(name));
    if (fn == null)
      throw new RuntimeException("Invalid SemanticFn name: " + name);

    fn.init(tree);
    return fn;
  }

  private void checkIsValidVar(String var) {
    if (!var.startsWith("@"))
      LogInfo.errors("Invalid variable: '%s' doesn't start with '@'", var);
  }
}
