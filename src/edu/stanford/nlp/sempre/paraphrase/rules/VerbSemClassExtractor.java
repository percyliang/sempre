package edu.stanford.nlp.sempre.paraphrase.rules;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.sempre.Dataset;
import edu.stanford.nlp.sempre.Example;
import edu.stanford.nlp.sempre.paraphrase.paralex.ParalexQuestionReader;
import edu.stanford.nlp.sempre.paraphrase.rules.VerbSemClassMatcher.VerbSemClassMatch;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import fig.basic.LogInfo;
import fig.basic.MapUtils;

public class VerbSemClassExtractor {

  public static class Options {
    public String verbSemclassFile = "lib/trans/resources/verb-semclass.txt";
  }
  public static Options opts = new Options();

  private Map<String,Counter<String>> verbToObject= new HashMap<String, Counter<String>>();
  private Map<String,Counter<String>> verbToSubject = new HashMap<String, Counter<String>>();
  private ParalexQuestionReader reader;
  private VerbSemClassMatcher matcher = VerbSemClassMatcher.getInstance();
  private Dataset dataset; //webQ dataset

  public VerbSemClassExtractor() throws IOException {
    reader = new ParalexQuestionReader();
    dataset = new Dataset();
    Dataset.opts.trainFrac=1.0;
    Dataset.opts.devFrac=0.0;
    dataset.read();
  }

  public void extract() throws IOException {
    extractParalex();
    extractWebQ();
    log();
  }

  private void extractWebQ() {
    for(Example example: dataset.examples("train")) {
      VerbSemClassMatch match = matcher.match(example.utterance);
      if(match!=null) {
        LogInfo.logs("Sentence: %s, match: %s",example.utterance,match);
        if(match.isSubj)
          addMatch(match,verbToSubject);
        else
          addMatch(match,verbToObject);
      } 
    }
  }

  private void extractParalex() throws IOException {
    String sentence;
    int i = 0;
    while((sentence = reader.next())!=null) {
      VerbSemClassMatch match = matcher.match(sentence);
      if(match!=null) {
        //        LogInfo.logs("Sentence: %s, match: %s",sentence,match);
        if(match.isSubj)
          addMatch(match,verbToSubject);
        else
          addMatch(match,verbToObject);
      }
      if(++i % 100000==0)
        LogInfo.logs("Number of lines: %s",i);
    }
  }

  private void log() {

    for(String verb: verbToObject.keySet()) {
      Counter<String> counter = verbToObject.get(verb);
      List<String> semClasses = Counters.toSortedList(counter, false);
      for(String semClass: semClasses) {
        LogInfo.logs("VerbSemClassExtractor v-->obj\tverb:%s\tsemclass:%s\tcount:%s",verb,semClass,
            counter.getCount(semClass));
      }
    }
    for(String verb: verbToSubject.keySet()) {
      Counter<String> counter = verbToSubject.get(verb);
      List<String> semClasses = Counters.toSortedList(counter, false);
      for(String semClass: semClasses) {
        LogInfo.logs("VerbSemClassExtractor v-->sub\tverb:%s\tsemclass:%s\tcount:%s",verb,semClass,
            counter.getCount(semClass));
      }
    }
  }

  private void addMatch(VerbSemClassMatch match,
      Map<String, Counter<String>> verbCounter) {

    MapUtils.putIfAbsent(verbCounter, match.verb, new ClassicCounter<String>());
    Counter<String> counter = verbCounter.get(match.verb);
    counter.incrementCount(match.semClass);   
  }
  /**
   * Generates a map from words (verbs/semclass) to a set of co-occurring words with counts
   * @param inFile
   * @return
   */
  public static Map<String,Counter<String>> getWordToWordCounterMap() {

    LogInfo.begin_track_printAll("uploading verb to semcalss");
    Map<String,Counter<String>> res = new HashMap<String,Counter<String>>();

    for(String line: IOUtils.readLines(opts.verbSemclassFile)) {

      String[] tokens = line.split("\t");
      String word1 = tokens[1].split(":")[1].split("\\s+")[0]; //get just the first word of the verb without preposition
      String word2 = tokens[2].split(":")[1];
      double count = Double.parseDouble(tokens[3].split(":")[1]);
      if(count>=3.0) {
        MapUtils.putIfAbsent(res, word1, new ClassicCounter<String>());
        res.get(word1).incrementCount(word2, count);
        MapUtils.putIfAbsent(res, word2, new ClassicCounter<String>());
        res.get(word2).incrementCount(word1, count);
      }
    }
    LogInfo.logs("Uploaded counters for %s words", res.size());
    LogInfo.end_track();
    return res;
  }

}
