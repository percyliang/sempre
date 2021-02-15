package edu.stanford.nlp.sempre.overnight;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.sempre.StringValue;
import fig.basic.LispTree;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by joberant on 2/24/15.
 * This converts
 * (targetValue (list (name fb:en.place.walton_county)))
 * to
 * (targetValue (string [(name fb:en.place.walton_county)]))
 * This has nothing to do with 'paraphrase', it's just here for no reason
 */
public final class ConvertTargetValueFromListToString {
  private ConvertTargetValueFromListToString() { }

  public static void main(String[] args) {

    try {
      PrintWriter writer = IOUtils.getPrintWriter(args[1]);
      Iterator<LispTree> trees = LispTree.proto.parseFromFile(args[0]);
      while (trees.hasNext()) {
        LispTree tree = trees.next();

        LispTree outTree = LispTree.proto.newList();
        outTree.addChild("example");
        outTree.addChild(tree.child(1));

        List<String> output = new ArrayList<>();
        LispTree targetValue = tree.child(3);
        if (!targetValue.child(0).value.equals("targetValue"))
          throw new RuntimeException("Expected a target value as second child: " + targetValue);
        LispTree list = targetValue.child(1);
        if (!list.child(0).value.equals("list"))
          throw new RuntimeException("Expected a list as first child: " + list);
        for (int i = 1; i < list.children.size(); ++i)
          output.add(list.child(i).toString());
        StringValue newTargetValue = new StringValue(output.toString());
        LispTree newTargetValueTree = LispTree.proto.newList();
        newTargetValueTree.addChild("targetValue");
        newTargetValueTree.addChild(newTargetValue.toLispTree());
        outTree.addChild(newTargetValueTree);
        outTree.print(120, 120, writer);
        writer.println();
      }
      writer.close();
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }
}
