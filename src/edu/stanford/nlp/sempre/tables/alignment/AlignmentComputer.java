package edu.stanford.nlp.sempre.tables.alignment;

import java.io.PrintWriter;
import java.util.List;

import edu.stanford.nlp.sempre.Formula;
import edu.stanford.nlp.sempre.tables.alignment.BitextData.BitextDataGroup;
import edu.stanford.nlp.sempre.tables.alignment.BitextData.BitextDatum;
import fig.basic.Pair;

public interface AlignmentComputer {
  // Given a BitextData, train an alignment model.
  public void align(BitextData bitextData);

  // Print the parameters
  public void dump(PrintWriter out);

  // Compute the scores of all formulas z in an example (x, y)
  public List<Pair<Formula, Double>> score(BitextDataGroup group);

  // Compute the score of a single formula z in an example (x, y)
  double score(BitextDatum datum);
}
