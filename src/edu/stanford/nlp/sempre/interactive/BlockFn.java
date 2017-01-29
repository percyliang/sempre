package edu.stanford.nlp.sempre.interactive;

import java.util.List;
import java.util.stream.Collectors;

import org.testng.collections.Lists;

import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.DerivationStream;
import edu.stanford.nlp.sempre.Example;
import edu.stanford.nlp.sempre.Formula;
import edu.stanford.nlp.sempre.ILUtils;
import edu.stanford.nlp.sempre.SemanticFn;
import edu.stanford.nlp.sempre.SingleDerivationStream;
import fig.basic.LispTree;
import fig.basic.LogInfo;
import fig.basic.Option;

/**
 * return a block sequencing all the children
 * Block here refers to a block of code, not the a voxel..
 * @author sidaw
 */
public class BlockFn extends SemanticFn {
  public static class Options {
    @Option(gloss = "verbosity") public int verbose = 0;
  }
  public static Options opts = new Options();

  List<ActionFormula.Mode> allModes = Lists.newArrayList(ActionFormula.Mode.block,
      ActionFormula.Mode.blockr, ActionFormula.Mode.isolate);
  ActionFormula.Mode mode = ActionFormula.Mode.block;
  boolean optional = true;

  public void init(LispTree tree) {
    super.init(tree);
    if (tree.child(1).value.equals("sequential")) mode = ActionFormula.Mode.sequential;
    else if (tree.child(1).value.equals("block")) mode = ActionFormula.Mode.block;
    else if (tree.child(1).value.equals("blockr")) mode = ActionFormula.Mode.blockr;
    else if (tree.child(1).value.equals("isolate")) mode = ActionFormula.Mode.isolate;
    else mode = ActionFormula.Mode.sequential;
  }

  public BlockFn(ActionFormula.Mode mode) {
    this.mode = mode;
  }
  
  public BlockFn() {
    this.mode = ActionFormula.Mode.sequential;
  }

  public DerivationStream call(final Example ex, final Callable c) {
    return new SingleDerivationStream() {
      @Override
      public Derivation createDerivation() {
        List<Derivation> args = c.getChildren();
        if (args.size() == 1) {
          Derivation onlyChild = args.get(0);
          
          if (onlyChild == null) return null;
          
          if (onlyChild.getStart() != 0  || onlyChild.getEnd() != ex.getTokens().size()) return null;
          // do not do anything to the core language
          // LogInfo.logs("BlockFn %s : %s Example.size=%d, callInfo(%d,%d)", onlyChild, mode, ex.getTokens().size(), onlyChild.getStart(), onlyChild.getEnd());
          if (onlyChild.allAnchored()) return null;
          if (!ILUtils.stripBlock(onlyChild).rule.isInduced()) return null;

          // if already blocked, do not do anything
          if ( allModes.contains(((ActionFormula)onlyChild.formula).mode)) return null;

          // default rule induction! put a block around
          if (((ActionFormula)onlyChild.formula).mode != ActionFormula.Mode.sequential) 
            return null;

          return new Derivation.Builder()
              .formula(new ActionFormula(mode, Lists.newArrayList(onlyChild.formula)))
              .withCallable(c)
              .createDerivation();
        } else return null;

//        // not used for now
//        Formula f = new ActionFormula(mode, 
//            args.stream().map(d -> d.formula).collect(Collectors.toList()));
//        Derivation res = new Derivation.Builder()
//            .formula(f)
//            .withCallable(c)
//            .createDerivation();
//        
//        return res;
      }
    };
  }
}
