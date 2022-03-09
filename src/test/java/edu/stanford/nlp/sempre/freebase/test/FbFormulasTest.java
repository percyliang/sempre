package edu.stanford.nlp.sempre.freebase.test;

import edu.stanford.nlp.sempre.freebase.FbFormulasInfo;
import edu.stanford.nlp.sempre.freebase.FbFormulasInfo.BinaryFormulaInfo;
import edu.stanford.nlp.sempre.freebase.FbFormulasInfo.UnaryFormulaInfo;
import edu.stanford.nlp.sempre.Formula;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;

public class FbFormulasTest {

  @Test
  public void formulaInfo() {
    FbFormulasInfo infoRepos = FbFormulasInfo.getSingleton();

    // 1
    BinaryFormulaInfo bInfo = infoRepos.getBinaryInfo(Formula.fromString("(lambda x (!fb:education.education.specialization (!fb:education.field_of_study.students_majoring (var x))))"));
    assertEquals(521.0, bInfo.popularity, 0.0001);
    assertEquals("fb:education.field_of_study", bInfo.expectedType1);
    assertEquals("fb:education.field_of_study", bInfo.expectedType2);
    boolean contains = bInfo.descriptions.contains("specialization") && bInfo.descriptions.contains("students majoring in this field");
    assertEquals(true, contains);
    // 2
    bInfo = infoRepos.getBinaryInfo(Formula.fromString("!fb:broadcast.content.broadcast"));
    assertEquals(4838.0, bInfo.popularity, 0.0001);
    assertEquals("fb:broadcast.broadcast", bInfo.expectedType1);
    assertEquals("fb:broadcast.content", bInfo.expectedType2);
    contains = bInfo.descriptions.contains("broadcasts");
    assertEquals(true, contains);
    // 3
    UnaryFormulaInfo uInfo = infoRepos.getUnaryInfo(Formula.fromString("(fb:type.object.type fb:location.country)"));
    assertEquals(574.0, uInfo.popularity, 0.0001);
    contains = uInfo.descriptions.contains("country") && uInfo.descriptions.contains("empire");
    assertEquals(true, contains);
    assertEquals("fb:location.country", uInfo.types.iterator().next());
    // 4
    uInfo = infoRepos.getUnaryInfo(Formula.fromString("(fb:people.person.profession fb:en.wrestler)"));
    assertEquals(1449.0, uInfo.popularity, 0.0001);
    contains = uInfo.descriptions.contains("wrestler")
        && uInfo.descriptions.contains("professional wrestler")
        && uInfo.descriptions.contains("pro wrestler");
    assertEquals(true, contains);
    assertEquals("fb:people.person", uInfo.types.iterator().next());
  }
}
