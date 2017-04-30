package edu.stanford.nlp.sempre.overnight;

import java.util.*;

import edu.stanford.nlp.sempre.*;

/**
 * Hard-coded hacks for pruning derivations in floating parser for overnight domains.
 */

public class OvernightDerivationPruningComputer extends DerivationPruningComputer {
  
  public OvernightDerivationPruningComputer(DerivationPruner pruner) {
    super(pruner);
  }

  @Override
  public Collection<String> getAllStrategyNames() {
    return Arrays.asList("violateHardConstraints");
  }

  @Override
  public String isPruned(Derivation deriv) {
    if (containsStrategy("violateHardConstraints") && violateHardConstraints(deriv)) return "violateHardConstraints";
    return null;
  }

  // Check a few hard constraints on each derivation
  private static boolean violateHardConstraints(Derivation deriv) {
    if (deriv.value != null) {
      if (deriv.value instanceof ErrorValue) return true;
      if (deriv.value instanceof StringValue) { //empty denotation
        if (((StringValue) deriv.value).value.equals("[]")) return true;
      }
      if (deriv.value instanceof ListValue) {
        List<Value> values = ((ListValue) deriv.value).values;
        // empty lists
        if (values.size() == 0) return true;
        // NaN
        if (values.size() == 1 && values.get(0) instanceof NumberValue) {
          if (Double.isNaN(((NumberValue) values.get(0)).value)) return true;
        }
        // If we are supposed to get a number but we get a string (some sparql weirdness)
        if (deriv.type.equals(SemType.numberType) &&
            values.size() == 1 &&
            !(values.get(0) instanceof NumberValue)) return true;
      }
    }
    return false;
  }

}
