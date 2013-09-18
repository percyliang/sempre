package edu.stanford.nlp.sempre;

import fig.basic.LispTree;

/**
 * Utilities for Value.
 *
 * @author Percy Liang
 */
public class Values {
  public static Value fromLispTree(LispTree tree) {
    if (tree.isLeaf())
      throw new RuntimeException("Invalid value: " + tree);
    String type = tree.child(0).value;
    if (type.equals("name")) return new NameValue(tree);
    if (type.equals("boolean")) return new BooleanValue(tree);
    if (type.equals("number")) return new NumberValue(tree);
    if (type.equals("string")) return new StringValue(tree);
    if (type.equals("list")) return new ListValue(tree);
    if (type.equals("description")) return new DescriptionValue(tree);
    if (type.equals("url")) return new UriValue(tree);
    if (type.equals("date")) return new DateValue(tree);
    if (type.equals("error")) return new ErrorValue(tree);
    throw new RuntimeException("Invalid value: " + tree);
  }
}
