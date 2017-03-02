package edu.stanford.nlp.sempre.interactive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import edu.stanford.nlp.sempre.Json;
import fig.basic.Evaluation;
import fig.basic.IOUtils;
import fig.basic.LogInfo;
import fig.basic.Option;
import fig.exec.Execution;

public class SimulationAnalyzer {
  public static List<String> plotInfo = new ArrayList<>();
  
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

  public synchronized static void addStats(Map<String, Object> query, String jsonResponse) {
    Map<String, Object> stats = getStats(jsonResponse);
    LogInfo.logs("stats: %s query: %s", stats, query);
    
    Map<String, Object> line = new LinkedHashMap<String, Object>(query);
    
    if (stats == null) return;
    
    // make sure no key conflict
    for (Entry<String, Object> entry : stats.entrySet()) {
      line.put("stats."+entry.getKey(), entry.getValue());
    }
    line.put("queryCount", ++queryCount);
    plotInfo.add(Json.writeValueAsStringHard(line));
    
    if (stats.get("type").equals("q")) {
      GrammarInducer.ParseStatus status = GrammarInducer.ParseStatus.fromString(stats.get("status").toString());
      int size = (Integer)stats.get("size");
      qEval.add("size", size);
      qEval.add("isCore", status == GrammarInducer.ParseStatus.Core);
      qEval.add("isInduced", status == GrammarInducer.ParseStatus.Induced);
    }
    
    if (stats.get("type").equals("accept")) {
      GrammarInducer.ParseStatus status = GrammarInducer.ParseStatus.fromString(stats.get("status").toString());
      int size = (Integer)stats.get("size");
      int rank = (Integer)stats.get("rank");
      acceptEval.add("size", size);
      if (rank!=-1)
        acceptEval.add("rank", rank);
      
      acceptEval.add("isCore", status == GrammarInducer.ParseStatus.Core);
      acceptEval.add("isInduced", status == GrammarInducer.ParseStatus.Induced);
    }
    
    if (stats.get("type").equals("def")) {
      int numRules = (Integer)stats.get("numRules");
    }
    
    
  }
  public static void flush() {
    // TODO Auto-generated method stub
    qEval.logStats("q");
    qEval.putOutput("q");
    
    acceptEval.logStats("accept");
    acceptEval.putOutput("accept");
    
    LogInfo.logs("Printing plotInfo to %s", Execution.getFile("plotInfo.json"));
    IOUtils.writeLinesHard(Execution.getFile("plotInfo.json"), plotInfo);
  }

}
