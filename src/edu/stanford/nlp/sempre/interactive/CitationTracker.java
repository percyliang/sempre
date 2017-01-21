package edu.stanford.nlp.sempre.interactive;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import edu.stanford.nlp.sempre.*;
import fig.basic.IOUtils;
import fig.basic.LispTree;
import fig.basic.LogInfo;
import fig.basic.Pair;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Base64;

/**
 * A rule specifies how to take a right hand of terminals and non-terminals.
 *
 * @author Percy Liang
 */
public class CitationTracker {
  public static final String IDPrefix = "id:";
  public static final String HeadPrefix = "head:";
  public static final String BodyPrefix = "body:";
  String uid = "undefined";
  Example ex;
  public CitationTracker(String uid, Example ex) {
    this.uid = uid;
    this.ex = ex;
  }

  public void citeRule(Rule rule) {
    writeLog(rule);
    writeSummary(rule);
  }
  
  // The summary is ONE SINGLE line of json, has cite, self, and head
  private synchronized void writeSummary(Rule rule) {
    String headCode = getHead(rule);
    String authorCode = getAuthor(rule);
    
    
    String summaryPath = Paths.get(ILUtils.opts.citationPath, authorCode, headCode + ".json").toString();
    File file = new File(summaryPath);
    file.getParentFile().mkdirs();
    
    Map<String, Object> summary;
    try {
      summary = Json.readMapHard(IOUtils.readLine(summaryPath));
      boolean selfcite = authorCode.equals(uid);
      if (!selfcite) {
        summary.put("cite", (Integer)summary.get("cite") + 1);
      } else {
        summary.put("self", (Integer)summary.get("self") + 1);
      }
    } catch (IOException e) {
      // e.printStackTrace();
      summary = defaultMap(rule);
    } 
    String jsonStr = Json.writeValueAsStringHard(summary);
    PrintWriter out = IOUtils.openOutHard(file);
    out.println(jsonStr);
    out.close();
  }
  
  private Map<String,Object> defaultMap(Rule rule) {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("cite", 0);
    summary.put("self", 0);
    summary.put("head", decode(getHead(rule)));
    summary.put("body", decode(getBody(rule)));
    return summary;
  }

  private synchronized void writeLog(Rule rule) {
    String headCode = getHead(rule);
    String authorCode = getAuthor(rule);
    String logPath = Paths.get(ILUtils.opts.citationPath, authorCode, headCode + ".json.log").toString();
    File file = new File(logPath);
    file.getParentFile().mkdirs();
    
    Map<String, Object> jsonMap = new LinkedHashMap<>();
    jsonMap.put("user", this.uid);
    // jsonMap.put("body", decode(getBody(rule)));
    jsonMap.put("time", LocalDateTime.now().toString());
    jsonMap.put("tokens", ex.getTokens());
    //jsonMap.put("head", decode(headCode));
    jsonMap.put("author", decode(authorCode));

    String jsonStr = Json.writeValueAsStringHard(jsonMap);
    PrintWriter out = IOUtils.openOutAppendHard(file);
    out.println(jsonStr);
    out.close();
  }

  public void citeAll(Derivation deriv) {
    if (deriv.rule!=null && deriv.rule.isInduced()) {
      LogInfo.logs("user %s is citing rule: %s", this.uid, deriv.rule.toString());
      citeRule(deriv.rule);
    }
    
    if (deriv.children == null) return;
    for (Derivation d : deriv.children) {
      citeAll(d);
    }
  }

  static String getAuthor(Rule rule) { return (getPrefix(rule, IDPrefix)); }
  static String getHead(Rule rule) { return (getPrefix(rule, HeadPrefix)); }
  static String getBody(Rule rule) { return (getPrefix(rule, BodyPrefix)); }
  
  private static String getPrefix(Rule rule, String prefix) {
    if (rule.info != null) {
      for (Pair<String, Double> p : rule.info) {
        if (p.getFirst().startsWith(prefix))
          return p.getFirst().substring(prefix.length());
      }
    }
    throw new RuntimeException(String.format("Prefix %s not found in rule %s", prefix, rule.toLispTree()));
  }
  
  public static String encode(String utt) {
    try {
      return URLEncoder.encode(utt, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    return Base64.getUrlEncoder().encodeToString(utt.getBytes());
    //return Base64.getUrlEncoder().encodeToString(utt.getBytes());
  }
  
  public static String decode(String code) {
    try {
      return URLDecoder.decode(code, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    return Base64.getUrlDecoder().decode(code).toString();
  }

}
