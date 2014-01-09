package edu.stanford.nlp.sempre;

import fig.basic.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import com.google.common.collect.Lists;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.*;

/**
 * Convert a Formula into a SPARQL query and execute it against some RDF endpoint.
 * Formal specification of SPARQL:
 *   http://www.w3.org/TR/rdf-sparql-query/
 *
 * @author Percy Liang
 */
public class SparqlExecutor extends Executor {
  public static class Options {
    @Option(gloss = "Maximum number of results to return")
    public int maxResults = 10;

    @Option(gloss = "Milliseconds to wait until opening connection times out")
    public int connectTimeoutMs = 1 * 60 * 1000;

    @Option(gloss = "Milliseconds to wait until reading connection times out")
    public int readTimeoutMs = 1 * 60 * 1000;

    @Option(gloss = "Save all SPARQL queries in a file so we don't have to hit the SPARQL endpoint too often")
    public String cachePath;

    @Option(gloss = "URL where the SPARQL server lives")
    public String endpointUrl;

    @Option(gloss = "Whether to return a table of results rather than just a list of results")
    public boolean returnTable = false;

    // TODO: remove this since this is a really bad hack.
    @Option(gloss = "If false, then enforce that denotation of (lambda x (border x)) does not contain (x,x)")
    public boolean lambdaAllowDiagonals = false;

    @Option(gloss = "Whether to include entity names (mostly for readability)")
    public boolean includeEntityNames = true;

    @Option public int verbose = 0;
  }

  public static Options opts = new Options();

  private final FreebaseInfo fbInfo;
  private final StringCache query2xmlCache;

  // Statistics on Sparql requests
  private static class SparqlStats {
    private StatFig timeFig = new StatFig();
    // Number of each type of error.
    private LinkedHashMap<String, Integer> errors = new LinkedHashMap<String, Integer>();
  }

  private SparqlStats queryStats = new SparqlStats();

  public SparqlExecutor() {
    this.fbInfo = FreebaseInfo.getSingleton();
    this.query2xmlCache = StringCacheUtils.create(opts.cachePath);
  }

  public class ServerResponse {
    public ServerResponse(String xml) { this.xml = xml; }
    public ServerResponse(ErrorValue error) { this.error = error; }
    String xml;
    ErrorValue error;
    long timeMs;
    boolean cached;  // Whether things were cached
    boolean beginTrack;  // Whether we started printing things out
  }

  // Make a request to the given SPARQL endpoint.
  // Return the XML.
  public ServerResponse makeRequest(String queryStr, String endpointUrl) {
    if (endpointUrl == null)
      throw new RuntimeException("No SPARQL endpoint url specified");

    try {
      String url = String.format("%s?query=%s&format=xml", endpointUrl, URLEncoder.encode(queryStr, "UTF-8"));
      URLConnection conn = new URL(url).openConnection();
      conn.setConnectTimeout(opts.connectTimeoutMs);
      conn.setReadTimeout(opts.readTimeoutMs);
      InputStream in = conn.getInputStream();

      // Read the response
      StringBuilder buf = new StringBuilder();
      BufferedReader reader = new BufferedReader(new InputStreamReader(in));
      String line;
      while ((line = reader.readLine()) != null)
        buf.append(line);

      // Check for blatant errors.
      String result = buf.toString();
      if (result.length() == 0)
        return new ServerResponse(ErrorValue.empty);
      if (result.startsWith("<!DOCTYPE html>"))
        return new ServerResponse(ErrorValue.badFormat);

      return new ServerResponse(buf.toString());
    } catch (SocketTimeoutException e) {
      return new ServerResponse(ErrorValue.timeout);
    } catch (IOException e) {
      LogInfo.errors("Server exception: %s", e);
      // Sometimes the SPARQL server throws a 408 to signify a server timeout.
      if (e.toString().contains("HTTP response code: 408"))
        return new ServerResponse(ErrorValue.server408);
      if (e.toString().contains("HTTP response code: 500"))
        return new ServerResponse(ErrorValue.server500);
      throw new RuntimeException(e);  // Haven't seen this happen yet...
    }
  }

  // For debugging only
  // Document extends Node
  public static void printDocument(Node node, OutputStream out) {
    try {
      TransformerFactory tf = TransformerFactory.newInstance();
      Transformer transformer = tf.newTransformer();
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
      transformer.setOutputProperty(OutputKeys.METHOD, "xml");
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

      transformer.transform(
          new DOMSource(node),
          new StreamResult(new OutputStreamWriter(out, "UTF-8")));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // Return
  //  - XML
  //  - Whether to print out details (coincides with whether this query was cached).
  public ServerResponse runQueryToGetXml(String queryStr, Formula formula) {
    //LogInfo.logs("%s %s", formula, queryStr);
    ServerResponse response = null;

    // Note: only cache for concrete queries.
    boolean useCache = query2xmlCache != null;

    // Try to look the query up in the cache.
    if (useCache) {
      // Contents either encodes an error or not.
      String contents = query2xmlCache.get(queryStr);
      if (contents != null) {
        ErrorValue error = ErrorValue.fromString(contents);
        if (error != null)
          response = new ServerResponse(error);
        else
          response = new ServerResponse(contents);
        response.cached = true;
      }
    }

    // If not cached, then make the actual request.
    if (response == null) {
      // Note: begin_track without end_track
      LogInfo.begin_track("SparqlExecutor.execute()");
      if (formula != null) LogInfo.logs("%s", formula);
      LogInfo.logs("%s", queryStr);

      // Make actual request
      StopWatch watch = new StopWatch();
      watch.start();
      response = makeRequest(queryStr, opts.endpointUrl);
      watch.stop();
      response.timeMs = watch.getCurrTimeLong();
      response.beginTrack = true;

      if (useCache)
        query2xmlCache.put(queryStr, response.error != null ? response.error.toString() : response.xml);
    }
    return response;
  }

  public static NodeList extractResultsFromXml(ServerResponse response) {
    return extractResultsFromXml(response.xml);
  }
  private static NodeList extractResultsFromXml(String xml) {
    DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
    NodeList results = null;
    try {
      DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
      Document doc = docBuilder.parse(new InputSource(new StringReader(xml)));
      results = doc.getElementsByTagName("result");
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (SAXException e) {
      LogInfo.errors("XML: %s", xml);
      //throw new RuntimeException(e);
      return null;
    } catch (ParserConfigurationException e) {
      throw new RuntimeException(e);
    }
    return results;
  }

  // Main entry point.
  public Response execute(Formula formula) {
    String prefix = "exec-";

    Evaluation stats = new Evaluation();
    // Convert to SPARQL
    Converter converter;
    try {
      converter = new Converter(formula);
    } catch (BadFormulaException e) {
      stats.add(prefix + "error", true);
      return new Response(ErrorValue.badFormula(e), stats);
    }

    ServerResponse serverResponse = runQueryToGetXml(converter.queryStr, formula);
    stats.add(prefix + "cached", serverResponse.cached);
    if (!serverResponse.cached)
      stats.add(prefix + "time", serverResponse.timeMs);

    //// Record statistics

    // Update/print sparql stats
    if (!serverResponse.cached) {
      queryStats.timeFig.add(serverResponse.timeMs);
      if (serverResponse.error != null) {
        MapUtils.incr(queryStats.errors, serverResponse.error.type, 1);
        if (serverResponse.beginTrack)
          LogInfo.logs("Error: %s", serverResponse.error);
      }
      if (serverResponse.beginTrack) {
        LogInfo.logs("time: %s", queryStats.timeFig);
        LogInfo.logs("errors: %s", queryStats.errors);
      }
    }

    // If error, then return out
    if (serverResponse.error != null) {
      if (serverResponse.beginTrack) LogInfo.end_track();
      if (!serverResponse.cached)
        stats.add(prefix + "error", true);
      return new Response(serverResponse.error, stats);
    }

    if (!serverResponse.cached)
      stats.add(prefix + "error", false);

    // Extract the results from XML now.
    NodeList results = extractResultsFromXml(serverResponse.xml);
    if (results == null) return new Response(ErrorValue.badFormat, stats);
    List<Value> values = new ValuesExtractor(serverResponse.beginTrack, formula, converter).extract(results);

    if (serverResponse.beginTrack) LogInfo.end_track();

    return new Response(new ListValue(values), stats);
  }

  ////////////////////////////////////////////////////////////
  // Convert a Formula into a SparqlExpr.
  class Converter {
    private int numVars = 0;  // Used to create new Sparql variables

    String queryStr;
    SparqlSelect query;  // Resulting SPARQL expression

    public Converter(Formula rootFormula) throws BadFormulaException {
      Ref<PrimitiveFormula> head = new Ref<PrimitiveFormula>();
      Map<VariableFormula, PrimitiveFormula> env = new LinkedHashMap<VariableFormula, PrimitiveFormula>();
      query = closeExistentialScope(convert(rootFormula, head, null, env), head, env, null, null);
      if (query.limit == -1)  // If it is not set
        query.limit = opts.maxResults;
      queryStr = "PREFIX fb: <" + FreebaseInfo.freebaseNamespace + "> " + query;
    }

    // Create a SELECT expression.
    // Note: corresponds to a box in DRT.
    private SparqlSelect closeExistentialScope(SparqlBlock block, Ref<PrimitiveFormula> head,
                                               Map<VariableFormula, PrimitiveFormula> env,
                                               String headAsValue, String headUnit) {
      SparqlSelect select = new SparqlSelect();

      // Ensure that the head is a variable rather than a primitive value.
      VariableFormula headVar = ensureIsVar(block, head);
      if (headUnit == null) headUnit = getUnit(block, headVar);
      select.selectVars.add(new SparqlSelect.Var(headVar, headAsValue, headUnit, false));

      // Get the name of the head
      if (opts.includeEntityNames && FreebaseInfo.ENTITY.equals(headUnit)) {
        VariableFormula headNameVar = new VariableFormula(headVar.name + "name");
        select.selectVars.add(new SparqlSelect.Var(headNameVar, null, FreebaseInfo.TEXT, true));
        block.addOptionalStatement(headVar, FreebaseInfo.NAME, headNameVar);
      }

      // Add the other variables in the environment (these are things we need to condition on)
      for (PrimitiveFormula formula : env.values()) {
        if (!(formula instanceof VariableFormula)) continue;
        VariableFormula condition = (VariableFormula)formula;
        if (headVar.equals(condition)) continue;  // Don't select the head again, since we already added that.
        select.selectVars.add(new SparqlSelect.Var(condition, null, getUnit(block, condition), false));
      }

      select.where = block;
      return select;
    }

    // Mutable |head| to make sure it contains a VariableFormula.
    private VariableFormula ensureIsVar(SparqlBlock block, Ref<PrimitiveFormula> head) {
      VariableFormula headVar;
      if (head.value instanceof VariableFormula) {
        headVar = (VariableFormula)head.value;
      } else {
        headVar = newVar();
        if (head.value != null)
          block.addStatement(headVar, "=", head.value);
        head.value = headVar;
      }
      return headVar;
    }

    // Return the unit (e.g., fb:en.meter) that |var| has in |expr|.
    // NOTE: this function is incomplete currently and might not always be
    // able to infer the unit; in that case, it will just return null.
    private String getUnit(SparqlExpr expr, VariableFormula var) {
      if (expr instanceof SparqlStatement) {
        SparqlStatement statement = (SparqlStatement)expr;
        // If the statement is ?x = <value>, then try to figure out the unit of value.
        if (var.equals(statement.arg1)) {
          if (statement.relation.equals("=") && statement.arg2 instanceof ValueFormula) {
            Value value = ((ValueFormula)statement.arg2).value;
            if (value instanceof NameValue) return FreebaseInfo.ENTITY;
            if (value instanceof BooleanValue) return FreebaseInfo.BOOLEAN;
            if (value instanceof NumberValue) return ((NumberValue)value).unit;
            if (value instanceof StringValue) return FreebaseInfo.TEXT;
            if (value instanceof DateValue) return FreebaseInfo.DATE;
          }
          if (!SparqlStatement.isOperator(statement.relation))
            return FreebaseInfo.ENTITY;
        }
        if (var.equals(statement.arg2))
          return fbInfo.getUnit2(statement.relation);
      } else if (expr instanceof SparqlBlock) {
        for (SparqlExpr subexpr : ((SparqlBlock)expr).children) {
          String unit = getUnit(subexpr, var);
          if (unit != null) return unit;
        }
      } else if (expr instanceof SparqlSelect) {
        SparqlSelect select = (SparqlSelect)expr;
        for (SparqlSelect.Var selectVar : select.selectVars) {
          if (selectVar.var.equals(var)) return selectVar.unit;
        }
        return getUnit(select.where, var);
      }
      return null;
    }

    // head, modifier: SPARQL variables (e.g., ?x13)
    // env: mapping from lambda-DCS variables (e.g., ?city) to SPARQL variables (?x13)
    private SparqlBlock convert(Formula rawFormula,
                                Ref<PrimitiveFormula> head, Ref<PrimitiveFormula> modifier,
                                Map<VariableFormula, PrimitiveFormula> env) {
      if (opts.verbose >= 5) LogInfo.begin_track("convert %s: head = %s, modifier = %s, env = %s", rawFormula, head, modifier, env);

      // Check binary/unary compatibility
      boolean isNameFormula = (rawFormula instanceof ValueFormula) && (((ValueFormula)rawFormula).value instanceof NameValue);  // Either binary or unary
      boolean needsBinary = (modifier != null);
      boolean providesBinary = rawFormula instanceof LambdaFormula || rawFormula instanceof ReverseFormula;
      if (!isNameFormula && needsBinary != providesBinary) {
        throw new RuntimeException("Binary/unary mis-match: " +
                                   rawFormula + " is " + (providesBinary ? "binary" : "unary") +
                                   ", but need " + (needsBinary ? "binary" : "unary"));
      }

      SparqlBlock block = new SparqlBlock();

      if (rawFormula instanceof ValueFormula) {  // e.g., fb:en.barack_obama or (number 3)
        @SuppressWarnings({"unchecked"})
        ValueFormula<Value> formula = (ValueFormula)rawFormula;
        if (modifier != null) {  // Binary predicate
          if (head.value == null) head.value = newVar();
          if (modifier.value == null) modifier.value = newVar();
          // Deal with primitive reverses (!fb:people.person.date_of_birth)
          String property = ((NameValue)formula.value).id;
          PrimitiveFormula arg1, arg2;
          if (FreebaseInfo.isReverseProperty(property)) {
            arg1 = modifier.value;
            property = property.substring(1);
            arg2 = head.value;
          } else {
            arg1 = head.value;
            arg2 = modifier.value;
          }

          // Annoying logic to deal with dates.
          // If we have
          //   ?x fb:people.person.date_of_birth "2003"^^xsd:datetime,
          // then create two statements:
          //   ?x fb:people.person.date_of_birth ?v
          //   ?v = "2003"^^xsd:datetime [this needs to be transformed]
          if (!SparqlStatement.isOperator(property)) {
            if (arg2 instanceof ValueFormula) {
              Value value = ((ValueFormula)arg2).value;
              if (value instanceof DateValue) {
                VariableFormula v = newVar();
                block.addStatement(v, "=", arg2);
                arg2 = v;
              }
            }
          }
          block.addStatement(arg1, property, arg2);
        } else {  // Unary predicate
          unify(block, head, formula);
        }
      } else if (rawFormula instanceof VariableFormula) {
        VariableFormula var = (VariableFormula)rawFormula;
        PrimitiveFormula value = env.get(var);
        if (value == null)
          throw new RuntimeException("Unbound variable: " + var + ", env = " + env);
        unify(block, head, value);
      } else if (rawFormula instanceof NotFormula) {
        NotFormula formula = (NotFormula)rawFormula;
        block.add(new SparqlNot(convert(formula.child, head, null, env)));
      } else if (rawFormula instanceof MergeFormula) {
        MergeFormula formula = (MergeFormula)rawFormula;
        switch (formula.mode) {
          case and:
            block.add(convert(formula.child1, head, null, env));
            block.add(convert(formula.child2, head, null, env));
            break;
          case or:
            SparqlUnion union = new SparqlUnion();
            union.add(convert(formula.child1, head, null, env));
            union.add(convert(formula.child2, head, null, env));
            block.add(union);
            break;
          default:
            throw new RuntimeException("Unhandled mode: " + formula.mode);
        }
      } else if (rawFormula instanceof JoinFormula) {
        // Join
        JoinFormula formula = (JoinFormula)rawFormula;
        Ref<PrimitiveFormula> intermediate = new Ref<PrimitiveFormula>();
        block.add(convert(formula.child, intermediate, null, env));
        block.add(convert(formula.relation, head, intermediate, env));
      } else if (rawFormula instanceof ReverseFormula) {
        // Reverse
        ReverseFormula formula = (ReverseFormula)rawFormula;
        block.add(convert(formula.child, modifier, head, env));  // Switch modifier and head
      } else if (rawFormula instanceof LambdaFormula) {
        // Lambda (new environment)
        LambdaFormula formula = (LambdaFormula)rawFormula;
        if (modifier.value == null) modifier.value = newVar();
        Map<VariableFormula, PrimitiveFormula> newEnv = createNewEnv(formula.body, env);  // Create new environment
        newEnv.put(new VariableFormula(formula.var), modifier.value);  // Map variable to modifier
        block.add(convert(formula.body, head, null, newEnv));
        // Place pragmatic constraint that head != modifier (for symmetric relations like spouse)
        if (!opts.lambdaAllowDiagonals)
          block.addStatement(head.value, "!=", modifier.value);
      } else if (rawFormula instanceof MarkFormula) {
        // Mark (new environment)
        MarkFormula formula = (MarkFormula)rawFormula;
        if (head.value == null) head.value = newVar();
        Map<VariableFormula, PrimitiveFormula> newEnv = createNewEnv(formula.body, env);  // Create new environment
        newEnv.put(new VariableFormula(formula.var), head.value);  // Map variable to head
        block.add(convert(formula.body, head, null, newEnv));
      } else if (rawFormula instanceof SuperlativeFormula) {
        // Superlative (new environment, close scope)
        SuperlativeFormula formula = (SuperlativeFormula)rawFormula;

        // boolean useOrderBy = formula.rank != 1 || formula.count != 1;
        boolean useOrderBy = true; // Setting this flag to always be true to avoid the buggy argmax 1 1 case.
        boolean isMax = formula.mode == SuperlativeFormula.Mode.argmax;
        if (useOrderBy) {
          // Method 1: use ORDER BY (can deal with offset and limit, but can't be nested, and doesn't handle ties at the top)
          // Recurse
          Map<VariableFormula, PrimitiveFormula> newEnv = createNewEnv(formula.head, env);
          SparqlBlock newBlock = convert(formula.head, head, null, newEnv);
          Ref<PrimitiveFormula> degree = new Ref<PrimitiveFormula>();
          newBlock.add(convert(formula.relation, head, degree, newEnv));

          // Apply the aggregation operation
          VariableFormula degreeVar = ensureIsVar(block, degree);

          // Force |degreeVar| to be selected as a variable.
          env.put(new VariableFormula("degree"), degreeVar);
          newEnv.put(new VariableFormula("degree"), degreeVar);

          SparqlSelect select = closeExistentialScope(newBlock, head, newEnv, null, null);
          select.sortVars.add(isMax ? new VariableFormula(applyVar("DESC", degreeVar)) : degreeVar);
          select.offset = formula.rank - 1;
          select.limit = formula.count;
          block.add(select);

        // TODO: Fix this else case. I haven't understood the code yet, but all I know right is that this part,
        // instead of resolving ties, seemingly answers with random entities
        } else {
          // Method 2: use MAX (can be nested, handles ties at the top)
          // (argmax 1 1 h r) ==> (h (r (mark degree (max ((reverse r) e)))))
          AggregateFormula.Mode mode = isMax ? AggregateFormula.Mode.max : AggregateFormula.Mode.min;
          Formula best = new MarkFormula("degree", new AggregateFormula(mode, new JoinFormula(new ReverseFormula(formula.relation), formula.head)));
          Formula transformed = new MergeFormula(MergeFormula.Mode.and, formula.head, new JoinFormula(formula.relation, best));
          //LogInfo.logs("TRANSFORMED: %s", transformed);
          block.add(convert(transformed, head, null, env));
        }
      } else if (rawFormula instanceof AggregateFormula) {
        // Aggregate (new environment, close scope)
        AggregateFormula formula = (AggregateFormula)rawFormula;

        // Recurse
        Map<VariableFormula, PrimitiveFormula> newEnv = createNewEnv(formula.child, env);
        Ref<PrimitiveFormula> newHead = new Ref<PrimitiveFormula>(newVar());
        SparqlBlock newBlock = convert(formula.child, newHead, null, newEnv);

        // Apply the aggregation operation
        ensureIsVar(block, head);
        String headAsValue = applyVar(formula.mode.toString(), (VariableFormula)newHead.value);
        String headUnit = formula.mode == AggregateFormula.Mode.count ? NumberValue.unitless : getUnit(newBlock, (VariableFormula)newHead.value);
        // Work around Virtuoso's inability to handle dates property:
        // http://pablomendes.wordpress.com/2011/05/19/sparql-xsddate-weirdness/
        if ((formula.mode == AggregateFormula.Mode.min || formula.mode == AggregateFormula.Mode.max) && FreebaseInfo.DATE.equals(headUnit)) {
          headAsValue = applyVar(formula.mode.toString(), applyVar("xsd:dateTime", (VariableFormula)newHead.value));

          Ref<PrimitiveFormula> intermediateHead = new Ref<PrimitiveFormula>();
          block.add(closeExistentialScope(newBlock, intermediateHead, newEnv, headAsValue, headUnit));
          // Note: this statement introduces a filter, which only works if there are other constraints on |head| (e.g., this was created by doing an argmax, method 2)
          block.addStatement(new VariableFormula(applyVar("xsd:dateTime", (VariableFormula)head.value)), "=", intermediateHead.value);
        } else {
          block.add(closeExistentialScope(newBlock, head, newEnv, headAsValue, headUnit));
        }
      } else {
        throw new RuntimeException("Unhandled formula: " + rawFormula);
      }

      if (opts.verbose >= 5) LogInfo.logs("return: head = %s, modifier = %s, env = %s", head, modifier, env);
      if (opts.verbose >= 5) LogInfo.end_track();

      return block;
    }

    // Copy |env|, but only keep the variables which are used in 
    // This is an important optimization for converting to SPARQL.
    private Map<VariableFormula, PrimitiveFormula> createNewEnv(Formula formula, Map<VariableFormula, PrimitiveFormula> env) {
      Map<VariableFormula, PrimitiveFormula> newEnv = new LinkedHashMap<VariableFormula, PrimitiveFormula>();
      for (VariableFormula key : env.keySet())
        if (Formulas.containsFreeVar(formula, key))
          newEnv.put(key, env.get(key));
      return newEnv;
    }

    private void unify(SparqlBlock block, Ref<PrimitiveFormula> head, PrimitiveFormula value) {
      if (head.value == null) {
        // |head| is not set, just use |value|.
        head.value = value;
      } else {
        // |head| is already set, so add a constraint that it equals |value|.
        block.addStatement(head.value, "=", value);
      }
    }

    // Helper functions
    private String applyVar(String func, VariableFormula var) { return applyVar(func, var.name); }
    private String applyVar(String func, String var) { return func + "(" + var + ")"; }

    private VariableFormula newVar() {
      numVars++;
      return new VariableFormula("?x" + numVars);
    }
  }

  ////////////////////////////////////////////////////////////
  // Take results of executing an SparqlExpr and produce a List of values.
  class ValuesExtractor {
    final boolean beginTrack;
    final Formula formula;
    final List<String> selectVars;
    final List<String> units;

    public ValuesExtractor(boolean beginTrack, Formula formula, Converter converter) {
      this.beginTrack = beginTrack;
      this.formula = formula;

      this.selectVars = Lists.newArrayList();
      this.units = Lists.newArrayList();
      for (SparqlSelect.Var var : converter.query.selectVars) {
        if (var.isAuxiliary) continue;
        this.selectVars.add(var.var.name);
        this.units.add(var.unit);
      }
    }

    // |results| is (result (binding (uri ...)) ...) or (result (binding (literal ...)) ...)
    List<Value> extract(NodeList results) {
      // For each result (row in a table)...
      if (beginTrack) LogInfo.begin_track("%d results", results.getLength());
      List<Value> values = new ArrayList<Value>();
      for (int i = 0; i < results.getLength(); i++) {
        Value value = nodeToValue(results.item(i));
        values.add(value);
        if (beginTrack) LogInfo.logs("%s", value);
      }
      if (beginTrack) LogInfo.end_track();
      return values;
    }

    private Value nodeToValue(Node result) {
      NodeList bindings = ((Element) result).getElementsByTagName("binding");

      // For each variable in selectVars, we're going to keep track of an |id|
      // (only for entities) and |description| (name or the literal value).
      List<String> ids = Lists.newArrayList();
      List<String> descriptions = Lists.newArrayList();
      for (int j = 0; j < selectVars.size(); j++) {
        ids.add(null);
        descriptions.add(null);
      }

      // For each binding j (contributes some information to one column)...
      for (int j = 0; j < bindings.getLength(); j++) {
        Element binding = (Element)bindings.item(j);

        String var = "?" + binding.getAttribute("name");
        int col;
        if (var.endsWith("name"))
          col = selectVars.indexOf(var.substring(0, var.length() - 4));
        else
          col = selectVars.indexOf(var);

        String uri = getTagValue("uri", binding);
        if (uri != null) ids.set(col, FreebaseInfo.uri2id(uri));

        String literal = getTagValue("literal", binding);
        if (literal != null) descriptions.set(col, literal);
      }

      // Go through the selected variables and build the actual value
      List<Value> row = Lists.newArrayList();
      for (int j = 0; j < selectVars.size(); j++) {
        String unit = units.get(j);
        String id = ids.get(j);
        String description = descriptions.get(j);

        // Convert the string representation back to a value based on the unit.
        Value value = null;
        if (unit == null) {
          value = new NameValue(id, description);
        } else if (unit.equals(FreebaseInfo.DATE)) {
          value = DateValue.parseDateValue(description);
        } else if (unit.equals(FreebaseInfo.TEXT)) {
          value = new StringValue(description);
        } else if (unit.equals(FreebaseInfo.BOOLEAN)) {
          value = new BooleanValue(Boolean.parseBoolean(description));
        } else if (unit.equals(FreebaseInfo.ENTITY)) {
          value = new NameValue(id, description);
        } else if (unit.equals(FreebaseInfo.CVT)) {
          LogInfo.warnings("%s returns CVT, probably not intended", formula);
          value = new NameValue(id, description);
        } else {
          value = new NumberValue("NAN".equals(description) ? Double.NaN : Double.parseDouble(description), unit);
        }
        row.add(value);
      }

      // Either keep just the head of the row or the entire row.
      if (opts.returnTable)
        return new ListValue(row);
      else
        return row.get(0);
    }
  }

  // Helper for parsing DOM.
  // Return the inner text of of a child element of |elem| with tag |tag|.
  public static String getTagValue(String tag, Element elem) {
    NodeList nodes = elem.getElementsByTagName(tag);
    if (nodes.getLength() == 0) return null;
    if (nodes.getLength() > 1)
      throw new RuntimeException("Multiple instances of " + tag);
    nodes = nodes.item(0).getChildNodes();
    if (nodes.getLength() == 0) return null;
    Node value = nodes.item(0);
    return value.getNodeValue();
  }

  ////////////////////////////////////////////////////////////

  public static class MainOptions {
    @Option(gloss = "Sparql expression to execute") public String sparql;
    @Option(gloss = "Formula to execute") public String formula;
    @Option(gloss = "File containing formauls to execute")
    public String formulasPath;
  }

  public static void main(String[] args) throws NumberFormatException, IOException {
    OptionsParser parser = new OptionsParser();
    MainOptions mainOpts = new MainOptions();
    parser.registerAll(new Object[]{"SparqlExecutor", SparqlExecutor.opts, "FreebaseInfo", FreebaseInfo.opts, "main", mainOpts});
    parser.parse(args);

    LogInfo.begin_track("main()");
    SparqlExecutor executor = new SparqlExecutor();

    if (mainOpts.formula != null) {
      LogInfo.logs("%s", executor.execute(Formulas.fromLispTree(LispTree.proto.parseFromString(mainOpts.formula))).value);
    }

    if (mainOpts.formulasPath != null) {
      Iterator<LispTree> trees = LispTree.proto.parseFromFile(mainOpts.formulasPath);
      while (trees.hasNext()) {
        LogInfo.logs("%s", executor.execute(Formulas.fromLispTree(trees.next())).value);
      }
    }

    if (mainOpts.sparql != null)
      LogInfo.logs("%s", executor.makeRequest(mainOpts.sparql, opts.endpointUrl));

    LogInfo.end_track();
  }
}

class BadFormulaException extends Exception {
  public static final long serialVersionUID = 86586128316354597L;
  String message;
  public BadFormulaException(String message) { this.message = message; }
  @Override
  public String toString() { return message; }
}
