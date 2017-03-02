package edu.stanford.nlp.sempre.interactive.blocks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;

import com.beust.jcommander.internal.Lists;

import edu.stanford.nlp.sempre.Dataset;
import edu.stanford.nlp.sempre.Example;
import edu.stanford.nlp.sempre.Master;
import edu.stanford.nlp.sempre.NaiveKnowledgeGraph;
import edu.stanford.nlp.sempre.StringValue;
import fig.basic.IOUtils;
import fig.basic.Option;
import fig.basic.Pair;
import fig.exec.Execution;

/**
 * Code to generate html documents from raw data, and sort them.
 *
 * @author Sida Wang
 */
public class Main implements Runnable {
  @Option public boolean log = false;
  @Option public boolean example = true;
  @Option public String path;
  @Option public String opath;
  @Option public int minExamples = 20;
  public final String groupname = "train";
  public void run() {
     try {
      List<String> summaries = new ArrayList<>();
      List<Map<String, String>> summaryMaps = new ArrayList<>();
      
      Files.walk(Paths.get(path)).forEach(filePath -> {
        if (Files.isRegularFile(filePath)) {
          System.out.println(filePath);
          Dataset dataset = new Dataset();
          Dataset.opts.inPaths = 
              new ArrayList<Pair<String, String>>();
          Dataset.opts.inPaths.add(new Pair<>(groupname,filePath.toString()));
          dataset.read();
          
          List<String> lines = new ArrayList<>();
          List<Example> examples = dataset.examples(groupname);
          examples.sort( new Comparator<Example>() {
            @Override
            public int compare(Example lhs, Example rhs) {
                return DateTime.parse(lhs.timeStamp).compareTo(DateTime.parse(rhs.timeStamp));
            }
          });
          String rawid = "";
          lines.add("<table> <tr><td> Time </td><td>Utterence</td> <td>NBPosition</td> <td> start </td> <td> goal </td></tr>");
          int totalNbest = 0;
          String randomexamples = "";
          for (int i=0; i<examples.size(); i++) {
            Example ex = examples.get(i);
            rawid = ex.id.substring("session:".length());
            NaiveKnowledgeGraph graph = (NaiveKnowledgeGraph)ex.context.graph;
            String context = ((StringValue)graph.triples.get(0).e1).value;
            DateTime stamp = DateTime.parse(ex.timeStamp);
            String timestr = stamp.toString("HH:mm:ss");
            totalNbest += ex.NBestInd;
            lines.add(String.format(
                "<tr><td> %s</td> <td>%s (%s)</td> <td>%d</td> <td>%s </td> <td> %s</td></tr>"
                , timestr,  ex.utterance, ex.getTokens(), ex.NBestInd,  context, ex.targetValue));
            if (i==0 || i==examples.size()/2 || i==examples.size()-1)
              randomexamples+=ex.utterance + ", ";
          }
          IOUtils.writeLinesHard(opath+rawid+".html", lines);
          lines.add("</table>");
          
          Map<String,String> summary = dataset.getDatasetSummary();
          summary.put("ID", rawid);
          summary.put("totalNbest", Integer.toString(totalNbest));
          summary.put("gist", randomexamples.length()>50?randomexamples.substring(0,50): randomexamples);
          summaryMaps.add(summary);
        }
      });
   
      summaryMaps.sort( new Comparator<Map<String,String>>() {
        @Override
        public int compare(Map<String,String> lhs, Map<String,String> rhs) {
          if (Integer.parseInt(lhs.get("totalNbest"))==0 && Integer.parseInt(rhs.get("totalNbest"))==0) return 0;
          if (Integer.parseInt(lhs.get("totalNbest"))==0) return 1;
          if (Integer.parseInt(rhs.get("totalNbest"))==0) return -1;
          return Integer.parseInt(lhs.get("totalNbest")) > Integer.parseInt(rhs.get("totalNbest"))? 1:-1;
        }
      });
      
      String[] actualHeaders = new String[]{"numTokenTypes", "numExamples."+groupname, 
          "numTokensPerExample", "totalNbest", "gist"};
      //String[] headers = new String[]{"numtypes", "numEx", "stats", "scrolls", "gist", "log"};
      String firstrow = String.format("<table><tr><td>%s<td>", "ID");
      firstrow += String.format("<td>%s<td>", "Log");
      for (String header : actualHeaders) {
        firstrow += String.format("<td>%s</td>", header);
      }
      firstrow += "</tr>";
      summaries.add(firstrow);
      for (Map<String,String> summary : summaryMaps) {
        if (Integer.parseInt(summary.get("numExamples."+groupname)) < minExamples) continue;
        String summaryrow = String.format("<tr><td><a href=\"%s.html\">%s</a><td>", summary.get("ID"), summary.get("ID"));
        summaryrow += String.format("<td><a href=\"../logs/%s.log\">log</a><td>", summary.get("ID"));
        for (String header : actualHeaders) {
          summaryrow += String.format("<td>%s</td>", summary.get(header));
        }
        summaryrow += "</tr>";
        summaries.add(summaryrow);
      }
      summaries.add("</table>");
      IOUtils.writeLinesHard(opath+"_summary.html", summaries);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  public static void main(String[] args) {
    Execution.run(args, "Main", new Main(), Master.getOptionsParser());
  }
}
