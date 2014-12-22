package edu.stanford.nlp.sempre;

import com.google.common.collect.Lists;
import fig.basic.*;
import fig.html.HtmlElement;
import fig.html.HtmlUtils;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.HttpCookie;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;

import java.io.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.math.BigInteger;
import java.security.SecureRandom;

import static fig.basic.LogInfo.logs;

final class SecureIdentifiers {
  private SecureIdentifiers() { }

  private static SecureRandom random = new SecureRandom();
  public static String getId() {
    return new BigInteger(130, random).toString(32);
  }
}

/**
 * This class implements a simple HTTP server which provides a web interface
 * into SEMPRE just like Master.runInteractivePrompt() exposes a command-line
 * tool.  Most of the work is dispatched to Master.processLine().
 * Cookies are used to store the session ID.
 *
 * @author Percy Liang
 */
public class Server {
  public static class Options {
    @Option public int port = 8400;
    @Option public int numThreads = 4;
    @Option public String title = "SEMPRE Demo";
    @Option public String headerPath;
    @Option public String basePath = "demo-www";
    @Option public int verbose = 1;
    @Option public int htmlVerbose = 1;
  }
  public static Options opts = new Options();

  Master master;
  public static final HtmlUtils H = new HtmlUtils();

  class Handler implements HttpHandler {
    public void handle(HttpExchange exchange) {
      try {
        new ExchangeState(exchange);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  class ExchangeState {
    // Input
    HttpExchange exchange;
    Map<String, String> reqParams = new HashMap<>();
    String remoteHost;

    // For header
    HttpCookie cookie;
    boolean isNewSession;
    String format;
    boolean jsonFormat() { return format.equals("json"); }

    // For writing main content

    public ExchangeState(HttpExchange exchange) throws IOException {
      this.exchange = exchange;

      URI uri = exchange.getRequestURI();
      this.remoteHost = exchange.getRemoteAddress().getHostName();

      // Don't use uri.getQuery: it can't distinguish between '+' and '-'
      String[] tokens = uri.toString().split("\\?");
      if (tokens.length == 2) {
        for (String s : tokens[1].split("&")) {
          String[] kv = s.split("=", 2);
          try {
            String key = URLDecoder.decode(kv[0], "UTF-8");
            String value = URLDecoder.decode(kv[1], "UTF-8");
            logs("%s => %s", key, value);
            reqParams.put(key, value);
          } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
          }
        }
      }
      this.format = MapUtils.get(reqParams, "format", "html");

      String cookieStr = exchange.getRequestHeaders().getFirst("Cookie");
      if (cookieStr != null) {  // Cookie already exists
        cookie = HttpCookie.parse(cookieStr).get(0);
        isNewSession = false;
      } else {
        if (!jsonFormat()) {
          cookie = new HttpCookie("sessionId", SecureIdentifiers.getId());
        } else {
          cookie = null;
        }
        isNewSession = true;  // Create a new cookie
      }

      String sessionId = null;
      if (cookie != null) sessionId = cookie.getValue();
      if (opts.verbose >= 2)
        LogInfo.logs("GET %s from %s (%ssessionId=%s)", uri, remoteHost, isNewSession ? "new " : "", sessionId);

      String uriPath = uri.getPath();
      if (uriPath.equals("/")) uriPath += "index.html";
      if (uriPath.equals("/sempre")) {
        handleQuery(sessionId);
      } else {
        getFile(opts.basePath + uriPath);
      }

      exchange.close();
    }

    String getMimeType(String path) {
      String[] tokens = path.split("\\.");
      String ext = tokens[tokens.length - 1];
      if (ext.equals("html")) return "text/html";
      if (ext.equals("css")) return "text/css";
      if (ext.equals("jpeg")) return "image/jpeg";
      if (ext.equals("gif")) return "image/gif";
      return "text/plain";
    }

    void setHeaders(String mimeType) throws IOException {
      Headers headers = exchange.getResponseHeaders();
      headers.set("Content-Type", mimeType);
      headers.set("Access-Control-Allow-Origin", "*");
      if (isNewSession && cookie != null)
        headers.set("Set-Cookie", cookie.toString());
      exchange.sendResponseHeaders(200, 0);
    }

    private HtmlElement makeInputBox(String line, String action) {
      return H.div().child(
          H.form().action(action)
           .child(H.text(line == null ? "" : line).cls("question").autofocus().size(50).name("q"))
           .child(H.button("Go").cls("ask"))
           .end());
    }

    private HtmlElement makeTooltip(HtmlElement main, HtmlElement aux) {
      return H.a().cls("info").child(main).child(H.span().cls("tooltip").child(aux));
    }
    private HtmlElement makeTooltip(HtmlElement main, HtmlElement aux, String link) {
      return H.a().href(link).cls("info").child(main).child(H.span().cls("tooltip").child(aux));
    }

    public final String freebaseWebsite = "http://www.freebase.com/";
    public String id2website(String id) {
      assert id.startsWith("fb:") : id;
      return freebaseWebsite + id.substring(3).replaceAll("\\.", "/");
    }

    HtmlElement valueToElem(Value value) {
      if (value == null) return H.span();
      if (value instanceof NameValue) {
        NameValue nameValue = (NameValue) value;
        return H.a().href(id2website(nameValue.id)).child(nameValue.description == null ? nameValue.id : nameValue.description);
      } else if (value instanceof NumberValue) {
        NumberValue numberValue = (NumberValue) value;
        return H.span().child(Fmt.D(numberValue.value) + (numberValue.unit.equals(NumberValue.unitless) ? "" : " " + numberValue.unit));
      } else if (value instanceof UriValue) {
        UriValue uriValue = (UriValue) value;
        return H.a().href(uriValue.value).child(uriValue.value);
      } else if (value instanceof DateValue) {
        DateValue dateValue = (DateValue) value;
        return H.span().child(dateValue.year + (dateValue.month == -1 ? "" : "-" + dateValue.month + (dateValue.day == -1 ? "" : "-" + dateValue.day)));
      } else if (value instanceof StringValue) {
        return H.span().child(((StringValue) value).value);
      } else if (value instanceof TableValue) {
        HtmlElement table = H.table().cls("valueTable");
        HtmlElement header = H.tr();
        boolean first = true;
        for (String item : ((TableValue) value).header) {
          if (!first) header.child(H.td("&nbsp;&nbsp;&nbsp;"));
          first = false;
          header.child(H.td(H.b(item)));
        }
        table.child(header);
        for (List<Value> rowValues : ((TableValue) value).rows) {
          HtmlElement row = H.tr();
          first = true;
          for (Value x : rowValues) {
            // TODO(pliang): add horizontal spacing only using CSS
            if (!first) row.child(H.td("&nbsp;&nbsp;&nbsp;"));
            first = false;
            row.child(H.td(valueToElem(x)));
          }
          table.child(row);
        }
        return table;
      } else {
        // Default rendering
        return H.span().child(value.toString());
      }
    }

    private HtmlElement makeAnswerBox(Master.Response response, String uri) {
      HtmlElement answer;
      if (response.getExample().getPredDerivations().size() == 0) {
        answer = H.span().child("(none)");
      } else {
        answer = valueToElem(response.getDerivation().getValue());
      }

      return H.table().child(
          H.tr()
           .child(
               H.td(
                   makeTooltip(
                       H.span().cls("correctButton").child("[Correct]"),
                       H.div().cls("bubble").child("If this answer is correct, click to add as a new training example!"),
                       uri + "&accept=" + response.getCandidateIndex())))
           .child(H.td(H.span().cls("answer").child(answer)))
           .end());
    }

    private HtmlElement makeGroup(List<HtmlElement> items) {
      HtmlElement table = H.table().cls("groupResponse");
      for (HtmlElement item : items)
        table.child(H.tr().child(H.td(item)));
      return table;
    }

    HtmlElement makeDetails(Master.Response response, String uri) {
      Example ex = response.getExample();
      List<HtmlElement> items = new ArrayList<HtmlElement>();
      if (opts.htmlVerbose >= 1)
        items.add(makeLexical(ex));
      if (ex.getPredDerivations().size() > 0) {
        if (opts.htmlVerbose >= 1) {
          items.add(makeDerivation(ex, response.getDerivation(), true));
          items.add(makeFeatures(response.getDerivation(), false));
        }
        items.add(makeCandidates(ex, uri));
      }

      return H.div().cls("details").child(makeGroup(items));
    }

    HtmlElement makeDerivation(Example ex, Derivation deriv, boolean moreInfo) {
      HtmlElement table = H.table();

      // Show the derivation
      table.child(H.tr().child(H.td(makeDerivationHelper(ex, deriv, "", moreInfo))));

      String header = "Derivation";
      return H.div()
              .child(H.span().cls("listHeader").child(header))
              .child(table);
    }

    HtmlElement makeDerivationHelper(Example ex, Derivation deriv, String indent, boolean moreInfo) {
      // TODO(pliang): make this prettier
      HtmlElement cat;
      if (moreInfo) {
        HtmlElement tooltip = H.div();
        tooltip.child(H.span(deriv.rule.toString()).cls("nowrap"));
        tooltip.child(makeFeatures(deriv, true));
        cat = makeTooltip(H.span(deriv.cat), tooltip);
      } else {
        cat = H.span(deriv.cat);
      }
      String description = cat + "[&nbsp;" + H.span().child(ex.phraseString(deriv.start, deriv.end)).cls("word") + "]" + " &rarr; " + deriv.formula;
      HtmlElement node = H.div().child(indent + description);
      for (Derivation child : deriv.children)
        node.child(makeDerivationHelper(ex, child, indent + "&nbsp;&nbsp;&nbsp;&nbsp;", moreInfo));
      return node;
    }

    HtmlElement makeFeatures(Derivation deriv, boolean local) {
      HtmlElement table = H.table();

      Params params = master.getParams();
      Map<String, Double> features = new HashMap<String, Double>();
      if (local)
        deriv.incrementLocalFeatureVector(1, features);
      else
        deriv.incrementAllFeatureVector(1, features);

      List<Map.Entry<String, Double>> entries = Lists.newArrayList();
      double sumValue = 0;
      for (Map.Entry<String, Double> entry : features.entrySet()) {
        String feature = entry.getKey();
        if (entry.getValue() == 0) continue;
        double value = entry.getValue() * params.getWeight(feature);
        sumValue += value;
        entries.add(new java.util.AbstractMap.SimpleEntry<String, Double>(feature, value));
      }
      Collections.sort(entries, new ValueComparator<String, Double>(false));
      table.child(
          H.tr()
           .child(H.td(H.b("Feature")))
           .child(H.td(H.b("Value")))
           .child(H.td(H.b("Weight"))));

      for (Map.Entry<String, Double> entry : entries) {
        String feature = entry.getKey();
        double value = entry.getValue();
        double weight = params.getWeight(feature);
        table.child(
            H.tr()
             .child(H.td(feature))
             .child(H.td(Fmt.D(MapUtils.getDouble(features, feature, 0))))
             .child(H.td(Fmt.D(weight))));
      }

      String header;
      if (local) {
        double localScore = deriv.localScore(params);
        double score = deriv.getScore();
        if (deriv.children == null)
          header = String.format("Local features (score = %s)", Fmt.D(score));
        else
          header = String.format("Local features (score = %s + %s = %s)", Fmt.D(score - localScore), Fmt.D(localScore), Fmt.D(score));
      } else {
        header = String.format("All features (score=%s, prob=%s)", Fmt.D(deriv.getScore()), Fmt.D(deriv.getProb()));
      }
      return H.div()
              .child(H.span().cls("listHeader").child(header))
              .child(table);
    }

    HtmlElement linkSelect(int index, String uri, String str) {
      return H.a().href(uri + "&select=" + index).child(str);
    }

    private HtmlElement makeCandidates(Example ex, String uri) {
      HtmlElement table = H.table().cls("candidateTable");
      HtmlElement header = H.tr()
         .child(H.td(H.b("Rank")))
         .child(H.td(H.b("Score")))
         .child(H.td(H.b("Answer")));
      if (opts.htmlVerbose >= 1)
         header.child(H.td(H.b("Formula")));
      table.child(header);
      for (int i = 0; i < ex.getPredDerivations().size(); i++) {
        Derivation deriv = ex.getPredDerivations().get(i);

        HtmlElement correct = makeTooltip(
          H.span().cls("correctButton").child("[Correct]"),
          H.div().cls("bubble").child("If this answer is correct, click to add as a new training example!"),
          uri + "&accept=" + i);
        String value = shorten(deriv.getValue() == null ? "" : deriv.getValue().toString(), 200);
        HtmlElement formula = makeTooltip(
          H.span(deriv.getFormula().toString()),
          H.div().cls("nowrap").child(makeDerivation(ex, deriv, false)),
          uri + "&select=" + i);
        HtmlElement row = H.tr()
           .child(H.td(linkSelect(i, uri, i + " " + correct)).cls("nowrap"))
           .child(H.td(Fmt.D(deriv.getScore())))
           .child(H.td(value)).style("width:250px");
        if (opts.htmlVerbose >= 1)
          row.child(H.td(formula));
        table.child(row);
      }
      return H.div()
              .child(H.span().cls("listHeader").child("Candidates"))
              .child(table);
    }

    private String shorten(String s, int n) {
      if (s.length() <= n) return s;
      return s.substring(0, n / 2) + "..." + s.substring(s.length() - n / 2);
    }

    private void markLexical(Derivation deriv, CandidatePredicates[] predicates) {
      // TODO(pliang): generalize this to the case where the formula is a
      // NameFormula but the child is a StringFormula?
      if (deriv.getRule() != null &&
          deriv.getRule().getSem() != null &&
          deriv.getRule().getSem().getClass().getSimpleName().equals("LexiconFn"))
        predicates[deriv.getStart()].add(deriv.getFormula(), deriv.getEnd() - deriv.getStart(), deriv.getScore());
      for (Derivation child : deriv.getChildren())
        markLexical(child, predicates);
    }

    class CandidatePredicates {
      // Parallel arrays
      List<Formula> predicates = new ArrayList<Formula>();
      List<Integer> spanLengths = new ArrayList<Integer>();
      List<Double> scores = new ArrayList<Double>();

      void add(Formula formula, int spanLength, double score) {
        predicates.add(formula);
        spanLengths.add(spanLength);
        scores.add(score);
      }
      int size() { return predicates.size(); }

      String formatPredicate(int i) {
        return predicates.get(i).toString() + (spanLengths.get(i) == 1 ? "" : " [" + spanLengths.get(i) + "]");
      }
    }

    // Move to fig
    double[] toDoubleArray(List<Double> l) {
      double[] a = new double[l.size()];
      for (int i = 0; i < l.size(); i++) a[i] = l.get(i);
      return a;
    }


    HtmlElement makeLexical(Example ex) {
      HtmlElement predicatesElem = H.tr();
      HtmlElement tokensElem = H.tr();

      // Mark all the predicates used in any derivation on the beam.
      // Note: this is not all possible.
      CandidatePredicates[] predicates = new CandidatePredicates[ex.getTokens().size()];
      for (int i = 0; i < ex.getTokens().size(); i++)
        predicates[i] = new CandidatePredicates();
      for (Derivation deriv : ex.getPredDerivations())
        markLexical(deriv, predicates);

      // Build up |predicatesElem| and |tokensElem|
      for (int i = 0; i < ex.getTokens().size(); i++) {
        tokensElem.child(
            H.td(
                makeTooltip(
                    H.span().cls("word").child(ex.getTokens().get(i)),
                    H.span().cls("tag").child("POS: " + ex.languageInfo.posTags.get(i)),
                    "")));

        if (predicates[i].size() == 0) {
          predicatesElem.child(H.td(""));
        } else {
          // Show possible predicates for a word
          HtmlElement pe = H.table().cls("predInfo");
          int[] perm = ListUtils.sortedIndices(toDoubleArray(predicates[i].scores), true);
          Set<String> formulaSet = new HashSet<String>();
          for (int j : perm) {
            String formula = predicates[i].formatPredicate(j);
            if (formulaSet.contains(formula)) continue;  // Dedup
            formulaSet.add(formula);
            double score = predicates[i].scores.get(j);
            pe.child(H.tr().child(H.td(formula)).child(H.td(Fmt.D(score))));
          }
          predicatesElem.child(H.td(makeTooltip(H.span().child(predicates[i].formatPredicate(perm[0])), pe, "")));
        }
      }

      return H.div().cls("lexicalResponse")
              .child(H.span().cls("listHeader").child("Lexical Triggers"))
              .child(H.table().child(predicatesElem).child(tokensElem));
    }

    String makeJson(Master.Response response) {
      Map<String, Object> json = new HashMap<String, Object>();
      List<Object> items = new ArrayList<Object>();
      json.put("candidates", items);
      for (Derivation deriv : response.getExample().getPredDerivations()) {
        Map<String, Object> item = new HashMap<String, Object>();
        Value value = deriv.getValue();
        if (value instanceof UriValue) {
          item.put("url", ((UriValue) value).value);
        } else if (value instanceof TableValue) {
          TableValue tableValue = (TableValue) value;
          item.put("header", tableValue.header);
          List<List<String>> rowsObj = new ArrayList<List<String>>();
          item.put("rows", rowsObj);
          for (List<Value> row : tableValue.rows) {
            List<String> rowObj = new ArrayList<String>();
            for (Value v : row)
              rowObj.add(v.toString());
            rowsObj.add(rowObj);
          }
        } else {
          item.put("value", value.toString());
        }
        item.put("score", deriv.score);
        item.put("prob", deriv.prob);
        items.add(item);
      }

      return Json.writeValueAsStringHard(json);
    }

    // Catch exception if any.
    Master.Response processQuery(Session session, String query) {
      try {
        return master.processQuery(session, query);
      } catch (Throwable t) {
        t.printStackTrace();
        return null;
      }
    }

    // If query is not already the last query, make it the last query.
    boolean ensureQueryIsLast(Session session, String query) {
      if (query != null && !query.equals(session.getLastQuery())) {
        Master.Response response = processQuery(session, query);
        if (response == null) return false;
      }
      return true;
    }

    void handleQuery(String sessionId) throws IOException {
      String query = reqParams.get("q");

      // If JSON, don't store cookies.
      Session session = master.getSession(sessionId);
      session.remoteHost = remoteHost;
      session.format = format;

      if (query == null) query = session.getLastQuery();
      if (query == null) query = "";
      logs("Server.handleQuery %s: %s", session.id, query);

      // Print header
      if (jsonFormat())
        setHeaders("application/json");
      else
        setHeaders("text/html");
      PrintWriter out = new PrintWriter(new OutputStreamWriter(exchange.getResponseBody()));
      if (!jsonFormat()) {
        out.println("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">");
        out.println(H.html().open());
        out.println(
            H.head()
             .child(H.title(opts.title))
             .child(H.link().rel("stylesheet").type("text/css").href("main.css"))
             .child(H.script().src("main.js"))
             .end());

        out.println(H.body().open());

        if (opts.headerPath != null) {
          for (String line : IOUtils.readLinesHard(opts.headerPath))
            out.println(line);
        }
      }

      String uri = exchange.getRequestURI().toString();

      // Encode the URL parameters into the freeform text.
      // A bit backwards, but keeps uniformity.
      String select = reqParams.get("select");
      if (select != null) {
        if (ensureQueryIsLast(session, query))
          query = LispTree.proto.newList("select", select).toString();
        else
          query = null;
      }
      String accept = reqParams.get("accept");
      if (accept != null) {
        if (ensureQueryIsLast(session, query))
          query = LispTree.proto.newList("accept", accept).toString();
        else
          query = null;
      }

      // Handle the request
      Master.Response masterResponse = null;
      if (query != null)
        masterResponse = processQuery(session, query);

      // Print history of exchanges
      if (session.context.exchanges.size() > 0 && !jsonFormat()) {
        HtmlElement context = H.table().cls("context");
        for (ContextValue.Exchange e : session.context.exchanges) {
          HtmlElement row = H.tr().child(H.td(H.span().cls("word").child(e.utterance)));
          row.child(H.td(H.span("&nbsp;&nbsp;&nbsp;&nbsp;"))).child(H.td(e.value.toString()));
          if (opts.htmlVerbose >= 1)
            row.child(H.td(H.span("&nbsp;&nbsp;&nbsp;&nbsp;"))).child(H.td(e.formula.toString()));
          context.child(row);
        }
        out.println(context.toString());
      }

      // Print input box for new utterance
      if (!jsonFormat()) {
        String defaultQuery = query != null ? query : session.getLastQuery();
        out.println(makeInputBox(defaultQuery, uri).toString());
      }

      if (masterResponse != null) {
        // Render answer
        Example ex = masterResponse.getExample();
        if (ex != null) {
          if (!jsonFormat()) {
            out.println(makeAnswerBox(masterResponse, uri).toString());
            out.println(makeDetails(masterResponse, uri).toString());
          } else {
            out.println(makeJson(masterResponse));
          }
        }

        if (!jsonFormat() && opts.htmlVerbose >= 1) {
          // Write response to user
          out.println(H.elem("pre").open());
          for (String outLine : masterResponse.getLines())
            out.println(outLine);
          out.println(H.elem("pre").close());
        }
      } else {
        if (query != null && !jsonFormat())
          out.println(H.span("Internal error!").cls("error"));
      }

      if (!jsonFormat()) {
        out.println(H.body().close());
        out.println(H.html().close());
      }

      out.close();
    }

    void getResults() throws IOException {
      setHeaders("application/json");
      Map<String, String> map = new HashMap<>();
      map.put("a", "3");
      map.put("b", "4");

      PrintWriter writer = new PrintWriter(new OutputStreamWriter(exchange.getResponseBody()));
      writer.println(Json.writeValueAsStringHard(map));
      writer.close();
    }

    void getFile(String path) throws IOException {
      if (!new File(path).exists()) {
        LogInfo.logs("File doesn't exist: %s", path);
        exchange.sendResponseHeaders(404, 0);  // File not found
        return;
      }

      setHeaders(getMimeType(path));
      if (opts.verbose >= 2)
        LogInfo.logs("Sending %s", path);
      OutputStream out = new BufferedOutputStream(exchange.getResponseBody());
      InputStream in = new FileInputStream(path);
      IOUtils.copy(in, out);
    }
  }

  public Server(Master master) {
    this.master = master;
  }

  void run() {
    try {
      String hostname = fig.basic.SysInfoUtils.getHostName();
      HttpServer server = HttpServer.create(new InetSocketAddress(opts.port), 10);
      ExecutorService pool = Executors.newFixedThreadPool(opts.numThreads);
      server.createContext("/", new Handler());
      server.setExecutor(pool);
      server.start();
      LogInfo.logs("Server started at http://%s:%s/sempre", hostname, opts.port);
      LogInfo.log("Press Ctrl-D to terminate.");
      while (LogInfo.stdin.readLine() != null) { }
      LogInfo.log("Shutting down server...");
      server.stop(0);
      LogInfo.log("Shutting down executor pool...");
      pool.shutdown();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
