package edu.stanford.nlp.sempre;

import fig.basic.LispTree;

/**
 * Utilities for Value.
 *
 * @author Percy Liang
 */
public final class Values {
  private Values() { }

  // Try to parse the LispTree into a value.
  // If it fails, just return null.
  public static Value fromLispTreeOrNull(LispTree tree) {
    if (tree.isLeaf())
      return null;
    String type = tree.child(0).value;
    if ("name".equals(type)) return new NameValue(tree);
    if ("boolean".equals(type)) return new BooleanValue(tree);
    if ("number".equals(type)) return new NumberValue(tree);
    if ("string".equals(type)) return new StringValue(tree);
    if ("list".equals(type)) return new ListValue(tree);
    if ("table".equals(type)) return new TableValue(tree);
    if ("description".equals(type)) return new DescriptionValue(tree);
    if ("url".equals(type)) return new UriValue(tree);
    if ("context".equals(type)) return new ContextValue(tree);
    if ("date".equals(type)) return new DateValue(tree);
    if ("error".equals(type)) return new ErrorValue(tree);
    if ("time".equals(type)) return new TimeValue(tree);
    return null;
  }

  // Try to parse.  If it fails, throw an exception.
  public static Value fromLispTree(LispTree tree) {
    Value value = fromLispTreeOrNull(tree);
    if (value == null)
      throw new RuntimeException("Invalid value: " + tree);
    return value;
  }

  public static Value fromString(String s) { return fromLispTree(LispTree.proto.parseFromString(s)); }
}
