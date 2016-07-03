package edu.stanford.nlp.sempre.interactive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.collect.Sets;

import fig.basic.LispTree;
import fig.basic.LogInfo;

/**
 * Used to store valid prefixes, with some tokenwise categories
 * Supports returning some random number of results
 * @author Sida Wang
 */
public class PrefixTrie {
  // these represent completed utterances at the current trie
  List<String> utterances = new ArrayList<>();
  HashMap<String, PrefixTrie> children = new HashMap<>();
  private static final Random random = new Random(1);

  // yellow -> color, int -> number, etc.
  Function<String, String> tokenMap = new Function<String, String>() {
    public Set<String> colors = Sets.newHashSet("cyan", "red", "brown", "orange", "yellow");
    public Set<String> attrs = Sets.newHashSet("col", "row", "height");
    public Set<String> comps = Sets.newHashSet("=", ">=", "<", ">", "<=");
    public Set<String> rels = Sets.newHashSet("leftof", "rightof", "frontof", "backof");
    @Override
    public String apply(String tok) {
      if (colors.contains(tok)) return "$Color";
      if (attrs.contains(tok)) return "$Number";
      if (comps.contains(tok)) return "$Comp";
      if (rels.contains(tok)) return "$Rel";
      if (tok.matches("^-?\\d+$")) return "$Number";
      return tok;
    }
  };

  PrefixTrie traverse(List<String> items) 
  {
    if (items == null || items.size() == 0) return this;
    String next = tokenMap.apply(items.get(0));
    if (!children.containsKey(next)) return null;
    return next(next).traverse(items.subList(1, items.size())); 
  }

  PrefixTrie next(String item) { return children.get(item); }

  public List<String> getAllMatches() {
    return null;
  }

  public List<String> getRandomMatches(int number) {
    int maxAttempts = 3 * number;
    List<String> matches = new ArrayList<>();
    for (int i = 0; i < maxAttempts; i++) {
      String current = getRandomMatch();
      if (!matches.contains(current))
        matches.add(current);
      if (matches.size() >= number) break;
    }
    return matches;
  }

  // a uniformly random match among current and its children
  public String getRandomMatch() {
    // LogInfo.logs("getRandom %s", this.toLispTree());
    if (children.size() == 0 && utterances.size() == 0)
      return "";
    else if (children.size() == 0)
      return getRandomUtterance();

    // might have no utterance
    int choice = random.nextInt(children.size());
    if (utterances.size() > 0)
      choice = random.nextInt(1 + children.size());

    if (choice == children.size()) // picked uttereance
      return getRandomUtterance();

    Iterator<String> iter = children.keySet().iterator();
    for (int i = 0; i < choice; i++) {
      iter.next();
    }
    String rand = iter.next();
    return children.get(rand).getRandomMatch();
  }

  private String getRandomUtterance() {
    int choice = random.nextInt(utterances.size());
    return utterances.get(choice);
  }

  public void add(List<String> tokens) { add(tokens, 0); }

  private void add(List<String> tokens, int i) {
    if (i == tokens.size()) {
      String utt = String.join(" ", tokens);
      if (!utterances.contains(utt)) // filter exact match
        utterances.add(utt);
      return;
    }

    String item = tokenMap.apply(tokens.get(i));
    PrefixTrie child = children.get(item);
    if (child == null)
      children.put(item, child = new PrefixTrie());
    child.add(tokens, i + 1);
  }

  public LispTree toLispTree() {
    return toLispTree(-1);
  }
  public LispTree toLispTree(int maxlevel) {
    LispTree trietree = LispTree.proto.newList();
    trietree.addChild("trie");
    trietree.addChild(LispTree.proto.newList(utterances));
    
    if (maxlevel != 0 && children.size() > 0) {
      Iterator<String> keys = children.keySet().iterator();
      while (keys.hasNext()) {
        String key = keys.next();
        LispTree child = children.get(key).toLispTree(maxlevel - 1);
        trietree.addChild(LispTree.proto.newList(key, child));
      }
    }
    return trietree;
  }
}
