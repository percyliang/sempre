package edu.stanford.nlp.sempre.freebase.test;

import org.testng.annotations.Test;

import edu.stanford.nlp.sempre.SemType;
import edu.stanford.nlp.sempre.SemTypeHierarchy;
import edu.stanford.nlp.sempre.freebase.*;
import fig.basic.LispTree;

/**
 * Test type system on Freebase schema.
 * @author Percy Liang
 */
public class FreebaseSemTypeTest {
  // For testing
  private static SemType T(String str) {
    return SemType.fromLispTree(LispTree.proto.parseFromString(str));
  }

  private static void verifyEquals(SemType predType, SemType wantedType) {
    if (!predType.toString().equals(wantedType.toString()))
      throw new RuntimeException(String.format("Wanted %s, but got %s", wantedType, predType));
  }

  private static void verifyMeet(String t1, String t2) { verifyMeet(t1, t2, t2); }
  private static void verifyMeet(String t1, String t2, String t) {
    verifyEquals(T(t1).meet(T(t2)), T(t));
    verifyEquals(T(t2).meet(T(t1)), T(t));
  }

  @Test public void simpleSemType() {
    FreebaseInfo.getSingleton();      // Load Freebase type hierarchy
    SemTypeHierarchy.opts.failOnUnknownTypes = false;
    verifyMeet("city", "city");
    verifyMeet("city", "country", "(union)");
    verifyMeet("city", "(union city country)", "city");
    verifyMeet("(union city country river)", "(union city country)");

    verifyEquals(T("(-> city fb:type.int)").apply(T("(union city country)")), T("fb:type.int"));
    verifyEquals(T("(-> city fb:type.int fb:type.float)").apply(T("(union city country)")).apply(T("fb:type.int")), T("fb:type.float"));
    verifyEquals(T("fb:type.datetime").apply(T("fb:common.topic")), T("(union)"));
    verifyEquals(T("(-> fb:location.citytown fb:type.datetime)").apply(T("fb:location.location")), T("fb:type.datetime"));
    verifyEquals(T("(-> fb:location.location fb:type.datetime)").apply(T("fb:location.citytown")), T("fb:type.datetime"));

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
    new FreebaseSemTypeTest().simpleSemType();
  }
}
