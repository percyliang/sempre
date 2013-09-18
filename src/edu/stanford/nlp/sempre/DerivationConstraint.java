package edu.stanford.nlp.sempre;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A DerivationConstraint filters the set of Derivations. Currently, it's just
 * used to visualize the Derivations.
 */
public class DerivationConstraint {
  // Regular expression on formula's toString()
  private final String formulaPattern;

  @JsonCreator
  public DerivationConstraint(String formulaPattern) {
    this.formulaPattern = formulaPattern;
  }

  // Intended just for JsonValue.
  @JsonValue
  public String getFormulaPattern() { return formulaPattern; }

  // Return the factor
  public boolean satisfies(Example ex, Derivation deriv) {
    //LogInfo.logs("satisfies: %s %s", deriv, formulaPattern);
    return deriv.getFormula().toString().matches(".*" + formulaPattern + ".*");
  }
}
