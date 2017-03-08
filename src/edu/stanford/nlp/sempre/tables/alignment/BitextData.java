package edu.stanford.nlp.sempre.tables.alignment;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import fig.basic.*;

/**
 * Preprocess examples for training an alignment model.
 *
 * @author ppasupat
 */
public class BitextData {

  public final List<BitextDataGroup> bitextDataGroups;
  public final Set<String> allTokens, allPredicates;

  public BitextData(List<Example> examples) {
    LogInfo.begin_track("Creating BitextData");
    bitextDataGroups = new ArrayList<>();
    for (Example ex : examples) {
      if (!ex.predDerivations.isEmpty())
        bitextDataGroups.add(new BitextDataGroup(ex));
    }
    // Collect all tokens and all predicates
    allTokens = new HashSet<>();
    allPredicates = new HashSet<>();
    for (BitextDataGroup group : bitextDataGroups) {
      allTokens.addAll(group.tokens);
      for (BitextDatum datum : group.bitextDatums) {
        allPredicates.addAll(datum.predicates);
      }
    }
    LogInfo.end_track();
  }

  public static class BitextDataGroup {
    public final Example ex;
    public final String id;
    public final List<String> tokens;
    public final List<BitextDatum> bitextDatums;

    public BitextDataGroup(Example ex) {
      this.ex = ex;
      id = ex.id;
      tokens = new ArrayList<>(ex.languageInfo.tokens);
      bitextDatums = new ArrayList<>();
      for (Derivation d : ex.predDerivations) {
        bitextDatums.add(new BitextDatum(this, d));
      }
    }
  }

  public static class BitextDatum {
    public final BitextDataGroup group;
    public final Formula formula;
    public final List<String> predicates;

    public BitextDatum(BitextDataGroup group, Derivation d) {
      this.group = group;
      formula = d.formula;
      predicates = new ArrayList<>();
      traversePredicates(formula.toLispTree());
    }

    private void traversePredicates(LispTree t) {
      if (t.isLeaf()) {
        predicates.add(t.value);
      } else {
        for (LispTree child : t.children)
          traversePredicates(child);
      }
    }
  }

}
