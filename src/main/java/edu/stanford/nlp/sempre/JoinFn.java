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
    @Option(gloss = "Verbose") public int verbose = 0;
    @Option public boolean showTypeCheckFailures = false;
    @Option public boolean typeInference = true;
    // TODO(joberant): this flag is for backward compatibility.  If we don't
    // need it for the new results, get rid of it.
    @Option public boolean specializedTypeCheck = false;
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
  // When we write a join (binary unary), unary goes into arg1, so if we want the unary to go into arg0, we need to reverse the binary.
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

  // Optionally specify the first of the two arguments to the JoinFn,
  // in which case, this function should only be called on one argument.
  // Note: this is confusing - arg0 here refers to the arguments to JoinFn, not
  // the arg0 and arg1 of the binary.
  private ConstantFn arg0Fn = null;
  public ConstantFn getArg0Fn() { return arg0Fn; }

  public void init(LispTree tree) {
    super.init(tree);
    for (int j = 1; j < tree.children.size(); j++) {
      String arg = tree.child(j).value;
      if (tree.child(j).isLeaf()) {
        switch (arg) {
          case "binary,unary":
            unaryFirst = false;
            break;
          case "unary,binary":
            unaryFirst = true;
            break;
          case "unaryCanBeArg0":
            unaryCanBeArg0 = true;
            break;
          case "unaryCanBeArg1":
            unaryCanBeArg1 = true;
            break;
          case "forward":
            unaryFirst = false;
            unaryCanBeArg1 = true;
            break;
          case "backward":
            unaryFirst = true;
            unaryCanBeArg1 = true;
            break;
          case "betaReduce":
            betaReduce = true;
            break;
          default:
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

  public DerivationStream call(Example ex, Callable c) {
    return new LazyJoinFnDerivs(ex, c);
  }

  public class LazyJoinFnDerivs extends MultipleDerivationStream {
    private int currIndex = 0;
    private List<Derivation> derivations = new ArrayList<>();
    private Example ex;
    private Callable callable;
    Derivation unaryDeriv, binaryDeriv;

    public LazyJoinFnDerivs(Example ex, Callable c) {
      this.ex = ex;
      this.callable = c;
      Derivation child0, child1;
      // TODO(pliang): we can actually push most of this logic into createDerivation()
      // don't need to get the exact size

      if (arg0Fn != null) {
        if (c.getChildren().size() != 1)
          throw new RuntimeException("Expected one argument (already have " + arg0Fn + "), but got args: " + c.getChildren());
        // This is just a virtual child which is not a derivation.
        DerivationStream ld = arg0Fn.call(ex, CallInfo.NULL_INFO);
        child0 = ld.next();
        child1 = c.child(0);
      } else {
        if (c.getChildren().size() != 2)
          throw new RuntimeException("Expected two arguments, but got: " + c.getChildren());
        child0 = c.child(0);
        child1 = c.child(1);
      }

      if (unaryFirst) {
        unaryDeriv = child0;
        binaryDeriv = child1;
      } else {
        binaryDeriv = child0;
        unaryDeriv = child1;
      }
    }

    @Override
    public int estimatedSize() {
      return 2;  // This is an upper bound
    }

    @Override
    public Derivation createDerivation() {
      if (currIndex == 0)
        doJoins(binaryDeriv, unaryDeriv);
      if (currIndex == derivations.size())
        return null;
      return derivations.get(currIndex++);
    }

    SemType specializedTypeCheck(SemType binaryType, SemType unaryType) {
      // Ugly special case for Free917/WebQuestions: when |that| is a
      // UnionSemType corresponding to an entity (e.g.,
      // fb:en.the_washington_post) and we are joining with a relation (e.g.,
      // fb:cvg.game_version.publisher), then we end up calling this function
      // with:
      // - that: (union fb:business.employer ...)
      // - argType: fb:cvg.cvg_publisher
      // The meet here is fb:cvg.cvg_publisher, but we actually want to return bottom (to be more stringent).
      SemType argType = binaryType.getArgType();
      if (unaryType instanceof TopSemType)  // Happens when we don't know the type of the unary
        return SemType.bottomType;
      if (unaryType instanceof AtomicSemType)  // Make things uniform
        unaryType = new UnionSemType(unaryType);
      if (unaryType instanceof UnionSemType && argType instanceof AtomicSemType) {
        for (SemType t : ((UnionSemType) unaryType).baseTypes)
          if (t instanceof AtomicSemType &&
              SemTypeHierarchy.singleton.getSupertypes(((AtomicSemType) t).name).contains(((AtomicSemType) argType).name))
            return binaryType.getRetType();
        return SemType.bottomType;
      }
      return binaryType.apply(unaryType);  // Default
    }

    // Return null if unable to join.
    private Derivation doJoin(Derivation binaryDeriv, Formula binaryFormula, SemType binaryType,
                              Derivation unaryDeriv, Formula unaryFormula, SemType unaryType,
                              String featureDesc) {
      // Do a coarse type check.
      SemType type = opts.specializedTypeCheck ? specializedTypeCheck(binaryType, unaryType) : binaryType.apply(unaryType);
      if (!type.isValid()) {
        if (opts.showTypeCheckFailures)
          LogInfo.warnings("JoinFn: type check failed: [%s : %s] JOIN [%s : %s]",
              binaryFormula, binaryType,
              unaryFormula, unaryType);
        return null;
      }

      Formula f;
      if (betaReduce) {
        if (!(binaryFormula instanceof LambdaFormula))
          throw new RuntimeException("Expected LambdaFormula as the binary, but got: " + binaryFormula + ", unary is " + unaryFormula);
        f = Formulas.lambdaApply((LambdaFormula) binaryFormula, unaryFormula);
      } else {
        f = new JoinFormula(binaryFormula, unaryFormula);
      }

      // Do full type inference.
      if (opts.typeInference) {
        SemType fullType = TypeInference.inferType(f);
        if (opts.verbose >= 2)
          LogInfo.logs("JoinFn.typeInference: %s => %s [coarse type = %s]", f, fullType, type);
        if (!fullType.isValid()) return null;  // Rule out logical form
        type = fullType;  // Use the more specific type
      }

      if (opts.verbose >= 3) {
        LogInfo.logs(
                "JoinFn: binary: %s [%s], unary: %s [%s], result: %s [%s]",
                binaryFormula, binaryType, unaryFormula, unaryType, f, type);
      }

      // Add features
      FeatureVector features = new FeatureVector();
      if (FeatureExtractor.containsDomain("joinPos") && featureDesc != null)
        features.add("joinPos", featureDesc);

     // FbFormulasInfo.touchBinaryFormula(binaryFormula);
      Derivation newDeriv = new Derivation.Builder()
              .withCallable(callable)
              .formula(f)
              .type(type)
              .localFeatureVector(features)
              .createDerivation();

      if (SemanticFn.opts.trackLocalChoices) {
        newDeriv.addLocalChoice(
            "JoinFn " +
                (binaryDeriv.start == -1 ? "-" : binaryDeriv.startEndString(ex.getTokens())) + " " + binaryDeriv.formula + " AND " +
                (unaryDeriv.start == -1 ? "-" : unaryDeriv.startEndString(ex.getTokens())) + " " + unaryDeriv.formula);
      }

      return newDeriv;
    }

    private void doJoins(Derivation binaryDeriv, Derivation unaryDeriv) {
      String binaryPos = ex.languageInfo.getCanonicalPos(binaryDeriv.start);
      String unaryPos = ex.languageInfo.getCanonicalPos(unaryDeriv.start);
      if (unaryCanBeArg0) {
        Derivation deriv = doJoin(
            binaryDeriv, Formulas.reverseFormula(binaryDeriv.formula), binaryDeriv.type.reverse(),
            unaryDeriv, unaryDeriv.formula, unaryDeriv.type,
            "binary=" + binaryPos + ",unary=" + unaryPos + "_reverse");
        if (deriv != null) derivations.add(deriv);
      }
      if (unaryCanBeArg1) {
        Derivation deriv = doJoin(
            binaryDeriv, binaryDeriv.formula, binaryDeriv.type,
            unaryDeriv, unaryDeriv.formula, unaryDeriv.type,
            "binary=" + binaryPos + ",unary=" + unaryPos);
        if (deriv != null) derivations.add(deriv);
      }
      Collections.sort(derivations, Derivation.derivScoreComparator);
    }
  }
}
