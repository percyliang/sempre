package edu.stanford.nlp.sempre.overnight;

import edu.stanford.nlp.io.IOUtils;
import fig.basic.LispTree;
import fig.basic.LogInfo;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;

/**
 * Created by joberant on 2/22/15.
 * Takes a file with lisp trees of original and canonical utterances
 * and creates the input for the berkley aligner
 */
public final class CreateBerkeleyAlignerInputFromLispTree {
  private CreateBerkeleyAlignerInputFromLispTree() { }

  //args[0]: lisp tree file
  //args[1] output directory
  public static void main(String[] args) {

    Iterator<LispTree> trees = LispTree.proto.parseFromFile(args[0]);
    try {
      PrintWriter writerOriginal = IOUtils.getPrintWriter(args[1] + ".e");
      PrintWriter writerUtterance = IOUtils.getPrintWriter(args[1] + ".f");
      LogInfo.logs("output directory=%s", args[1]);

      int i = 0;
      while (trees.hasNext()) {
        i++;
        LispTree tree = trees.next();
        LispTree utteranceTree = tree.child(1);
        LispTree originalTree = tree.child(2);
        if (!utteranceTree.child(0).value.equals("utterance"))
          throw new RuntimeException("First child is not an utterance " + utteranceTree);
        if (!originalTree.child(0).value.equals("original"))
          throw new RuntimeException("second child is not the original " + originalTree);
        String uttearnce = utteranceTree.child(1).value;
        if (uttearnce.endsWith("?") || uttearnce.endsWith("."))
          uttearnce = uttearnce.substring(0, uttearnce.length() - 1);
        String original = originalTree.child(1).value;
        writerOriginal.println(original);
        writerUtterance.println(uttearnce);
      }
      LogInfo.logs("Numebr of trees=%s", i);
      writerOriginal.close();
      writerUtterance.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
