package edu.stanford.nlp.sempre.paraphrase.rules;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

import com.google.common.collect.Lists;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.sempre.Json;
import edu.stanford.nlp.sempre.LanguageInfo;
import edu.stanford.nlp.sempre.paraphrase.ParaphraseDataset;
import edu.stanford.nlp.sempre.paraphrase.ParaphraseExample;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import fig.basic.LogInfo;
import fig.basic.Option;

public class SubstitutionRuleExtractor {
  
  public static class Options {
    @Option public String dataFile;
    @Option public int verbose=1;
    @Option public int maxExamples=Integer.MAX_VALUE;
  }
  public static Options opts = new Options();
  
  private Counter<LemmaPosRule> ruleBase = new ClassicCounter<LemmaPosRule>();
  
  public SubstitutionRuleExtractor() {
    //don't do ner
    LanguageInfo.opts.annotators = Lists.newArrayList("tokenize", "ssplit", "pos", "lemma");
  }
  public void extractRules() throws IOException {
    LogInfo.begin_track_printAll("Extracting substitution rules:");
    int i = 0;   
    BufferedReader reader = IOUtils.getBufferedFileReader(opts.dataFile);
    ParaphraseDataset dataset = Json.readValueHard(reader, ParaphraseDataset.class);
    for(ParaphraseExample ex: dataset.paraphraseExamples("train")) {
      extractRule(ex);
      if(++i % 10000 == 0)
        LogInfo.logs("Number of examples gone through: %s",i);
      if(i>=opts.maxExamples)
        break;
    }
    LogInfo.end_track();
  }

  private void extractRule(ParaphraseExample ex) {

    ParaphraseAlignment alignment = ex.align();
    //first check if we can extract rule
    if(alignment.isLegalSingleGap()) { 
      LemmaPosRule rule = ex.getRule(alignment.getSourceInterval(),alignment.getTargetInterval());
      if(rule.isEmpty() && opts.verbose>=1) {
        LogInfo.logs("SubstitutionRuleExtractor.extractRule: empty rule for source=%s, target=%s",ex.source,ex.target);
      }
      if(opts.verbose>=3)
        LogInfo.logs("SubstitutionRuleExtractor.extract: example source=%s, example target=%s rule=%s",ex.source,ex.target,rule);
      ruleBase.incrementCount(rule);
      ruleBase.incrementCount(rule.reverseRule());
    }
  }
  
  public void log() {
    LogInfo.begin_track("Logging rules");
    List<LemmaPosRule> sorted = Counters.toSortedList(ruleBase, false);
    for(LemmaPosRule rule: sorted) {
      LogInfo.logs("SubstitutionRuleExtractor.log: rule:%s\tcount:%s",rule,ruleBase.getCount(rule));
    }
    LogInfo.end_track();
  }
  
  public static void main(String[] args) throws IOException {
    
    SubstitutionRuleExtractor.opts.dataFile="/Users/jonathanberant/Research/temp/webquestions.train";
    SubstitutionRuleExtractor extractor = new SubstitutionRuleExtractor();
    extractor.extractRules();
    extractor.log();
    
  }
}
