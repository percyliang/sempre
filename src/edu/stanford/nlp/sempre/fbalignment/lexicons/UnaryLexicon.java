package edu.stanford.nlp.sempre.fbalignment.lexicons;

import com.google.common.base.Strings;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.sempre.FbFormulasInfo;
import edu.stanford.nlp.sempre.FbFormulasInfo.UnaryFormulaInfo;
import edu.stanford.nlp.sempre.Formula;
import edu.stanford.nlp.sempre.Json;
import edu.stanford.nlp.sempre.fbalignment.lexicons.LexicalEntry.LexiconValue;
import edu.stanford.nlp.sempre.fbalignment.lexicons.LexicalEntry.UnaryLexicalEntry;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Pair.BySecondReversePairComparator;
import fig.basic.LogInfo;
import fig.basic.MapUtils;
import fig.basic.Option;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class UnaryLexicon {

  public static class Options {
    @Option(gloss = "Number of results return by the lexicon")
    public int maxEntries = 1000;
    @Option(gloss = "Path to unary lexicon file")
    public String unaryLexiconFilePath = "lib/fb_data/6/unaryInfoStringAndAlignment.txt";
    @Option(gloss = "Whether to prune the lexicon") public boolean pruneLexicon = false;
    @Option(gloss = "Number of entries to leave after pruning")
    public int pruneBeamSize = 5;
    @Option(gloss = "Threshold for filtering unaries")
    public int unaryFilterThreshold = 5;
    @Option(gloss = "Verbosity")
    public int verbose = 0;

  }

  public static Options opts = new Options();

  private Map<String, Map<Pair<Formula, String>, UnaryLexicalEntry>> lexemeToEntryMap;
  private static FbFormulasInfo fbFormulasInfo;

  public final static String INTERSECTION = "intersection";
  public final static String NL_SIZE = "nl_size";
  public final static String FB_SIZE = "fb_size";

  public UnaryLexicon() {

    fbFormulasInfo = FbFormulasInfo.getSingleton();
    lexemeToEntryMap = new HashMap<String, Map<Pair<Formula,String>, UnaryLexicalEntry>>();

    //get lexicon files names
    List<String> unaryLexiconFileNames = new LinkedList<String>();
    if (!Strings.isNullOrEmpty(opts.unaryLexiconFilePath))
      unaryLexiconFileNames.add(opts.unaryLexiconFilePath);

    for (String lexiconFileName : unaryLexiconFileNames) {
      loadLexiconFileIntoMap(lexiconFileName, lexemeToEntryMap);
    }

    if (opts.pruneLexicon) {
      LogInfo.begin_track("Pruning lexicon");
      System.out.println("Pruning with beam size: " + opts.pruneBeamSize);
      pruneLexicon(opts.pruneBeamSize);
      LogInfo.end_track("Pruning lexicon");
    }
  }

  public void pruneLexicon(int topK) {

    for (String normLexeme : lexemeToEntryMap.keySet()) {
      pruneLexicon(normLexeme, topK);
    }
  }
  /** For every NL phrase choose the top-k matching ones */
  private void pruneLexicon(String normLexeme, int topK) {

    Map<Pair<Formula, String>, UnaryLexicalEntry> entries = lexemeToEntryMap.get(normLexeme);
    if (entries.size() <= topK)
      return; // no need to prune
    //sort by jaccard
    List<Pair<Pair<Formula, String>, Double>> formulaToScoreList = new LinkedList<Pair<Pair<Formula, String>, Double>>();
    for (Pair<Formula, String> formulaAndSource : entries.keySet()) {
      UnaryLexicalEntry uEntry = entries.get(formulaAndSource);
      double jaccard = computeJaccard(uEntry.alignmentScores);
      formulaToScoreList.add(new Pair<Pair<Formula, String>, Double>(formulaAndSource, jaccard));
    }
    Collections.sort(formulaToScoreList, new BySecondReversePairComparator<Pair<Formula, String>, Double>());

    for (int i = topK; i < formulaToScoreList.size(); ++i) {
      entries.remove(formulaToScoreList.get(i).first());
    }
  }

  private double computeJaccard(Map<String,Double> alignmentScores) {
    double intersection = MapUtils.getDouble(alignmentScores, INTERSECTION, 0.0);
    double nlSize = MapUtils.getDouble(alignmentScores, NL_SIZE, 0.0);
    double fbSize = MapUtils.getDouble(alignmentScores, FB_SIZE, 0.0);

    double smoothedJaccard = intersection / (nlSize + fbSize - intersection + 5);
    return smoothedJaccard;
  }

  private UnaryLexicon(
      Map<String, Map<Pair<Formula, String>, UnaryLexicalEntry>> nlToFormulaAndAlignmentMap) {
    this.lexemeToEntryMap = nlToFormulaAndAlignmentMap;
  }

  public static UnaryLexicon fromUnaryLexiconFiles(List<String> lexiconFileNames, int numOfResults) {

    Map<String, Map<Pair<Formula, String>, UnaryLexicalEntry>> nlToFormulaAndAlignmentMap =
        new HashMap<String, Map<Pair<Formula, String>, UnaryLexicalEntry>>();

    for (String lexiconFileName : lexiconFileNames) {
      loadLexiconFileIntoMap(lexiconFileName, nlToFormulaAndAlignmentMap);
    }
    return new UnaryLexicon(nlToFormulaAndAlignmentMap);
  }

  private static void loadLexiconFileIntoMap(String lexiconFileName,
      Map<String, Map<Pair<Formula, String>, UnaryLexicalEntry>> nlToFormulaAndAlignmentMap) {
    LogInfo.begin_track("Loading lexicon file " + lexiconFileName);

    for (String line : IOUtils.readLines(lexiconFileName)) {
      LexiconValue lv = Json.readValueHard(line, LexiconValue.class);
      addEntry(lv.lexeme, lv.source, lv.formula, lv.features, nlToFormulaAndAlignmentMap);
    }
    LogInfo.log("Number of entries: " + nlToFormulaAndAlignmentMap.size());
    LogInfo.end_track();
  }

  private static void addEntry(String nl, String source, Formula formula, Map<String,Double> featureMap,
      Map<String, Map<Pair<Formula, String>, UnaryLexicalEntry>> nlToFormulaAndAlignmentMap) {

    if(fbFormulasInfo.getUnaryInfo(formula)==null) {
      if(opts.verbose>=3)
        LogInfo.log("Missing info for unary: " + formula);
    }
    else {
      UnaryFormulaInfo uInfo = fbFormulasInfo.getUnaryInfo(formula);
      UnaryLexicalEntry uEntry = new UnaryLexicalEntry(nl, nl,  new TreeSet<String>(uInfo.descriptions), formula, EntrySource.parseSourceDesc(source),
          uInfo.popularity, new TreeMap<String,Double>(featureMap), uInfo.types);
      
      Map<Pair<Formula, String>, UnaryLexicalEntry> formulaToAlignmentInfoMap = nlToFormulaAndAlignmentMap.get(nl);
      if (formulaToAlignmentInfoMap == null) {
        formulaToAlignmentInfoMap = new HashMap<Pair<Formula,String>, UnaryLexicalEntry>();
        formulaToAlignmentInfoMap.put(new Pair<Formula, String>(formula, source.toString()), uEntry);
        nlToFormulaAndAlignmentMap.put(nl, formulaToAlignmentInfoMap);
      } else {
        //assumes same name and formula appear only once in the file 
        formulaToAlignmentInfoMap.put(new Pair<Formula, String>(formula, source.toString()), uEntry);
      }
    }
  }

  /**
   * @param alignmentFile - file containing alignment from stemmed NL to
   *                      Freebase unaries
   * @param stringFile    - file containing string info and popularity about all
   *                      Freebase unaries
   */
  public static UnaryLexicon fromStringFileAndAlignmentFile(String alignmentFile, String stringFile) {

    LogInfo.begin_track("Generating nl to info from string match file");
    Map<String, Map<Pair<Formula, String>, UnaryLexicalEntry>> nlToFormulaAndAlignmentMap =
        generateFromStringFileNlToFormulaMap(stringFile);
    LogInfo.end_track();
    LogInfo.begin_track("Adding alignment info");
    addAlignmentInfo(alignmentFile, nlToFormulaAndAlignmentMap);
    LogInfo.end_track();
    return new UnaryLexicon(nlToFormulaAndAlignmentMap);
  }

  public static void saveUnaryLexiconFromStringFileAndAlignmentFile(String alignmentFile, String stringFile, String lexiconFile) throws IOException {
    UnaryLexicon lexicon = fromStringFileAndAlignmentFile(alignmentFile, stringFile);
    lexicon.saveUnaryLexiconFile(lexiconFile);
  }

  private static void addAlignmentInfo(String alignmentFile, 
      Map<String, Map<Pair<Formula, String>, UnaryLexicalEntry>> nlToFormulaAndAlignmentMap) {

    int i = 0;
    for (String line : IOUtils.readLines(alignmentFile)) {
      i++;
      if (i == 1)
        continue;
      String[] tokens = line.split("\t");
      String nl = tokens[0]; //it is already stemmed and normalized
      Formula formula = Formula.fromString(tokens[1]);
      Map<String,Double> features = new TreeMap<String, Double>();
      features.put(INTERSECTION, Double.parseDouble(tokens[2]));
      features.put(NL_SIZE, Double.parseDouble(tokens[3]));
      features.put(FB_SIZE, Double.parseDouble(tokens[4]));

      addEntry(nl, EntrySource.ALIGNMENT.toString(), formula, features, nlToFormulaAndAlignmentMap);
      if (i % 10000 == 0)
        LogInfo.log("Number of lines: " + i);
    }
  }

  private static Map<String, Map<Pair<Formula, String>, UnaryLexicalEntry>> generateFromStringFileNlToFormulaMap(String stringFile) {

    Map<String, Map<Pair<Formula, String>, UnaryLexicalEntry>> res = 
        new HashMap<String, Map<Pair<Formula, String>, UnaryLexicalEntry>>();

    int i = 0;
    for (String line : IOUtils.readLines(stringFile)) {
      
      String[] tokens = line.split("\t");
      String nl = tokens[3].toLowerCase();
      Formula formula = Formula.fromString(tokens[1]);
      if(fbFormulasInfo.getUnaryInfo(formula)==null) {
          LogInfo.log("Skipping on formula since info is missing from schema: " + formula);
      }
      else {
        UnaryFormulaInfo uInfo = fbFormulasInfo.getUnaryInfo(formula);

        Map<Pair<Formula, String>, UnaryLexicalEntry> formulaToAlignmentInfoMap = res.get(nl);
        if (formulaToAlignmentInfoMap == null) {
          formulaToAlignmentInfoMap = new HashMap<Pair<Formula,String>, UnaryLexicalEntry>();
          res.put(nl, formulaToAlignmentInfoMap);
        }
        UnaryLexicalEntry uEntry = 
            formulaToAlignmentInfoMap.get(new Pair<Formula,String>(formula,EntrySource.STRING_MATCH.toString()));
        if (uEntry == null) {
          
          uEntry = new UnaryLexicalEntry(nl, nl, new TreeSet<String>(uInfo.descriptions), formula, EntrySource.STRING_MATCH, uInfo.popularity,
              new TreeMap<String,Double>(), uInfo.types); 
          formulaToAlignmentInfoMap.put(new Pair<Formula, String>(formula, EntrySource.STRING_MATCH.toString()), uEntry);
        }
        i++;
        if (i % 1000 == 0)
          LogInfo.log("Adding mapping from nl: " + nl + " to formula " + formula.toString());  
      }
    }
    return res;
  }

  public void saveUnaryLexiconFile(String outFile) throws IOException {

    PrintWriter writer = IOUtils.getPrintWriter(outFile);
    for (String nl : lexemeToEntryMap.keySet()) {
      for (Pair<Formula, String> formulaAndSource : lexemeToEntryMap.get(nl).keySet()) {
        UnaryLexicalEntry uEntry = lexemeToEntryMap.get(nl).get(formulaAndSource);
        LexiconValue lv = new LexiconValue(nl, formulaAndSource.first(), formulaAndSource.second(), uEntry.alignmentScores);
        writer.println(Json.writeValueAsStringHard(lv));
      }
    }
    writer.close();
  }

  public List<UnaryLexicalEntry> lookupEntries(String textDesc) throws IOException {

    List<UnaryLexicalEntry> res = new LinkedList<UnaryLexicalEntry>();

    String normalizedTextDesc = textDesc.toLowerCase();
    Map<Pair<Formula, String>, UnaryLexicalEntry> entries = lexemeToEntryMap.get(normalizedTextDesc);

    if (entries != null) {
      for (Pair<Formula, String> formulaAndSource : entries.keySet()) {        
        UnaryLexicalEntry uEntry = entries.get(formulaAndSource);
        if (valid(uEntry))
          res.add(uEntry);
      }
      Collections.sort(res, new UnaryLexicalEntryComparator());
    }
    return res.subList(0, Math.min(res.size(), opts.maxEntries));
  }

  /** Checks if an entry is valid (e.g. we filter if intersection is too small) */
  private boolean valid(UnaryLexicalEntry lexicalEntry) {
    return (lexicalEntry.source != EntrySource.ALIGNMENT || 
        MapUtils.getDouble(lexicalEntry.alignmentScores, INTERSECTION, 0.0) >= opts.unaryFilterThreshold);
  }

  public void printStats() {

    Set<Formula> formulaSet = new HashSet<Formula>();
    for (String nl : lexemeToEntryMap.keySet()) {
      for (Pair<Formula, String> formulaAndSource : lexemeToEntryMap.get(nl).keySet()) {
        formulaSet.add(formulaAndSource.first());
      }
    }
    LogInfo.log("number of phrases: " + lexemeToEntryMap.size());
    LogInfo.log("Number fo formulas: " + formulaSet.size());
  }

  public static class UnaryLexicalEntryComparator implements Comparator<UnaryLexicalEntry> {

    public static final String INTERSECTION = "intersection";

    @Override
    public int compare(UnaryLexicalEntry arg0, UnaryLexicalEntry arg1) {

      double arg0Intersection = MapUtils.getDouble(arg0.alignmentScores, INTERSECTION,0.0);
      double arg1Intersection = MapUtils.getDouble(arg1.alignmentScores, INTERSECTION,0.0);
      if (arg0Intersection > arg1Intersection)
        return -1;
      if (arg0Intersection < arg1Intersection)
        return 1;
      if (arg0.popularity > arg1.popularity)
        return -1;
      if (arg0.popularity < arg1.popularity)
        return 1;
      return 0;
    }
  }
}
