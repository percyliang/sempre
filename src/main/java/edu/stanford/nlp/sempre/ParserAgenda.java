package edu.stanford.nlp.sempre;

import fig.basic.LogInfo;
import fig.basic.PriorityQueue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Data structure for agenda in reinforcement parser
 * Created by joberant on 10/20/14.
 */
public interface ParserAgenda<PrioritizedDerivationStream> extends Iterable<PrioritizedDerivationStream> {
  void sort();
  boolean add(PrioritizedDerivationStream item, double priority);
  int size();
  void clear();
  PrioritizedDerivationStream pop();
  PrioritizedDerivationStream get(int i);
  void remove(PrioritizedDerivationStream pds, int index);
}

class ListParserAgenda implements ParserAgenda<PrioritizedDerivationStream> {

  private List<PrioritizedDerivationStream> agenda = new ArrayList<>();

  @Override
  public void sort() {
    Collections.sort(agenda);
  }

  @Override
  public boolean add(PrioritizedDerivationStream item, double priority) {
    return agenda.add(item);
  }

  @Override
  public int size() {
    return agenda.size();
  }

  @Override
  public void clear() {
    agenda.clear();
  }

  @Override
  public PrioritizedDerivationStream pop() {
   // todo - replace sort with finding max (check if makes it faster)
    sort();
    PrioritizedDerivationStream pds = agenda.get(0);
    remove(pds, 0);
    return pds;
  }

  @Override
  public PrioritizedDerivationStream get(int i) {
    return agenda.get(i);
  }

  @Override
  public void remove(PrioritizedDerivationStream pds, int index) {
    PrioritizedDerivationStream last = agenda.remove(agenda.size() - 1);
    if (last != pds)
      agenda.set(index, last);
  }

  @Override
  public Iterator<PrioritizedDerivationStream> iterator() {
    return agenda.iterator();
  }
}

class QueueParserAgenda implements ParserAgenda<PrioritizedDerivationStream> {

  private PriorityQueue<PrioritizedDerivationStream> agenda = new PriorityQueue<>();

  @Override
  public void sort() {  }

  @Override
  public boolean add(PrioritizedDerivationStream item, double priority) {
    return agenda.add(item, priority);
  }

  @Override
  public int size() {
    return agenda.size();
  }

  @Override
  public void clear() {
   // hopefully this is never called since we sample just one
    LogInfo.warning("QueueParserAgenda: clear is only called when we have more than one sample");
    while (agenda.hasNext())
      agenda.next();
  }

  @Override
  public PrioritizedDerivationStream pop() {
    return agenda.next();
  }

  @Override
  public PrioritizedDerivationStream get(int i) {
    throw new RuntimeException("Not supported");
  }

  @Override
  public void remove(PrioritizedDerivationStream pds, int index) {
    throw new RuntimeException("Not supported");
  }

  @Override
  public Iterator<PrioritizedDerivationStream> iterator() {
    throw new RuntimeException("Not supported");
  }
}
