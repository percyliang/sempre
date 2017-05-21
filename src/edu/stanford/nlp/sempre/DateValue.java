package edu.stanford.nlp.sempre;

import fig.basic.LispTree;
import java.util.Calendar;
import java.util.Date;

public class DateValue extends Value {
  public final int year;
  public final int month;
  public final int day;
  public final int hour;
  public final int minute;
  public final double second;

  // Format: YYYY-MM-DD (from Freebase).
  // Return null if it's not a valid date string.
  public static DateValue parseDateValue(String dateStr) {
    if (dateStr.equals("PRESENT_REF")) return now();
    if (dateStr.startsWith("OFFSET")) return null;

    // We don't handle the following things:
    //   - "30 A.D" since its value is "+0030"
    //   - "Dec 20, 2009 10:04am" since its value is "2009-12-20T10:04"
    int year = -1, month = -1, day = -1;
    int hour = 0, minute = 0;
    double second = 0;
    boolean isBC = dateStr.startsWith("-");
    if (isBC) dateStr = dateStr.substring(1);

    String[] dateParts;
    String[] timeParts = null;
    String timeStr;

    if (dateStr.indexOf('T') != -1) {
      timeStr = dateStr.substring(dateStr.indexOf('T') + 1, dateStr.length());
      dateStr = dateStr.substring(0, dateStr.indexOf('T'));
      timeParts = timeStr.split(":");
    }

    dateParts = dateStr.split("-");
    if (dateParts.length > 3)
      throw new RuntimeException("Date has more than 3 parts: " + dateStr);

    if (dateParts.length >= 1) year = parseIntRobust(dateParts[0]) * (isBC ? -1 : 1);
    if (dateParts.length >= 2) month = parseIntRobust(dateParts[1]);
    if (dateParts.length >= 3) day = parseIntRobust(dateParts[2]);

    if (timeParts != null) {
      if (timeParts.length >= 1) hour = parseIntRobust(timeParts[0]);
      if (timeParts.length >= 2) minute = parseIntRobust(timeParts[1]);
      if (timeParts.length >= 3) second = parseDoubleRobust(timeParts[2]);

      return new DateValue(year, month, day, hour, minute, second);
    } else {
      return new DateValue(year, month, day);
    }
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

  private static double parseDoubleRobust(String i) {
    try {
      return Double.parseDouble(i);
    } catch (NumberFormatException ex) {
      return 0;
    }
  }

  public static DateValue now() {
    Calendar cal = Calendar.getInstance();
    int year = cal.get(Calendar.YEAR);
    int month = cal.get(Calendar.MONTH);
    int day = cal.get(Calendar.DAY_OF_MONTH);
    int hour = cal.get(Calendar.HOUR_OF_DAY);
    int minute = cal.get(Calendar.MINUTE);
    double second = cal.get(Calendar.SECOND) + 0.001 * cal.get(Calendar.MILLISECOND);
    return new DateValue(year, month, day, hour, minute, second);
  }

  public DateValue(int year, int month, int day) {
    this.year = year;
    this.month = month;
    this.day = day;
    this.hour = 0;
    this.minute = 0;
    this.second = 0;
  }

  public DateValue(int year, int month, int day, int hour, int minute, double second) {
    this.year = year;
    this.month = month;
    this.day = day;
    this.hour = hour;
    this.minute = minute;
    this.second = second;
  }

  public DateValue(LispTree tree) {
    this.year = Integer.valueOf(tree.child(1).value);
    this.month = Integer.valueOf(tree.child(2).value);
    this.day = Integer.valueOf(tree.child(3).value);
    if (tree.children.size() > 3) {
      this.hour = Integer.valueOf(tree.child(4).value);
      this.minute = Integer.valueOf(tree.child(5).value);
      this.second = Double.valueOf(tree.child(6).value);
    } else {
      this.hour = 0;
      this.minute = 0;
      this.second = 0;
    }
  }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("date");
    tree.addChild(String.valueOf(year));
    tree.addChild(String.valueOf(month));
    tree.addChild(String.valueOf(day));
    if (hour != 0 || minute != 0 || second != 0) {
      tree.addChild(String.valueOf(hour));
      tree.addChild(String.valueOf(minute));
      tree.addChild(String.valueOf(second));
    }
    return tree;
  }

  @Override public String sortString() { return "" + year + "/" + month + "/" + day; }
  public String isoString() {
    return "" + (year == -1 ? "xxxx" : String.format("%04d", year))
        + "-" + (month == -1 ? "xx" : String.format("%02d", month))
        + "-" + (day == -1 ? "xx" : String.format("%02d", day));
  }

  @Override public int hashCode() {
    int hash = 0x7ed55d16;
    hash = hash * 0xd3a2646c + year;
    hash = hash * 0xd3a2646c + month;
    hash = hash * 0xd3a2646c + day;
    hash = hash * 0xd3a2646c + hour;
    hash = hash * 0xd3a2646c + minute;
    hash = hash * 0xd3a2646c + (new Double(second)).hashCode();
    return hash;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DateValue that = (DateValue) o;
    if (this.year != that.year) return false;
    if (this.month != that.month) return false;
    if (this.day != that.day) return false;
    if (this.hour != that.hour) return false;
    if (this.minute != that.minute) return false;
    if (this.second != that.second) return false;
    return true;
  }
}
