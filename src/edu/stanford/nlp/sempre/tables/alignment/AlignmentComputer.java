package edu.stanford.nlp.sempre.tables.alignment;

interface AlignmentComputer {
  // Return (word, predicate) => alignment score
  DoubleMap align();
}
