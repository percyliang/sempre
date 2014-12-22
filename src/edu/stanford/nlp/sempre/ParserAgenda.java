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
public interface ParserAgenda<E extends Comparable> extends Iterable<E> {
  void sort();
  boolean add(E item, double priority);
  int size();
  void clear();
  E pop();
  E get(int i);
  void remove(E pds, int index);
}

class ListParserAgenda<E extends Comparable> implements ParserAgenda<E> {

  private List<E> agenda = new ArrayList<>();

  @Override
  public void sort() {
    Collections.sort(agenda);
  }

  @Override
  public boolean add(E item, double priority) {
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
  public E pop() {
   // todo - replace sort with finding max
    sort();
    E pds = agenda.get(0);
    remove(pds, 0);
    return pds;
  }

  @Override
  public E get(int i) {
    return agenda.get(i);
  }

  @Override
  public void remove(E pds, int index) {
    E last = agenda.remove(agenda.size() - 1);
    if (last != pds)
      agenda.set(index, last);
  }

  @Override
  public Iterator<E> iterator() {
    return agenda.iterator();
  }
}

class QueueParserAgenda<E extends Comparable> implements ParserAgenda<E> {

  private PriorityQueue<E> agenda = new PriorityQueue<>();

  @Override
  public void sort() {  }

  @Override
  public boolean add(E item, double priority) {
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
  public E pop() {
    return agenda.next();
  }

  @Override
  public E get(int i) {
    throw new RuntimeException("Not supported");
  }

  @Override
  public void remove(E pds, int index) {
    throw new RuntimeException("Not supported");
  }

  @Override
  public Iterator<E> iterator() {
    throw new RuntimeException("Not supported");
  }
}
