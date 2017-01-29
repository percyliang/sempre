package edu.stanford.nlp.sempre.interactive;
import java.util.Map;
import edu.stanford.nlp.sempre.Json;
import fig.basic.Evaluation;
import fig.basic.LogInfo;

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

  public synchronized static void add(Object sessionId, String query, String jsonResponse) {
    Map<String, Object> stats = getStats(jsonResponse);
    LogInfo.log(stats);
    if (stats == null) return;
    
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
    
    acceptEval.logStats("a");
    acceptEval.putOutput("a");
  }

}
