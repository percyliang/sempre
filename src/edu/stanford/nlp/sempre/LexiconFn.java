package edu.stanford.nlp.sempre;

import com.google.common.base.Joiner;
import edu.stanford.nlp.sempre.fbalignment.lexicons.EntrySource;
import edu.stanford.nlp.sempre.fbalignment.lexicons.LexicalEntry;
import edu.stanford.nlp.sempre.fbalignment.lexicons.LexicalEntry.BinaryLexicalEntry;
import edu.stanford.nlp.sempre.fbalignment.lexicons.Lexicon;
import edu.stanford.nlp.sempre.fbalignment.lexicons.EntityLexicon;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
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
    @Option(gloss = "Keep entries with at most this distance")
    public double maxDistance = 100;
    @Option(gloss = "Keep entries with at most this distance")
    public double entityMaxDistance = Integer.MAX_VALUE;
    @Option(gloss = "Keep entries with at least this popularity")
    public double minPopularity = 0;
    @Option(gloss = "Number of entities to return from entity lexicon")
    public int maxEntityEntries = 100; //we filter here and not in entity lexicon so that don't need different cache for different numbers
    @Option(gloss = "Verbose") public int verbose = 0;
    @Option(gloss = "Class name for lexicon") public String lexiconClassName;
  }

  public static Options opts = new Options();

  private static Lexicon lexicon;
  public static Evaluation lexEval = new Evaluation();

  private String mode;  // unary, binary, or entity
  private EntityLexicon.SearchStrategy entitySearchStrategy = Lexicon.opts.entitySearchStrategy;  // For entities, how to search
  private TextToTextMatcher textToTextMatcher = new TextToTextMatcher();
  private FbFormulasInfo fbFormulaInfo;

  public LexiconFn() throws IOException {
    lexicon = Lexicon.getSingleton();
    fbFormulaInfo = FbFormulasInfo.getSingleton();
  }

  public void init(LispTree tree) {
    super.init(tree);
    for (int i = 1; i < tree.children.size(); i++) {
      String value = tree.child(i).value;
      if (value.equals("unary")) this.mode = "unary";
      else if (value.equals("binary")) this.mode = "binary";
      else if (value.equals("entity")) this.mode = "entity";
      else if (value.equals("allowInexact")/*deprecate this*/ || value.equals("inexact")) this.entitySearchStrategy = EntityLexicon.SearchStrategy.inexact;
      else if (value.equals("fbsearch")) this.entitySearchStrategy = EntityLexicon.SearchStrategy.fbsearch;
      else throw new RuntimeException("Invalid argument: " + value);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LexiconFn lexiconFn = (LexiconFn) o;
    if (!mode.equals(lexiconFn.mode)) return false;
    return true;
  }

  boolean shouldKeep(LexicalEntry entry) {
    if (entry.getPopularity() < opts.minPopularity) return false;
    if (entry.getDistance() > opts.maxDistance) return false;
    if (entry instanceof LexicalEntry.EntityLexicalEntry && entry.getDistance() > opts.entityMaxDistance)
      return false;
    return true;
  }

  // Input: set of types coming from the lexicon {fb:common.topic, fb:people.person, ...}
  // Output: remove any element which is in the transitive closure. {fb:people.person, ...}
  private UnionSemType setToType(Set<String> types) {
    FbFormulasInfo info = FbFormulasInfo.getSingleton();
    Set<String> resultTypes = new HashSet<String>(types);
    for (String entityType : types) {
      for (String supertype : info.getIncludedTypesInclusive(entityType)) {
        if (!supertype.equals(entityType))
          resultTypes.remove(supertype);
      }
    }
    UnionSemType type = new UnionSemType();
    for (String entityType : resultTypes)
      type.add(new EntitySemType(entityType));
    return type;
  }

  // Convert LexicalEntry into a form consumable by the semantic parser.
  private Derivation convert(Example ex,
      Callable c,
      String mode,
      String word,
      LexicalEntry entry,
      Counter<Pair<String, String>> typesThatAlreadyAppeared) {
    FeatureVector features = new FeatureVector();
    SemType type;

    if (FeatureExtractor.containsDomain("basicStats")) {
      features.addWithBias("basicStats", mode + ".popularity", Math.log(entry.getPopularity() + 1));
      //features.addWithBias(mode + ".distance", entry.getDistance());
    }

    if (mode.equals("entity")) {
      // Entities
      LexicalEntry.EntityLexicalEntry eEntry = (LexicalEntry.EntityLexicalEntry) entry;

      if (FeatureExtractor.containsDomain("tokensDistance")) {
        for (String feature : eEntry.tokenEditDistanceFeatures.keySet()) {
          double value = eEntry.tokenEditDistanceFeatures.getCount(feature);
          features.addWithBias("tokensDistance", mode + "." + feature, value);
        }
      }

      type = setToType(((LexicalEntry.EntityLexicalEntry) entry).getTypes());
    } else if (mode.equals("unary")) {
      // Unaries
      LexicalEntry.UnaryLexicalEntry uEntry = (LexicalEntry.UnaryLexicalEntry) entry;

      // Alignment scores features
      if (FeatureExtractor.containsDomain("alignmentScores")) {
        for (String feature : uEntry.alignmentScores.keySet()) {
          features.addWithBias("alignmentScores", mode + "." + feature, Math.log(MapUtils.getDouble(uEntry.alignmentScores, feature, 0.0) + 1));
        }
      }

      if (FeatureExtractor.containsDomain("basicStats")) {
        if (uEntry.getDistance() < 0.0001)
          features.add("basicStats", "unary.equal");

        //features.addWithBias(mode + ".distance", entry.getDistance());

        //adding the source of the lexical entry as a feature
        features.add("basicStats", mode + ",source=" + uEntry.source);
      }

      type = setToType(((LexicalEntry.UnaryLexicalEntry) entry).getTypes());
    } else if (mode.equals("binary")) {
      // Binaries
      LexicalEntry.BinaryLexicalEntry bEntry = (LexicalEntry.BinaryLexicalEntry) entry;

      // Alignment scores features
      if (FeatureExtractor.containsDomain("alignmentScores")) {
        for (String feature : bEntry.alignmentScores.keySet()) {
          //HACK for graph prop
          if (bEntry.source == EntrySource.GRAPHPROP)
            features.addWithBias("alignmentScores", mode + "." + feature, Math.log(MapUtils.getDouble(bEntry.alignmentScores, feature, 0.0)));
          else
            features.addWithBias("alignmentScores", mode + "." + feature, Math.log(MapUtils.getDouble(bEntry.alignmentScores, feature, 0.0) + 1));
        }

        //adding a feature if this is the top ranked alignment for this pair of types
        //this assumes that the entries are passed sorted!!!!
        // TODO: this seems dangerous
        Pair<String, String> expectedTypes = new Pair<String, String>(bEntry.expectedType1, bEntry.expectedType2);
        if (typesThatAlreadyAppeared.getCount(expectedTypes) < 1) {
          features.add("alignmentScores", "binary.top");
          typesThatAlreadyAppeared.incrementCount(expectedTypes);
        }
      }

      if (FeatureExtractor.containsDomain("basicStats")) {
        //adding the source of the lexical entry as a feature
        features.add("basicStats", mode + "." + bEntry.source);

        //adding edit distance feature - replaced by text to text matcher
        //features.addWithBias(mode + ".distance", entry.getDistance());
      }

      //text to text match features
      features.add(
          textToTextMatcher.extractFeatures(
              ex.languageInfo.tokens.subList(c.getStart(), c.getEnd()),
              ex.languageInfo.posTags.subList(c.getStart(), c.getEnd()),
              ex.languageInfo.lemmaTokens.subList(c.getStart(), c.getEnd()),
              bEntry.fbDescriptions));

      // Note that expectedType2 is the argument type, expectedType1 is the return type.
      type = new FuncSemType(new EntitySemType(bEntry.getExpectedType2()), new EntitySemType(bEntry.getExpectedType1()));
    } else {
      throw new RuntimeException("Invalid mode: " + mode);
    }

    Derivation newDeriv = new Derivation.Builder()
    .withCallable(c)
    .formula(entry.formula)
    .type(type)
    .localFeatureVector(features)
    .createDerivation();

    // Doesn't generalize, but add it for now, otherwise not separable
    if (FeatureExtractor.containsDomain("lexAlign"))
      newDeriv.addFeature("lexAlign", word + " --- " + newDeriv.formula);

    if (SemanticFn.opts.trackLocalChoices)
      newDeriv.localChoices.add("LexiconFn " + newDeriv.startEndString(ex.getTokens()) + " " + entry);

    if (opts.verbose >= 3) {
      LogInfo.logs(
          "LexiconFn: %s [%s => %s ~ %s | %s]: popularity = %s, distance = %s, type = %s, source=%s",
          mode, word, entry.normalizedTextDesc, entry.fbDescriptions, newDeriv.formula,
          entry.getPopularity(), entry.getDistance(), newDeriv.type,entry.source);
    }
    return newDeriv;
  }

  public List<Derivation> call(Example ex, Callable c) {
    if (opts.verbose >= 2)
      LogInfo.begin_track("LexicalFn.call: %s", c.childStringValue(0));

    String phrase = c.childStringValue(0);
    List<Derivation> results = new ArrayList<Derivation>();

    try {
      // Entities
      if (mode.equals("entity")) {
        List<? extends LexicalEntry> entries = lexicon.lookupEntities(phrase, entitySearchStrategy);
        if (opts.verbose >= 2)
          LogInfo.logs("LexiconFn.call %s: %s => %s entries", mode, phrase, entries.size());

        lexEval.add("entity", entries.isEmpty() ? false : true);
        entries = entries.subList(0, Math.min(opts.maxEntityEntries, entries.size()));

        for (LexicalEntry entry : entries)
          results.add(convert(ex, c, "entity", phrase, entry, null));
      }

      // Unaries
      if (mode.equals("unary")) {
        List<? extends LexicalEntry> entries = lexicon.lookupUnaryPredicates(phrase);
        if (opts.verbose >= 2)
          LogInfo.logs("LexiconFn.call %s: %s => %s entries", mode, phrase, entries.size());
        lexEval.add("unary", entries.isEmpty() ? false : true);
        for (LexicalEntry entry : entries)
          results.add(convert(ex, c, "unary", phrase, entry, null));
      }

      // Binaries
      if (mode.equals("binary")) {
        List<? extends LexicalEntry> entries = lexicon.lookupBinaryPredicates(phrase);
        if (opts.verbose >= 2)
          LogInfo.logs("LexiconFn.call %s: %s => %s entries", mode, phrase, entries.size());
        lexEval.add("binary", entries.isEmpty() ? false : true);

        //this set will contain pairs of types that appeared, each time a new one appears
        //it is guaranteed it is the top one for this pair of types since the list is ordered
        //THIS ASSUMES THAT THE ENTRIES ARE SORTED 
        Counter<Pair<String, String>> typesThatAlreadyAppeared = new ClassicCounter<Pair<String, String>>();

        for (LexicalEntry entry : entries) {
          //while the lexicon has CVT entries we just ignore them
          if (fbFormulaInfo.isCvt(((BinaryLexicalEntry) entry).expectedType1) || fbFormulaInfo.isCvt(((BinaryLexicalEntry) entry).expectedType2))
            continue;

          Derivation deriv = convert(ex, c, "binary", phrase, entry, typesThatAlreadyAppeared);
          // add context matching feature
          if (FeatureExtractor.containsDomain("context")) {
            if (!doesContextMatch(ex, deriv, (BinaryLexicalEntry) entry, phrase))
              deriv.addFeature("context", "binary.contextMismatch");
          }
          results.add(deriv);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    } 

    if (opts.verbose >= 2) LogInfo.end_track();

    return results;
  }

  /** For now this ignores stemming!!! */
  private static boolean doesContextMatch(Example ex, Derivation deriv, BinaryLexicalEntry bEntry, String word) {

    if (bEntry.isFullLexemeEqualToNormalizedText())
      return true;
    //get the left and right context surrounding the core (normalized text)
    String[] leftContext = bEntry.getLeftContext();
    String[] rightContext = bEntry.getRightContext();
    //match right context
    for (int i = 0; i < rightContext.length; ++i) {
      //in this case all context words were matched and some were dropped but there was no mismatch
      if (deriv.end + i >= ex.numTokens() || ex.token(deriv.end + i).equals("?"))
        break;

      if (!rightContext[0].equals(ex.lemmaToken(deriv.end + i))) {
        if (opts.verbose >= 1) {
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

    //match right context
    for (int i = 0; i < leftContext.length; ++i) {
      if (deriv.start - i - 1 < 0) //in this case all context words were matched and some were dropped but there was no mismatch
        break;
      if (!leftContext[leftContext.length - i - 1].equals(ex.lemmaToken(deriv.start - i - 1))) {
        if (opts.verbose >= 1) {
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
}

