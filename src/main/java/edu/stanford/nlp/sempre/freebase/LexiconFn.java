package edu.stanford.nlp.sempre.freebase;

import com.google.common.base.Joiner;
import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.freebase.lexicons.LexicalEntry;
import edu.stanford.nlp.sempre.freebase.lexicons.LexicalEntry.BinaryLexicalEntry;
import fig.basic.*;
import org.apache.lucene.queryparser.classic.ParseException;

import java.io.IOException;
import java.util.*;

/**
 * Looks up a string into the lexicon, interfacing with the fbalignment code.
 * @author Percy Liang
 */
public class LexiconFn extends SemanticFn {
  public static class Options {
    // Note: we filter here and not in entity lexicon so that don't need different cache for different numbers.
    @Option(gloss = "Number of entities to return from entity lexicon")
    public int maxEntityEntries = 100;
    @Option public int maxUnaryEntries = Integer.MAX_VALUE;
    @Option public int maxBinaryEntries = Integer.MAX_VALUE;

    @Option(gloss = "Verbose") public int verbose = 0;
    @Option(gloss = "Class name for lexicon") public String lexiconClassName;
    @Option public boolean useHistogramFeatures = true;
  }

  public static Options opts = new Options();
  private static Lexicon lexicon;
  public static Evaluation lexEval = new Evaluation();

  private String mode;  // unary, binary, or entity
  private EntityLexicon.SearchStrategy entitySearchStrategy;  // For entities, how to search
  private TextToTextMatcher textToTextMatcher = new TextToTextMatcher();
  private FbFormulasInfo fbFormulaInfo;

  public static final Map<Pair<String, Formula>, Set<Integer>> correctEntryToExampleIds =
          new HashMap<>();

  public LexiconFn() throws IOException {
    lexicon = Lexicon.getSingleton();
    fbFormulaInfo = FbFormulasInfo.getSingleton();
  }

  public void init(LispTree tree) {
    super.init(tree);
    for (int i = 1; i < tree.children.size(); i++) {
      String value = tree.child(i).value;

      // mode
      if (value.equals("unary")) this.mode = "unary";
      else if (value.equals("binary")) this.mode = "binary";
      else if (value.equals("entity")) this.mode = "entity";
      // entity search strategy
      // TODO(joberant): aren't we saying these should be set by a flag so the cache doesn't get messed up?
      else if (value.equals("inexact")) this.entitySearchStrategy = EntityLexicon.SearchStrategy.inexact;
      else if (value.equals("exact")) this.entitySearchStrategy = EntityLexicon.SearchStrategy.exact;
      else if (value.equals("fbsearch")) this.entitySearchStrategy = EntityLexicon.SearchStrategy.fbsearch;
      else throw new RuntimeException("Invalid argument: " + value);
    }
  }

  public static void getEntityEntryFeatures(LexicalEntry.EntityLexicalEntry eEntry, FeatureVector features,
                                            Callable c, Example ex) {

    if (FeatureExtractor.containsDomain("basicStats")) {
      if (opts.useHistogramFeatures)
        features.addHistogram("basicStats", "entity.popularity ", eEntry.getPopularity());
      else
        features.addWithBias("basicStats", "entity.popularity", Math.log(eEntry.getPopularity() + 1));
    }
    if (FeatureExtractor.containsDomain("entityFeatures")) {
      for (String feature : eEntry.entityFeatures.keySet()) {
        double value = eEntry.entityFeatures.getCount(feature);
        features.addWithBias("entityFeatures", "entity." + feature, value);
      }
      features.add("entityFeatures", "entity.pos=" + ex.languageInfo.posSeq(c.getStart(), c.getEnd()));
      features.add("entityFeatures", "entity.mention_length=" + (c.getEnd() - c.getStart()));
    }
    if (FeatureExtractor.containsDomain("lexAlign"))
      features.add("lexAlign", eEntry.textDescription + " --- " + eEntry.formula);
  }

  public static void getUnaryEntryFeatures(LexicalEntry.UnaryLexicalEntry uEntry, FeatureVector features) {
    if (FeatureExtractor.containsDomain("basicStats")) {
      if (opts.useHistogramFeatures)
        features.addHistogram("basicStats", "unary.popularity ", uEntry.getPopularity() + 1);
      else
        features.addWithBias("basicStats", "unary.popularity", Math.log(uEntry.getPopularity() + 1));
    }
    // Alignment scores features
    if (FeatureExtractor.containsDomain("alignmentScores")) {
      for (String feature : uEntry.alignmentScores.keySet()) {
        features.addWithBias("alignmentScores",  "unary." + feature, Math.log(MapUtils.getDouble(uEntry.alignmentScores, feature, 0.0) + 1));
      }
    }

    if (FeatureExtractor.containsDomain("basicStats")) {
      if (uEntry.getDistance() < 0.0001)
        features.add("basicStats", "unary.equal");

      // adding the source of the lexical entry as a feature
      features.add("basicStats", "unary.source=" + uEntry.source);
    }
    if (FeatureExtractor.containsDomain("lexAlign"))
      features.add("lexAlign", uEntry.textDescription + " --- " + uEntry.formula);
  }

  public static void getBinaryEntryFeatures(BinaryLexicalEntry bEntry, FeatureVector features) {
    if (FeatureExtractor.containsDomain("basicStats")) {
      if (opts.useHistogramFeatures)
        features.addHistogram("basicStats", "binary.popularity ", bEntry.getPopularity() + 1);
      else
        features.addWithBias("basicStats", "binary.popularity", Math.log(bEntry.getPopularity() + 1));
      // adding the source of the lexical entry as a feature
      features.add("basicStats", "binary." + bEntry.source);
    }
    // Alignment scores features
    if (FeatureExtractor.containsDomain("alignmentScores")) {
      for (String feature : bEntry.alignmentScores.keySet()) {
        features.addWithBias("alignmentScores", "binary." + feature, Math.log(MapUtils.getDouble(bEntry.alignmentScores, feature, 0.0) + 1));
      }
    }
    if (FeatureExtractor.containsDomain("lexAlign"))
      features.add("lexAlign", bEntry.textDescription + " --- " + bEntry.formula);
  }

  // Convert LexicalEntry into a form consumable by the semantic parser.
  private Derivation convert(Example ex,
                             Callable c,
                             String mode,
                             String word,
                             LexicalEntry entry) {
    FeatureVector features = new FeatureVector();
    SemType type;

    switch (mode) {
      case "entity":
        // Entities
        LexicalEntry.EntityLexicalEntry eEntry = (LexicalEntry.EntityLexicalEntry) entry;
        getEntityEntryFeatures(eEntry, features, c, ex);
        type = ((LexicalEntry.EntityLexicalEntry) entry).type;
        break;
      case "unary":
        // Unaries
        LexicalEntry.UnaryLexicalEntry uEntry = (LexicalEntry.UnaryLexicalEntry) entry;
        getUnaryEntryFeatures(uEntry, features);
        type = ((LexicalEntry.UnaryLexicalEntry) entry).type;
        break;
      case "binary":
        // Binaries
        BinaryLexicalEntry bEntry = (BinaryLexicalEntry) entry;
        getBinaryEntryFeatures(bEntry, features);
        // features that depend on entry but also on the example
        features.add(
                textToTextMatcher.extractFeatures(
                        ex.languageInfo.tokens.subList(c.getStart(), c.getEnd()),
                        ex.languageInfo.posTags.subList(c.getStart(), c.getEnd()),
                        ex.languageInfo.lemmaTokens.subList(c.getStart(), c.getEnd()),
                        bEntry.fbDescriptions));

        // Note that expectedType2 is the argument type, expectedType1 is the return type.
        type = SemType.newFuncSemType(bEntry.getExpectedType2(), bEntry.getExpectedType1());
        break;
      default:
        throw new RuntimeException("Invalid mode: " + mode);
    }

    Derivation newDeriv = new Derivation.Builder()
            .withCallable(c)
            .formula(entry.formula)
            .type(type)
            .localFeatureVector(features)
            .createDerivation();

    if (SemanticFn.opts.trackLocalChoices)
      newDeriv.addLocalChoice("LexiconFn " + newDeriv.startEndString(ex.getTokens()) + " " + entry);

    if (opts.verbose >= 3) {
      LogInfo.logs(
              "LexiconFn: %s [%s => %s ~ %s | %s]: popularity = %s, distance = %s, type = %s, source=%s",
              mode, word, entry.normalizedTextDesc, entry.fbDescriptions, newDeriv.formula,
              entry.getPopularity(), entry.getDistance(), newDeriv.type, entry.source);
    }
    return newDeriv;
  }

  public DerivationStream call(Example ex, Callable c) {

    if (opts.verbose >= 5) LogInfo.begin_track("LexicalFn.call: %s", c.childStringValue(0));

    String query = c.childStringValue(0);
    DerivationStream res;

    try {
      switch (mode) {
        // Entities
        case "entity": {
          // if (opts.verbose >= 2)
          // LogInfo.log("LexiconFn: querying for entity: " + query);

          List<? extends LexicalEntry> entries = lexicon.lookupEntities(query, entitySearchStrategy);
          lexEval.add("entity", !entries.isEmpty());
          entries = entries.subList(0, Math.min(opts.maxEntityEntries, entries.size()));
          res = new LazyLexiconFnDerivs(ex, c, entries, query);
          break;
        }
        // Unaries
        case "unary": {
            List<? extends LexicalEntry> entries = lexicon.lookupUnaryPredicates(query);
          lexEval.add("unary", !entries.isEmpty());
          entries = entries.subList(0, Math.min(opts.maxUnaryEntries, entries.size()));
          res = new LazyLexiconFnDerivs(ex, c, entries, query);
          break;
        }
        // Binaries
        case "binary": {
          List<? extends LexicalEntry> entries = lexicon.lookupBinaryPredicates(query);
          lexEval.add("binary", !entries.isEmpty());
          List<LexicalEntry> filteredEntries = new ArrayList<>();
          // filter cvt entries (TODO(joberant): remove this hack)
          for (LexicalEntry entry : entries) {
            if (!fbFormulaInfo.isCvt(((BinaryLexicalEntry) entry).expectedType1)
                    && !fbFormulaInfo.isCvt(((BinaryLexicalEntry) entry).expectedType2))
              filteredEntries.add(entry);
          }
          filteredEntries = filteredEntries.subList(0, Math.min(opts.maxBinaryEntries, filteredEntries.size()));
          res = new LazyLexiconFnDerivs(ex, c, filteredEntries, query);
          break;
        }
        default:
          throw new RuntimeException("Illegal mode: " + mode);
      }
    } catch (IOException | ParseException e) {
      throw new RuntimeException(e);
    }

    if (opts.verbose >= 5) LogInfo.end_track();
    return res;
  }

  // if there was bridging then have a rule from tokens to binary
  @Override
  public void addFeedback(Example ex) {
    LogInfo.begin_track("LexiconFn.addFeedback");
    Set<Pair<String, Formula>> correctLexemeFormulaMatches = collectLexemeFormulaPairs(ex);

    for (Pair<String, Formula> pair: correctLexemeFormulaMatches) {
      LogInfo.logs("LexiconFn.addFeedback: %s => %s", pair.getFirst(), pair.getSecond());
      // TODO(joberant): hack to get id
      MapUtils.addToSet(correctEntryToExampleIds, pair, Integer.parseInt(ex.id.substring(ex.id.lastIndexOf(':') + 1)));
      BinaryLexicon.getInstance().updateLexicon(pair, correctEntryToExampleIds.get(pair).size());
    }
    LogInfo.end_track();
  }

  private static Set<Pair<String, Formula>> collectLexemeFormulaPairs(Example ex) {
    Set<Pair<String, Formula>> res = new HashSet<>();
    Set<Pair<String, Formula>> temp = new HashSet<>();
    for (Derivation correctDerivation : ex.getCorrectDerivations()) {
      // get all join formulas
      List<Formula> relations =
              correctDerivation.formula.mapToList(formula -> {
                List<Formula> res1 = new ArrayList<>();
                if (formula instanceof JoinFormula)
                  res1.add(((JoinFormula) formula).relation);
                return res1;
              }, false);
      Set<Integer> validIndices = new HashSet<>();
      findValidIndices(correctDerivation, validIndices);
      // match formulas
      for (Formula relation : relations) {
        for (int i = 0; i < ex.numTokens(); ++i) {
          if (LanguageInfo.isContentWord(ex.posTag(i)) &&
                  validIndices.contains(i)) {
            temp.add(Pair.newPair(ex.languageInfo.lemmaTokens.get(i), relation));
          }
        }
      }
    }
    // reverse invalid relations
    for (Pair<String, Formula> pair: temp) {
      if (!BinaryLexicon.getInstance().validBinaryFormula(pair.getSecond())) {
        pair.setSecond(FbFormulasInfo.getSingleton().equivalentFormula(pair.getSecond()));
      }
      res.add(pair);
    }
    return res;
  }

  @Override
  public void sortOnFeedback(Params params) {
    LogInfo.begin_track("Learner.sortLexiconOnFeedback");
    BinaryLexicon.getInstance().sortLexiconByFeedback(params);
    UnaryLexicon.getInstance().sortLexiconByFeedback(params);
    LogInfo.end_track();
  }

  // todo - this method is grammar specific and that is bad
  private static void findValidIndices(Derivation deriv, Set<Integer> indices) {
    if (deriv.cat.equals("$Entity"))
      return;
    if (deriv.children.size() == 0) {
      for (int i = deriv.start; i < deriv.end; ++i)
        indices.add(i);
      return;
    }
    for (Derivation child : deriv.children)
      findValidIndices(child, indices);
  }

  /** For now this ignores stemming!!! */
  private static boolean doesContextMatch(Example ex, Derivation deriv, BinaryLexicalEntry bEntry) {

    if (bEntry.isFullLexemeEqualToNormalizedText())
      return true;
    // get the left and right context surrounding the core (normalized text)
    String[] leftContext = bEntry.getLeftContext();
    String[] rightContext = bEntry.getRightContext();
    // match right context
    for (int i = 0; i < rightContext.length; ++i) {
      // in this case all context words were matched and some were dropped but there was no mismatch
      if (deriv.end + i >= ex.numTokens() || ex.token(deriv.end + i).equals("?"))
        break;

      if (!rightContext[0].equals(ex.lemmaToken(deriv.end + i))) {
        if (opts.verbose >= 4) {
          LogInfo.logs(
                  "RIGHT CONTEXT MISMATCH: full lexeme=%s, normalized text=%s left context=%s, right context=%s example=%s, formula=%s",
                  bEntry.fullLexeme,
                  bEntry.normalizedTextDesc,
                  Joiner.on(' ').join(leftContext),
                  Joiner.on(' ').join(rightContext),
                  Joiner.on(' ').join(ex.languageInfo.tokens),
                  bEntry.formula);
        }
        return false;
      }
    }

    // match right context
    for (int i = 0; i < leftContext.length; ++i) {
      if (deriv.start - i - 1 < 0) // in this case all context words were matched and some were dropped but there was no mismatch
        break;
      if (!leftContext[leftContext.length - i - 1].equals(ex.lemmaToken(deriv.start - i - 1))) {
        if (opts.verbose >= 2) {
          LogInfo.logs(
                  "LEFT CONTEXT MISMATCH: full lexeme=%s, normalized text=%s left context=%s, right context=%s example=%s, formula=%s",
                  bEntry.fullLexeme,
                  bEntry.normalizedTextDesc,
                  Joiner.on(' ').join(leftContext),
                  Joiner.on(' ').join(rightContext),
                  Joiner.on(' ').join(ex.languageInfo.tokens),
                  bEntry.formula);
        }
        return false;
      }
    }
    return true;
  }

  public class LazyLexiconFnDerivs extends MultipleDerivationStream {

    private Example ex;
    private Callable callable;
    private List<? extends LexicalEntry> entries; // we get one derivation from each entry
    private String query;
    private int currIndex = 0;

    public LazyLexiconFnDerivs(Example ex, Callable c, List<? extends LexicalEntry> entries, String query) {
      this.ex = ex;
      this.callable = c;
      this.entries = entries;
      this.query = query;
    }

    @Override
    public int estimatedSize() {
      return entries.size() - currIndex;
    }

    @Override
    public Derivation createDerivation() {
      if (currIndex == entries.size())
        return null;
      LexicalEntry currEntry = entries.get(currIndex++);
      Derivation res;
      switch (mode) {
        case "entity":
          res = convert(ex, callable, "entity", query, currEntry);
          break;
        case "unary":
          res = convert(ex, callable, "unary", query, currEntry);
          break;
        case "binary":
          res = convert(ex, callable, "binary", query, currEntry);
          // add context matching feature
          if (FeatureExtractor.containsDomain("context")) {
            if (!doesContextMatch(ex, res, (BinaryLexicalEntry) currEntry))
              res.addFeature("context", "binary.contextMismatch");
          }
          break;
        default:
          throw new RuntimeException("Illegal mode: " + mode);
      }
      return res;
    }
  }
}

