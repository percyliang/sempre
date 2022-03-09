package edu.stanford.nlp.sempre.tables.alter;

import java.util.*;

import edu.stanford.nlp.sempre.*;

public class ValueCanonicalizer {

  public static final ErrorValue ERROR = new ErrorValue("ERROR");

  public static Value canonicalize(Value value) {
    if (value instanceof ErrorValue) {
      return ERROR;
    } else if (value instanceof ListValue) {
      List<Value> stuff = ((ListValue) value).values;
      List<Value> canonical = new ArrayList<>();
      for (Value x : stuff) {
        if (x instanceof DateValue) {
          DateValue date = (DateValue) x;
          if (date.month == -1 && date.day == -1)
            canonical.add(new NumberValue(date.year));
          else
            canonical.add(x);
        } else {
          canonical.add(x);
        }
      }
      ListValue canonList = new ListValue(canonical).getUnique();
      return (canonList.values.size() == 1) ? canonList.values.get(0) : canonList;
    } else {
      return value;    // Probably infinite value
    }
  }

}