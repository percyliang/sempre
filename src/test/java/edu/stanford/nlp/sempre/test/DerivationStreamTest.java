package edu.stanford.nlp.sempre.test;

import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.SingleDerivationStream;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;

/**
 * @author Percy Liang
 */
public class DerivationStreamTest {
  @Test
  public void single() {
    SingleDerivationStream s = new SingleDerivationStream() {
      public Derivation createDerivation() {
        return new Derivation.Builder().cat("NP").createDerivation();
      }
    };
    assertEquals(true, s.hasNext());
    assertEquals(true, s.hasNext());
    assertEquals(true, s.hasNext());
    assertEquals("NP", s.next().cat);
    assertEquals(false, s.hasNext());
    assertEquals(false, s.hasNext());

    s = new SingleDerivationStream() {
      public Derivation createDerivation() { return null; }
    };
    assertEquals(false, s.hasNext());
  }
}
