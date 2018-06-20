package edu.stanford.nlp.sempre.freebase;

import com.google.common.base.Strings;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.freebase.FbFormulasInfo.BinaryFormulaInfo;
import edu.stanford.nlp.sempre.freebase.lexicons.EntrySource;
import edu.stanford.nlp.sempre.freebase.lexicons.LexicalEntry.BinaryLexicalEntry;
import edu.stanford.nlp.sempre.freebase.lexicons.LexicalEntry.LexiconValue;
import edu.stanford.nlp.sempre.freebase.lexicons.normalizers.EntryNormalizer;
import edu.stanford.nlp.sempre.freebase.lexicons.normalizers.PrepDropNormalizer;
import fig.basic.*;
import fig.exec.Execution;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Lexicon for binary predicates, "born" --> fb:people.person.place_of_birth
 * @author jonathanberant
 */
public final class BinaryLexicon {

  public static class Options {
    @Option(gloss = "Number of results return by the lexicon")
    public int maxEntries = 1000;
    @Option(gloss = "Path to binary lexicon files")
    public String binaryLexiconFilesPath = "lib/fb_data/7/binaryInfoStringAndAlignment.txt";
    @Option(gloss = "Verbosity") public int verbose = 0;
    @Option(gloss = "Alignment score to sort by")
    public String keyToSortBy = INTERSECTION;
  }

  private static BinaryLexicon binaryLexicon;
  public static BinaryLexicon getInstance() {
    if (binaryLexicon == null)
      try {
        binaryLexicon = new BinaryLexicon();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    return binaryLexicon;
  }

  public static Options opts = new Options();

  private EntryNormalizer lexiconLoadingNormalizer;
  private FbFormulasInfo fbFormulasInfo;

  public static final String INTERSECTION = "Intersection_size_typed";

  Map<String, List<BinaryLexicalEntry>> lexemeToEntryList = new HashMap<>();

  private BinaryLexicon() throws IOException {
    if (Strings.isNullOrEmpty(opts.binaryLexiconFilesPath))
      throw new RuntimeException("Missing unary lexicon file");
    fbFormulasInfo = FbFormulasInfo.getSingleton();
    // if we omit prepositions then the lexicon normalizer does that, otherwise, it is a normalizer that does nothing
    lexiconLoadingNormalizer = new PrepDropNormalizer(); // the alignment lexicon already contains stemmed stuff so just need to drop prepositions
    read(opts.binaryLexiconFilesPath);
  }

  private void read(String lexiconFile) throws IOException {

    LogInfo.begin_track_printAll("Loading binary lexicon file " + lexiconFile);
    for (String line : IOUtils.readLines(lexiconFile)) {
      LexiconValue lv = Json.readValueHard(line, LexiconValue.class);
      String lexemeKey = lv.lexeme;
      String normalizedLexemeKey = lexiconLoadingNormalizer.normalize(lexemeKey);
      // add lexeme and normalized lexeme
      addEntryToMap(lexemeKey, lv);
      if (!lexemeKey.equals(normalizedLexemeKey)) {
        addEntryToMap(normalizedLexemeKey, lv);
      }
    }
    sortLexiconEntries();
    LogInfo.log("Number of entries: " + lexemeToEntryList.size());
    LogInfo.end_track();
  }

  public void addEntryToMap(String lexemeKey, LexiconValue lv) {
    List<BinaryLexicalEntry> bEntries = buildEntry(lv, lexemeKey);
    for (BinaryLexicalEntry bEntry : bEntries)
      MapUtils.addToList(lexemeToEntryList, lexemeKey, bEntry);
  }

  private void sortLexiconEntries() {
    for (List<BinaryLexicalEntry> entries: lexemeToEntryList.values()) {
      Collections.sort(entries, new BinaryLexEntryByCounterComparator());
    }
  }

  public List<BinaryLexicalEntry> buildEntry(LexiconValue lexValue, String lexemeKey) {

    EntrySource source = EntrySource.parseSourceDesc(lexValue.source);
    BinaryFormulaInfo info = fbFormulasInfo.getBinaryInfo(lexValue.formula);

    if (!validBinaryFormula(lexValue.formula))
      return Collections.emptyList();

    if (info == null) {
      if (opts.verbose >= 3)
        LogInfo.log("BinaryLexicon: skipping entry since there is no info for formula: " + lexValue.formula.toString());
      return Collections.emptyList();
    }
    // get alignment features
    Map<String, Double> alignmentScores = new TreeMap<>(lexValue.features);

    if (fbFormulasInfo.isCvtFormula(info) && source == EntrySource.STRING_MATCH) {

      List<BinaryLexicalEntry> entries = new ArrayList<>();
      for (BinaryFormulaInfo cvtInfo : fbFormulasInfo.getCvtExpansions(info)) {
        entries.add(
                new BinaryLexicalEntry(
                        lexemeKey, lexemeKey, new HashSet<>(cvtInfo.descriptions), cvtInfo.formula, source,
                        cvtInfo.popularity, cvtInfo.expectedType1, cvtInfo.expectedType2, cvtInfo.unitId, cvtInfo.unitDesc, alignmentScores, lexValue.lexeme));
      }
      return entries;
    } else {
      BinaryLexicalEntry entry = new BinaryLexicalEntry(
              lexemeKey, lexemeKey, new HashSet<>(info.descriptions), lexValue.formula, source,
              info.popularity, info.expectedType1, info.expectedType2, info.unitId, info.unitDesc, alignmentScores, lexValue.lexeme);
      return Collections.singletonList(entry);
    }
  }

  public List<BinaryLexicalEntry> lookupEntries(String textDesc) throws IOException {
    List<BinaryLexicalEntry> entries = lexemeToEntryList.get(textDesc.toLowerCase());
    if (entries != null) {
      List<BinaryLexicalEntry> res = new ArrayList<>();
      for (int i = 0; i <  Math.min(entries.size(), opts.maxEntries); ++i) {
        res.add(entries.get(i));
      }
      return res;
    }
    return Collections.emptyList();
  }

  /** If the property has a reverse, keep it if it reversed*/
  public boolean validBinaryFormula(Formula formula) {
    if (fbFormulasInfo.hasOpposite(formula)) {
      boolean valid = fbFormulasInfo.isReversed(formula);
      if (opts.verbose >= 3) {
        if (!valid)
          LogInfo.logs("BinaryLexicon: invalid formula: %s", formula);
        else
          LogInfo.logs("BinaryLexicon: valid formula: %s", formula);
      }
      return valid;
    }
    return true;
  }

  public void updateLexicon(Pair<String, Formula> lexemeFormulaPair, int support) {
    StopWatchSet.begin("BinaryLexicon.updateLexicon");
    if (opts.verbose > 0)
      LogInfo.logs("Pair=%s, score=%s", lexemeFormulaPair, support);
    boolean exists = false;
    String lexeme = lexemeFormulaPair.getFirst();
    Formula formula = lexemeFormulaPair.getSecond();

    List<BinaryLexicalEntry> bEntries = MapUtils.get(lexemeToEntryList, lexeme, Collections.<BinaryLexicalEntry>emptyList());
    for (BinaryLexicalEntry bEntry : bEntries) {
      if (bEntry.formula.equals(formula)) {
        bEntry.alignmentScores.put("Feedback", (double) support);
        if (opts.verbose > 0)
          LogInfo.logs("Entry exists: %s", bEntry);
        exists = true;
        break;
      }
    }
    if (!exists) {
      BinaryFormulaInfo bInfo = fbFormulasInfo.getBinaryInfo(formula);
      if (bInfo == null) {
        LogInfo.warnings("BinaryLexicon.updateLexicon: no binary info for %s", formula);
        return;
      }
      BinaryLexicalEntry newEntry =
              new BinaryLexicalEntry(
                      lexeme, lexeme, new HashSet<>(bInfo.descriptions), bInfo.formula, EntrySource.FEEDBACK,
                      bInfo.popularity, bInfo.expectedType1, bInfo.expectedType2, bInfo.unitId, bInfo.unitDesc, new HashMap<>(), lexeme);
      MapUtils.addToList(lexemeToEntryList, lexeme, newEntry);
      newEntry.alignmentScores.put("Feedback", (double) support);
      LogInfo.logs("Adding new binary entry=%s", newEntry);

    }
    StopWatchSet.end();
  }

  public void sortLexiconByFeedback(Params params) {
    StopWatchSet.begin("BinaryLexicon.sortLexiconByFeedback");
    LogInfo.log("Number of entries: " + lexemeToEntryList.size());
    BinaryLexEntrybyFeaturesComparator comparator =
            new BinaryLexEntrybyFeaturesComparator(params);
    for (String lexeme : lexemeToEntryList.keySet()) {
      Collections.sort(lexemeToEntryList.get(lexeme), comparator);
      if (opts.verbose > 1) {
        LogInfo.logs("Sorted list for lexeme=%s", lexeme);
        for (BinaryLexicalEntry bEntry : lexemeToEntryList.get(lexeme)) {
          FeatureVector fv = new FeatureVector();
          LexiconFn.getBinaryEntryFeatures(bEntry, fv);
          LogInfo.logs("Entry=%s, dotprod=%s", bEntry, fv.dotProduct(comparator.params));
        }
      }
    }
    try {
      // Output the lexicon to the execution directory.
      String path = Execution.getFile("lexicon");
      if (path != null) {
        PrintWriter writer = fig.basic.IOUtils.openOut(path);
        for (String lexeme : lexemeToEntryList.keySet()) {
          writer.println(lexeme + "\t" + lexemeToEntryList.get(lexeme));
        }
        writer.flush();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }


    StopWatchSet.end();
  }

  public class BinaryLexEntrybyFeaturesComparator implements Comparator<BinaryLexicalEntry> {

    public final Params params;
    public BinaryLexEntrybyFeaturesComparator(Params params) {
      this.params = params;
    }
    @Override
    public int compare(BinaryLexicalEntry entry1, BinaryLexicalEntry entry2) {

      FeatureVector features1 = new FeatureVector();
      FeatureVector features2 = new FeatureVector();
      LexiconFn.getBinaryEntryFeatures(entry1, features1);
      LexiconFn.getBinaryEntryFeatures(entry2, features2);
      double score1 = features1.dotProduct(params);
      double score2 = features2.dotProduct(params);
      if (score1 > score2) return -1;
      if (score1 < score2) return +1;
      // back off to usual thing
      double entry1Score = MapUtils.getDouble(entry1.alignmentScores, opts.keyToSortBy, 0.0);
      double entry2Score = MapUtils.getDouble(entry2.alignmentScores, opts.keyToSortBy, 0.0);

      if (entry1Score > entry2Score)
        return -1;
      if (entry1Score < entry2Score)
        return +1;
      if (entry1.popularity > entry2.popularity)
        return -1;
      if (entry1.popularity < entry2.popularity)
        return +1;
      return 0;
    }
  }

  public class BinaryLexEntryByCounterComparator implements Comparator<BinaryLexicalEntry> {

    @Override
    public int compare(BinaryLexicalEntry entry1, BinaryLexicalEntry entry2) {
      double entry1Score = MapUtils.getDouble(entry1.alignmentScores, opts.keyToSortBy, 0.0);
      double entry2Score = MapUtils.getDouble(entry2.alignmentScores, opts.keyToSortBy, 0.0);

      if (entry1Score > entry2Score)
        return -1;
      if (entry1Score < entry2Score)
        return +1;
      if (entry1.popularity > entry2.popularity)
        return -1;
      if (entry1.popularity < entry2.popularity)
        return +1;
      // to do - this is to break ties - make more efficient
      int stringComparison = entry1.formula.toString().compareTo(entry2.formula.toString());
      if (stringComparison < 0)
        return -1;
      if (stringComparison > 0)
        return +1;
      return 0;
    }
  }
}
