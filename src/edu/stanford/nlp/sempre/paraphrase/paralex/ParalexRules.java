package edu.stanford.nlp.sempre.paraphrase.paralex;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.sempre.LanguageInfo.LanguageUtils;
import edu.stanford.nlp.sempre.fbalignment.utils.DoubleContainer;
import edu.stanford.nlp.sempre.paraphrase.Context;
import edu.stanford.nlp.sempre.paraphrase.Interval;
import fig.basic.LogInfo;
import fig.basic.MapUtils;
import fig.basic.Option;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParalexRules {

  public static class Options {
    @Option public int verbose = 0;
    @Option public String paralexQuestions;
    @Option public String paralexAlignments;
  }
  public static Options opts = new Options();

  private Map<String,Interval> lemmaToInterval = new HashMap<String, Interval>();
  private Map<Context,Map<Context,DoubleContainer>> rulesMap = new HashMap<Context, Map<Context,DoubleContainer>>();

  public ParalexRules() {
    LogInfo.begin_track_printAll("Building rule map");
    populateLemmaToPosMap();
    extractRules();
    lemmaToInterval.clear(); //to save memory
    if(opts.verbose>3)
      log();
    LogInfo.end_track();
  }

  private void log() {
    for(Context c1: rulesMap.keySet()) {
      for(Context c2: rulesMap.get(c1).keySet()) {
        LogInfo.log("ParalexRules.log:\t"+c1+"\t"+c2+"\t"+rulesMap.get(c1).get(c2).value());
      }
    }
  }

  private void extractRules() {

    int numOfNoNounRules = 0;
    int numOfBadAlignments = 0;
    int numOfUsedParaphrases = 0;
    for(String line: IOUtils.readLines(opts.paralexAlignments)) {
      String[] tokens = line.split("\t");
      if(!lemmaToInterval.containsKey(tokens[0]) || !lemmaToInterval.containsKey(tokens[1])) {
        numOfNoNounRules++;
        continue;
      }
      Interval interval1 = lemmaToInterval.get(tokens[0]);
      Interval interval2 = lemmaToInterval.get(tokens[1]);
      if(nounNounAlignment(tokens[2],interval1,interval2)) {
        List<String> question1 = Arrays.asList(tokens[0].split("\\s+"));
        List<String> question2 = Arrays.asList(tokens[1].split("\\s+"));
        Context context1 = new Context(question1, interval1);
        Context context2 = new Context(question2, interval2);
        if(!context1.equals(context2)) {
          insertRule(context1,context2);
          insertRule(context2,context1);
        }
        numOfUsedParaphrases++;
      }
      else {
        numOfBadAlignments++;
      }
    }
    LogInfo.logs("ParalexRules.extractRules: number of rules without noun: %s, number of bad alignments=%s, number of used paraphrases=%s",numOfNoNounRules,
        numOfBadAlignments,numOfUsedParaphrases);
  }

  private void insertRule(Context context1, Context context2) {
    Map<Context,DoubleContainer> context1Rules = rulesMap.get(context1);
    if(context1Rules==null) {
      context1Rules = new HashMap<Context, DoubleContainer>();
      rulesMap.put(context1, context1Rules);
    }
    DoubleContainer count = context1Rules.get(context2);
    if(count==null) {
      count = new DoubleContainer(0.0);
      context1Rules.put(context2, count);
    }
    count.set(count.value()+1);
  }

  private boolean nounNounAlignment(String alignmentDesc, Interval interval1,
      Interval interval2) {

    String[] alignments = alignmentDesc.split("\\s+");
    for(int i = 0; i < alignments.length; ++i) {
      String[] pair = alignments[i].split("-");
      Integer index1 = Integer.parseInt(pair[0]);
      Integer index2 = Integer.parseInt(pair[1]);
      if((interval1.contains(index1) && !interval2.contains(index2)) || 
          (!interval1.contains(index1) && interval2.contains(index2))) {
        return false;
      }
    }
    return true;
  }

  private void populateLemmaToPosMap() {

    int numOfLinesWithLessThanFourTokens = 0;
    int numOfLinesWithDifferentNumberOfTokens = 0;
    int numOfLinesWithNotOneNoun = 0;
    for(String line: IOUtils.readLines(opts.paralexQuestions)) {
      String[] tokens = line.split("\t");
      if(tokens.length<4) {
        numOfLinesWithLessThanFourTokens++;
        continue;
      }

      String[] posTags = tokens[2].split("\\s+");
      String[] lemmas = tokens[3].split("\\s+");
      if(posTags.length!=lemmas.length) {
        numOfLinesWithDifferentNumberOfTokens++;
        continue;
      }
      Interval interval = getNounInterval(posTags);
      if(interval!=null) {
        if(opts.verbose>=3)
          LogInfo.logs("ParalexRules.populateLemmaToPos: a single noun=%s",line);
        lemmaToInterval.put(tokens[3], interval);
      }
      else {
        if(opts.verbose>=3) 
          LogInfo.logs("ParalexRules.populateLemmaToPos: not a single noun=%s",line);
        numOfLinesWithNotOneNoun++;
      }
    }
    LogInfo.logs("lines with less than four fields: %s, lines with different num of pos and lemmas: %s, lines with not 1 NN: %s, " +
        "lines uploaded=%s",
        numOfLinesWithLessThanFourTokens,
        numOfLinesWithDifferentNumberOfTokens,numOfLinesWithNotOneNoun,lemmaToInterval.size());
  }

  /**
   * get noun interval, if there is more than one noun interval or none - return null
   * @param posTags
   * @return
   */
  private Interval getNounInterval(String[] posTags) {
    int start=-1,end=-1;
    //find first noun 
    for(int i = 0; i < posTags.length; ++i) {
      if(LanguageUtils.isProperNoun(posTags[i])) {
        start=i;
        break;
      }
    }
    //find last noun
    for(int i = 0; i < posTags.length; ++i) {
      if(LanguageUtils.isProperNoun(posTags[posTags.length-1-i])) {
        end=posTags.length-i;
        break;
      }
    }
    //if no nouns return null
    if(end<=start)
      return null;
    //if more than one noun return null
    for(int i = start+1; i < end; ++i) {
      if(!LanguageUtils.sameProperNounClass(posTags[i], posTags[i-1]))
        return null;
    }
    return new Interval(start, end);
  }

  public DoubleContainer match(Context questionContext, Context candidate) {
    if(rulesMap.containsKey(questionContext)){
      return MapUtils.get(rulesMap.get(questionContext),candidate,new DoubleContainer(0.0));
    }
    return new DoubleContainer(0.0);
  }
  
  public static void main(String[] args) {
    opts.paralexQuestions = args[0];
    opts.paralexAlignments = args[1];
    ParalexRules rules = new ParalexRules();
    rules.log();
  }
}
