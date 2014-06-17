package edu.stanford.nlp.sempre;

import java.util.Comparator;

import edu.stanford.nlp.sempre.FbFormulasInfo.BinaryFormulaInfo;
import edu.stanford.nlp.sempre.FbFormulasInfo.UnaryFormulaInfo;
import edu.stanford.nlp.sempre.FormulaRetriever.EntityInfo;
import edu.stanford.nlp.sempre.MergeFormula.Mode;
import fig.basic.LogInfo;

public class FormulaGenerationInfo {

  public final UnaryFormulaInfo uInfo;
  public final BinaryFormulaInfo bInfo;
  public final BinaryFormulaInfo injectedInfo;
  public final EntityInfo entityInfo1;
  public final EntityInfo entityInfo2;
  public final boolean isCount;
  public final boolean isInject;
  public final boolean isUnary;

  public FormulaGenerationInfo(BinaryFormulaInfo bInfo,BinaryFormulaInfo injectedInfo, EntityInfo entity1,
      EntityInfo entity2, UnaryFormulaInfo uInfo, boolean isCount, boolean isInject, boolean isUnary) {
    super();
    this.bInfo = bInfo;
    this.injectedInfo=injectedInfo;
    this.entityInfo1 = entity1;
    this.entityInfo2 = entity2;
    this.isCount = isCount;
    this.isInject = isInject;
    this.isUnary = isUnary;
    this.uInfo = uInfo;
  } 

  public String getQuestionWord() {
    return isCount? "how many" : "what";
  }

  /*
   * Basically looks at all fields and constructs the formula accordingly
   */
  public Formula generateFormula() {

    boolean generateAggregate=false;
    if(isCount &&
        !(bInfo.expectedType1.equals(FreebaseInfo.INT) ||
            bInfo.expectedType1.equals(FreebaseInfo.FLOAT)))
      generateAggregate=true;
    //NO INJECTION
    if(!generateAggregate && !isInject) {
      if(isUnary) {
        return new MergeFormula(MergeFormula.Mode.and,
            uInfo.formula, new JoinFormula(bInfo.formula,entityInfo1.entity));
      }
      else {
        return new JoinFormula(bInfo.formula, entityInfo1.entity);
      }
    }
    if(generateAggregate && !isInject) {
      if(!isUnary) {
        return new AggregateFormula(AggregateFormula.Mode.count, new JoinFormula(bInfo.formula, entityInfo1.entity));
      }
      else {
        return new AggregateFormula(AggregateFormula.Mode.count, new MergeFormula(MergeFormula.Mode.and,
            uInfo.formula, new JoinFormula(bInfo.formula,entityInfo1.entity)));
      }
    }
    //WITH INJECTION
    if(isInject) {
      JoinFormula bReduced = (JoinFormula) Formulas.betaReduction(new JoinFormula(bInfo.formula, entityInfo1.entity));
      JoinFormula injectionJoin = new JoinFormula(injectedInfo.formula,entityInfo2.entity);
      Formula merge = new MergeFormula(Mode.and, bReduced.child, injectionJoin);
      Formula injectedFormula;
      if(isUnary)
        injectedFormula = new MergeFormula(Mode.and,uInfo.formula,new JoinFormula(bReduced.relation, merge));
      else
        injectedFormula = new JoinFormula(bReduced.relation, merge);
      if(generateAggregate)
        return new AggregateFormula(AggregateFormula.Mode.count,injectedFormula);
      return injectedFormula;
    }
    //Should not reach this
    throw new RuntimeException("Does not support formula generation from info="+this.toString());
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("bInfo="+bInfo.formula+", ");
    sb.append("eInfo1="+entityInfo1.entity+", ");
    if(uInfo!=null) 
      sb.append("uInfo="+uInfo.formula+", ");
    if(entityInfo2!=null)
      sb.append("eInfo2="+entityInfo2.entity+", ");
    sb.append("count="+isCount);
    return sb.toString();
  }

  public void log() {
    LogInfo.logs("FormulaGenerationInfo: uFormula=%s, bFormula=%s, injectedFormula=%s, eInfo1=%s, eInfo2=%s, isCount=%s, isInject=%s, isUnary=%s",
        uInfo,bInfo,injectedInfo,entityInfo1,entityInfo2,isCount,isInject,isUnary);
  }

  public class RetrievedFormulasComparator implements Comparator<FormulaGenerationInfo> {

    @Override
    public int compare(FormulaGenerationInfo o1,
        FormulaGenerationInfo o2) {
      if(o1.entityInfo1.popularity>o2.entityInfo1.popularity)
        return -1;
      if(o1.entityInfo1.popularity<o2.entityInfo1.popularity)
        return 1;
      if(o1.bInfo.popularity>o2.bInfo.popularity)
        return -1;
      if(o1.bInfo.popularity<o2.bInfo.popularity)
        return 1;
      return 0;        
    }
  }
}