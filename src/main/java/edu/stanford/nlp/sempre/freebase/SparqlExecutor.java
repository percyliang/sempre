package edu.stanford.nlp.sempre.freebase;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.cache.StringCache;
import edu.stanford.nlp.sempre.cache.StringCacheUtils;
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

    @Option(gloss = "Whether to return a table of results rather than a list of entities (needed to support 'capital of each state')")
    public boolean returnTable = false;

    // FIXME TODO(pliang): remove this since this is a really bad hack.
    @Option(gloss = "If false, then enforce that denotation of (lambda x (border x)) does not contain (x,x)")
    public boolean lambdaAllowDiagonals = true;

    @Option(gloss = "Whether to include entity names (mostly for readability)")
    public boolean includeEntityNames = true;

    @Option(gloss = "Whether to return supporting information (e.g., 'length' for the 'longest river')")
    public boolean includeSupportingInfo = false;

    @Option public int verbose = 1;
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
    if (opts.verbose >= 3) LogInfo.logs("SparqlExecutor.execute: %s", queryStr);
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
    // if (response == null || response.xml == null || response.xml.contains("TIMEOUT")) {
    if (response == null) {
      // Note: begin_track without end_track
      if (opts.verbose >= 1) {
        LogInfo.begin_track("SparqlExecutor.execute: %s", formula);
        if (opts.verbose >= 2) LogInfo.logs("%s", queryStr);
      }

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
      // throw new RuntimeException(e);
      return null;
    } catch (ParserConfigurationException e) {
      throw new RuntimeException(e);
    }
    return results;
  }

  // Main entry point.
  public Response execute(Formula formula, ContextValue context) {
    // Note: don't do beta reduction here to preserve the semantics of lambda DCS.
    // Beta reduction should be triggered deliberately in the SemanticFn.
    return execute(formula, 0, opts.maxResults);
  }
  public synchronized Response execute(Formula formula, int offset, int maxResults) {
    if (opts.verbose >= 3)
      LogInfo.logs("SparqlExecutor.execute: %s", formula);
    String prefix = "exec-";

    Evaluation stats = new Evaluation();
    // Convert to SPARQL
    Converter converter;
    try {
      converter = new Converter(formula, offset, maxResults);
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
        if (serverResponse.beginTrack && opts.verbose >= 1)
          LogInfo.logs("Error: %s", serverResponse.error);
      }
      if (serverResponse.beginTrack && opts.verbose >= 2) {
        LogInfo.logs("time: %s", queryStats.timeFig);
        LogInfo.logs("errors: %s", queryStats.errors);
      }
    }

    // If error, then return out
    if (serverResponse.error != null) {
      if (serverResponse.beginTrack && opts.verbose >= 1) LogInfo.end_track();
      if (!serverResponse.cached)
        stats.add(prefix + "error", true);
      return new Response(serverResponse.error, stats);
    }

    if (!serverResponse.cached)
      stats.add(prefix + "error", false);

    // Extract the results from XML now.
    NodeList results = extractResultsFromXml(serverResponse.xml);
    if (results == null) return new Response(ErrorValue.badFormat, stats);
    Value value = new ValuesExtractor(serverResponse.beginTrack, formula, converter).extract(results);

    if (serverResponse.beginTrack && opts.verbose >= 1) LogInfo.end_track();

    return new Response(value, stats);
  }

  ////////////////////////////////////////////////////////////
  // Convert a Formula into a SparqlExpr.
  class Converter {
    private int numVars = 0;  // Used to create new Sparql variables

    // Unit for each SPARQL variable.
    Map<VariableFormula, String> unitsMap = new HashMap<VariableFormula, String>();  // ?y => fb:en.meter

    // For each variable, a description
    Map<VariableFormula, String> descriptionsMap = new HashMap<VariableFormula, String>();  // ?y => "Height (meters)"

    String queryStr;
    SparqlSelect query;  // Resulting SPARQL expression

    // The state used to a SELECT statement (DRT box in which all variables are existentially closed).
    class Box {
      // These are the variables that are first selected.
      List<VariableFormula> initialVars = new ArrayList<VariableFormula>();

      // Mapping from lambda DCS variables to SPARQL variables (which are unique across the entire formula, not just this box).
      Map<VariableFormula, PrimitiveFormula> env = new LinkedHashMap<VariableFormula, PrimitiveFormula>();

      // Some SPARQL variables are bound to quantities based on the SELECT statement (e.g., COUNT(?x)).
      Map<VariableFormula, String> asValuesMap = new HashMap<VariableFormula, String>();  // e.g., ?y => COUNT(?x) or ?y => (?x1 + ?x2)
    }

    public Converter(Formula rootFormula, int offset, int maxResults) throws BadFormulaException {
      Ref<PrimitiveFormula> head = new Ref<PrimitiveFormula>();
      Box box = new Box();

      rootFormula = stripOuterLambdas(box, rootFormula);
      SparqlBlock block = convert(rootFormula, head, null, box);
      query = closeExistentialScope(block, head, box);

      // Select all the variables that appear in the block if we want to return a table.
      // (Provide the evidence.)
      if (opts.includeSupportingInfo) {
        for (SparqlExpr expr : block.children) {
          if (!(expr instanceof SparqlStatement)) continue;
          SparqlStatement stmt = (SparqlStatement) expr;
          if (stmt.arg1 instanceof VariableFormula) addSelectVar(box, query, block, (VariableFormula) stmt.arg1, false);
          if (stmt.arg2 instanceof VariableFormula) addSelectVar(box, query, block, (VariableFormula) stmt.arg2, false);
        }
      }

      if (query.offset == 0) // If not set
        query.offset = offset;
      if (query.limit == -1)  // If not set
        query.limit = maxResults;
      queryStr = "PREFIX fb: <" + FreebaseInfo.freebaseNamespace + "> " + query;
    }

    // Strip off lambdas and add the variables to the environment
    // For example, in (lambda x (lambda y BODY)), we would create an
    // environment {x:NEW_VAR, y:NEW_VAR}, and interpret BODY as a unary.
    private Formula stripOuterLambdas(Box box, Formula formula) {
      while (formula instanceof LambdaFormula) {
        LambdaFormula lambda = ((LambdaFormula) formula);
        VariableFormula var = newVar();
        box.env.put(new VariableFormula(lambda.var), var);
        box.initialVars.add(var);
        formula = lambda.body;
      }
      return formula;
    }

    // Create a SELECT expression (a DRT box).
    private SparqlSelect closeExistentialScope(SparqlBlock block, Ref<PrimitiveFormula> head, Box box) {
      // Optimization: if block only contains one select statement, then can optimize and just return that.
      if (block.children.size() == 1 && block.children.get(0) instanceof SparqlSelect && box.initialVars.size() == 0)
        return (SparqlSelect) block.children.get(0);

      SparqlSelect select = new SparqlSelect();

      // Add initial variables
      for (VariableFormula var : box.initialVars)
        addSelectVarWithName(box, select, block, var);

      // Add head variable (ensure that the head is a variable rather than a primitive value)
      VariableFormula headVar = ensureIsVar(box, block, head);
      addSelectVarWithName(box, select, block, headVar);

      // Add the other supporting variables in the environment (for communicating with nested blocks, e.g. for superlatives).
      for (PrimitiveFormula formula : box.env.values()) {
        if (!(formula instanceof VariableFormula)) continue;
        VariableFormula supportingVar = (VariableFormula) formula;
        addSelectVarWithName(box, select, block, supportingVar);
      }

      select.where = block;
      return select;
    }

    // Add the variable |var|, but also potentially
    private void addSelectVarWithName(Box box, SparqlSelect select, SparqlBlock block, VariableFormula var) {
      addSelectVar(box, select, block, var, false);

      // Get the name of the head
      String unit = unitsMap.get(var);
      // LogInfo.logs("unit[%s] = %s", var, unit);
      if (opts.includeEntityNames && FreebaseInfo.ENTITY.equals(unit)) {
        VariableFormula nameVar = new VariableFormula(var.name + "name");
        addSelectVar(box, select, block, nameVar, true);
        addOptionalStatement(block, var, FreebaseInfo.NAME, nameVar);
      }
    }

    private void addSelectVar(Box box, SparqlSelect select, SparqlBlock block, VariableFormula var, boolean isAuxiliary) {
      if (opts.verbose >= 5) LogInfo.logs("addSelectVar: %s : %s | %s", var, box.asValuesMap.get(var), box.asValuesMap);

      // Check if alrady exists; if so, don't add it again
      for (SparqlSelect.Var oldVar : select.selectVars)
        if (oldVar.var.equals(var)) return;

      select.selectVars.add(new SparqlSelect.Var(var, box.asValuesMap.get(var), unitsMap.get(var), isAuxiliary, descriptionsMap.get(var)));
    }

    // Mutable |head| to make sure it contains a VariableFormula.
    private VariableFormula ensureIsVar(Box box, SparqlBlock block, Ref<PrimitiveFormula> head) {
      VariableFormula headVar;
      if (head.value instanceof VariableFormula) {
        headVar = (VariableFormula) head.value;
      } else {
        headVar = newVar();
        if (head.value != null) {
          // LogInfo.logs("ensureIsVar: %s : %s", headVar, head.value);
          Value value = ((ValueFormula) head.value).value;
          if (value instanceof NumberValue) {  // encode as (3 as ?x1) [FILTER doesn't work for isolated numbers]
            box.asValuesMap.put(headVar, Formulas.getString(head.value));
            unitsMap.put(headVar, valueToUnit(((ValueFormula) head.value).value));
          } else {  // encode as (FILTER (?x1 = fb:en.barack_obama))
            addStatement(block, headVar, "=", head.value);
            addEntityStatement(block, headVar);
          }
        }
        head.value = headVar;
      }
      return headVar;
    }

    // Add statement as well as updating the units information.
    private void addOptionalStatement(SparqlBlock block, PrimitiveFormula arg1, String property, PrimitiveFormula arg2) { addStatement(block, arg1, property, arg2, true); }
    private void addStatement(SparqlBlock block, PrimitiveFormula arg1, String property, PrimitiveFormula arg2) { addStatement(block, arg1, property, arg2, false); }
    private void addStatement(SparqlBlock block, PrimitiveFormula arg1, String property, PrimitiveFormula arg2, boolean optional) {
      block.addStatement(arg1, property, arg2, optional);

      if (arg1 instanceof VariableFormula) {
        VariableFormula var = (VariableFormula) arg1;

        // If the statement is ?x = <value>, then extract unit from value.
        if (property.equals("=") && arg2 instanceof ValueFormula) {
          updateUnit(var, valueToUnit(((ValueFormula) arg2).value));
        } else if (property.equals(FreebaseInfo.TYPE)) {
          String type = Formulas.getString(arg2);
          updateUnit(var, fbInfo.typeToUnit(type, null));
          if (descriptionsMap.get(var) == null)
            descriptionsMap.put(var, fbInfo.getName(type));
        } else if (!SparqlStatement.isOperator(property)) {
          updateUnit(var, fbInfo.getUnit1(property));
        }

        if (descriptionsMap.get(var) == null) {
          descriptionsMap.put(var, fbInfo.getName(fbInfo.getArg1Type(property)));
          if (opts.verbose >= 3) LogInfo.logs("description arg1=%s => %s => %s", var, fbInfo.getArg1Type(property), descriptionsMap.get(var));
        }
      }

      if (arg2 instanceof VariableFormula) {
        // Get unit from Freebase property.
        VariableFormula var = (VariableFormula) arg2;
        updateUnit(var, fbInfo.getUnit2(property));
        descriptionsMap.put(var, fbInfo.getName(property));
        if (opts.verbose >= 3) LogInfo.logs("description arg2=%s => %s => %s", var, property, descriptionsMap.get(var));
      }
    }

    void addEntityStatement(SparqlBlock block, VariableFormula var) {
      // This is dangerous because in the DB, not all entities are necessarily labeled with fb:common.topic
      //addStatement(block, var, FreebaseInfo.TYPE, new ValueFormula(new NameValue(FreebaseInfo.ENTITY)));
      // Only needed when includeEntityNames = true.
      addStatement(block, var, FreebaseInfo.TYPE, newVar());
    }

    // Update the unit of |var| if necessary.
    void updateUnit(VariableFormula var, String unit) {
      if (opts.verbose >= 5) LogInfo.logs("updateUnit: %s : %s", var, unit);
      if (unit == null) return;
      String oldUnit = unitsMap.get(var);
      if (oldUnit == null) { unitsMap.put(var, unit); return; }

      // This replacement isn't quite kosher from a subtyping relation point of
      // view (we're dealing with units, not types).
      if (oldUnit.equals(NumberValue.unitless)) { unitsMap.put(var, unit); return; }  // fb:en.meter replaces fb:en.unitless
      if (oldUnit.equals(FreebaseInfo.ENTITY)) { unitsMap.put(var, unit); return; }  // fb:en.cvt replaces fb:common.topic
      if (oldUnit.equals(FreebaseInfo.CVT)) return;  // Keep CVT

      if (!unit.equals(oldUnit))
        LogInfo.errors("Unit mis-match for %s: old is '%s', new is '%s'", var, oldUnit, unit);
    }

    void updateAsValues(Box box, VariableFormula var, String asValue) {
      if (opts.verbose >= 5) LogInfo.logs("updateAsValues: %s : %s", var, asValue);
      box.asValuesMap.put(var, asValue);
    }

    private String valueToUnit(Value value) {
      // Note: units are fine grained on numbers but coarse on entities.
      if (value instanceof NameValue) return FreebaseInfo.ENTITY;  // Assume this is not a CVT
      if (value instanceof BooleanValue) return FreebaseInfo.BOOLEAN;
      if (value instanceof NumberValue) return ((NumberValue) value).unit;
      if (value instanceof StringValue) return FreebaseInfo.TEXT;
      if (value instanceof DateValue) return FreebaseInfo.DATE;
      return null;
    }

    // Main conversion function.
    // head, modifier: SPARQL variables (e.g., ?x13)
    // box:
    // - env: mapping from lambda-DCS variables (e.g., ?city) to SPARQL variables (?x13)
    // - asValuesMap: additional constraints
    private SparqlBlock convert(Formula rawFormula, Ref<PrimitiveFormula> head, Ref<PrimitiveFormula> modifier, Box box) {
      if (opts.verbose >= 5) LogInfo.begin_track("convert %s: head = %s, modifier = %s, env = %s", rawFormula, head, modifier, box.env);

      // Check binary/unary compatibility
      boolean isNameFormula = (rawFormula instanceof ValueFormula) && (((ValueFormula) rawFormula).value instanceof NameValue);  // Either binary or unary
      boolean needsBinary = (modifier != null);
      boolean providesBinary = rawFormula instanceof LambdaFormula || rawFormula instanceof ReverseFormula;
      if (!isNameFormula && needsBinary != providesBinary) {
        throw new RuntimeException("Binary/unary mis-match: " +
                                   rawFormula + " is " + (providesBinary ? "binary" : "unary") +
                                   ", but need " + (needsBinary ? "binary" : "unary"));
      }

      SparqlBlock block = new SparqlBlock();

      if (rawFormula instanceof ValueFormula) {  // e.g., fb:en.barack_obama or (number 3)
        @SuppressWarnings({ "unchecked" })
        ValueFormula<Value> formula = (ValueFormula) rawFormula;
        if (modifier != null) {  // Binary predicate
          if (head.value == null) head.value = newVar();
          if (modifier.value == null) modifier.value = newVar();
          // Deal with primitive reverses (!fb:people.person.date_of_birth)
          String property = ((NameValue) formula.value).id;
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
          //   ?x fb:people.person.date_of_birth "2003"^xsd:datetime,
          // then create two statements:
          //   ?x fb:people.person.date_of_birth ?v
          //   ?v = "2003"^xsd:datetime [this needs to be transformed]
          if (!SparqlStatement.isOperator(property)) {
            if (arg2 instanceof ValueFormula) {
              Value value = ((ValueFormula) arg2).value;
              if (value instanceof DateValue) {
                VariableFormula v = newVar();
                addStatement(block, v, "=", arg2);
                arg2 = v;
              }
            }
          }
          addStatement(block, arg1, property, arg2);
        } else {  // Unary predicate
          unify(block, head, formula);
        }
      } else if (rawFormula instanceof VariableFormula) {
        VariableFormula var = (VariableFormula) rawFormula;
        PrimitiveFormula value = box.env.get(var);
        if (value == null)
          throw new RuntimeException("Unbound variable: " + var + ", env = " + box.env);
        unify(block, head, value);
      } else if (rawFormula instanceof NotFormula) {
        NotFormula formula = (NotFormula) rawFormula;
        block.add(new SparqlNot(convert(formula.child, head, null, box)));
      } else if (rawFormula instanceof MergeFormula) {
        MergeFormula formula = (MergeFormula) rawFormula;
        switch (formula.mode) {
          case and:
            block.add(convert(formula.child1, head, null, box));
            block.add(convert(formula.child2, head, null, box));
            break;
          case or:
            SparqlUnion union = new SparqlUnion();
            ensureIsVar(box, block, head);
            union.add(convert(formula.child1, head, null, box));
            union.add(convert(formula.child2, head, null, box));
            block.add(union);
            break;
          default:
            throw new RuntimeException("Unhandled mode: " + formula.mode);
        }
      } else if (rawFormula instanceof JoinFormula) {
        // Join
        JoinFormula formula = (JoinFormula) rawFormula;
        Ref<PrimitiveFormula> intermediate = new Ref<PrimitiveFormula>();
        block.add(convert(formula.child, intermediate, null, box));
        block.add(convert(formula.relation, head, intermediate, box));
      } else if (rawFormula instanceof ReverseFormula) {
        // Reverse
        ReverseFormula formula = (ReverseFormula) rawFormula;
        block.add(convert(formula.child, modifier, head, box));  // Switch modifier and head
      } else if (rawFormula instanceof LambdaFormula) {
        // Lambda (new environment, same scope)
        LambdaFormula formula = (LambdaFormula) rawFormula;
        if (modifier.value == null) modifier.value = newVar();
        Box newBox = createNewBox(formula.body, box);  // Create new environment
        newBox.env.put(new VariableFormula(formula.var), modifier.value);  // Map variable to modifier
        block.add(convert(formula.body, head, null, newBox));
        // Place pragmatic constraint that head != modifier (for symmetric relations like spouse)
        if (!opts.lambdaAllowDiagonals)
          block.addStatement(head.value, "!=", modifier.value, false);
        returnAsValuesMap(box, newBox);
      } else if (rawFormula instanceof MarkFormula) {
        // Mark (new environment, same scope)
        MarkFormula formula = (MarkFormula) rawFormula;
        if (head.value == null) head.value = newVar();
        Box newBox = createNewBox(formula.body, box);  // Create new environment
        newBox.env.put(new VariableFormula(formula.var), head.value);  // Map variable to head (ONLY difference with lambda)
        block.add(convert(formula.body, head, null, newBox));
        returnAsValuesMap(box, newBox);
      } else if (rawFormula instanceof SuperlativeFormula) {
        // Superlative (new environment, close scope)
        SuperlativeFormula formula = (SuperlativeFormula) rawFormula;

        int rank = Formulas.getInt(formula.rank);
        int count = Formulas.getInt(formula.count);

        boolean useOrderBy = rank != 1 || count != 1;
        boolean isMax = formula.mode == SuperlativeFormula.Mode.argmax;
        if (useOrderBy) {
          // Method 1: use ORDER BY
          // + can deal with offset and limit
          // - but can't be nested
          // - doesn't handle ties at the top
          // Recurse
          Box newBox = createNewBox(formula.head, box);  // Create new environment
          SparqlBlock newBlock = convert(formula.head, head, null, newBox);
          Ref<PrimitiveFormula> degree = new Ref<PrimitiveFormula>();
          newBlock.add(convert(formula.relation, head, degree, newBox));

          // Apply the aggregation operation
          VariableFormula degreeVar = ensureIsVar(box, block, degree);

          // Force |degreeVar| to be selected as a variable.
          box.env.put(new VariableFormula("degree"), degreeVar);
          newBox.env.put(new VariableFormula("degree"), degreeVar);

          SparqlSelect select = closeExistentialScope(newBlock, head, newBox);
          select.sortVars.add(isMax ? new VariableFormula(applyVar("DESC", degreeVar)) : degreeVar);
          select.offset = rank - 1;
          select.limit = count;
          block.add(select);
        } else {
          // Method 2: use MAX
          // - can't deal with offset and limit
          // + can be nested
          // + handles ties at the top
          // (argmax 1 1 h r) ==> (h (r (mark degree (max ((reverse r) e)))))
          AggregateFormula.Mode mode = isMax ? AggregateFormula.Mode.max : AggregateFormula.Mode.min;
          Formula best = new MarkFormula("degree", new AggregateFormula(mode, new JoinFormula(new ReverseFormula(formula.relation), formula.head)));
          Formula transformed = new MergeFormula(MergeFormula.Mode.and, formula.head, new JoinFormula(formula.relation, best));
          if (opts.verbose >= 5) LogInfo.logs("TRANSFORMED: %s", transformed);
          block.add(convert(transformed, head, null, box));
        }
      } else if (rawFormula instanceof AggregateFormula) {
        // Aggregate (new environment, close scope)
        AggregateFormula formula = (AggregateFormula) rawFormula;
        ensureIsVar(box, block, head);

        // Recurse
        Box newBox = createNewBox(formula.child, box);  // Create new environment
        Ref<PrimitiveFormula> newHead = new Ref<PrimitiveFormula>(newVar());  // Stores the aggregated value
        SparqlBlock newBlock = convert(formula.child, newHead, null, newBox);

        VariableFormula var = (VariableFormula) head.value;  // e.g., ?x

        // Variable representing the aggregation
        VariableFormula newVar = (VariableFormula) newHead.value;  // e.g., ?y = COUNT(?x)
        String headUnit = formula.mode == AggregateFormula.Mode.count ? NumberValue.unitless : unitsMap.get(newHead.value);
        updateUnit(var, headUnit);
        descriptionsMap.put(var, capitalize(formula.mode.toString()));

        // If do aggregation on dates, need to convert wrap with xsd:datetime
        boolean specialDateHandling =
          (formula.mode == AggregateFormula.Mode.min || formula.mode == AggregateFormula.Mode.max) &&
          FreebaseInfo.DATE.equals(headUnit);

        if (specialDateHandling) {
          VariableFormula dateVar = new VariableFormula(var.name + "date");
          updateAsValues(newBox, dateVar, applyVar(formula.mode.toString(), SparqlUtils.dateTimeStr(newVar)));  // ?dateVar AS max(xsd:datetime(?newVar))
          block.add(closeExistentialScope(newBlock, new Ref<PrimitiveFormula>(dateVar), newBox));
          addStatement(block, new VariableFormula(SparqlUtils.dateTimeStr(var)), "=", dateVar);  // Add xsd:datetime(?var) = ?dateVar on the outside.
          // PROBLEM: (max ...) doesn't work by itself because the variable returned (var) is only involved in the previous construction,
          // but this is fine in conjunction with other things like argmax.
        } else {
          updateAsValues(newBox, var, applyVar(formula.mode.toString(), newVar));  // ?var AS COUNT(?newVar)
          block.add(closeExistentialScope(newBlock, head, newBox));
        }
      } else if (rawFormula instanceof ArithmeticFormula) {  // (+ (number 3) (number 5))
        ArithmeticFormula formula = (ArithmeticFormula) rawFormula;
        Ref<PrimitiveFormula> newHead1 = new Ref<PrimitiveFormula>();
        Ref<PrimitiveFormula> newHead2 = new Ref<PrimitiveFormula>();
        block.add(convert(formula.child1, newHead1, null, box));
        block.add(convert(formula.child2, newHead2, null, box));
        if (head.value == null) head.value = newVar();
        VariableFormula var = (VariableFormula) head.value;
        PrimitiveFormula var1 = newHead1.value;
        PrimitiveFormula var2 = newHead2.value;
        updateAsValues(box, var, applyOpVar(ArithmeticFormula.modeToString(formula.mode), var1, var2));
        String unit = unitsMap.get(var1);  // Just take the unit from variable 1
        if (unit == null) unit = NumberValue.unitless;
        String newUnit;
        switch (formula.mode) {
          case add:
            newUnit = unit;
            break;
          case sub:
            newUnit = FreebaseInfo.DATE.equals(unit) ? NumberValue.yearUnit : unit;
            break;
          default:
            // Don't even try to get the unit right
            newUnit = NumberValue.unitless;
            break;
        }
        updateUnit(var, newUnit);
        descriptionsMap.put(var, "Number");  // This is weak
      } else {
        throw new RuntimeException("Unhandled formula: " + rawFormula);
      }

      if (opts.verbose >= 5) LogInfo.logs("return: head = %s, modifier = %s, env = %s", head, modifier, box.env);
      if (opts.verbose >= 5) LogInfo.end_track();

      return block;
    }

    private String capitalize(String s) { return Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    // Copy |box|'s |env|, but only keep the variables which are used in |formula| (these are the free variables).
    // This is an important optimization for converting to SPARQL.
    private Box createNewBox(Formula formula, Box box) {
      Box newBox = new Box();
      for (VariableFormula key : box.env.keySet())
        if (Formulas.containsFreeVar(formula, key))
          newBox.env.put(key, box.env.get(key));
      return newBox;
    }

    // Copy asValuesMap constraints from newBox to box.
    // This is for when we create a new environment (newBox), but maintain the same scope,
    // so we don't rely on closeExistentialScope to include the asValuesMap constraints.
    private void returnAsValuesMap(Box box, Box newBox) {
      for (Map.Entry<VariableFormula, String> e : newBox.asValuesMap.entrySet()) {
        if (box.asValuesMap.containsKey(e.getKey()))
          throw new RuntimeException("Copying asValuesMap involves overwriting: " + box + " <- " + newBox);
        box.asValuesMap.put(e.getKey(), e.getValue());
      }
    }

    private void unify(SparqlBlock block, Ref<PrimitiveFormula> head, PrimitiveFormula value) {
      if (head.value == null) {
        // |head| is not set, just use |value|.
        head.value = value;
      } else {
        // |head| is already set, so add a constraint that it equals |value|.
        // This happens when the logical form is just a single entity (e.g., fb:en.barack_obama).
        addStatement(block, head.value, "=", value);
        if (head.value instanceof VariableFormula && value instanceof ValueFormula && ((ValueFormula) value).value instanceof NameValue)
          addEntityStatement(block, (VariableFormula) head.value);
      }
    }

    // Helper functions
    private String applyVar(String func, VariableFormula var) { return applyVar(func, var.name); }
    private String applyVar(String func, String var) {
      if (func.equals("count")) var = "DISTINCT " + var;
      return func + "(" + var + ")";
    }

    private String applyOpVar(String func, PrimitiveFormula var1, PrimitiveFormula var2) {
      // Special function for taking the difference between dates.
      LogInfo.logs("%s %s", var1, var2);
      if (func.equals("-") && (var1 instanceof VariableFormula) && FreebaseInfo.DATE.equals(unitsMap.get(var1)))
        return "bif:datediff(\"year\"," + SparqlUtils.dateTimeStr(var2) + "," + SparqlUtils.dateTimeStr(var1) + ")";
      else if (func.equals("+") && (var1 instanceof VariableFormula) && FreebaseInfo.DATE.equals(unitsMap.get(var1)))
        return "bif:dateadd(\"year\"," + Formulas.getString(var2) + "," + SparqlUtils.dateTimeStr(var1) + ")";  // date + number
      else
        return '(' + Formulas.getString(var1) + ' ' + func + ' ' + Formulas.getString(var2) + ')';
    }

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
    final List<String> header;

    public ValuesExtractor(boolean beginTrack, Formula formula, Converter converter) {
      this.beginTrack = beginTrack;
      this.formula = formula;

      this.selectVars = Lists.newArrayList();
      this.units = Lists.newArrayList();
      this.header = Lists.newArrayList();
      for (SparqlSelect.Var var : converter.query.selectVars) {
        if (var.isAuxiliary) continue;
        this.selectVars.add(var.var.name);
        this.units.add(var.unit);
        this.header.add(var.description);
      }
    }

    // |results| is (result (binding (uri ...)) ...) or (result (binding (literal ...)) ...)
    Value extract(NodeList results) {
      // For each result (row in a table)...
      if (beginTrack && opts.verbose >= 2) {
        LogInfo.begin_track("%d results", results.getLength());
        if (opts.returnTable) LogInfo.logs("Header: %s", header);
      }

      List<Value> firstValues = new ArrayList<Value>();  // If not returning a table
      List<List<Value>> rows = new ArrayList<List<Value>>();  // If returning table

      for (int i = 0; i < results.getLength(); i++) {
        List<Value> row = nodeToValue(results.item(i));
        if (opts.returnTable)
          rows.add(row);
        else
          firstValues.add(row.get(0));
        if (beginTrack && opts.verbose >= 2) LogInfo.logs("Row %d: %s", i, row);
      }
      if (beginTrack && opts.verbose >= 2) LogInfo.end_track();

      if (opts.returnTable)
        return new TableValue(header, rows);
      else
        return new ListValue(firstValues);
    }

    private List<Value> nodeToValue(Node result) {
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
        Element binding = (Element) bindings.item(j);

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
          value = description == null ? null : DateValue.parseDateValue(description);
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
          value = new NumberValue("NAN".equals(description) || description == null ? Double.NaN : Double.parseDouble(description), unit);
        }
        row.add(value);
      }
      return row;
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
    @Option(gloss = "File containing formulas to execute")
    public String formulasPath;
  }

  public static void main(String[] args) throws IOException {
    OptionsParser parser = new OptionsParser();
    MainOptions mainOpts = new MainOptions();
    parser.registerAll(new Object[]{"SparqlExecutor", SparqlExecutor.opts, "FreebaseInfo", FreebaseInfo.opts, "main", mainOpts});
    parser.parse(args);

    LogInfo.begin_track("main()");
    SparqlExecutor executor = new SparqlExecutor();

    if (mainOpts.formula != null) {
      LogInfo.logs("%s", executor.execute(Formulas.fromLispTree(LispTree.proto.parseFromString(mainOpts.formula)), null).value);
    }

    if (mainOpts.formulasPath != null) {
      Iterator<LispTree> trees = LispTree.proto.parseFromFile(mainOpts.formulasPath);
      while (trees.hasNext()) {
        LogInfo.logs("%s", executor.execute(Formulas.fromLispTree(trees.next()), null).value);
      }
    }

    if (mainOpts.sparql != null)
      LogInfo.logs("%s", executor.makeRequest(mainOpts.sparql, opts.endpointUrl).xml);

    LogInfo.end_track();
  }
}
