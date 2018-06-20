package edu.stanford.nlp.sempre.interactive;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import edu.stanford.nlp.sempre.Json;
import fig.basic.Evaluation;
import fig.basic.IOUtils;
import fig.basic.LogInfo;
import fig.exec.Execution;

public class SimulationAnalyzer {
  @SuppressWarnings("unchecked")
  public static Map<String, Object> getStats(String jsonResponse) {
    Map<String, Object> response = Json.readMapHard(jsonResponse);
    if (response.containsKey("stats"))
      return (Map<String, Object>) response.get("stats");
    return null;
  }

  static Evaluation qEval = new Evaluation();
  static Evaluation acceptEval = new Evaluation();
  static Evaluation dEval = new Evaluation();
  static int queryCount = 0;

  // add stats to the query.
  public synchronized static void addStats(Map<String, Object> query, String jsonResponse) {
    Map<String, Object> stats = getStats(jsonResponse);
    Map<String, Object> line = new LinkedHashMap<String, Object>(query);
    LogInfo.logs("stats: %s", stats);
    if (stats == null) {
      LogInfo.logs("No stats");
      LogInfo.log(query);
      LogInfo.log(jsonResponse);
      return;
    }

    // make sure no key conflict
    for (Entry<String, Object> entry : stats.entrySet()) {
      line.put("stats." + entry.getKey(), entry.getValue());
    }
    line.put("queryCount", ++queryCount);
    PrintWriter infoFile = IOUtils.openOutAppendHard(Execution.getFile("plotInfo.json"));
    infoFile.println(Json.writeValueAsStringHard(line));
    infoFile.close();

    if (!stats.containsKey("type"))
      return;

    if (stats.get("type").equals("def") && !stats.containsKey("error")) {
      qEval.add("def.head_len", (Integer) stats.get("head_len"));
      qEval.add("def.json_len", (Integer) stats.get("json_len"));
      qEval.add("def.num_failed", (Integer) stats.get("num_failed"));
      qEval.add("def.num_body", (Integer) stats.get("num_body"));
      qEval.add("def.num_rules", (Integer) stats.get("num_rules"));
      qEval.add("def.time",  (Integer) stats.get("count"));
    }

    if (stats.get("type").equals("q") && !stats.containsKey("error")) {
      GrammarInducer.ParseStatus status = GrammarInducer.ParseStatus.fromString(stats.get("status").toString());
      int size = (Integer) stats.get("size");
      qEval.add("q.size", size);
      qEval.add("q.isCore", status == GrammarInducer.ParseStatus.Core);
      qEval.add("q.isInduced", status == GrammarInducer.ParseStatus.Induced);
    }

    if (stats.get("type").equals("accept") && !stats.containsKey("error")) {
      GrammarInducer.ParseStatus status = GrammarInducer.ParseStatus.fromString(stats.get("status").toString());
      int size = (Integer) stats.get("size");
      int rank = (Integer) stats.get("rank");
      acceptEval.add("size", size);
      if (rank != -1)
        acceptEval.add("rank", rank);

      acceptEval.add("isCore", status == GrammarInducer.ParseStatus.Core);
      acceptEval.add("isInduced", status == GrammarInducer.ParseStatus.Induced);
    }
  }

  public synchronized static void flush() {
    // TODO Auto-generated method stub
    qEval.logStats("q");
    qEval.putOutput("q");

    acceptEval.logStats("accept");
    acceptEval.putOutput("accept");
  }
}
