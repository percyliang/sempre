package edu.stanford.nlp.sempre;

import fig.basic.LispTree;
/**
 * Takes a word and returns it as an unidentified term.
 *
 * @author emlozin
 */
public class OpenTypeFn extends SemanticFn {
 boolean type = false;
 boolean rel= false;
 boolean entity= false;

 public OpenTypeFn() { }

 public OpenTypeFn(String delim) {
   if (delim.equals("type"))
     type = true;
   else if (delim.equals("relation"))
     rel = true;
   else if (delim.equals("entity"))
     entity = true;
   else
     throw new RuntimeException("Wrong mode");
 }

  public void init(LispTree tree) {
    super.init(tree);
    if (tree.child(1).value.equals("type"))
      type = true;
    else if (tree.child(1).value.equals("relation"))
      rel = true;
    else if (tree.child(1).value.equals("entity"))
      entity = true;
    else
      throw new RuntimeException("Wrong mode");
  }

  public DerivationStream call(Example ex, final Callable c) {
    return new SingleDerivationStream() {
      @Override
      public Derivation createDerivation() {
        StringBuilder out = new StringBuilder();
        if (type) out.append("OpenType'");
        if (rel) out.append("OpenRel'");
        if (entity) out.append("OpenEntity'");
        for (int i = 0; i < c.getChildren().size(); i++) {
          if (i > 0)
            out.append(" ");
          out.append(c.childStringValue(i).replaceAll("'"," "));
        }
        out.append("''");

        if (SimpleLexicon.getSingleton().lookup(out.toString().substring(out.toString().indexOf("'")+1,out.toString().indexOf("''"))).isEmpty())
              return new Derivation.Builder()
                      .withCallable(c)
                      .formula(new ValueFormula<NameValue>(new NameValue(out.toString())))
                      .type(SemType.entityType)
                      .createDerivation();
        else
              return null;
      }
    };
  }
}
