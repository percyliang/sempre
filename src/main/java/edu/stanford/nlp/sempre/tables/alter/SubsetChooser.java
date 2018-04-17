package edu.stanford.nlp.sempre.tables.alter;

import java.util.Collection;

public interface SubsetChooser {

  public Subset chooseSubset(String id, DenotationData denotationData);
  public Subset chooseSubset(String id, DenotationData denotationData, Collection<Integer> forbiddenTables);

}
