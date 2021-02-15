package edu.stanford.nlp.sempre.freebase.test;

import edu.stanford.nlp.sempre.freebase.lexicons.TokenLevelMatchFeatures;
import edu.stanford.nlp.stats.Counter;
import org.testng.annotations.Test;

import java.util.Arrays;

import static org.testng.AssertJUnit.assertEquals;

public class TokenMatchTest {

  @Test
  public void tokenMatch() {
    String[] text = new String[]{"what", "tv", "program", "have", "hugh", "laurie", "create"};
    String[] pattern = new String[]{"program", "create"};
    Counter<String> match = TokenLevelMatchFeatures.extractTokenMatchFeatures(Arrays.asList(text), Arrays.asList(pattern), true);
    assertEquals(0.5, match.getCount("prefix"), 0.00001);
    assertEquals(0.5, match.getCount("suffix"), 0.00001);
  }
}
