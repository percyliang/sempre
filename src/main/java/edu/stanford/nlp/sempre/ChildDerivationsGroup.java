package edu.stanford.nlp.sempre;

import java.util.List;

/**
 * A group containing one or two lists of potential child derivations.
 *
 * The motivation is to group potential child derivations based on type compatibility.
 * For example, when building (and __ __), considering all pairs of derivations
 * is time-wasting since a lot of pairs don't type-check. We instead group
 * derivations by type, and only apply the rule to the pairs that type-check.
 * 
 * This idea also extends to one-argument rules. For example, for (sum ___),
 * we should only look at child derivations with number type.
 *
 * During parsing, for each DerivationGroup:
 * - For a one-argument rule (derivations2 == null):
 *   Apply the rule on all derivations in derivations1
 * - For a two-argument rule (derivations2 != null):
 *   Apply the rule to all pairs (d1, d2) where d1 is in derivations1 and d2 is in derivations2
 *
 * @author ppasupat
 */
public class ChildDerivationsGroup {
  public final List<Derivation> derivations1, derivations2;

  public ChildDerivationsGroup(List<Derivation> derivations1) {
    this.derivations1 = derivations1;
    this.derivations2 = null;
  }

  public ChildDerivationsGroup(List<Derivation> derivations1, List<Derivation> derivations2) {
    this.derivations1 = derivations1;
    this.derivations2 = derivations2;
  }
}
