package edu.stanford.nlp.sempre.tables.alignment;

import fig.basic.LogInfo;

class ProductAlignmentComputer implements AlignmentComputer {

  private final AlignmentComputer aligner1, aligner2;

  public ProductAlignmentComputer(AlignmentComputer aligner1, AlignmentComputer aligner2) {
    this.aligner1 = aligner1;
    this.aligner2 = aligner2;
  }

  DoubleMap wordToPred, predToWord;

  @Override
  public DoubleMap align() {
    Thread t1, t2;
    t1 = new Thread(new Runnable() {
      @Override public void run() {
        LogInfo.logs("wordToPred STARTED!");
        wordToPred = aligner1.align();
      }
    });
    t2 = new Thread(new Runnable() {
      @Override public void run() {
        LogInfo.logs("predToWord STARTED!");
        predToWord = aligner2.align();
      }
    });
    LogInfo.begin_threads();
    t1.start();
    t2.start();
    try {
      t1.join();
      t2.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
      LogInfo.fail(e);
    }
    LogInfo.end_threads();
    return DoubleMap.product(wordToPred, predToWord);
  }

}
