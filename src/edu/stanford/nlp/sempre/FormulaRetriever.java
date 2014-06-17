package edu.stanford.nlp.sempre;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.sempre.paraphrase.ParaphraseUtils;
import org.apache.lucene.queryparser.classic.ParseException;

import com.google.common.base.Joiner;

import edu.stanford.nlp.sempre.FbFormulasInfo.BinaryFormulaInfo;
import edu.stanford.nlp.sempre.FbFormulasInfo.UnaryFormulaInfo;
import edu.stanford.nlp.sempre.LanguageInfo.LanguageUtils;
import edu.stanford.nlp.sempre.fbalignment.lexicons.LexicalEntry.EntityLexicalEntry;
import edu.stanford.nlp.sempre.fbalignment.lexicons.Lexicon;
import edu.stanford.nlp.sempre.paraphrase.ParsingExample;
import fig.basic.IntPair;
import fig.basic.LogInfo;
import fig.basic.Option;
import fig.basic.Pair;

/**
 * Retrieves a candidate set of formulas for a question mainly by identifying an entity and growing 
 * logical forms around it
 * In general this class is pretty specific to the templates described in the ACL 2014 submission
 * @author jonathanberant
 *
 */
public class FormulaRetriever {

  public static class Options {
    @Option public int verbose = 0;
    @Option(gloss="Whether to generate 'how many' questions")
    public boolean supportCountUtterances=false;
    @Option(gloss="Whether to filter relations") 
    public boolean filterRelations=true;
    @Option(gloss="Whether to conservatively find entities") 
    public boolean conservativeEntityExtraction=true;
    @Option(gloss="Number of entities to keep (done here and no in Entity Lexicon," +
        " so that cache is still valid even if we change this number") 
    public int maxEntries=10;
    @Option(gloss="Whether to create unaries")
    public boolean createUnaries=true;
    @Option(gloss="Whether to create injections")
    public boolean createInjections=true;
  }

  public static Options opts = new Options();
  private final FbFormulasInfo fbFormulasInfo = FbFormulasInfo.getSingleton();

  private Lexicon lexicon;
  private boolean removeEquivalents;

  public FormulaRetriever(boolean removeEquivalents) throws IOException {
    lexicon = Lexicon.getSingleton();
    this.removeEquivalents = removeEquivalents;
  }

  public List<FormulaGenerationInfo> retrieveFormulas(ParsingExample ex) {

    List<FormulaGenerationInfo> res = new ArrayList<FormulaGenerationInfo>();
    try {
      boolean isCount = isCountUtterance(ex.utterance);
      List<Pair<IntPair, EntityLexicalEntry>> entities = getLexiconEntities(ex.languageInfo);
      joinWithBinariesAndInject(ex.languageInfo,res, isCount, entities);
      createUnaries(ex.languageInfo,res);
      LogInfo.logs("FormulaRetriver.retrieveLexiconFromulas: number of formulas=%s",res.size());
    } catch (ParseException | IOException e) {
      throw new RuntimeException(e);
    }
    return res;
  }

  public List<FormulaGenerationInfo> retrieveFormulas(Example ex) {

    LogInfo.begin_track("Retrieve formulas");
    List<FormulaGenerationInfo> res = new ArrayList<FormulaGenerationInfo>();
    try {
      boolean isCount = isCountUtterance(ex.utterance);
      List<Pair<IntPair, EntityLexicalEntry>> entities = getLexiconEntities(ex.languageInfo);
      joinWithBinariesAndInject(ex.languageInfo,res, isCount, entities);
      createUnaries(ex.languageInfo,res);
      LogInfo.logs("FormulaRetriver.retrieveLexiconFromulas: number of formulas=%s",res.size());
    } catch (ParseException | IOException e) {
      throw new RuntimeException(e);
    }
    LogInfo.end_track();
    return res;
  }

  private void joinWithBinariesAndInject(LanguageInfo lInfo, List<FormulaGenerationInfo> res, boolean isCount,
      List<Pair<IntPair, EntityLexicalEntry>> entities) throws ParseException, IOException {
    for(Pair<IntPair,EntityLexicalEntry> spanAndEntryPair: entities) {
      joinEntityWithBinariesAndInject(lInfo, res, isCount, entities, spanAndEntryPair);
    }
  }

  private void createUnaries(LanguageInfo lInfo, List<FormulaGenerationInfo> fgInfos) {

    List<FormulaGenerationInfo> toAdd = new ArrayList<FormulaGenerationInfo>();
    for(FormulaGenerationInfo fgInfo: fgInfos) {
      toAdd.addAll(createUnaries(lInfo, fgInfo));
    }
    fgInfos.addAll(toAdd);
  }

  private void joinEntityWithBinariesAndInject(LanguageInfo lInfo, List<FormulaGenerationInfo> res,
      boolean isCount, List<Pair<IntPair, EntityLexicalEntry>> entities, Pair<IntPair, EntityLexicalEntry> spanAndEntryPair)
          throws ParseException, IOException {
    EntityLexicalEntry entityEntry = spanAndEntryPair.getSecond();
    int binaryCounter=0;
    for(String type: entityEntry.types) {
      for(Formula binary: fbFormulasInfo.getBinariesForType2(type)) {

        BinaryFormulaInfo bInfo = fbFormulasInfo.getBinaryInfo(binary);
        EntityInfo eInfo = new EntityInfo(entityEntry.fbDescriptions.iterator().next(),entityEntry.formula, entityEntry.popularity,spanAndEntryPair.getFirst());
        //hack to have less things
        if(toFilter(bInfo,entityEntry))
          continue;
        binaryCounter++;

        if(opts.verbose>=3) {
          LogInfo.logs("FormulaRetriver.retrieveLexiconFormulas: " +
              "text=%s, entity=%s, entityDesc=%s, entity popularity=%s, binary=%s, binary popularity=%s",
              entityEntry.textDescription,entityEntry.formula,entityEntry.fbDescriptions,entityEntry.popularity,
              binary,bInfo.popularity);
        }
        FormulaGenerationInfo fgInfo = new FormulaGenerationInfo(bInfo,null,eInfo,null, null, isCount, false,false);
        res.add(fgInfo);
        //now try to inject also
        if(opts.createInjections) {
          injectBinaries(lInfo,res,fgInfo,entities);
        }
      }
    }
    LogInfo.logs("Number of binaries for %s=%s", entityEntry.formula,binaryCounter);
  }

  private List<FormulaGenerationInfo> createUnaries(LanguageInfo lInfo, FormulaGenerationInfo inFgInfo) {

    List<FormulaGenerationInfo> res = new ArrayList<FormulaGenerationInfo>();
    Set<String> subtypes = fbFormulasInfo.getSubtypesExclusive(inFgInfo.bInfo.expectedType1);
    for(String subtype: subtypes) {
      if(badDomain(subtype))
        continue;
      Formula type1Formula = new JoinFormula(FreebaseInfo.TYPE, new ValueFormula<Value>(new NameValue(subtype)));
      UnaryFormulaInfo uInfo =  fbFormulasInfo.getUnaryInfo(type1Formula);
      if(uInfo!=null) {
        for(String description: uInfo.descriptions) {
          if(validDescription(description)) {
            List<String> descriptionTokens = Arrays.asList(description.split("\\s+"));
            IntPair unarySpan = getUnarySpan(lInfo); //where should we match the description
            if(ParaphraseUtils.matchLists(lInfo.tokens.subList(unarySpan.first, unarySpan.second), descriptionTokens) ||
                ParaphraseUtils.matchLists(lInfo.lemmaTokens.subList(unarySpan.first, unarySpan.second), descriptionTokens)) {
              FormulaGenerationInfo fInfo = 
                  new FormulaGenerationInfo(inFgInfo.bInfo, inFgInfo.injectedInfo, inFgInfo.entityInfo1, inFgInfo.entityInfo2, uInfo,
                      inFgInfo.isCount,inFgInfo.isInject,true);
              res.add(fInfo);
              if(opts.verbose>=3)
                fInfo.log();
              break;
            }
          }
        }
      }
    }
    return res;
  }

  private IntPair getUnarySpan(LanguageInfo languageInfo) {
    if(!(languageInfo.lemmaTokens.get(0).equals("what") || languageInfo.lemmaTokens.get(0).equals("which")))
      return new IntPair();
    int start=1, end=1;
    for(; end < languageInfo.numTokens(); ++end) {
      if(languageInfo.posTags.get(end).startsWith("V"))
        break;
    }
    return new IntPair(start,end);
  }

  private boolean validDescription(String description) {
    if(description.equals("do") || description.equals("be") || description.equals("have"))
      return false;
    return true;
  }

  private void injectBinaries(LanguageInfo lInfo, List<FormulaGenerationInfo> res, FormulaGenerationInfo fgInfo,
      List<Pair<IntPair, EntityLexicalEntry>> entities) throws ParseException, IOException {

    if(!(fgInfo.bInfo.formula instanceof LambdaFormula)) return;
    //1. Find the binaries that can be injected
    List<Formula> injections = fbFormulasInfo.getInjectableBinaries(fgInfo.bInfo.formula);
    //2. For each one find the type2 and try to find entities
    LogInfo.begin_track("Injecting %s injections to binary %s",injections.size(),fgInfo.bInfo.formula);
    for(Formula injection: injections) {
      BinaryFormulaInfo injectionInfo = fbFormulasInfo.getBinaryInfo(injection);
      List<EntityInfo> injectedEntities = findInjectedEntities(lInfo,fgInfo.entityInfo1.span,injectionInfo.expectedType2,entities);
      for(EntityInfo injectedEntity: injectedEntities) {
        res.add(new FormulaGenerationInfo(fgInfo.bInfo, injectionInfo, fgInfo.entityInfo1, injectedEntity, fgInfo.uInfo, fgInfo.isCount, true, fgInfo.isUnary));
      }
    }
    LogInfo.end_track();
  }

  //hacky method
  private List<EntityInfo> findInjectedEntities(LanguageInfo lInfo,
      IntPair excludedSpan, String exType, List<Pair<IntPair, EntityLexicalEntry>> exampleEntityEntries) throws ParseException, IOException {

    List<EntityInfo> res = new ArrayList<FormulaRetriever.EntityInfo>();
    if(exType.equals(FreebaseInfo.DATE)) {
      findInjectedTimeEntities(lInfo, excludedSpan, res);
    }
    else {
      if(badDomain(exType)) return res;
      if(opts.conservativeEntityExtraction) { //TODO try and simplify this block
        Set<IntPair> maximalNonOverlappingSpans = ParaphraseUtils.getMaxNonOverlappingSpans(lInfo.getNamedEntitiesAndProperNouns()); //cache the NEs to save time
        for(IntPair entitySpan: maximalNonOverlappingSpans) {

          if(ParaphraseUtils.intervalIntersect(entitySpan, excludedSpan) ||
              lInfo.nerTags.get(entitySpan.first).equals("DATE")) 
            continue;

          String entityTokens = lInfo.phrase(entitySpan.first, entitySpan.second);
          String entityLemmas = lInfo.lemmaPhrase(entitySpan.first, entitySpan.second);
          List<EntityLexicalEntry> entries = getEntityEntries(entityTokens);

          for(EntityLexicalEntry entry: entries) {
            if(!entry.types.contains(exType))
              continue;
            String entryDesc = entry.fbDescriptions.iterator().next();
            if(!entryDesc.equals(entityTokens) && 
                !entryDesc.equals(entityLemmas))
              continue;
            res.add(new EntityInfo(entryDesc, entry.formula, entry.popularity,entitySpan));
            if(opts.verbose>=3)
              LogInfo.logs("FormulaRetriver.findInjectedEntities: Adding injected entity=%s, description=%s, extype=%s",entry.formula,entryDesc,exType);
          }
        }
      }
      else {
        for(Pair<IntPair,EntityLexicalEntry> spanAndEntry: exampleEntityEntries) {
          if(ParaphraseUtils.intervalIntersect(spanAndEntry.getFirst(), excludedSpan))
            continue;
          EntityLexicalEntry entry = spanAndEntry.getSecond();
          if(spanAndEntry.getSecond().types.contains(exType)) {
            String entryDesc = entry.fbDescriptions.iterator().next();
            res.add(new EntityInfo(entryDesc, entry.formula, entry.popularity,spanAndEntry.getFirst()));
          }
        }
      }
    }
    return res;
  }

  private void findInjectedTimeEntities(LanguageInfo lInfo, IntPair excludedSpan,
      List<EntityInfo> res) {
    for(int i = 0; i < lInfo.tokens.size(); i++) {
      if(lInfo.isNumberAndDate(i)) {
        IntPair span = new IntPair(i,i+1);
        if(ParaphraseUtils.intervalIntersect(span, excludedSpan))
          continue;
        String token = lInfo.tokens.get(i);
        if(DateValue.parseDateValue(token).year!=-1) {
          EntityInfo entityInfo = new EntityInfo(token, new ValueFormula<DateValue>(DateValue.parseDateValue(token)),0,span);
          res.add(entityInfo);
          if(opts.verbose>=3)
            LogInfo.logs("FormulaRetiever.findInjectedEntities: Adding injected time entity=%s",entityInfo);
        }
      }
    }
  }

  private boolean toFilter(BinaryFormulaInfo bInfo,
      EntityLexicalEntry entityEntry) {

    String binaryDesc = bInfo.formula.toString();
    String expectedType1 = bInfo.expectedType1;
    if(removeEquivalents && fbFormulasInfo.hasOpposite(bInfo.formula)) { //we generate the equivalences in QuestionGenerator
      if(!fbFormulasInfo.isReversed(bInfo.formula))
        return true;
    }
    if(badDomain(binaryDesc) || fbFormulasInfo.isCvt(expectedType1))
      return true;
    if(opts.filterRelations && ParaphraseUtils.isInteger(entityEntry.textDescription))
      return true;
    if(opts.filterRelations && FreebaseInfo.isPrimitive(expectedType1))
      return true;
    return false;
  }

  private boolean badDomain(String str) {
    if(str.contains("fb:common.topic.alias"))
      return false;
    if(opts.filterRelations) {
      return str.contains("fb:user.") || str.contains("fb:base.") || str.contains("fb:dataworld.") ||
          str.contains("fb:type.") || str.contains("fb:common.") || str.contains("fb:freebase.");
    }
    else {
      return str.contains("fb:user.") || str.contains("fb:common.");
    }
  }  

  private boolean isCountUtterance(String utterance) {
    if(!opts.supportCountUtterances)
      return false;
    return utterance.startsWith("how many") || 
        utterance.startsWith("how much") || 
        utterance.startsWith("number of") ||
        utterance.startsWith("what is the number of");
  }

  private List<Pair<IntPair,EntityLexicalEntry>> getLexiconEntities(LanguageInfo lInfo) throws ParseException, IOException {

    List<Pair<IntPair,EntityLexicalEntry>> res = new ArrayList<Pair<IntPair,EntityLexicalEntry>>();
    LogInfo.begin_track("Retrieving entities");
    if(opts.conservativeEntityExtraction) { //for webquestions
      conservativeEntityExtraction(lInfo, res); 
    }
    else {
      allSpansEntityExtraction(lInfo, res); //for free917
    }
    LogInfo.logs("number of entity entries=%s",res.size());
    LogInfo.end_track();
    return res;
  }

  /*
   * Go over all spans and get lexical entries
   */
  private void allSpansEntityExtraction(LanguageInfo lInfo,
      List<Pair<IntPair, EntityLexicalEntry>> res) throws ParseException,
      IOException {
    for(int i = 0; i <= lInfo.tokens.size()-1; i++) {
      for(int j = i+1; j <= lInfo.tokens.size(); j++) {
        String entityDesc = lInfo.phrase(i, j);
        String entityLemmas = lInfo.lemmaPhrase(i, j);
        if(opts.verbose>=3)
          LogInfo.logs("Retrieving: entry=%s",entityDesc);
        List<EntityLexicalEntry> entries = getEntityEntries(entityDesc);
        if(entries.isEmpty())
          entries = getEntityEntries(entityLemmas);
        for(EntityLexicalEntry entry: entries) {
          res.add(Pair.newPair(new IntPair(i, j), entry));
        }
      }
    }
  }

  /**
   * Generates entities conservatively, using 4 rules of backoff
   * @param ex
   * @param res
   * @param excludedSpans
   * @throws ParseException
   * @throws IOException
   */
  private void conservativeEntityExtraction(LanguageInfo lInfo, List<Pair<IntPair,EntityLexicalEntry>> res)
      throws ParseException, IOException {

    Set<IntPair> allEntitySpans = lInfo.getNamedEntitiesAndProperNouns();
    Set<IntPair> maximalNonOverlappingSpans = ParaphraseUtils.getMaxNonOverlappingSpans(allEntitySpans);
    //first try to get exact match for maximal spans of named entities
    for(IntPair maximalEntitySpan: maximalNonOverlappingSpans) {
      String entityDesc = lInfo.phrase(maximalEntitySpan.first, maximalEntitySpan.second);
      List<EntityLexicalEntry> entries = getEntityEntries(entityDesc);
      for(EntityLexicalEntry entry: entries)
        res.add(Pair.newPair(maximalEntitySpan, entry));
    }
    //then try to get exact match for named entities (not maximal span)
    if(res.isEmpty()) {
      for(IntPair entitySpan: allEntitySpans) {
        String entityDesc = lInfo.phrase(entitySpan.first, entitySpan.second);
        List<EntityLexicalEntry> entries = getEntityEntries(entityDesc);
        for(EntityLexicalEntry entry: entries)
          res.add(Pair.newPair(entitySpan, entry));
      }
    }
    //if can't just go over NNPs and NE
    if(res.isEmpty()) {
      for(int i = 0; i < lInfo.numTokens(); i++) {
        if(LanguageUtils.isEntity(lInfo, i)) {
          List<EntityLexicalEntry> entries = getEntityEntries(lInfo.tokens.get(i));
          for(EntityLexicalEntry entry: entries)
            res.add(Pair.newPair(new IntPair(i,i+1), entry));
        }
      }
    }
    //if can't try all content words
    if(res.isEmpty()) {
      for(int i = 0; i < lInfo.numTokens(); i++) {
        if(LanguageUtils.isContentWord(lInfo.getCanonicalPos(i))) {
          List<EntityLexicalEntry> entries = getEntityEntries(lInfo.tokens.get(i));
          for(EntityLexicalEntry entry: entries)
            res.add(Pair.newPair(new IntPair(i,i+1), entry));
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private List<EntityLexicalEntry> getEntityEntries(String phrase)
      throws IOException, ParseException {
    List<EntityLexicalEntry> res = (List<EntityLexicalEntry>)lexicon.lookupEntities(phrase, Lexicon.opts.entitySearchStrategy);
    return res.subList(0, Math.min(res.size(), opts.maxEntries)); //we do filtering here and not at lexicon so cache does not change
  }

  /**
   * Minimal information necessary for generating formula and extarcting features
   * @author jonathanberant
   */
  public class EntityInfo {
    public final IntPair span;
    public final String desc;
    public final Formula entity;
    public final double popularity;

    public EntityInfo(String description, Formula entity, double popularity, IntPair span) {
      this.desc = description;
      this.entity = entity;
      this.popularity = popularity;
      this.span = span;
    }

    public String toString() {
      return Joiner.on('\t').join(desc,entity,popularity);
    }
  }
}
