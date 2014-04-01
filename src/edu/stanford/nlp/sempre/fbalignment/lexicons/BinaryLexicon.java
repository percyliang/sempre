package edu.stanford.nlp.sempre.fbalignment.lexicons;

import com.google.common.collect.Lists;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.sempre.FbFormulasInfo;
import edu.stanford.nlp.sempre.FbFormulasInfo.BinaryFormulaInfo;
import edu.stanford.nlp.sempre.Formula;
import edu.stanford.nlp.sempre.Json;
import edu.stanford.nlp.sempre.fbalignment.lexicons.LexicalEntry.BinaryLexicalEntry;
import edu.stanford.nlp.sempre.fbalignment.lexicons.LexicalEntry.LexiconValue;
import edu.stanford.nlp.sempre.fbalignment.lexicons.normalizers.EntryNormalizer;
import edu.stanford.nlp.sempre.fbalignment.lexicons.normalizers.IdentityNormalizer;
import edu.stanford.nlp.sempre.fbalignment.lexicons.normalizers.PrepDropNormalizer;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Pair.BySecondReversePairComparator;
import edu.stanford.nlp.util.Triple;
import fig.basic.LogInfo;
import fig.basic.MapUtils;
import fig.basic.Option;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Lexicon for binary predicates
 * @author jonathanberant
 */
public class BinaryLexicon {

  public static class Options {
    @Option(gloss = "Number of results return by the lexicon")
    public int maxEntries = 1000;
    @Option(gloss = "Whether to drop the preposition when querying the lexicon")
    public boolean prepDropNormalization = true;
    @Option(gloss = "Path to binary lexicon files")
    public List<String> binaryLexiconFilesPath = Lists.newArrayList("lib/fb_data/6/binaryInfoStringAndAlignment.txt");
    @Option(gloss = "Whether to prune the lexicon") public boolean pruneLexicon = false;
    @Option(gloss = "Number of entries to leave after pruning")
    public int pruneBeamSize = 5;

    @Option(gloss = "Verbosity") public int verbose = 0;
    @Option(gloss = "Whether to use Jaccard as the only alignment statistic")
    public boolean useOnlyJaccard = false;
    @Option(gloss = "Alignment score to sort by")
    public String keyToSortBy = INTERSECTION;
  }

  public static Options opts = new Options();

  private EntryNormalizer lexiconLoadingNormalizer;
  private FbFormulasInfo fbFormulasInfo;
  
  public static final String INTERSECTION = "Intersection_size_typed";
  public static final String NL_TYPED = "NL_typed_size";
  public static final String FB_TYPED = "FB_typed_size";
  

  Map<String, Map<Pair<Formula, String>, BinaryLexicalEntry>> lexemeToEntryMap = new HashMap<String, Map<Pair<Formula, String>, BinaryLexicalEntry>>();

  public BinaryLexicon() throws IOException {

    fbFormulasInfo = FbFormulasInfo.getSingleton();

    //if we omit prepositions then the lexicon normalizer does that, otherwise, it is a normalizer that does nothing
    if (opts.prepDropNormalization) {
      lexiconLoadingNormalizer = new PrepDropNormalizer(); // the alignment lexicon already contains stemmed stuff so just need to drop prepositions
    } else {
      lexiconLoadingNormalizer = new IdentityNormalizer();
    }

    for (String lexiconFile : opts.binaryLexiconFilesPath) {
      uploadFile(lexiconFile);
    }

    if (opts.pruneLexicon) {
      LogInfo.begin_track("Pruning lexicon");
      LogInfo.log("Pruning with beam size: " + opts.pruneBeamSize);
      pruneLexicon(opts.pruneBeamSize);
      LogInfo.end_track("Pruning lexicon");
    }
  }

  private void uploadFile(String lexiconFile) throws IOException {

    LogInfo.begin_track_printAll("Loading lexicon file " + lexiconFile);
    for (String line : IOUtils.readLines(lexiconFile)) {
     
      LexiconValue lv = Json.readValueHard(line, LexiconValue.class);
      String lexemeKey = lv.lexeme;
      String normalizedLexemeKey = lexiconLoadingNormalizer.normalize(lexemeKey);
      addEntryToMap(lexemeToEntryMap, lexemeKey, lv);
      if (!lexemeKey.equals(normalizedLexemeKey)) {
        addEntryToMap(lexemeToEntryMap, normalizedLexemeKey, lv);
      }
    }
    LogInfo.log("Number of entries: " + lexemeToEntryMap.size());
    LogInfo.end_track();
  }
  
  private void addEntryToMap(Map<String, Map<Pair<Formula, String>, BinaryLexicalEntry>> lexemeToEntryMap, String mapKey, LexiconValue lv) {
    Map<Pair<Formula, String>, BinaryLexicalEntry> entries = lexemeToEntryMap.get(mapKey);
    if (entries == null) {
      lexemeToEntryMap.put(mapKey, entries = new HashMap<Pair<Formula, String>, LexicalEntry.BinaryLexicalEntry>());
    }
    addEntry(entries, lv, mapKey);
  }

  public void addEntry(Map<Pair<Formula, String>, BinaryLexicalEntry> entries, LexiconValue lexValue, String mapKey) {

    List<BinaryLexicalEntry> binaryEntries = buildEntry(lexValue, mapKey);
    for (BinaryLexicalEntry binaryEntry : binaryEntries) {
      Pair<Formula, String> formulaLexemePair = new Pair<Formula, String>(binaryEntry.formula, binaryEntry.fullLexeme);
      if (entries.containsKey(formulaLexemePair)) {
        BinaryLexicalEntry otherEntry = entries.get(formulaLexemePair);
        if (!binaryEntry.identicalFormulaInfo(otherEntry)) {
          throw new RuntimeException("Different entries for same formula, existing entry: " + otherEntry + ", new entry: " + binaryEntry);
        }
        //hack
        if (binaryEntry.source == EntrySource.STRING_MATCH && otherEntry.source == EntrySource.STRING_MATCH)
          continue;
        if (binaryEntry.source == EntrySource.ALIGNMENT && otherEntry.source == EntrySource.STRING_MATCH) {
          otherEntry.alignmentScores = binaryEntry.alignmentScores;
          otherEntry.source = binaryEntry.source;
          continue;
        }
        if (binaryEntry.source == EntrySource.STRING_MATCH && otherEntry.source == EntrySource.ALIGNMENT)
          continue;
        for (String scoreDesc : binaryEntry.alignmentScores.keySet()) {
          if (otherEntry.alignmentScores.containsKey(scoreDesc))
            throw new RuntimeException("While trying to merge entries found that both entries have the score: " + scoreDesc);
          otherEntry.alignmentScores.put(scoreDesc, binaryEntry.alignmentScores.get(scoreDesc));
        }
      } else {
        entries.put(new Pair<Formula, String>(binaryEntry.formula, binaryEntry.fullLexeme), binaryEntry);
      }
    }
  }

  public List<BinaryLexicalEntry> buildEntry(LexiconValue lexValue, String mapKey) {

    EntrySource source = EntrySource.parseSourceDesc(lexValue.source);
    BinaryFormulaInfo info = fbFormulasInfo.getBinaryInfo(lexValue.formula);
    
    if(!validBinaryFormula(lexValue.formula))
      return Collections.emptyList();
    
    if(info==null) {
      if(opts.verbose>=3)
        LogInfo.log("BinaryLexicon: skipping entry since there is no info for formula: " + lexValue.formula.toString());
      return Collections.emptyList();
    }
    //get alignment features
    Map<String,Double> alignmentScores = new TreeMap<String,Double>(lexValue.features);
    
    if (fbFormulasInfo.isCvtFormula(info) && source == EntrySource.STRING_MATCH) {

      List<BinaryLexicalEntry> entries = new ArrayList<LexicalEntry.BinaryLexicalEntry>();
      for (BinaryFormulaInfo cvtInfo : fbFormulasInfo.getCvtExpansions(info)) {
        entries.add(
            new BinaryLexicalEntry(
                mapKey, mapKey, new HashSet<String>(cvtInfo.descriptions), cvtInfo.formula, source,
                cvtInfo.popularity, cvtInfo.expectedType1, cvtInfo.expectedType2, cvtInfo.unitId, cvtInfo.unitDesc, alignmentScores, lexValue.lexeme));
      }
      return entries;
    } else {
      BinaryLexicalEntry entry = new BinaryLexicalEntry(
          mapKey, mapKey, new HashSet<String>(info.descriptions), lexValue.formula, source,
          info.popularity, info.expectedType1, info.expectedType2, info.unitId, info.unitDesc, alignmentScores, lexValue.lexeme);
      return Collections.singletonList(entry);
    }
  }

  public List<BinaryLexicalEntry> lookupEntries(String textDesc) throws IOException {

    List<BinaryLexicalEntry> res = new LinkedList<BinaryLexicalEntry>();
    Map<Pair<Formula, String>, BinaryLexicalEntry> entries = lexemeToEntryMap.get(textDesc.toLowerCase());

    if (entries != null) {
      for (BinaryLexicalEntry binaryEntry : entries.values()) {
        if (opts.useOnlyJaccard)
          binaryEntry.retainJaccardOnly();
        res.add(binaryEntry);
      }
      Collections.sort(res, new BinaryLexicalEntryComparator(opts.keyToSortBy));
    }
    return res.subList(0, Math.min(res.size(), opts.maxEntries));
  }

  public void printLexiconStats() {

    Set<String> fullLexemes = new HashSet<String>();
    Set<String> typedNomralizedLexemes = new HashSet<String>();
    Set<Formula> logicalForms = new HashSet<Formula>();

    for (String lexeme : lexemeToEntryMap.keySet()) {
      for (BinaryLexicalEntry binaryEntry : lexemeToEntryMap.get(lexeme).values()) {
        fullLexemes.add(binaryEntry.fullLexeme);
        typedNomralizedLexemes.add(binaryEntry.normalizedTextDesc + ";" + binaryEntry.expectedType1 + ";" + binaryEntry.expectedType2);
        logicalForms.add(binaryEntry.formula);
      }
    }
    LogInfo.log("number of lexemes: " + lexemeToEntryMap.size());
    LogInfo.log("Number of full lexemes: " + fullLexemes.size());
    LogInfo.log("Number of typed normalized lexemes: " + typedNomralizedLexemes.size());
    LogInfo.log("Number of logical forms: " + logicalForms.size());
  }

  public void pruneLexicon(int topK) {

    for (String normLexeme : lexemeToEntryMap.keySet()) {
      pruneLexicon(normLexeme, topK);
    }
  }
  /** For every NL phrase choose the top-k matching ones */
  private void pruneLexicon(String normLexeme, int topK) {

    Map<Pair<Formula, String>, BinaryLexicalEntry> entries = lexemeToEntryMap.get(normLexeme);
    //sort by jaccard
    List<BinaryLexicalEntry> entryList = CollectionUtils.toList(entries.values());
    Collections.sort(entryList, new EntriesJaccardComparator());
    //keep the top 5
    List<BinaryLexicalEntry> newEntries = new LinkedList<BinaryLexicalEntry>();
    newEntries.addAll(entryList.subList(0, Math.min(entries.size(), topK)));
    entries.clear();
    for (BinaryLexicalEntry newEntry : newEntries)
      entries.put(new Pair<Formula, String>(newEntry.formula, newEntry.fullLexeme), newEntry);
  }


  /** for every typed NL phrase choose the top-k */

  public void pruneLexiconTyped(String normLexeme, int topK) {

    Map<Pair<Formula, String>, BinaryLexicalEntry> entries = lexemeToEntryMap.get(normLexeme);

    //sort by triples
    Map<Triple<String, String, String>, List<Pair<BinaryLexicalEntry, Double>>> typedNlToScore = new HashMap<Triple<String, String, String>, List<Pair<BinaryLexicalEntry, Double>>>();
    for (BinaryLexicalEntry entry : entries.values()) {

      Triple<String, String, String> fullLexAndTypes = new Triple<String, String, String>(entry.fullLexeme, entry.expectedType1, entry.expectedType2);

      List<Pair<BinaryLexicalEntry, Double>> typedEntries = typedNlToScore.get(fullLexAndTypes);
      if (typedEntries == null) {
        typedEntries = new LinkedList<Pair<BinaryLexicalEntry, Double>>();
        typedNlToScore.put(fullLexAndTypes, typedEntries);
      }
      typedEntries.add(new Pair<BinaryLexicalEntry, Double>(entry, MapUtils.getDouble(entry.alignmentScores, INTERSECTION, 0.0)));
    }

    List<BinaryLexicalEntry> newEntries = new LinkedList<BinaryLexicalEntry>();

    for (Triple<String, String, String> typedNl : typedNlToScore.keySet()) {

      List<Pair<BinaryLexicalEntry, Double>> scoreList = typedNlToScore.get(typedNl);
      Collections.sort(scoreList, new BySecondReversePairComparator<BinaryLexicalEntry, Double>());
      for (int i = 0; i < Math.min(scoreList.size(), topK); ++i)
        newEntries.add(scoreList.get(i).first());
    }
    entries.clear();
    for (BinaryLexicalEntry newEntry : newEntries) {
      entries.put(new Pair<Formula, String>(newEntry.formula, newEntry.fullLexeme), newEntry);
    }
  }

  public static class EntriesJaccardComparator implements Comparator<BinaryLexicalEntry> {

    @Override
    public int compare(BinaryLexicalEntry o1, BinaryLexicalEntry o2) {

      double smoothedJaccard1 = computeUntypedJaccardFromAlignmentScores(o1.alignmentScores);
      double smoothedJaccard2 = computeUntypedJaccardFromAlignmentScores(o2.alignmentScores);

      if (smoothedJaccard1 > smoothedJaccard2)
        return -1;
      else if (smoothedJaccard1 < smoothedJaccard2)
        return 1;
      return 0;
    }

    public static double computeUntypedJaccardFromAlignmentScores(Map<String,Double> alignmentScores) {

      double intersection = MapUtils.getDouble(alignmentScores, INTERSECTION, 0.0);
      double nlSize = MapUtils.getDouble(alignmentScores, NL_TYPED, 0.0);
      double fbSize = MapUtils.getDouble(alignmentScores, FB_TYPED, 0.0);

      double smoothedJaccard = intersection / (nlSize + fbSize - intersection + 5);
      return smoothedJaccard;
    }
  }

  /** If the property has a reverse, keep it if it reversed*/
  public boolean validBinaryFormula(Formula formula) {
    if(fbFormulasInfo.hasOpposite(formula)) {
      boolean valid = fbFormulasInfo.isReversed(formula);
      if(opts.verbose>=3) {
        if(!valid) 
          LogInfo.logs("BinaryLexicon: invalid formula: %s",formula);
        else
          LogInfo.logs("BinaryLexicon: valid formula: %s",formula);
      }
      return valid;
    }
    return true;
  }

  public void printLexemeAndFbDesc(String out) throws IOException {
    PrintWriter writer = IOUtils.getPrintWriter(out);

    for (String lexeme : lexemeToEntryMap.keySet()) {
      Map<Pair<Formula, String>, BinaryLexicalEntry> entries = lexemeToEntryMap.get(lexeme);
      for (Pair<Formula, String> pair : entries.keySet()) {
        BinaryLexicalEntry entry = entries.get(pair);
        if (entry.jaccard() > 0.001)
          writer.println(pair.second + "\t" + entry.fbDescriptions + "\t" + entry.jaccard());
      }
    }
    writer.close();
  }

  public static class BinaryLexicalEntryComparator implements Comparator<BinaryLexicalEntry> {

    public String keyToSortby = null;

    public BinaryLexicalEntryComparator(String keyToSortBy) {
      assert (keyToSortBy != null);
      this.keyToSortby = keyToSortBy;
    }

    @Override
    public int compare(BinaryLexicalEntry arg0, BinaryLexicalEntry arg1) {

      double arg0Score = MapUtils.getDouble(arg0.alignmentScores, keyToSortby, 0.0);
      double arg1Score = MapUtils.getDouble(arg1.alignmentScores, keyToSortby, 0.0);

      if (arg0Score > arg1Score)
        return -1;
      if (arg0Score < arg1Score)
        return 1;
      if (arg0.popularity > arg1.popularity)
        return -1;
      if (arg0.popularity < arg1.popularity)
        return 1;
      return 0;
    }
  }
}
