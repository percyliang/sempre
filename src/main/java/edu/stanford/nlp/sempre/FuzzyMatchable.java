package edu.stanford.nlp.sempre;

import java.util.*;

import edu.stanford.nlp.sempre.FuzzyMatchFn.FuzzyMatchFnMode;

/**
 * Interface for knowledge sources that, given a phrase, can retrieve all its
 * predicates that (fuzzily) match the phrase.
 *
 * @author ppasupat
 */
public interface FuzzyMatchable {

  /**
   * Return all entities / unaries / binaries that approximately match the
   * string formed by joining sentence[startIndex], ..., sentence[endIndex-1]
   * with spaces.
   *
   * This allows the algorithm to consider the context of the term being matched.
   *
   * One possible implementation, which ignores the context, is calling
   * getFuzzyMatchedFormulas(term, mode) where
   * term = String.join(" ", sentence.subList(startIndex, endIndex))
   */
  public abstract Collection<Formula> getFuzzyMatchedFormulas(
      List<String> sentence, int startIndex, int endIndex, FuzzyMatchFnMode mode);

  /**
   * Return all entities / unaries / binaries that approximately match the term
   */
  public abstract Collection<Formula> getFuzzyMatchedFormulas(String term, FuzzyMatchFnMode mode);

  /**
   * Return all possible entities / unaries / binaries
   */
  public abstract Collection<Formula> getAllFormulas(FuzzyMatchFnMode mode);

}
