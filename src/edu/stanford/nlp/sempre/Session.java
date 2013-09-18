package edu.stanford.nlp.sempre;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import fig.basic.LispTree;

/** A Session contains the information specific to an individual. */
public class Session {
  String id;  // Session id
  String lastQuery;  // Last thing query (non-command) typed by the user.
  String lastRemoteAddr;

  Map<String, LispTree> macros = new HashMap<String, LispTree>();  // Map from macro name to its replacement value

  List<Example> examples = new ArrayList<Example>(); // List of recent examples.

  public Session(String id) {
    this.id = id;
  }

  public void setLastRemoteAddr(String lastRemoteAddr) {
    this.lastRemoteAddr = lastRemoteAddr;
  }

  public String getLastQuery() { return lastQuery; }

  @Override
  public String toString() {
    return String.format("%s [%d examples, %d macros]; last: %s", id, examples.size(), macros.size(), lastQuery);
  }
}
