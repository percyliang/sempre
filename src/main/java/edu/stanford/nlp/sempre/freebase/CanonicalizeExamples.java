package edu.stanford.nlp.sempre.freebase;

import com.google.common.base.Function;
import edu.stanford.nlp.sempre.*;
import fig.basic.*;
import fig.exec.Execution;

import java.io.PrintWriter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Replaces all names (e.g., fb:m.02mjmr) with canonical identifiers (e.g., fb:en.barack_obama)
 * For creating the dataset.
 *
 * @author Percy Liang
 */
public class CanonicalizeExamples implements Runnable {
  @Option(required = true, gloss = "File mapping ids to canonical ids")
  public String canonicalIdMapPath;
  @Option(required = true, gloss = "Input path to examples to canonicalize (output to same directory) with .canonicalized extension")
  public List<String> examplePaths;

  Map<String, String> canonicalIdMap;

  private String convert(String name) {
    boolean reverse = false;
    if (name.startsWith("!")) {
      name = name.substring(1);
      reverse = true;
    }
    name = MapUtils.get(canonicalIdMap, name, name);
    if (reverse) name = "!" + name;
    return name;
  }

  public Formula convert(Formula formula) {
    return formula.map(
        new Function<Formula, Formula>() {
          public Formula apply(Formula formula) {
            String name = Formulas.getNameId(formula);
            if (name != null) {
              name = convert(name);
              return Formulas.newNameFormula(name);
            }
            return null;
          }
        });
  }

  public void run() {
    canonicalIdMap = edu.stanford.nlp.sempre.freebase.Utils.readCanonicalIdMap(canonicalIdMapPath);

    for (String inPath : examplePaths) {
      String outPath = inPath + ".canonicalized";
      LogInfo.logs("Converting %s => %s", inPath, outPath);
      Iterator<LispTree> it = LispTree.proto.parseFromFile(inPath);
      PrintWriter out = IOUtils.openOutHard(outPath);
      while (it.hasNext()) {
        LispTree tree = it.next();
        if (!"example".equals(tree.child(0).value))
          throw new RuntimeException("Bad: " + tree);
        for (int i = 1; i < tree.children.size(); i++) {
          LispTree subtree = tree.child(i);
          if ("targetFormula".equals(subtree.child(0).value)) {
            for (int j = 1; j < subtree.children.size(); j++) {
              Formula formula = Formulas.fromLispTree(subtree.child(j));
              formula = convert(formula);
              subtree.children.set(j, formula.toLispTree());  // Use converted formula
            }
          }
        }
        out.println(tree.toStringWrap(100));
      }
      out.close();
    }
  }

  public static void main(String[] args) {
    Execution.run(args, new CanonicalizeExamples());
  }
}
