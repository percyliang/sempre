package edu.stanford.nlp.sempre;

import com.google.common.base.Joiner;
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
public class FbFormulasInfo {
  // Everyone should use the singleton.
  private static FbFormulasInfo fbFormulaInfo;
  public static FbFormulasInfo getSingleton() {
    if (fbFormulaInfo == null) fbFormulaInfo = new FbFormulasInfo();
    return fbFormulaInfo;
  }

  private static Set<Formula> binaryFormulasTouched = new HashSet<Formula>(); //all binary formulas touched during parsing in join and bridge

  private FreebaseInfo freebaseInfo=null;
  public Map<Formula, BinaryFormulaInfo> binaryFormulaInfoMap = new HashMap<Formula, BinaryFormulaInfo>();
  public Map<Formula, UnaryFormulaInfo> unaryFormulaInfoMap = new HashMap<Formula, UnaryFormulaInfo>();
  private Map<String, Set<BinaryFormulaInfo>> typeToNumericalPredicates = new HashMap<String, Set<BinaryFormulaInfo>>();
  private Map<String, Set<Formula>> atomicExtype2ToBinaryMap = new HashMap<String, Set<Formula>>(); //contains map to all atomic properties
  private Map<String, Set<Formula>> extype2ToNonCvtBinaryMap = new HashMap<String, Set<Formula>>(); //contains map to all binaries for which extype 1 is not a CVT
  private Map<Formula, Set<BinaryFormulaInfo>> cvtExpansionsMap = new HashMap<Formula, Set<BinaryFormulaInfo>>();
  private Map<String,Set<Formula>> cvtTypeToBinaries = new HashMap<String,Set<Formula>>();


  private FbFormulasInfo() {
    try {
      freebaseInfo = FreebaseInfo.getSingleton();
      loadFormulaInfo(); 
      computeNumericalPredicatesMap();
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (NumberFormatException e) {
      throw new RuntimeException(e);
    } 
  }

  public static void touchBinaryFormula(Formula binary) {
    binaryFormulasTouched.add(binary);
  }

  public static int numOfTouchedBinaries() {
    return binaryFormulasTouched.size();
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
    List<BinaryFormulaInfo> entriesToAdd = new LinkedList<FbFormulasInfo.BinaryFormulaInfo>();
    for (Formula formula : binaryFormulaInfoMap.keySet()) {
      BinaryFormulaInfo info = binaryFormulaInfoMap.get(formula);
      Formula reverseFormula = reverseFormula(formula);

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

  private void loadFormulaInfo() throws NumberFormatException, IOException {

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
      MapUtils.addToSet(atomicExtype2ToBinaryMap, info.expectedType2, info.formula);
      if(!isCvt(info.expectedType1)) {
        addMappingFromType2ToFormula(info.expectedType2, info.formula);
      }
    }

    LogInfo.log("Generate formulas through CVTs");
    generateCvtFormulas(); //generate formulas for CVTs
    LogInfo.log("Current number of binary formulas: " + binaryFormulaInfoMap.size());
    LogInfo.end_track();
  }

  /**
   * Adding mapping from type 2 to formula - makes sure to insert just one of the 2 equivalent formulas if they exist
   * @param type2
   * @param formula
   */
  private void addMappingFromType2ToFormula(String type2, Formula formula) {
    MapUtils.addToSet(extype2ToNonCvtBinaryMap, type2, formula);        
  }

  private void generateCvtFormulas() throws FileNotFoundException {

    List<BinaryFormulaInfo> toAdd = new ArrayList<FbFormulasInfo.BinaryFormulaInfo>();
    for (BinaryFormulaInfo innerInfo : binaryFormulaInfoMap.values()) {

      if (isCvt(innerInfo.expectedType1)) { //if expected type 1 is a CVT
        MapUtils.addToSet(cvtTypeToBinaries, innerInfo.expectedType1, innerInfo.formula);

        Set<Formula> outers = atomicExtype2ToBinaryMap.get(innerInfo.expectedType1); //find those whose expected type 2 is that CVT
        for (Formula outer : outers) {
          BinaryFormulaInfo outerInfo = binaryFormulaInfoMap.get(outer);
          if (!isLegalCvt(innerInfo.formula, outer))
            continue;

          //build new formula
          LambdaFormula cvtFormula = new LambdaFormula("x", new JoinFormula(outer, new JoinFormula(innerInfo.formula, new VariableFormula("x"))));

          BinaryFormulaInfo newFormulaInfo = binaryFormulaInfoMap.get(cvtFormula);
          if (newFormulaInfo == null) {
            String exType1 = outerInfo.expectedType1;
            if (exType1 == null)
              throw new RuntimeException("Missing expected type 1 for formula: " + outer);

            List<String> newDescriptions = new LinkedList<String>();
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
    //if (isEquivalent(reverseFormula(outer), inner)) //don't expand if reverse is equivalent
    //return false;
    return true;
  }

  /** Reverse the order of arguments */
  //Takes in a |rawFormula| which is a function.
  public static Formula reverseFormula(Formula rawFormula) {
    if (rawFormula instanceof ValueFormula) {
      @SuppressWarnings({"unchecked"})
      ValueFormula<NameValue> vf = (ValueFormula<NameValue>) rawFormula;
      return reverseNameFormula(vf);
    } else if (rawFormula instanceof LambdaFormula) {
      // Convert (lambda x (relation1 (relation2 (var x)))) <=> (lambda x (!relation2 (!relation1 (var x))))
      // Note: currently only handles chains.  Make this more generic.
      LambdaFormula formula = (LambdaFormula) rawFormula;
      if (formula.body instanceof JoinFormula || formula.body instanceof VariableFormula)
        return new LambdaFormula(formula.var, reverseChain(formula.body, new VariableFormula(formula.var)));
      else
        return new ReverseFormula(formula);
    } else {
      return new ReverseFormula(rawFormula);
      //throw new RuntimeException("Not handled: " + rawFormula);
    }
  }

  // Helper function for reverseFormula().
  private static Formula reverseChain(Formula source, Formula result) {
    if (source instanceof JoinFormula) {
      JoinFormula join = (JoinFormula) source;
      return reverseChain(join.child, new JoinFormula(reverseFormula(join.relation), result));
    } else if (source instanceof VariableFormula) {
      return result;
    } else {
      throw new RuntimeException("Not handled: " + source);
    }
  }

  // !fb:people.person.place_of_birth <=> fb:people.person.place_of_birth
  private static ValueFormula<NameValue> reverseNameFormula(ValueFormula<NameValue> formula) {
    String id = formula.value.id;
    return new ValueFormula<NameValue>(
        new NameValue(FreebaseInfo.isReverseProperty(id) ?
            id.substring(1) : "!" + id));
  }

  /** supports chains only */
  public boolean hasOpposite(String formula) {
    LispTree tree = LispTree.proto.parseFromString(formula);
    if (tree.isLeaf()) {
      String fbProperty = FreebaseInfo.isReverseProperty(tree.value) ? tree.value.substring(1) : tree.value;
      return freebaseInfo.fbPropertyHasOpposite(fbProperty);
    } else {
      // Un-reverse everything.
      String binary1 = tree.child(2).child(0).value;
      binary1 = FreebaseInfo.isReverseProperty(binary1) ? binary1.substring(1) : binary1;
      String binary2 = tree.child(2).child(1).child(0).value;
      binary2 = FreebaseInfo.isReverseProperty(binary2) ? binary2.substring(1) : binary2;
      return freebaseInfo.fbPropertyHasOpposite(binary1) && freebaseInfo.fbPropertyHasOpposite(binary2);
    }
  }

  /** supports chains only */
  public boolean hasOpposite(Formula formula) {
    LispTree tree = formula.toLispTree();
    if (tree.isLeaf()) {
      String fbProperty = FreebaseInfo.isReverseProperty(tree.value) ? tree.value.substring(1) : tree.value;
      return freebaseInfo.fbPropertyHasOpposite(fbProperty);
    } else {
      // Un-reverse everything.
      String binary1 = tree.child(2).child(0).value;
      binary1 = FreebaseInfo.isReverseProperty(binary1) ? binary1.substring(1) : binary1;
      String binary2 = tree.child(2).child(1).child(0).value;
      binary2 = FreebaseInfo.isReverseProperty(binary2) ? binary2.substring(1) : binary2;
      return freebaseInfo.fbPropertyHasOpposite(binary1) && freebaseInfo.fbPropertyHasOpposite(binary2);
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

  public static Formula buildLambdaFormula(String binary1, String binary2, boolean reverse) {

    Formula binary1Formula = reverse ? Formulas.newNameFormula("!" + binary1) : Formulas.newNameFormula(binary1);
    Formula binary2Formula = reverse ? Formulas.newNameFormula("!" + binary2) : Formulas.newNameFormula(binary2);
    Formula join1 = new JoinFormula(binary2Formula, new VariableFormula("x"));
    Formula join2 = new JoinFormula(binary1Formula, join1);
    return new LambdaFormula("x", join2);
  }

  public boolean isEquivalent(Formula formula1, Formula formula2) {
    if (formula1.equals(formula2))
      return true;
    if (hasOpposite(formula1)) {
      Formula equiv1 = equivalentFormula(formula1);
      return equiv1.equals(formula2);
    }
    return false;
  }

  private boolean isOpposite(Formula formula1, Formula formula2) {

    if(isReversed(formula1) && !isReversed(formula2)) {
      String formula1Desc = formula1.toString().substring(1);
      return formula1Desc.equals(formula2.toString());
    }
    if(isReversed(formula2) && !isReversed(formula1)) {
      String formula2Desc = formula2.toString().substring(1);
      return formula2Desc.equals(formula1.toString());
    }
    if(hasOpposite(formula1)) {
      Formula equivalentFormula = equivalentFormula(formula1);
      if(isReversed(equivalentFormula)) {
        String equivalentFormaulDesc = equivalentFormula.toString().substring(1);
        return equivalentFormaulDesc.equals(formula2.toString());
      }
      else {
        String formula2Desc = formula2.toString().substring(1);
        return formula2Desc.equals(equivalentFormula.toString());
      }
    }
    return false;
  }

  public Set<String> getIncludedTypesInclusive(String subtype) {
    return freebaseInfo.getIncludedTypesInclusive(subtype);
  }

  public Set<String> getSubtypesExclusive(String supertype) {
    return freebaseInfo.getSubTypesExclusive(supertype);
  }

  public Set<Formula> getBinariesForType2(String type) {
    return MapUtils.get(extype2ToNonCvtBinaryMap, type, new HashSet<Formula>());
  }

  public Set<Formula> getAtomicBinariesForType2(String type) {
    return MapUtils.get(atomicExtype2ToBinaryMap, type, new HashSet<Formula>());
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

  public List<Formula> getInjectableBinaries(Formula formula) {
    List<Formula> res = new ArrayList<Formula>();
    if(!(formula instanceof LambdaFormula)) return res;
    LambdaFormula lambdaFormula = (LambdaFormula) formula;
    Formula first = ((JoinFormula) lambdaFormula.body).relation;
    Formula second = ((JoinFormula) ((JoinFormula) lambdaFormula.body).child).relation;
    Set<Formula> binaryFormulas = expandCvts(getBinaryInfo(first).expectedType2);

    for(Formula binaryFormula: binaryFormulas) {
      if(!second.equals(binaryFormula) &&
          !isOpposite(first, binaryFormula)) {
        res.add(binaryFormula);
      }
    }
    return res;
  }

  public Map<Formula, BinaryFormulaInfo> getFormulaInfoMap() {
    return binaryFormulaInfoMap;
  }

  public boolean isCvt(String type) {
    return freebaseInfo.isCvt(type);
  }

  public static class BinaryFormulaInfo {
    public Formula formula;
    public String expectedType1;
    public String expectedType2;
    public String unitId = "";
    public String unitDesc = "";
    public List<String> descriptions=new LinkedList<String>();
    public double popularity;

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
      return reverseFormula(formula).toString() + "\t" + popularity + "\t" + expectedType2 + "\t" + expectedType1 + "\t" + unitId + "\t"
          + unitDesc + "\t" + Joiner.on("###").join(descriptions);
    }

    public static List<String> tokenizeFbDescription(String fbDesc) {
      List<String> res = new ArrayList<String>();
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
          descriptions.size()==0 || 
          popularity == 0.0)
        return false;
      return true;
    }

    public SemType getSemType() {
      return new FuncSemType(new EntitySemType(expectedType2), new EntitySemType(expectedType1));
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
      if (formula == null || descriptions == null || descriptions.size()==0 || 
          popularity == 0.0)
        return false;
      return true;
    }

    public String toString() {
      return formula + "\t" + popularity + "\t" + Joiner.on("###").join(descriptions);
    }

    public String getRepresentativeDescrption() {
      if(descriptions.get(0).contains("/") &&
          descriptions.size()>1)
        return descriptions.get(1);
      return descriptions.get(0);
    }
  }
}
