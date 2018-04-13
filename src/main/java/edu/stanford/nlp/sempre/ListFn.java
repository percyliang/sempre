package edu.stanford.nlp.sempre;

import fig.basic.LispTree;

/**
 * Takes two strings and returns their concatenation.
 *
 * @author Percy Liang
 */
public class ListFn extends SemanticFn {

  public ListFn() { }

  public void init(LispTree tree) {
    super.init(tree);
  }

  public DerivationStream call(Example ex, final Callable c) {
    return new SingleDerivationStream() {
      @Override
      public Derivation createDerivation() {
        LispTree listTree = LispTree.proto.newList();
        listTree.addChild("list");
        for (int i = 0; i < c.getChildren().size(); i++) {
          listTree.addChild(c.child(i).getFormula().toLispTree());
        }
        return new Derivation.Builder()
                .withCallable(c)
                .withListFormulaFrom(listTree)
                .createDerivation();
      }
    };
  }
//  public DerivationStream call(Example ex, final Callable c) {
//    return new SingleDerivationStream() {
//      @Override
//      public Derivation createDerivation() {
//        StringBuilder out = new StringBuilder();
//        if (!delim.equals(",")) out.append("(");
//          for (int i = 0; i < c.getChildren().size(); i++) {
//            if (i > 0) {
//              if (delim.equals(" or ") || delim.equals(" and ")) out.append(")");
//              out.append(delim);
//              if (delim.equals(" or ") || delim.equals(" and ")) out.append("(");
//            }
//            if (c.childStringValue(i) != null)
//              out.append(c.childStringValue(i));
//            else
//              out.append(c.child(i).getFormula().toString());
//          }
//        if (!delim.equals(","))  out.append(")");
//        return new Derivation.Builder()
//                .withCallable(c)
//                .withStringFormulaFrom(out.toString())
//                .createDerivation();
//      }
//    };
//  }
}
