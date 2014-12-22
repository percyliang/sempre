package edu.stanford.nlp.sempre.freebase;

import java.util.*;
import com.google.common.collect.Lists;
import fig.basic.*;
import edu.stanford.nlp.sempre.*;

/**
 * Utilities for constructing SPARQL expressions.
 * Note that we represent a subset of the SPARQL.
 * @author Percy Liang
 */
public interface SparqlExpr {
}

// Example: { <expr> ... <expr> }
class SparqlBlock implements SparqlExpr {
  public final List<SparqlExpr> children = Lists.newArrayList();

  public SparqlBlock add(SparqlExpr expr) {
    if (expr instanceof SparqlBlock)
      this.children.addAll(((SparqlBlock) expr).children);
    else
      this.children.add(expr);
    return this;
  }

  private String getId(PrimitiveFormula formula) {
    if (!(formula instanceof ValueFormula)) return null;
    Value value = ((ValueFormula) formula).value;
    if (!(value instanceof NameValue)) return null;
    return ((NameValue) value).id;
  }

  private boolean isPrimitiveType(String id) {
    if (FreebaseInfo.BOOLEAN.equals(id)) return true;
    if (FreebaseInfo.INT.equals(id)) return true;
    if (FreebaseInfo.FLOAT.equals(id)) return true;
    if (FreebaseInfo.DATE.equals(id)) return true;
    if (FreebaseInfo.TEXT.equals(id)) return true;
    return false;
  }

  void addStatement(PrimitiveFormula arg1, String property, PrimitiveFormula arg2, boolean optional) {
    if (!property.startsWith("fb:") && !SparqlStatement.isOperator(property) && !SparqlStatement.isSpecialFunction(property) && !SparqlStatement.isIndependent(property))
      throw new RuntimeException("Invalid SPARQL property: " + property);
    // Ignore statements like:
    //   ?x fb:type.object.type fb:type.datetime
    // because we should have already captured the semantics using other formulas that involve ?x.
    if (property.equals(FreebaseInfo.TYPE)) {
      String id = getId(arg2);
      if (isPrimitiveType(id) || FreebaseInfo.ANY.equals(id)) return;
    }
    if (SparqlStatement.isIndependent(property)) return;  // Nothing connecting arg1 and arg2
    add(new SparqlStatement(arg1, property, arg2, optional));
  }

  @Override public String toString() {
    List<String> strings = Lists.newArrayList();
    for (SparqlExpr expr : children) {
      if (expr instanceof SparqlSelect)
        strings.add("{ " + expr + " }");
      else
        strings.add(expr.toString());
    }
    return "{ " + StrUtils.join(strings, " . ") + " }";
  }
}

// Example: SELECT ?x ?y WHERE { ... } ORDER BY ?x ?y LIMIT 10 OFFSET 3
class SparqlSelect implements SparqlExpr {
  static class Var {
    public final VariableFormula var;
    public final String asValue;  // for COUNT(?x3) as ?x2
    public final String unit;  // Specifies the types of the variable (used to parse back the results)
    public final boolean isAuxiliary;  // Whether this is supporting information (e.g., names)
    public final String description;  // Human-friendly for display

    public Var(VariableFormula var, String asValue, String unit, boolean isAuxiliary, String description) {
      this.var = var;
      this.asValue = asValue;
      this.unit = unit;
      this.isAuxiliary = isAuxiliary;
      this.description = description;
    }

    @Override public String toString() {
      if (asValue == null) return var.name;
      return "(" + asValue + " AS " + var.name + ")";
    }
  }

  public final List<Var> selectVars = Lists.newArrayList();

  public SparqlBlock where;
  public final List<VariableFormula> sortVars = Lists.newArrayList();
  public int offset = 0;  // Start at this point when returning results
  public int limit = -1;  // Number of results to return

  @Override public String toString() {
    StringBuilder out = new StringBuilder();
    out.append("SELECT DISTINCT");
    for (Var var : selectVars)
      out.append(" " + var.toString());
    out.append(" WHERE " + where);
    if (sortVars.size() > 0) {
      out.append(" ORDER BY");
      for (PrimitiveFormula sortVar : sortVars)
        out.append(" " + SparqlUtils.plainStr(sortVar));
    }
    if (limit != -1) out.append(" LIMIT " + limit);
    if (offset != 0) out.append(" OFFSET " + offset);
    return out.toString();
  }
}

// Example: { ... } UNION { ... } UNION { ... }
class SparqlUnion implements SparqlExpr {
  public final List<SparqlBlock> children = Lists.newArrayList();
  public SparqlUnion add(SparqlBlock block) { this.children.add(block); return this; }
  @Override public String toString() {
    return "{ " + StrUtils.join(children, " UNION ") + " }";
  }
}

// Example: FILTER NOT EXISTS { ... }
class SparqlNot implements SparqlExpr {
  public final SparqlBlock block;
  public SparqlNot(SparqlBlock block) { this.block = block; }
  @Override public String toString() {
    return "FILTER NOT EXISTS " + block;
  }
}

class SparqlStatement implements SparqlExpr {
  public final PrimitiveFormula arg1;
  public final String relation;
  public final PrimitiveFormula arg2;
  public boolean optional;
  public String options;

  public SparqlStatement(PrimitiveFormula arg1, String relation, PrimitiveFormula arg2, boolean optional) {
    this.arg1 = arg1;
    this.relation = relation;
    this.arg2 = arg2;
    this.optional = optional;
    this.options = null;
  }

  public SparqlStatement(PrimitiveFormula arg1, String relation, PrimitiveFormula arg2, boolean optional, String options) {
    this(arg1, relation, arg2, optional);
    this.options = options;
  }

  public static boolean isIndependent(String relation) { return relation.equals(":"); }

  public static boolean isOperator(String relation) {
    return relation.equals("=") || relation.equals("!=") ||
        relation.equals("<") || relation.equals(">") ||
        relation.equals("<=") || relation.equals(">=");
  }

  public static boolean isSpecialFunction(String relation) {
    return relation.equals("STRSTARTS") || relation.equals("STRENDS");
  }

  public String simpleString() {
    // Workaround for annoying dates:
    // http://answers.semanticweb.com/questions/947/dbpedia-sparql-endpoint-xsddate-comparison-weirdness
    if (arg2 instanceof ValueFormula && ((ValueFormula) arg2).value instanceof DateValue) {
      if (isOperator(relation)) {
        if (relation.equals("=")) {
          // (= (date 2000 -1 -1)) really means (>= (2000 -1 -1)) and (< (2001 -1 -1))
          DateValue startDate = (DateValue) ((ValueFormula) arg2).value;
          DateValue endDate = advance(startDate);
          return SparqlUtils.dateTimeStr(arg1) + " >= " + SparqlUtils.dateTimeStr(new ValueFormula<DateValue>(startDate)) + ") . FILTER (" +
                 SparqlUtils.dateTimeStr(arg1) + " < " + SparqlUtils.dateTimeStr(new ValueFormula<DateValue>(endDate));
        }
        if (relation.equals("<=")) {
          // (<= (date 2000 -1 -1)) really means (< (date 2001 -1 -1))
          DateValue startDate = (DateValue) ((ValueFormula) arg2).value;
          DateValue endDate = advance(startDate);
          return SparqlUtils.dateTimeStr(arg1) + " < " + SparqlUtils.dateTimeStr(new ValueFormula<DateValue>(endDate));
        }
        if (relation.equals(">")) {
          // (> (date 2000 -1 -1)) really means >= (date 2001 -1 -1)
          DateValue startDate = (DateValue) ((ValueFormula) arg2).value;
          DateValue endDate = advance(startDate);
          return SparqlUtils.dateTimeStr(arg1) + " >= " + SparqlUtils.dateTimeStr(new ValueFormula<DateValue>(endDate));
        }
        if (relation.equals("<") || relation.equals(">="))
          return SparqlUtils.dateTimeStr(arg1) + " " + relation + " " + SparqlUtils.dateTimeStr(arg2);
        // Note: != is not treated specially
      }
    }

    return SparqlUtils.plainStr(arg1) + " " + relation + " " + SparqlUtils.plainStr(arg2);
  }

  private DateValue advance(DateValue date) {
    // TODO(pliang): deal with carrying over
    if (date.day != -1) return new DateValue(date.year, date.month, date.day + 1);
    if (date.month != -1) return new DateValue(date.year, date.month + 1, -1);
    return new DateValue(date.year + 1, -1, -1);
  }

  public String toString() {
    String result;
    if (isSpecialFunction(relation)) {  // Special functions
      result = "FILTER (" + relation + "(" + SparqlUtils.plainStr(arg1) + "," + SparqlUtils.plainStr(arg2) + "))";
    } else if (isOperator(relation)) {
      result = "FILTER (" + simpleString() + ")";
    } else if (optional) {
      result = "OPTIONAL { " + simpleString() + " }";
    } else {
      result = simpleString();
    }

    if (this.options != null)
      result += " " + this.options;

    return result;
  }
}

final class SparqlUtils {
  private SparqlUtils() { }

  public static String dateTimeStr(PrimitiveFormula formula) {
    return "xsd:datetime(" + plainStr(formula) + ")";
  }

  public static String plainStr(PrimitiveFormula formula) {
    if (formula instanceof VariableFormula) return ((VariableFormula) formula).name;

    Value value = ((ValueFormula) formula).value;

    if (value instanceof StringValue) {
      String s = ((StringValue) value).value;
      return "\"" + s.replaceAll("\"", "\\\\\"") + "\"" + (s.equals("en") ? "" : "@en");
    }
    if (value instanceof NameValue) return ((NameValue) value).id;
    if (value instanceof NumberValue) return ((NumberValue) value).value + "";
    if (value instanceof DateValue) {
      DateValue date = (DateValue) value;
      if (date.month == -1) return "\"" + date.year + "\"" + "^^xsd:datetime";
      if (date.day == -1) return "\"" + date.year + "-" + date.month + "\"" + "^^xsd:datetime";
      return "\"" + date.year + "-" + date.month + "-" + date.day + "\"" + "^^xsd:datetime";
    }
    throw new RuntimeException("Unhandled primitive: " + value);
  }
}

