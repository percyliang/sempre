package edu.stanford.nlp.sempre.freebase;

import com.google.common.base.Joiner;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.sempre.*;
import fig.basic.LispTree;
import fig.basic.LogInfo;
import fig.basic.MapUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

/**
 * Class for keeping info and manipulating FB formulas. For example, given a
 * Freebase formula computes the reverse of the formula (flipping arguments) and
 * the equivalent formula (using reverse property from Freebase) Reversing works
 * now only for chains
 *
 * @author jonathanberant
 */
public final class FbFormulasInfo {
  // Everyone should use the singleton.
  private static FbFormulasInfo fbFormulaInfo;

  public static FbFormulasInfo getSingleton() {
    if (fbFormulaInfo == null) {
      fbFormulaInfo = new FbFormulasInfo();
    }
    return fbFormulaInfo;
  }

  private FreebaseInfo freebaseInfo = null;
  public Map<Formula, BinaryFormulaInfo> binaryFormulaInfoMap = new HashMap<>();
  public Map<Formula, UnaryFormulaInfo> unaryFormulaInfoMap = new HashMap<>();
  private Map<String, Set<BinaryFormulaInfo>> typeToNumericalPredicates = new HashMap<>();
  private Map<String, List<Formula>> atomicExtype2ToBinaryMap = new HashMap<>(); // contains map to all atomic properties
  private Map<String, List<Formula>> extype2ToNonCvtBinaryMap = new HashMap<>(); // contains map to all binaries for which extype 1 is not a CVT
  private Map<Formula, Set<BinaryFormulaInfo>> cvtExpansionsMap = new HashMap<>();
  private Map<String, Set<Formula>> cvtTypeToBinaries = new HashMap<>();

  private Comparator<Formula> formulaComparator;

  private FbFormulasInfo() {
    try {
      freebaseInfo = FreebaseInfo.getSingleton();
      loadFormulaInfo();
      computeNumericalPredicatesMap();
    } catch (IOException | NumberFormatException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Map that given a type provides all Freebase predicates that have that type
   * as expected type 2 and a number for expected type 1
   */
  private void computeNumericalPredicatesMap() {
    for (BinaryFormulaInfo info : binaryFormulaInfoMap.values()) {
      if (info.expectedType1.equals("fb:type.int") || info.expectedType1.equals("fb:type.float")) {
        MapUtils.addToSet(typeToNumericalPredicates, info.expectedType2, info);
      }
    }
  }

  public Set<BinaryFormulaInfo> getNumericalPredicates(String expectedType) {
    return MapUtils.get(typeToNumericalPredicates, expectedType, new HashSet<BinaryFormulaInfo>());
  }

  private void computeReverseFormulaInfo() {
    List<BinaryFormulaInfo> entriesToAdd = new LinkedList<>();
    for (Formula formula : binaryFormulaInfoMap.keySet()) {
      BinaryFormulaInfo info = binaryFormulaInfoMap.get(formula);
      Formula reverseFormula = Formulas.reverseFormula(formula);

      if (!binaryFormulaInfoMap.containsKey(reverseFormula)) {
        entriesToAdd.add(
                new BinaryFormulaInfo(
                        reverseFormula,
                        info.expectedType2, info.expectedType1, info.unitId, info.unitDesc, info.descriptions, info.popularity));
      }
    }
    LogInfo.log("Adding reverse formulas: " + entriesToAdd.size());
    for (BinaryFormulaInfo e : entriesToAdd) {
      binaryFormulaInfoMap.put(e.formula, e);
    }
  }

  public BinaryFormulaInfo getBinaryInfo(Formula formula) {
    return binaryFormulaInfoMap.get(formula);
  }

  public UnaryFormulaInfo getUnaryInfo(Formula formula) {
    return unaryFormulaInfoMap.get(formula);
  }

  private void loadFormulaInfo() throws IOException {

    LogInfo.begin_track("Loading formula info...");
    LogInfo.log("Adding schema properties");
    binaryFormulaInfoMap = freebaseInfo.createBinaryFormulaInfoMap();
    unaryFormulaInfoMap = freebaseInfo.createUnaryFormulaInfoMap();
    LogInfo.log("Current number of binary formulas: " + binaryFormulaInfoMap.size());
    LogInfo.log("Current number of unary formulas: " + unaryFormulaInfoMap.size());

    LogInfo.log("Compuing reverse for schema formulas");
    computeReverseFormulaInfo();
    LogInfo.log("Current number of binary formulas: " + binaryFormulaInfoMap.size());
    for (BinaryFormulaInfo info : binaryFormulaInfoMap.values()) {

      MapUtils.addToList(atomicExtype2ToBinaryMap, info.expectedType2, info.formula);
      if (!isCvt(info.expectedType1)) {
        addMappingFromType2ToFormula(info.expectedType2, info.formula);
      }
    }

    LogInfo.log("Generate formulas through CVTs");
    generateCvtFormulas(); // generate formulas for CVTs
    LogInfo.log("Current number of binary formulas: " + binaryFormulaInfoMap.size());
    // we first sort by popularity
    Comparator<Formula> comparator = getPopularityComparator();
    sortType2ToBinaryMaps(comparator);
    LogInfo.end_track();
  }

  public void sortType2ToBinaryMaps(Comparator<Formula> comparator) {
    this.formulaComparator = comparator;
    for (List<Formula> binaries: atomicExtype2ToBinaryMap.values())
      Collections.sort(binaries, comparator);

    for (List<Formula> binaries: extype2ToNonCvtBinaryMap.values())
      Collections.sort(binaries, comparator);

  }

  public int compare(Formula f1, Formula f2) {
    return formulaComparator.compare(f1, f2);
  }

  /**
   * Adding mapping from type 2 to formula - makes sure to insert just one of the 2 equivalent formulas if they exist
   */
  private void addMappingFromType2ToFormula(String type2, Formula formula) {
    MapUtils.addToList(extype2ToNonCvtBinaryMap, type2, formula);
  }

  private void generateCvtFormulas() throws FileNotFoundException {

    List<BinaryFormulaInfo> toAdd = new ArrayList<>();
    for (BinaryFormulaInfo innerInfo : binaryFormulaInfoMap.values()) {

      if (isCvt(innerInfo.expectedType1)) { // if expected type 1 is a CVT
        MapUtils.addToSet(cvtTypeToBinaries, innerInfo.expectedType1, innerInfo.formula);

        List<Formula> outers = atomicExtype2ToBinaryMap.get(innerInfo.expectedType1); // find those whose expected type 2 is that CVT
        for (Formula outer : outers) {
          BinaryFormulaInfo outerInfo = binaryFormulaInfoMap.get(outer);
          if (!isLegalCvt(innerInfo.formula, outer))
            continue;

          // build new formula
          LambdaFormula cvtFormula = new LambdaFormula("x", new JoinFormula(outer, new JoinFormula(innerInfo.formula, new VariableFormula("x"))));

          BinaryFormulaInfo newFormulaInfo = binaryFormulaInfoMap.get(cvtFormula);
          if (newFormulaInfo == null) {
            String exType1 = outerInfo.expectedType1;
            if (exType1 == null)
              throw new RuntimeException("Missing expected type 1 for formula: " + outer);

            List<String> newDescriptions = new LinkedList<>();
            newDescriptions.add(outerInfo.descriptions.get(0));
            newDescriptions.add(innerInfo.descriptions.get(0));

            newFormulaInfo = new BinaryFormulaInfo(cvtFormula, exType1, innerInfo.expectedType2, newDescriptions, Math.min(outerInfo.popularity, innerInfo.popularity));
            toAdd.add(newFormulaInfo);
          }
          MapUtils.addToSet(cvtExpansionsMap, innerInfo.formula, newFormulaInfo);
          MapUtils.addToSet(cvtExpansionsMap, outerInfo.formula, newFormulaInfo);
        }
      }
    }
    for (BinaryFormulaInfo info : toAdd) {
      addMappingFromType2ToFormula(info.expectedType2, info.formula);
      binaryFormulaInfoMap.put(info.formula, info);
    }
  }

  private boolean isLegalCvt(Formula inner, Formula outer) {
    if (FreebaseInfo.isReverseProperty(inner.toString()) && !FreebaseInfo.isReverseProperty(outer.toString()))
      return false;
    if (!FreebaseInfo.isReverseProperty(inner.toString()) && FreebaseInfo.isReverseProperty(outer.toString()))
      return false;
    return true;
  }

  /** supports chains only */
  public boolean hasOpposite(String formula) { return hasOpposite(LispTree.proto.parseFromString(formula)); }
  public boolean hasOpposite(Formula formula) { return hasOpposite(formula.toLispTree()); }
  private boolean hasOpposite(LispTree tree) {
    if (tree.isLeaf()) {
      String fbProperty = FreebaseInfo.isReverseProperty(tree.value) ? tree.value.substring(1) : tree.value;
      return freebaseInfo.propertyHasOpposite(fbProperty);
    } else {
      // Un-reverse everything.
      String binary1 = tree.child(2).child(0).value;
      binary1 = FreebaseInfo.isReverseProperty(binary1) ? binary1.substring(1) : binary1;
      String binary2 = tree.child(2).child(1).child(0).value;
      binary2 = FreebaseInfo.isReverseProperty(binary2) ? binary2.substring(1) : binary2;
      return freebaseInfo.propertyHasOpposite(binary1) && freebaseInfo.propertyHasOpposite(binary2);
    }
  }

  /** supports chains only */
  public boolean isReversed(Formula formula) {
    LispTree tree = formula.toLispTree();
    if (tree.isLeaf())
      return FreebaseInfo.isReverseProperty(tree.value);
    else
      return FreebaseInfo.isReverseProperty(tree.child(2).child(0).value);
  }

  /** assumes we checked there is an opposite formula */
  public Formula equivalentFormula(String formula) {
    LispTree tree = LispTree.proto.parseFromString(formula);
    return equivalentFormula(tree);
  }

  public Formula equivalentFormula(Formula formula) {
    LispTree tree = formula.toLispTree();
    return equivalentFormula(tree);
  }

  // two formulas can be equivalent because there are two names for every edge using the reverse label
  //fb:people.person.place_of_birth --> !fb:location.location.people_born_here
  //!fb:people.person.place_of_birth --> fb:location.location.people_born_here
  public Formula equivalentFormula(LispTree tree) {

    if (tree.isLeaf()) {
      boolean rev = FreebaseInfo.isReverseProperty(tree.value);
      String fbProperty = rev ? tree.value.substring(1) : tree.value;
      String oppositeProperty = freebaseInfo.getOppositeFbProperty(fbProperty);
      return rev ? Formulas.newNameFormula(oppositeProperty) : Formulas.newNameFormula("!" + oppositeProperty);
    } else {
      String binary1 = tree.child(2).child(0).value;
      binary1 = FreebaseInfo.isReverseProperty(binary1) ? binary1.substring(1) : binary1;
      String binary2 = tree.child(2).child(1).child(0).value;
      binary2 = FreebaseInfo.isReverseProperty(binary2) ? binary2.substring(1) : binary2;
      String oppositeBinary1 = freebaseInfo.getOppositeFbProperty(binary1);
      String oppositeBinary2 = freebaseInfo.getOppositeFbProperty(binary2);
      boolean rev = FreebaseInfo.isReverseProperty(tree.child(2).child(0).value);
      return buildLambdaFormula(oppositeBinary1, oppositeBinary2, !rev);
    }
  }

  //input: |binary1|=fb:people.place_lived.location,
  // |binary2|=fb:people.person.places_lived, |reverse|=true
  //output: (lambda x (!fb:people.place_lived.location (!fb:people.person.places_lived (var x))))
  public static Formula buildLambdaFormula(String binary1, String binary2, boolean reverse) {

    Formula binary1Formula = reverse ? Formulas.newNameFormula("!" + binary1) : Formulas.newNameFormula(binary1);
    Formula binary2Formula = reverse ? Formulas.newNameFormula("!" + binary2) : Formulas.newNameFormula(binary2);
    Formula join1 = new JoinFormula(binary2Formula, new VariableFormula("x"));
    Formula join2 = new JoinFormula(binary1Formula, join1);
    return new LambdaFormula("x", join2);
  }



  //for binary formulas that are paths in the graph, if formula1 is a path s-->t
  //then formula2 is the opposite if it is the path t-->s
  // fb:people.person.place_of_birth is the opposite of !fb:people.person.place_of_birth
  // fb:people.person.place_of_birth is the opposite of fb:
  private boolean isOpposite(Formula formula1, Formula formula2) {

    if (isReversed(formula1) && !isReversed(formula2)) {
      String formula1Desc = formula1.toString().substring(1);
      return formula1Desc.equals(formula2.toString());
    }
    if (isReversed(formula2) && !isReversed(formula1)) {
      String formula2Desc = formula2.toString().substring(1);
      return formula2Desc.equals(formula1.toString());
    }
    if (hasOpposite(formula1)) {
      Formula equivalentFormula = equivalentFormula(formula1);
      if (isReversed(equivalentFormula)) {
        String equivalentFormaulDesc = equivalentFormula.toString().substring(1);
        return equivalentFormaulDesc.equals(formula2.toString());
      } else {
        String formula2Desc = formula2.toString().substring(1);
        return formula2Desc.equals(equivalentFormula.toString());
      }
    }
    return false;
  }

  public List<Formula> getBinariesForType2(String type) {
    return MapUtils.get(extype2ToNonCvtBinaryMap, type, new ArrayList<Formula>());
  }

  public List<Formula> getAtomicBinariesForType2(String type) {
    return MapUtils.get(atomicExtype2ToBinaryMap, type, new ArrayList<Formula>());
  }

  public boolean isCvtFormula(BinaryFormulaInfo info) {
    return isCvt(info.expectedType1) || isCvt(info.expectedType2);
  }

  public Set<BinaryFormulaInfo> getCvtExpansions(BinaryFormulaInfo info) {
    return MapUtils.getSet(cvtExpansionsMap, info.formula);
  }

  public Set<Formula> expandCvts(String cvt) {
    return MapUtils.getSet(cvtTypeToBinaries, cvt);
  }

  //For a binary lambda formula that goes through CVTs, find all binaries that can
  //be injected to this lambda binary formula.
  //example:
  //input: (lambda (fb:people.person.places_lived (fb:people.place_lived.location (var x))))
  //output: fb:people.place_lived/start_date, fb:people.place_lived.end_date
  public List<Formula> getInjectableBinaries(Formula formula) {
    List<Formula> res = new ArrayList<>();
    if (!(formula instanceof LambdaFormula)) return res;
    LambdaFormula lambdaFormula = (LambdaFormula) formula;
    Formula first = ((JoinFormula) lambdaFormula.body).relation;
    Formula second = ((JoinFormula) ((JoinFormula) lambdaFormula.body).child).relation;
    Set<Formula> binaryFormulas = expandCvts(getBinaryInfo(first).expectedType2);

    for (Formula binaryFormula : binaryFormulas) {
      if (!second.equals(binaryFormula) && !isOpposite(first, binaryFormula)) {
        res.add(binaryFormula);
      }
    }
    return res;
  }

  public boolean isCvt(String type) {
    return freebaseInfo.isCvt(type);
  }

  public Comparator<Formula> getPopularityComparator() {
    Counter<Formula> counter = new ClassicCounter<>();
    for (Formula binaryFormula : binaryFormulaInfoMap.keySet())
      counter.incrementCount(binaryFormula, binaryFormulaInfoMap.get(binaryFormula).popularity);

    return new FormulaByCounterComparator(counter);
  }

  public class FormulaByCounterComparator implements Comparator<Formula> {

    private Counter<Formula> fCounter;

    public FormulaByCounterComparator(Counter<Formula> fCounter) {
      this.fCounter = fCounter;
    }
    public int compare(Formula f1, Formula f2) {
      double count1 = fCounter.getCount(f1);
      double count2 = fCounter.getCount(f2);
      if (count1 > count2) return -1;
      if (count1 < count2) return +1;
      double pop1 = binaryFormulaInfoMap.get(f1).popularity;
      double pop2 = binaryFormulaInfoMap.get(f2).popularity;
      if (pop1 > pop2) return -1;
      if (pop1 < pop2) return +1;
      return 0;
    }
    public double getCount(Formula f) { return fCounter.getCount(f); }
  }

  public class FormulaByFeaturesComparator implements Comparator<Formula> {

    private Params params;

    public FormulaByFeaturesComparator(Params params) {
      this.params = params;
    }
    public int compare(Formula f1, Formula f2) {

      FeatureVector features1 = BridgeFn.getBinaryBridgeFeatures(fbFormulaInfo.getBinaryInfo(f1));
      FeatureVector features2 = BridgeFn.getBinaryBridgeFeatures(fbFormulaInfo.getBinaryInfo(f2));

      double score1 = features1.dotProduct(params);
      double score2 = features2.dotProduct(params);
      if (score1 > score2) return -1;
      if (score1 < score2) return +1;
      double pop1 = binaryFormulaInfoMap.get(f1).popularity;
      double pop2 = binaryFormulaInfoMap.get(f2).popularity;
      if (pop1 > pop2) return -1;
      if (pop1 < pop2) return +1;
      return 0;
    }
  }

  //Information from freebase about binary formulas
  public static class BinaryFormulaInfo {
    public Formula formula; //fb:people.person.place_of_birth
    public String expectedType1; //fb:people.person
    public String expectedType2; //fb:location.location
    public String unitId = ""; //fb:en.meter
    public String unitDesc = ""; //Meter
    public List<String> descriptions = new LinkedList<>(); // "place of birth"
    public double popularity; //Number of instances of binary in KB: 16184.0

    public BinaryFormulaInfo(Formula formula, String exType1, String exType2, List<String> descs, double popularity) {
      this.formula = formula;
      this.expectedType1 = exType1;
      this.expectedType2 = exType2;
      this.descriptions = descs;
      this.popularity = popularity;
      this.unitId = "";
      this.unitDesc = "";
    }
    public BinaryFormulaInfo(Formula formula, String exType1, String exType2, String unitId, String unitDesc, List<String> descs, double popularity) {
      this.formula = formula;
      this.expectedType1 = exType1;
      this.expectedType2 = exType2;
      this.descriptions = descs;
      this.popularity = popularity;
      this.unitId = "";
      this.unitDesc = "";
    }
    public String toString() {
      return formula.toString() + "\t" + popularity + "\t" + expectedType1 + "\t" + expectedType2 + "\t" + unitId + "\t"
              + unitDesc + "\t" + Joiner.on("###").join(descriptions);
    }
    public String toReverseString() {
      return Formulas.reverseFormula(formula).toString() + "\t" + popularity + "\t" + expectedType2 + "\t" + expectedType1 + "\t" + unitId + "\t"
              + unitDesc + "\t" + Joiner.on("###").join(descriptions);
    }

    public static List<String> tokenizeFbDescription(String fbDesc) {
      List<String> res = new ArrayList<>();
      String[] tokens = fbDesc.split("\\s+");
      for (String token : tokens) {
        token = token.replace("(", "");
        token = token.replace(")", "");
        token = token.replace("\"", "");
        res.add(token);
      }
      return res;
    }

    public boolean isComplete() {
      if (formula == null || expectedType1 == null || expectedType2 == null ||
              expectedType1.equals("") || expectedType2.equals("") || descriptions == null ||
              descriptions.size() == 0 ||
              popularity == 0.0)
        return false;
      return true;
    }

    public SemType getSemType() {
      return SemType.newFuncSemType(expectedType2, expectedType1);
    }

    public String extractDomain(Formula binary) {
      LispTree tree = binary.toLispTree();
      String property = tree.isLeaf() ? tree.value : tree.child(2).child(0).value;
      if (property.startsWith("!"))
        property = property.substring(1);
      return property.substring(0, property.indexOf('.'));
    }
  }

  public static class UnaryFormulaInfo {

    public Formula formula;
    public double popularity;
    public List<String> descriptions;
    public Set<String> types;

    public UnaryFormulaInfo(Formula formula, double popularity,
                            List<String> descriptions, Set<String> types) {

      this.formula = formula;
      this.popularity = popularity;
      this.descriptions = descriptions;
      this.types = types;
    }

    public boolean isComplete() {
      if (formula == null || descriptions == null || descriptions.size() == 0 ||
              popularity == 0.0)
        return false;
      return true;
    }

    public String toString() {
      return formula + "\t" + popularity + "\t" + Joiner.on("###").join(descriptions);
    }

    public String getRepresentativeDescrption() {
      if (descriptions.get(0).contains("/") && descriptions.size() > 1)
        return descriptions.get(1);
      return descriptions.get(0);
    }
  }
}
