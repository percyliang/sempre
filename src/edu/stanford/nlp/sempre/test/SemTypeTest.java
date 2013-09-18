package edu.stanford.nlp.sempre.test;

import edu.stanford.nlp.sempre.SemType;
import fig.basic.LispTree;
import org.testng.annotations.Test;

/**
 * Test Formulas.
 * @author Percy Liang
 */
public class SemTypeTest {
  // For testing
  private static SemType T(String str) {
    return SemType.fromLispTree(LispTree.proto.parseFromString(str));
  }
  private static void verify(SemType predType, SemType wantedType) {
    if (!predType.toString().equals(wantedType.toString()))
      throw new RuntimeException(String.format("Wanted %s, but got %s", wantedType, predType));
  }
  private static void verifySupertypeOf(String supertype, String subtype) {
    if (!T(supertype).isSupertypeOf(T(subtype)))
      throw new RuntimeException(supertype + " is not a supertype of " + subtype);
  }

  @Test public void simpleSemType() {
    verify(T("city").meet(T("city")), T("city"));
    verify(T("city").meet(T("country")), T("(union)"));
    verify(T("city").meet(T("(union city country)")), T("city"));
    verify(T("(union country city river)").meet(T("(union city country)")), T("(union country city)"));
    verify(T("(-> city fb:type.int)").apply(T("(union city country)")), T("fb:type.int"));
    verify(T("(-> city fb:type.int fb:type.float)").apply(T("(union city country)")).apply(T("fb:type.int")), T("fb:type.float"));
    verify(T("fb:type.datetime").apply(T("fb:common.topic")), T("(union)"));
    verify(T("(-> fb:location.citytown fb:type.datetime)").apply(T("fb:location.location")), T("(union)"));
    verify(T("(-> fb:location.location fb:type.datetime)").apply(T("fb:location.citytown")), T("fb:type.datetime"));
    verifySupertypeOf("(-> fb:location.location fb:type.number)", "(-> fb:location.location fb:type.float)");
  }
}
