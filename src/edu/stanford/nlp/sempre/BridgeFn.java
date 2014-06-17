package edu.stanford.nlp.sempre;

import edu.stanford.nlp.sempre.FbFormulasInfo.BinaryFormulaInfo;
import edu.stanford.nlp.sempre.MergeFormula.Mode;
import fig.basic.LispTree;
import fig.basic.LogInfo;
import fig.basic.Option;
import fig.basic.IOUtils;

import java.io.IOException;
import java.util.*;

/**
 * Bridge between two derivations by type-raising one of them.
 *
 * @author jonathanberant
 */
public class BridgeFn extends SemanticFn {

  private static final Formula intFormula = Formulas.fromLispTree(LispTree.proto.parseFromString("(fb:type.object.type fb:type.int)"));
  private static final Formula floatFormula = Formulas.fromLispTree(LispTree.proto.parseFromString("(fb:type.object.type fb:type.float)"));

  public static class Options {
    @Option(gloss = "Verbose") public int verbose = 0;
    @Option(gloss = "Whether to allow entity bridging with no binary string match") public boolean looseEntBridge = false;
    @Option(gloss = "List of binary formulas to use during bridging") public String binariesFile = "";
  }

  public static Options opts = new Options();

  private FbFormulasInfo fbFormulaInfo = null;
  private String description;
  private boolean headFirst;
  private TextToTextMatcher textToTextMatcher;
  private static HashSet<Formula> binariesToUse;

  public void init(LispTree tree) {
    super.init(tree);
    if (tree.children.size() != 3)
      throw new RuntimeException("Number of children is: " + tree.children.size());
    if (!tree.child(2).value.equals("headFirst") && !tree.child(2).value.equals("headLast"))
      throw new RuntimeException("Bad argument for head position: " + tree.child(2).value);
    if (!tree.child(1).value.equals("unary") && !tree.child(1).value.equals("inject") && !tree.child(1).value.equals("entity"))
      throw new RuntimeException("Bad description: " + tree.child(1).value);

    this.description = tree.child(1).value;
    headFirst = tree.child(2).value.equals("headFirst");
  }

  public BridgeFn() {
    fbFormulaInfo = FbFormulasInfo.getSingleton();
    textToTextMatcher = TextToTextMatcher.getSingleton();
    binariesToUse = new HashSet<Formula>();
    if (!opts.binariesFile.equals("")) {
      readBinaries();
    }
  }

  public static void readBinaries() {
    for (String line : IOUtils.readLinesHard(opts.binariesFile)) {
      if (line.startsWith("#")) continue;
      if (line.equals("")) continue;
      binariesToUse.add(Formula.fromString(line));
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BridgeFn bridgeFn = (BridgeFn) o;
    if (headFirst != bridgeFn.headFirst) return false;
    if (!description.equals(bridgeFn.description)) return false;
    return true;
  }

  @Override
  public List<Derivation> call(Example ex, Callable c) {
    try {
      if (description.equals("unary")) {
        return bridgeUnary(ex, c);
      } else if (description.equals("inject")) {
        return injectIntoCvt(ex, c);
      } else if (description.equals("entity")) {
        return bridgeEntity(ex, c);
      } else {
        throw new RuntimeException("Invalid (expected unary, inject, or entity): " + description);
      }
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  private boolean isCvt(Derivation headDeriv) {
    if (!(headDeriv.formula instanceof JoinFormula))
      return false;
    JoinFormula join = (JoinFormula) headDeriv.formula;
    if (join.relation instanceof LambdaFormula)
      return true;
    if (join.child instanceof JoinFormula || join.child instanceof MergeFormula)
      return true;
    return false;
  }

  // Return all the entity supertypes of |type|.
  // TODO: make this more efficient.
  private Set<String> getSupertypes(SemType type, Set<String> supertypes) {
    if (type instanceof EntitySemType)
      supertypes.addAll(fbFormulaInfo.getIncludedTypesInclusive(((EntitySemType) type).name));
    else if (type instanceof UnionSemType)
      for (SemType baseType : ((UnionSemType) type).baseTypes)
        getSupertypes(baseType, supertypes);
    else {
      // FIXME HACK for when passing binary into lambda formula and
      // getSuperTypes doesn't work
      getSupertypes(SemType.fromString("topic"), supertypes);
      //throw new RuntimeException("Unexpected type (must be unary): " + type);
    }
    return supertypes;
  }

  private List<Derivation> bridgeUnary(Example ex, Callable c) throws IOException {

    List<Derivation> res = new ArrayList<Derivation>();

    // Example: modifier[Hanks] head[movies]
    Derivation headDeriv = headFirst ? c.child(0) : c.child(1);
    Derivation modifierDeriv = !headFirst ? c.child(0) : c.child(1);

    Set<String> headTypes = getSupertypes(headDeriv.type, new HashSet<String>());
    Set<String> modifierTypes = getSupertypes(modifierDeriv.type, new HashSet<String>());

    HashSet<String> usedBinaries = new HashSet<String>();

    for (String modifierType : modifierTypes) { // For each head type...
      Set<Formula> binaries;
      if (binariesToUse.size() == 0)
        binaries = fbFormulaInfo.getBinariesForType2(modifierType);
      else
        binaries = binariesToUse;

      for (Formula binary : binaries) {  // For each possible binary...

        if (usedBinaries.contains(binary.toString()))
          continue;

        BinaryFormulaInfo binaryInfo = fbFormulaInfo.getBinaryInfo(binary);

        if (opts.verbose >= 3)
          LogInfo.logs("%s => %s", modifierType, binary);

        if (headTypes.contains(binaryInfo.expectedType1)) {

          Formula bridgedFormula = buildBridge(headDeriv.formula, modifierDeriv.formula, binary);
          FbFormulasInfo.touchBinaryFormula(binary);
          usedBinaries.add(binary.toString());

          Derivation newDeriv = new Derivation.Builder()
              .withCallable(c)
              .formula(bridgedFormula)
              .type(headDeriv.type)
              .createDerivation();

          if (SemanticFn.opts.trackLocalChoices) {
            newDeriv.localChoices.add(
                String.format(
                    "BridgeFn: %s %s --> %s %s --> %s %s",
                    headDeriv.startEndString(ex.getTokens()), headDeriv.formula,
                    ex.getTokens().subList(c.child(0).end, c.child(1).start),
                    binary,
                    modifierDeriv.startEndString(ex.getTokens()), modifierDeriv.formula));
          }

          // Add features
          if (ex != null) {
            newDeriv.addFeature("BridgeFn", "unary");

            // Popularity of the binary
            newDeriv.addFeatureWithBias("BridgeFn", "popularity", Math.log(binaryInfo.popularity + 1));

            // head modifier POS tags
            String headModifierOrder = headFirst ? "head-modifier" : "modifier-head";
            newDeriv.addFeature("BridgeFn",
                "order=" + headModifierOrder + "," +
                "pos=" +
                ex.languageInfo.getCanonicalPos(headDeriv.start) + "-" +
                ex.languageInfo.getCanonicalPos(modifierDeriv.start));

            addBinaryMatchFeatures(ex, modifierDeriv, binary, newDeriv); //HACKY
            List<List<String>> exampleInfo = generateExampleInfo(ex, c); //this is not done in text to text matcher so done here

            FeatureVector vector = textToTextMatcher.extractFeatures(
                exampleInfo.get(0), exampleInfo.get(1), exampleInfo.get(2),
                new HashSet<String>(binaryInfo.descriptions));
            newDeriv.addFeatures(vector);
          }
          res.add(newDeriv);
        }
      }
    }
    return res;
  }

  //see whether the binary bridge can also be retrieved with the alignment lexicon
  private void addBinaryMatchFeatures(Example ex, Derivation modifierDeriv, Formula binary, Derivation newDeriv) throws IOException {
    if (!FeatureExtractor.containsDomain("bridgeBinaryMatch")) return;

    for (int i = 0; i < ex.languageInfo.lemmaTokens.size(); ++i) {
      if (i >= modifierDeriv.start && i < modifierDeriv.end)
        continue;
      String pos = ex.languageInfo.posTags.get(i);
      String lemma = ex.languageInfo.lemmaTokens.get(i);
      if (pos.startsWith("NN") || (pos.startsWith("VB") && !pos.equals("VBD-AUX")) || pos.equals("JJ") || pos.equals("IN")) {

        LexiconFn fn = new LexiconFn();
        fn.init(LispTree.proto.parseFromString("(LexiconFn binary)"));

        Derivation child = new Derivation.Builder()
            .cat("$CompositeRel").start(i).end(i + 1)
            .children(new ArrayList<Derivation>())
            .withStringFormulaFrom(lemma)
            .createDerivation();
        CallInfo c = new CallInfo("$Binary", i, i + 1, null, Collections.singletonList(child));
        List<Derivation> derivations = fn.call(ex, c);
        for (Derivation deriv : derivations) {
          if (deriv.formula.equals(binary) || deriv.formula.equals(FbFormulasInfo.reverseFormula(binary))) {
            if (opts.verbose >= 1) {
              LogInfo.logs("BridgeFn: lemma %s matched bridged binary %s", lemma, binary);
            }
            newDeriv.addFeatures(deriv);
            return;
          }
        }
      }
    }
  }

  //bridge without a unary - simply by looking at binaries leading to the entity and string matching binary description to example tokens/lemmas/stems
  private List<Derivation> bridgeEntity(Example ex, Callable c) throws IOException {

    List<Derivation> res = new ArrayList<Derivation>();
    Derivation modifierDeriv = c.child(0);

    Set<String> modifierTypes = getSupertypes(modifierDeriv.type, new HashSet<String>());

    for (String modifierType : modifierTypes) { // For each head type...
      Set<Formula> binaries = fbFormulaInfo.getBinariesForType2(modifierType);
      for (Formula binary : binaries) {  // For each possible binary...
        BinaryFormulaInfo binaryInfo = fbFormulaInfo.getBinaryInfo(binary);

        if (opts.verbose >= 3)
          LogInfo.logs("%s => %s", modifierType, binary);

        List<List<String>> exampleInfo = generateExampleInfo(ex, c); //this is not done in text to text matcher so done here
        if (textToTextMatcher.existsTokenMatch(exampleInfo.get(0), exampleInfo.get(2), new HashSet<String>(binaryInfo.descriptions))
            || opts.looseEntBridge) {
          Formula join = new JoinFormula(binary, modifierDeriv.formula);
          FbFormulasInfo.touchBinaryFormula(binary);          

          Derivation newDeriv = new Derivation.Builder()
              .withCallable(c)
              .formula(join)
              .type(new EntitySemType(binaryInfo.expectedType1))
              .createDerivation();
          if (opts.verbose >= 2)
            LogInfo.logs("BridgeStringFn: %s", join);

          //features
          if (ex != null) {
            newDeriv.addFeature("BridgeFn", "entity");
            newDeriv.addFeatureWithBias("BridgeFn", "popularity", Math.log(binaryInfo.popularity + 1));

            addBinaryMatchFeatures(ex, modifierDeriv, binary, newDeriv); //HACKY
            FeatureVector textMatchFeatures = textToTextMatcher.extractFeatures(
                exampleInfo.get(0), exampleInfo.get(1), exampleInfo.get(2),
                new HashSet<String>(binaryInfo.descriptions));
            newDeriv.addFeatures(textMatchFeatures);

            //addTokenMatchFeatures(tokenStemFeatures.first(), newDeriv, "binary_token");
            //addTokenMatchFeatures(tokenStemFeatures.second(), newDeriv, "binary_stem");           
            //addWordSimilarityFeatures(ex, newDeriv, binaryInfo); // (1) edit distance (2) word similarity 
          }
          //newDeriv.localFeatureVector.add("bridge_lex_"+binaryInfo.expectedType1+"-->"+binary); //causes overfitting with 300 training examples
          res.add(newDeriv);
        }
      }
    }
    return res;
  }

  //generate from example array of content word tokens/lemmas/stems that are not dominated by child derivations
  private List<List<String>> generateExampleInfo(Example ex, Callable c) {

    List<String> tokens = new ArrayList<String>();
    List<String> posTags = new ArrayList<String>();
    List<String> lemmas = new ArrayList<String>();
    List<List<String>> res = new ArrayList<List<String>>();
    res.add(tokens);
    res.add(posTags);
    res.add(lemmas);

    Derivation modifierDeriv = headFirst ? c.child(1) : c.child(0);

    for (int i = 0; i < ex.languageInfo.tokens.size(); ++i) {
      if (i >= modifierDeriv.start && i < modifierDeriv.end) { //do not consider the modifier words {
        continue;
      }
      tokens.add(ex.languageInfo.tokens.get(i));
      posTags.add(ex.languageInfo.posTags.get(i));
      lemmas.add(ex.languageInfo.lemmaTokens.get(i));
    }
    return res;
  }

  private List<Derivation> injectIntoCvt(Example ex, Callable c) {
    List<Derivation> res = new ArrayList<Derivation>();

    // Example: modifier[Braveheart] head[Mel Gibson plays in]
    Derivation headDeriv = headFirst ? c.child(0) : c.child(1);
    if (!isCvt(headDeriv)) //only works on cvts
      return res;

    Derivation modifierDeriv = !headFirst ? c.child(0) : c.child(1);
    JoinFormula headFormula = (JoinFormula)Formulas.betaReduction(headDeriv.formula);
    //find the type of the cvt node
    Set<String> headTypes = Collections.singleton(fbFormulaInfo.getBinaryInfo(headFormula.relation).expectedType2);
    Set<String> modifierTypes = getSupertypes(modifierDeriv.type, new HashSet<String>());

    for (String modifierType : modifierTypes) {
      Set<Formula> binaries = fbFormulaInfo.getAtomicBinariesForType2(modifierType); //here we use atomic binaries since we inject into a CVT
      for (Formula binary : binaries) {  // For each possible binary...
        BinaryFormulaInfo info = fbFormulaInfo.getBinaryInfo(binary);

        if (headTypes.contains(info.expectedType1)) {
          Formula bridgedFormula = buildBridgeFromCvt(headFormula, modifierDeriv.formula, binary);
          FbFormulasInfo.touchBinaryFormula(binary);
          Derivation newDeriv = new Derivation.Builder()
              .withCallable(c)
              .formula(bridgedFormula)
              .type(headDeriv.type)
              .createDerivation();
          if (opts.verbose >= 3)
            LogInfo.logs("BridgeFn: injecting %s to %s --> %s ", modifierDeriv.formula, headFormula, bridgedFormula);

          if (ex != null) {
            String headModifierOrder = headFirst ? "head-modifier" : "modifier-head";
            newDeriv.addFeature("BridgeFn",
                "inject_order=" + headModifierOrder + "," + "pos=" +
                ex.languageInfo.getCanonicalPos(headDeriv.start) + "-" +
                ex.languageInfo.getCanonicalPos(modifierDeriv.start));
          }
          newDeriv.addFeature("BridgeFn", "binary=" + binary);

          res.add(newDeriv);
        }
      }
    }
    return res;
  }

  // Checks whether "var" is used as a binary in "formula"
  private boolean varIsBinary(Formula formula, String var) {
    boolean isBinary = false;
    LispTree tree = formula.toLispTree();
    VariableFormula vf = new VariableFormula(var);
    for (LispTree child : tree.children) {
      if (child.isLeaf())
        continue;
      if (child.children.size() == 2 && vf.equals(Formulas.fromLispTree(child.child(0)))) {
        isBinary = true;
        break;
      }
      if (varIsBinary(Formulas.fromLispTree(child), var)) {
        isBinary = true;
        break;
      }
    }
    return isBinary;
  }

  private Formula buildBridge(Formula headFormula, Formula modifierFormula, Formula binary) {
    // Handle cases like "what state has the most cities" where "has the" is mapped
    // to "contains" predicate via bridging but "most" triggers a nested lambda w/
    // argmax on a count relation
    // (Corresponds to $MetaMetaOperator in grammar)
    if (modifierFormula instanceof LambdaFormula) {
      LambdaFormula lf = (LambdaFormula) modifierFormula;
      if (varIsBinary(lf, lf.var)) {
        Formula newBinary = Formulas.lambdaApply(lf, binary);
        if (newBinary instanceof LambdaFormula) {
          Formula result = Formulas.lambdaApply((LambdaFormula) newBinary, headFormula);
          return result;
        }
      }
    }

    Formula join = new JoinFormula(binary, modifierFormula);
    Formula merge = new MergeFormula(Mode.and, headFormula, join);
    //Don't merge on ints and floats
    return (headFormula.equals(intFormula) || headFormula.equals(floatFormula)) ? join : merge;
  }

  private Formula buildBridgeFromCvt(JoinFormula headFormula, Formula modifierFormula, Formula binary) {
    Formula join = new JoinFormula(binary, modifierFormula);
    Formula merge = new MergeFormula(Mode.and, headFormula.child, join);
    return new JoinFormula(headFormula.relation, merge);
  }
}
