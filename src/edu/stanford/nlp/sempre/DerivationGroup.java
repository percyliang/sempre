package edu.stanford.nlp.sempre;

import java.util.List;

/**
 * Represents one or two lists of derivations.
 *
 * The motivation is to group derivations based on type compatibility.
 * For example, when building (and __ __), considering all pairs of derivations
 * is time-wasting since a lot of pairs don't type-check. We instead group
 * derivations by type, and only apply the rule to the pairs that type-check.
 *
 * During parsing, for each DerivationGroup:
 * - (if derivations2 == null) apply the rule on all derivations in derivations1
 * - (otherwise) apply the rule to all pairs (d1, d2) where d1 is in derivations1
 *   and d2 is in derivations2
 *
 * @author ppasupat
 */
public class DerivationGroup {
  public final List<Derivation> derivations1, derivations2;

  public DerivationGroup(List<Derivation> derivations1) {
    this.derivations1 = derivations1;
    this.derivations2 = null;
  }

  public DerivationGroup(List<Derivation> derivations1, List<Derivation> derivations2) {
    this.derivations1 = derivations1;
    this.derivations2 = derivations2;
  }
}