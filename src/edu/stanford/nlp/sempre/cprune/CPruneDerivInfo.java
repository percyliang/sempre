package edu.stanford.nlp.sempre.cprune;

import java.util.Map;
import java.util.List;

public class CPruneDerivInfo {

  public Map<String, Symbol> treeSymbols;
  public Map<String, Symbol> ruleSymbols;
  public List<String> customRuleStrings;
  public boolean containsCrossReference;

}
