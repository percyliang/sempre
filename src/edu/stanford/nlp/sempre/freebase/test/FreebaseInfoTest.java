package edu.stanford.nlp.sempre.freebase.test;

import edu.stanford.nlp.sempre.freebase.FreebaseInfo;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;

/**
 * Test FreebaseInfo.
 * @author Percy Liang
 */
public class FreebaseInfoTest {
  @Test public void units() {
    FreebaseInfo info = FreebaseInfo.getSingleton();
    assertEquals(FreebaseInfo.ENTITY, info.getUnit1("fb:people.person.place_of_birth"));
    assertEquals(FreebaseInfo.ENTITY, info.getUnit2("fb:people.person.place_of_birth"));

    assertEquals(FreebaseInfo.ENTITY, info.getUnit1("fb:people.person.date_of_birth"));
    assertEquals(FreebaseInfo.DATE, info.getUnit2("fb:people.person.date_of_birth"));

    assertEquals(FreebaseInfo.ENTITY, info.getUnit1("fb:people.person.height_meters"));
    assertEquals("fb:en.meter", info.getUnit2("fb:people.person.height_meters"));
  }
}
