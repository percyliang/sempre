package edu.stanford.nlp.sempre.test;

import static org.testng.AssertJUnit.assertEquals;

import org.testng.annotations.Test;

import edu.stanford.nlp.sempre.*;
import fig.basic.*;

/**
 * Test type system.
 * @author Percy Liang
 * @author ppasupat
 */
public class SemTypeTest {
  // For testing
  private static SemType T(String str) {
    return SemType.fromLispTree(LispTree.proto.parseFromString(str));
  }

  private static void verifyEquals(SemType predType, SemType wantedType) {
    assertEquals(wantedType.toString(), predType.toString());
  }

  private static void verifyMeet(String t1, String t2) { verifyMeet(t1, t2, t2); }
  private static void verifyMeet(String t1, String t2, String t) {
    verifyEquals(T(t1).meet(T(t2)), T(t));
    verifyEquals(T(t2).meet(T(t1)), T(t));
  }

  @Test public void simpleSemType() {
    SemTypeHierarchy.opts.failOnUnknownTypes = false;
    verifyMeet("city", "city");
    verifyMeet("city", "country", "(union)");
    verifyMeet("city", "(union city country)", "city");
    verifyMeet("(union city country river)", "(union city country)");

    verifyEquals(T("(-> city fb:type.int)").apply(T("(union city country)")), T("fb:type.int"));
    verifyEquals(T("(-> city fb:type.int fb:type.float)").apply(T("(union city country)")).apply(T("fb:type.int")), T("fb:type.float"));
    verifyEquals(T("fb:type.datetime").apply(T("fb:common.topic")), T("(union)"));
    verifyEquals(T("(-> fb:type.int fb:type.datetime)").apply(T("fb:type.number")), T("fb:type.datetime"));
    verifyEquals(T("(-> fb:type.number fb:type.datetime)").apply(T("fb:type.int")), T("fb:type.datetime"));

    verifyMeet("(-> fb:location.location fb:type.number)", "(-> fb:location.location fb:type.float)");
    verifyMeet("fb:common.topic", "fb:location.location");
    verifyMeet("fb:type.any", "fb:type.boolean");
    verifyMeet("fb:type.any", "fb:type.number");
    verifyMeet("fb:type.any", "fb:type.datetime");
    verifyMeet("fb:type.any", "fb:type.cvt");
    verifyMeet("fb:type.any", "fb:type.text");
    verifyMeet("fb:type.any", "fb:location.location");
    verifyMeet("fb:type.any", "fb:common.topic");

    verifyMeet("fb:common.topic", "fb:common.topic");
    verifyMeet("top", "(-> t t)");
    verifyMeet("top", "fb:type.datetime");
    verifyMeet("top", "(union a b)");

    verifyMeet("(-> (-> a b) top)", "(-> top (-> a b))", "(-> (-> a b) (-> a b))");
    verifyMeet("(-> (union city country) person)", "(-> city (union person dog))", "(-> city person)");
  }

  public static void main(String[] args) {
    new SemTypeTest().simpleSemType();
  }
}
