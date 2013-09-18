package edu.stanford.nlp.sempre;

import fig.basic.LispTree;

public class DateValue extends Value {
  public final int year;
  public final int month;
  public final int day;

  // Format: YYYY-MM-DD (from Freebase).
  // Return null if it's not a valid date string.
  public static DateValue parseDateValue(String dateStr) {
    if (dateStr.equals("PRESENT_REF")) return null;
    if (dateStr.startsWith("OFFSET")) return null;

    // We don't handle the following things:
    //   - "30 A.D" since its value is "+0030"
    //   - "Dec 20, 2009 10:04am" since its value is "2009-12-20T10:04"
    int year = -1, month = -1, day = -1;
    boolean isBC = dateStr.startsWith("-");
    if (isBC) dateStr = dateStr.substring(1);
    String dateParts[];
    dateParts = dateStr.split("-");
    if (dateParts.length > 3)
      throw new RuntimeException("Date has more than 3 parts: " + dateStr);

    if (dateParts.length >= 1) year = parseIntRobust(dateParts[0]) * (isBC ? -1 : 1);
    if (dateParts.length >= 2) month = parseIntRobust(dateParts[1]);
    if (dateParts.length >= 3) day = parseIntRobust(dateParts[2]);

    return new DateValue(year, month, day);
  }

  private static int parseIntRobust(String i) {
    int val;
    try {
      val = Integer.parseInt(i);
    } catch (NumberFormatException ex) {
      val = -1;
    }
    return val;
  }

  public DateValue(int year, int month, int day) {
    this.year = year;
    this.month = month;
    this.day = day;
  }

  public DateValue(LispTree tree) {
    this.year = Integer.valueOf(tree.child(1).value);
    this.month = Integer.valueOf(tree.child(2).value);
    this.day = Integer.valueOf(tree.child(3).value);
  }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("date");
    tree.addChild(String.valueOf(year));
    tree.addChild(String.valueOf(month));
    tree.addChild(String.valueOf(day));
    return tree;
  }

  public double getCompatibility(Value thatValue) {
    if (!(thatValue instanceof DateValue))
      return 0;
    DateValue that = (DateValue) thatValue;
    // TODO: Only comparing the year right now.
    boolean perfectMatch = (this.year == that.year);
    //&& (this.month == that.month)
    //&& (this.day == that.day);
    return perfectMatch ? 1.0 : 0.0;
  }

  @Override public int hashCode() {
    int hash = 0x7ed55d16;
    hash = hash * 0xd3a2646c + year;
    hash = hash * 0xd3a2646c + month;
    hash = hash * 0xd3a2646c + day;
    return hash;
  }

  @Override public boolean equals(Object thatObj) {
    if (!(thatObj instanceof DateValue)) return false;
    DateValue that = (DateValue)thatObj;
    if (this.year != that.year) return false;
    if (this.month != that.month) return false;
    if (this.day != that.day) return false;
    return true;
  }
}
