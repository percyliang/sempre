package edu.stanford.nlp.sempre;

import java.util.List;

// Type information for each function in CallFormula.
public class CallTypeInfo {
  public final String func;
  public final List<SemType> argTypes;
  public final SemType retType;

  public CallTypeInfo(String func, List<SemType> argTypes, SemType retType) {
    this.func = func;
    this.argTypes = argTypes;
    this.retType = retType;
  }
}
