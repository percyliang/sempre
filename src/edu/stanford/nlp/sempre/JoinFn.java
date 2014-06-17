package edu.stanford.nlp.sempre;

import fig.basic.LispTree;
import fig.basic.LogInfo;
import fig.basic.Option;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Input: two children (a binary and a unary in some order).
 * JoinFn really serve two roles:
 * 1. binary is just a relation (e.g., !fb:people.person.place_of_birth), in
 *    which case we do a join-project.
 * 2. binary is a lambda calculus expression (e.g., (lambda x (count (var
 *    x)))), in which case we do (macro) function application.
 *
 * @author Percy Liang
 */
public class JoinFn extends SemanticFn {
  public static class Options {
    @Option(gloss = "whether to do a hard type-check")
    public boolean hardTypeCheck = true;
    @Option(gloss = "Verbose") public int verbose = 0;
  }

  public static Options opts = new Options();

  // The arguments to JoinFn can be either:
  //  - binary unary
  //  - unary binary
  private boolean unaryFirst = false;

  // A binary has two arguments, arg0 and arg1.
  //   1. arg0 = fb:en.barack_obama, binary = fb:people.person.place_of_birth, arg1 = fb:en.honolulu
  //      From a relation point viewpoint, arg0 is the subject and arg1 is the object.
  //   2. arg0 = fb:en.barack_obama, binary = (lambda x (fb:people.person.place_of_birth (var x))), arg1 = fb:en.honolulu
  //      From a function application viewpoint, arg1 is the argument and arg0 is the return type.
  // The unary can be placed into arg0 of the binary or arg1 of the binary.
  // When we write a join (binary unary), unary goes into arg1, so if we want the unary to go into arg1, we need to reverse the binary.
  private boolean unaryCanBeArg0 = false;
  private boolean unaryCanBeArg1 = false;

  // If we want to do a betaReduction rather than creating a JoinFormula.
  private boolean betaReduce = false;

  /**
   * There are four different ways binaries and unaries can be combined:
   * - where was unary[Obama] binary[born]? (unary,binary unaryCanBeArg0)
   * - unary[Spanish] binary[speaking] countries (unary,binary unaryCanBeArg1)
   * - binary[parents] of unary[Obama] (binary,unary unaryCanBeArg0)
   * - has binary[parents] unary[Obama] (binary,unary unaryCanBeArg1)
   */

  // Optionally specify the first of the two arguments,
  // in which case, this function should only be called on one argument.
  private ConstantFn arg0Fn = null;

  public void init(LispTree tree) {
    super.init(tree);
    for (int j = 1; j < tree.children.size(); j++) {
      String arg = tree.child(j).value;
      if (tree.child(j).isLeaf()) {
        if ("binary,unary".equals(arg))
          unaryFirst = false;
        else if ("unary,binary".equals(arg))
          unaryFirst = true;
        else if ("unaryCanBeArg0".equals(arg))
          unaryCanBeArg0 = true;
        else if ("unaryCanBeArg1".equals(arg))
          unaryCanBeArg1 = true;
        else if ("forward".equals(arg)) {
          unaryFirst = false;
          unaryCanBeArg1 = true;
        } else if ("backward".equals(arg)) {
          unaryFirst = true;
          unaryCanBeArg1 = true;
        } else if ("betaReduce".equals(arg)) {
          betaReduce = true;
        } else {
          throw new RuntimeException("Invalid argument: " + arg);
        }
      } else {
        if ("arg0".equals(tree.child(j).child(0).value)) {
          arg0Fn = new ConstantFn();
          arg0Fn.init(tree.child(j));
        } else {
          throw new RuntimeException("Invalid argument: " + tree.child(j));
        }
      }
    }

    if (!unaryCanBeArg0 && !unaryCanBeArg1)
      throw new RuntimeException("At least one of unaryCanBeArg0 and unaryCanBeArg1 must be set");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    JoinFn joinFn = (JoinFn) o;
    if (unaryCanBeArg0 != joinFn.unaryCanBeArg0) return false;
    if (unaryCanBeArg1 != joinFn.unaryCanBeArg1) return false;
    if (unaryFirst != joinFn.unaryFirst) return false;
    if (betaReduce != joinFn.betaReduce) return false;
    if (arg0Fn != null ? !arg0Fn.equals(joinFn.arg0Fn) : joinFn.arg0Fn != null)
      return false;
    return true;
  }

  private List<String> intersect(List<String> types0, List<String> types1) {
    List<String> types = new ArrayList<String>();
    for (String t : types0)
      if (types1.contains(t))
        types.add(t);
    return types;
  }

  public List<Derivation> call(Example ex, Callable c) {
    Derivation child0 = null;
    Derivation child1 = null;

    if (arg0Fn != null) {
      if (c.getChildren().size() != 1)
        throw new RuntimeException(
            "Expected one argument (already have " + arg0Fn +
                "), but got args: " + c.getChildren());
      // This is just a virtual child which is not a derivation.
      child0 = arg0Fn.call(ex, CallInfo.NULL_INFO).get(0);
      child1 = c.child(0);
    } else {
      if (c.getChildren().size() != 2)
        throw new RuntimeException("Expected two arguments, but got: " + c.getChildren());
      child0 = c.child(0);
      child1 = c.child(1);
    }

    Derivation binaryDeriv, unaryDeriv;
    if (unaryFirst) {
      unaryDeriv = child0;
      binaryDeriv = child1;
    } else {
      binaryDeriv = child0;
      unaryDeriv = child1;
    }

    List<Derivation> results = new ArrayList<Derivation>();
    performJoins(ex, c, binaryDeriv, unaryDeriv, results);
    return results;
  }

  private void performJoins(Example ex, Callable c, Derivation binaryDeriv, Derivation unaryDeriv, List<Derivation> results) {
    // Note that the derivations might not start or end because they're not anchored in the text.
    String binaryPos = binaryDeriv.start == -1 ? "NONE" : ex.languageInfo.getCanonicalPos(binaryDeriv.start);
    String unaryPos = unaryDeriv.start == -1 ? "NONE" : ex.languageInfo.getCanonicalPos(unaryDeriv.start);
    if (unaryCanBeArg1) {
      results.addAll(
          performJoin(
              ex, c,
              binaryDeriv, binaryDeriv.formula, binaryDeriv.type,
              unaryDeriv, unaryDeriv.formula, unaryDeriv.type,
              "binary=" + binaryPos + ",unary=" + unaryPos));
    }
    if (unaryCanBeArg0) {
      results.addAll(
          performJoin(
              ex, c,
              binaryDeriv, FbFormulasInfo.reverseFormula(binaryDeriv.formula), binaryDeriv.type.reverse(),
              unaryDeriv, unaryDeriv.formula, unaryDeriv.type,
              "binary=" + binaryPos + ",unary=" + unaryPos + "_reverse"));
    }
  }

  public List<Derivation> performJoin(Example ex, Callable c,
                                       Derivation binaryDeriv, Formula binaryFormula, SemType binaryType,
                                       Derivation unaryDeriv, Formula unaryFormula, SemType unaryType,
                                       String featureDesc) {
    FeatureVector features = new FeatureVector();
    SemType type = binaryType.apply(unaryType);

    // Add features
    if (FeatureExtractor.containsDomain("joinPos") && featureDesc != null)
      features.add("joinPos", featureDesc);

    if (opts.verbose >= 3) {
      LogInfo.logs(
          "JoinFn: binary: %s [%s], unary: %s [%s], result: [%s]",
          binaryFormula, binaryType, unaryFormula, unaryType, type);
    }

    if (!type.isValid()) {
      if (opts.hardTypeCheck)
        return Collections.emptyList();  // Don't accept logical forms that don't type check
      else {
        if (FeatureExtractor.containsDomain("typeCheck"))
          features.add("typeCheck", "joinMismatch");  // Just add a feature
      }
    }

    Formula f;
    if (betaReduce) {
      if (!(binaryFormula instanceof LambdaFormula))
        throw new RuntimeException("Expected LambdaFormula as the binary, but got: " + binaryFormula + ", unary is " + unaryFormula);
      f = Formulas.lambdaApply((LambdaFormula)binaryFormula, unaryFormula);
    } else {
      f = new JoinFormula(binaryFormula, unaryFormula);
    }
    
    FbFormulasInfo.touchBinaryFormula(binaryFormula);
    Derivation newDeriv = new Derivation.Builder()
        .withCallable(c)
        .formula(f)
        .type(type)
        .localFeatureVector(features)
        .createDerivation();

    if (SemanticFn.opts.trackLocalChoices) {
      newDeriv.localChoices.add(
          "JoinFn " +
              binaryDeriv.startEndString(ex.getTokens()) + " " + binaryDeriv.formula + " AND " +
              unaryDeriv.startEndString(ex.getTokens()) + " " + unaryDeriv.formula);
    }

    return Collections.singletonList(newDeriv);
  }
}
