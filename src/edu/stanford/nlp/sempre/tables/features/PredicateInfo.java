package edu.stanford.nlp.sempre.tables.features;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.*;
import fig.basic.*;

/**
 * Represents a predicate in the formula.
 *
 * Also contains additional information such as type and original string.
 *
 * @author ppasupat
 */
public class PredicateInfo {
  public static class Options {
    @Option(gloss = "Consider the formula types when generating the predicate list")
    public boolean traverseWithFormulaTypes = false;
    @Option(gloss = "Conversion between (reverse [NameValue]) and ![NameValue]")
    public ReverseNameValueConversion reverseNameValueConversion = ReverseNameValueConversion.none;
    @Option(gloss = "Allow repreated predicates")
    public boolean allowRepeats = false;
    @Option(gloss = "Maximum length of predicate string")
    public int maxPredicateLength = 40;
    @Option(gloss = "Perform beta reduction before finding predicates")
    public boolean betaReduce = false;
  }
  public static Options opts = new Options();

  static enum ReverseNameValueConversion { allBang, allReverse, none };

  static enum PredicateType { KEYWORD, ENTITY, BINARY };

  public final String predicate;
  public final String originalString;
  public final PredicateType type;

  public PredicateInfo(String predicate, ContextValue context) {
    this.predicate = predicate;
    this.type = inferType(predicate);
    String s = getOriginalString(predicate, context);
    this.originalString = (s == null) ? null : s.toLowerCase();
  }

  public static PredicateType inferType(String predicate) {
    if (predicate.charAt(0) == '!') predicate = predicate.substring(1);
    if (predicate.startsWith(CanonicalNames.PREFIX)) {
      if (CanonicalNames.isUnary(predicate)) {
        return PredicateType.ENTITY;
      } else if (CanonicalNames.isBinary(predicate)) {
        return PredicateType.BINARY;
      } else {
        throw new RuntimeException("Unrecognized predicate: " +  predicate);
      }
    } else {
      return PredicateType.KEYWORD;
    }
  }

  @Override
  public String toString() {
    return predicate + "(" + originalString + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !(o instanceof PredicateInfo)) return false;
    return predicate.equals(((PredicateInfo) o).predicate);
  }

  @Override
  public int hashCode() {
    return predicate.hashCode();
  }

  // ============================================================
  // Get original strings and lemmas
  // ============================================================

  // Lemma cache
  private static final Map<String, String> lemmaCache = new HashMap<>();

  // Helper function: get lemma form
  public static synchronized String getLemma(String s) {
    if (s == null || s.trim().isEmpty()) return null;
    String lemma = lemmaCache.get(s);
    if (lemma == null) {
      LanguageInfo langInfo = LanguageAnalyzer.getSingleton().analyze(s);
      lemma = (langInfo.numTokens() == 0) ? "" : langInfo.lemmaPhrase(0, langInfo.numTokens());
      lemmaCache.put(s, lemma);
    }
    return lemma;
  }

  // Helper function: get original string from the table
  public static String getOriginalString(String predicate, Example ex) {
    return getOriginalString(predicate, ex.context);
  }

  //Helper function: get original string from the table
  public static String getOriginalString(String predicate, ContextValue context) {
    if (context == null || context.graph == null || !(context.graph instanceof TableKnowledgeGraph))
      return null;
    return getOriginalString(predicate, (TableKnowledgeGraph) context.graph);
  }

  // Helper function: get original string from the table
  public static String getOriginalString(String predicate, TableKnowledgeGraph graph) {
    String s = graph.getOriginalString(predicate);
    s = getLemma(s);
    if (s != null && s.trim().isEmpty()) s = null;
    return s;
  }

  // ============================================================
  // Get the list of all PredicateInfos
  // ============================================================

  public static List<PredicateInfo> getPredicateInfos(Example ex, Derivation deriv) {
    Collection<PredicateInfo> predicates;
    Formula formula = deriv.formula;
    if (opts.betaReduce) formula = Formulas.betaReduction(formula);
    if (opts.traverseWithFormulaTypes) {
      // Traverse on formula. Be more careful when generating predicates
      FormulaTraverser traverser = new FormulaTraverser(ex);
      traverser.traverse(formula);
      predicates = traverser.predicates;
    } else {
      // Traverse on lisp tree (ignore formula types)
      LispTreeTraverser traverser = new LispTreeTraverser(ex);
      traverser.traverse(formula.toLispTree());
      predicates = traverser.predicates;
    }
    List<PredicateInfo> answer = new ArrayList<>();
    for (PredicateInfo p : predicates) {
      if (p.originalString == null || p.originalString.length() <= opts.maxPredicateLength)
        answer.add(p);
    }
    return answer;
  }

  private static class LispTreeTraverser {
    public final Collection<PredicateInfo> predicates;
    private final ContextValue context;

    public LispTreeTraverser(Example ex) {
      this.predicates = opts.allowRepeats ? new ArrayList<>() : new HashSet<>();
      this.context = ex.context;
    }

    public void traverse(LispTree tree) {
      if (tree.isLeaf()) {
        predicates.add(new PredicateInfo(tree.value, context));
      } else {
        for (LispTree child : tree.children)
          traverse(child);
      }
    }
  }

  private static class FormulaTraverser {
    public final Collection<PredicateInfo> predicates;
    private final ContextValue context;

    public FormulaTraverser(Example ex) {
      this.predicates = opts.allowRepeats ? new ArrayList<>() : new HashSet<>();
      this.context = ex.context;
    }

    public void traverse(Formula formula) {
      if (formula instanceof ValueFormula) {
        Value value = ((ValueFormula) formula).value;
        if (value instanceof NumberValue) {
          NumberValue number = (NumberValue) value;
          predicates.add(new PredicateInfo("number", context));
          predicates.add(new PredicateInfo(Fmt.D(number.value), context));

        } else if (value instanceof DateValue) {
          DateValue date = (DateValue) value;
          predicates.add(new PredicateInfo("date", context));
          // Use prefixes to distinguish from numbers
          predicates.add(new PredicateInfo("y:" + Fmt.D(date.year), context));
          predicates.add(new PredicateInfo("m:" + Fmt.D(date.month), context));
          predicates.add(new PredicateInfo("d:" + Fmt.D(date.day), context));

        } else if (value instanceof StringValue) {
          StringValue string = (StringValue) value;
          predicates.add(new PredicateInfo("string", context));
          predicates.add(new PredicateInfo(string.value, context));

        } else if (value instanceof NameValue) {
          NameValue name = (NameValue) value;
          String id = name.id;
          if (opts.reverseNameValueConversion == ReverseNameValueConversion.allReverse
              && id.startsWith("!") && !id.equals("!=")) {
            predicates.add(new PredicateInfo("reverse", context));
            id = id.substring(1);
          }
          predicates.add(new PredicateInfo(id, context));

        }

      } else if (formula instanceof JoinFormula) {
        JoinFormula join = (JoinFormula) formula;
        traverse(join.relation); traverse(join.child);

      } else if (formula instanceof ReverseFormula) {
        ReverseFormula reverse = (ReverseFormula) formula;
        if (opts.reverseNameValueConversion == ReverseNameValueConversion.allBang
            && reverse.child instanceof ValueFormula
            && ((ValueFormula) reverse.child).value instanceof NameValue) {
          String id = ((NameValue) ((ValueFormula) reverse.child).value).id;
          id = id.startsWith("!") ? id.substring(1) : ("!" + id);
          traverse(new ValueFormula(new NameValue(id)));
        } else {
          predicates.add(new PredicateInfo("reverse", context));
          traverse(reverse.child);
        }

      } else if (formula instanceof MergeFormula) {
        MergeFormula merge = (MergeFormula) formula;
        predicates.add(new PredicateInfo(merge.mode.toString(), context));
        traverse(merge.child1); traverse(merge.child2);

      } else if (formula instanceof AggregateFormula) {
        AggregateFormula aggregate = (AggregateFormula) formula;
        predicates.add(new PredicateInfo(aggregate.mode.toString(), context));
        traverse(aggregate.child);

      } else if (formula instanceof SuperlativeFormula) {
        SuperlativeFormula superlative = (SuperlativeFormula) formula;
        predicates.add(new PredicateInfo(superlative.mode.toString(), context));
        // Skip the "(number 1) (number 1)" part
        traverse(superlative.head); traverse(superlative.relation);

      } else if (formula instanceof ArithmeticFormula) {
        ArithmeticFormula arithmetic = (ArithmeticFormula) formula;
        predicates.add(new PredicateInfo(arithmetic.mode.toString(), context));
        traverse(arithmetic.child1); traverse(arithmetic.child2);

      } else if (formula instanceof VariableFormula) {
        // Skip variables

      } else if (formula instanceof MarkFormula) {
        MarkFormula mark = (MarkFormula) formula;
        predicates.add(new PredicateInfo("mark", context));
        // Skip variable
        traverse(mark.body);

      } else if (formula instanceof LambdaFormula) {
        LambdaFormula lambda = (LambdaFormula) formula;
        predicates.add(new PredicateInfo("lambda", context));
        // Skip variable
        traverse(lambda.body);

      } else {
        throw new RuntimeException("[PredicateInfo] Cannot handle formula " + formula);
      }
    }
  }

  // ============================================================
  // Formula normalization
  // ============================================================

  public static LispTree normalizeFormula(Example ex, Derivation deriv) {
    Formula formula = deriv.formula;
    if (opts.betaReduce) formula = Formulas.betaReduction(formula);
    return new FormulaNormalizer(ex, formula).getNormalizedLispTree();
  }

  private static class FormulaNormalizer {
    private final ContextValue context;
    private final Formula formula;
    private LispTree normalized;
    private Map<String, String> foundFields;

    public FormulaNormalizer(Example ex, Formula formula) {
      this.context = ex.context;
      this.formula = formula;
      foundFields = new HashMap<>();
    }

    private String getNormalizedPredicate(String predicate) {
      if (predicate.charAt(0) == '!') return "!" + getNormalizedPredicate(predicate.substring(1));
      if (predicate.equals(CanonicalNames.TYPE)) return "@type";
      if (predicate.equals(TableTypeSystem.ROW_TYPE)) return "@row";
      if (predicate.startsWith(TableTypeSystem.ROW_PROPERTY_NAME_PREFIX)) {
        String fieldname = TableTypeSystem.getIdAfterPeriod(predicate, TableTypeSystem.ROW_PROPERTY_NAME_PREFIX);
        if (predicate.equals(TableTypeSystem.ROW_NEXT_VALUE.id) || predicate.equals(TableTypeSystem.ROW_INDEX_VALUE.id))
          return "@" + fieldname;
        if (!foundFields.containsKey(fieldname)) foundFields.put(fieldname, "" + foundFields.size());
        return "r" + foundFields.get(fieldname);
      } else if (predicate.startsWith(TableTypeSystem.CELL_NAME_PREFIX)) {
        if (predicate.startsWith(TableTypeSystem.CELL_PROPERTY_NAME_PREFIX)) {
          return "@" + TableTypeSystem.getIdAfterPeriod(predicate, TableTypeSystem.CELL_PROPERTY_NAME_PREFIX);
        }
        String fieldname = TableTypeSystem.getIdAfterUnderscore(predicate, TableTypeSystem.CELL_NAME_PREFIX);
        if (!foundFields.containsKey(fieldname)) foundFields.put(fieldname, "" + foundFields.size());
        return "c" + foundFields.get(fieldname);
      }
      return predicate;
    }

    public LispTree getNormalizedLispTree() {
      if (normalized == null) normalized = traverse(formula);
      return normalized;
    }

    public LispTree traverse(Formula formula) {
      LispTree tree = LispTree.proto.newList();

      if (formula instanceof ValueFormula) {
        Value value = ((ValueFormula) formula).value;
        if (value instanceof NumberValue) {
          NumberValue number = (NumberValue) value;
          return LispTree.proto.newLeaf("$number");

        } else if (value instanceof DateValue) {
          DateValue date = (DateValue) value;
          return LispTree.proto.newLeaf("$date");

        } else if (value instanceof StringValue) {
          StringValue string = (StringValue) value;
          return LispTree.proto.newLeaf("$string");

        } else if (value instanceof NameValue) {
          NameValue name = (NameValue) value;
          String id = name.id;
          if (opts.reverseNameValueConversion == ReverseNameValueConversion.allReverse
              && id.startsWith("!") && !id.equals("!=")) {
            tree.addChild(LispTree.proto.newLeaf("reverse"));
            id = id.substring(1);
            tree.addChild(getNormalizedPredicate(id));
          } else {
            return LispTree.proto.newLeaf(getNormalizedPredicate(id));
          }

        }

      } else if (formula instanceof JoinFormula) {
        JoinFormula join = (JoinFormula) formula;
        tree.addChild(traverse(join.relation)).addChild(traverse(join.child));

      } else if (formula instanceof ReverseFormula) {
        ReverseFormula reverse = (ReverseFormula) formula;
        if (opts.reverseNameValueConversion == ReverseNameValueConversion.allBang
            && reverse.child instanceof ValueFormula
            && ((ValueFormula) reverse.child).value instanceof NameValue) {
          String id = ((NameValue) ((ValueFormula) reverse.child).value).id;
          id = id.startsWith("!") ? id.substring(1) : ("!" + id);
          return LispTree.proto.newLeaf(getNormalizedPredicate(id));
        } else {
          tree.addChild(LispTree.proto.newLeaf("reverse")).addChild(traverse(reverse.child));
        }

      } else if (formula instanceof MergeFormula) {
        MergeFormula merge = (MergeFormula) formula;
        tree.addChild(merge.mode.toString()).addChild(traverse(merge.child1)).addChild(traverse(merge.child2));

      } else if (formula instanceof AggregateFormula) {
        AggregateFormula aggregate = (AggregateFormula) formula;
        tree.addChild(aggregate.mode.toString()).addChild(traverse(aggregate.child));

      } else if (formula instanceof SuperlativeFormula) {
        SuperlativeFormula superlative = (SuperlativeFormula) formula;
        tree.addChild(superlative.mode.toString()).addChild(traverse(superlative.head)).addChild(traverse(superlative.relation));

      } else if (formula instanceof ArithmeticFormula) {
        ArithmeticFormula arithmetic = (ArithmeticFormula) formula;
        tree.addChild(arithmetic.mode.toString()).addChild(traverse(arithmetic.child1)).addChild(traverse(arithmetic.child2));

      } else if (formula instanceof VariableFormula) {
        return LispTree.proto.newLeaf("$var");

      } else if (formula instanceof MarkFormula) {
        MarkFormula mark = (MarkFormula) formula;
        tree.addChild("mark").addChild(traverse(mark.body));

      } else if (formula instanceof LambdaFormula) {
        LambdaFormula lambda = (LambdaFormula) formula;
        tree.addChild("lambda").addChild(traverse(lambda.body));

      } else {
        throw new RuntimeException("[PredicateInfo] Cannot handle formula " + formula);
      }

      return tree;
    }
  }
}
