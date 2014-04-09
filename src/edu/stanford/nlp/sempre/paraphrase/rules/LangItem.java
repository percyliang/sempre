package edu.stanford.nlp.sempre.paraphrase.rules;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.sempre.LanguageInfo;
import edu.stanford.nlp.sempre.paraphrase.Interval;
import edu.stanford.nlp.sempre.paraphrase.rules.LanguageExpToken.RepeatType;
import edu.stanford.nlp.sempre.paraphrase.rules.LanguageExpToken.TokenType;
import fig.basic.LispTree;
import fig.basic.MapUtils;

/**
 * A regexp that matches to a LanguageInfo
 * The format of an item is (item (pos [NN]+) (ner [DATE]*))
 * @author jonathanberant
 *
 */
public class LangItem {

  public final LispTree expressionTree;
  private NFA nfa;
  
  public LangItem(LispTree tree) {
    expressionTree = tree;
    nfa = new NFA(expressionTree);
  }

  public LispTree toLispTree() {
    return expressionTree;
  }

  public String toString() {
    return expressionTree.toString();
  }

  public boolean match(LanguageInfo utterance, int start, int end) {
    return match(utterance, start).contains(new Interval(start, end));
  }

  public List<Interval> match(LanguageInfo utterance, int start) {

    List<Interval> res = new ArrayList<Interval>();
    res.addAll(nfa.match(utterance, start));
    return res;
  }
  
  /**
   * Language item is defined to contain just a single language expression token
   * @return
   */
  public LanguageExpToken getLangExpToken() {
    return new LanguageExpToken(expressionTree.child(1).child(0).value,
        expressionTree.child(1).child(1).value);
  }

  public static class NFA {

    private int numOfStates;
    private Map<LanguageExpToken,Set<Integer>>[] outgoingEdges;
    private Map<LanguageExpToken,Set<Integer>>[] incomingEdges;
    private Set<Integer> startStates = new LinkedHashSet<Integer>();

    @SuppressWarnings("unchecked")
    public NFA(LispTree expressionTree) {
      numOfStates=1;
      startStates.add(0);
      outgoingEdges = (Map<LanguageExpToken, Set<Integer>>[]) 
          Array.newInstance(HashMap.class,expressionTree.children.size());
      incomingEdges = (Map<LanguageExpToken, Set<Integer>>[]) 
          Array.newInstance(HashMap.class,expressionTree.children.size());
      compileExpression(expressionTree);
    }

    private void compileExpression(LispTree exp) {

      for(int i = 1; i < exp.children.size();++i) {

        LispTree child = exp.child(i);
        int currState = numOfStates-1;
        LanguageExpToken langExpToken = new LanguageExpToken(child.child(0).value, child.child(1).value);

        if(langExpToken.repeat==RepeatType.PLUS) {
          addEdge(currState, currState+1,langExpToken);
          addEdge(currState+1, currState+1,langExpToken);
        }
        else if(langExpToken.repeat==RepeatType.STAR) {
          addEdge(currState, currState+1,langExpToken);
          addEdge(currState+1, currState+1,langExpToken);
          //deal with previous edges
          Map<LanguageExpToken,Set<Integer>> prevEdges = incomingEdges[currState]; //could be NULL - for efficiency I allow this
          if(prevEdges!=null) {
            for(LanguageExpToken prevToken: prevEdges.keySet()) {
              for(Integer prevState: prevEdges.get(prevToken)) {
                addEdge(prevState, currState+1, prevToken);
              }
            }
          }
          //deal with start state
          if(startStates.contains(currState))
            startStates.add(currState+1);
        }
        else if(langExpToken.repeat==RepeatType.Q_MARK) {
          addEdge(currState, currState+1,langExpToken);
          //deal with previous edges
          Map<LanguageExpToken,Set<Integer>> prevEdges = incomingEdges[currState]; //could be NULL - for efficiency I allow this
          if(prevEdges!=null) {
            for(LanguageExpToken prevToken: prevEdges.keySet()) {
              for(Integer prevState: prevEdges.get(prevToken)) {
                addEdge(prevState, currState+1, prevToken);
              }
            }
          }
          //deal with start state
          if(startStates.contains(currState))
            startStates.add(currState+1);
        }
        else 
          addEdge(currState, currState+1,langExpToken);
        numOfStates++;
      }
    }

    private void addEdge(int source, int destination, LanguageExpToken label){
      Map<LanguageExpToken,Set<Integer>> out = outgoingEdges[source];
      if(out==null) {
        out = new HashMap<LanguageExpToken, Set<Integer>>();
        outgoingEdges[source]=out;
      }
      Map<LanguageExpToken,Set<Integer>> in = incomingEdges[destination];
      if(in==null) {
        in = new HashMap<LanguageExpToken, Set<Integer>>();
        incomingEdges[destination]=in;
      }

      MapUtils.addToSet(out, label, destination);
      MapUtils.addToSet(in, label, source);
    }

    private List<Interval> match(LanguageInfo utterance, int startIndex) {

      List<Interval> res = new ArrayList<Interval>();

      Set<Integer> currStates = new HashSet<Integer>();
      currStates.addAll(startStates);
      if(currStates.contains(numOfStates-1))
        res.add(new Interval(startIndex, startIndex));

      for(int i = startIndex; i < utterance.numTokens(); ++i) {
        Set<Integer> newStates = new HashSet<Integer>();
        for(Integer currState: currStates) {
          Map<LanguageExpToken,Set<Integer>> out = outgoingEdges[currState]; //could be NULL - allowed for efficiency
          if(out!=null) {
            for(LanguageExpToken langExpToken: out.keySet()) {
              if(matchLangExpTokenToLangInfo(langExpToken, utterance, i)) {
                newStates.addAll(out.get(langExpToken));
              }
            }
          }
        }
        currStates = newStates;
        if(currStates.contains(numOfStates-1)) //match
          res.add(new Interval(startIndex, i+1));
      }
      return res;
    }

    private boolean matchLangExpTokenToLangInfo(LanguageExpToken langExpToken,
        LanguageInfo info, int infoIndex) {
      if(langExpToken.type==TokenType.pos) {
        return langExpToken.valuePattern.matcher(info.posTags.get(infoIndex)).matches();
      }
      if(langExpToken.type==TokenType.ner) {
        return langExpToken.valuePattern.matcher(info.nerTags.get(infoIndex)).matches();
      }
      if(langExpToken.type==TokenType.lemma) {
        return langExpToken.valuePattern.matcher(info.lemmaTokens.get(infoIndex)).matches();
      }
      if(langExpToken.type==TokenType.token) {
        return langExpToken.valuePattern.matcher(info.tokens.get(infoIndex)).matches();
      }
      throw new RuntimeException("illegal language expression token: " + langExpToken.toLispTree()); 
    }
  }
}
