package edu.stanford.nlp.sempre;

import java.util.*;

/**
 * Utilities for floating rules.
 */
public final class FloatingRuleUtils {
  private FloatingRuleUtils() { }   // Should not be called.

  /**
   * Get the anchored sub-derivations (sub-trees) of a derivation.
   * I.e., gets all sub-derivations that are associated with a span of the utterance.
   */
  public static List<Derivation> getDerivationAnchors(Derivation deriv) {
    List<Derivation> anchors = new ArrayList<>();
    if (deriv.rule.isAnchored()) {
      // if the sub-derivation is anchored to a span just add it
      anchors.add(deriv);
    } else if (!(deriv.children == null || deriv.children.size() == 0)) {
      // if the derivation is not anchored but has children, recurse into children
      for (Derivation child : deriv.children)
        anchors.addAll(getDerivationAnchors(child));
    }
    return anchors;
  }

  /**
   * Helper function to ensure that anchored spans are only used once in a final derivation.
   * for example if A spans (or has a child that spans) [0, 3] and B spans (or has a child
   * that spans) [2, 4] then we have an overlap.
   */
  public static boolean derivationAnchorsOverlap(Derivation a, Derivation b) {
    /*
    List<Derivation> aRoots = getDerivationAnchors(a);
    List<Derivation> bRoots = getDerivationAnchors(b);
    for (Derivation aRoot : aRoots) {
      for (Derivation bRoot : bRoots) {
        if (aRoot.start < bRoot.end && bRoot.start < aRoot.end)
          return true;
      }
    }
    return false;
    */
    boolean[] aAnchors = a.getAnchoredTokens(), bAnchors = b.getAnchoredTokens();
    for (int i = 0; i < aAnchors.length && i < bAnchors.length; i++)
      if (aAnchors[i] && bAnchors[i]) return true;
    return false;
  }
}
