package edu.stanford.nlp.sempre;

import fig.basic.LispTree;

import java.util.ArrayList;
import java.util.List;


/**
 * Represents the description part of a NameValue ("Barack Obama" rather than
 * the id fb:en.barack_obama).
 *
 * @author Andrew Chou
 */
public class DescriptionValue extends Value {
  public final String value;

  public DescriptionValue(LispTree tree) {
    List<String> full_description = new ArrayList<>();
    for (LispTree child:tree.children.subList(1,tree.children.size())){
      if(child.value != null) {
        full_description.add(child.value.toString());
      }
      else{
        full_description.add(child.toString());
      }
    }
    this.value = (String.join(" ", full_description)).replaceAll("\"","");
  }
  public DescriptionValue(String value) {this.value = value; }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("description");
    tree.addChild(value);
    return tree;
  }

  @Override public int hashCode() { return value.hashCode(); }
  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DescriptionValue that = (DescriptionValue) o;
    return this.value.equals(that.value);
  }
}
