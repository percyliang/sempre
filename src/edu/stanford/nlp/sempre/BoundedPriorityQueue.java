package edu.stanford.nlp.sempre;

import java.util.*;

/**
 * Created by joberant on 3/27/14.
 * A priority queue that holds no more than N elements
 */
public class BoundedPriorityQueue<E> extends TreeSet<E> {
  private static final long serialVersionUID = 5724671156522771658L;
  private int elementsLeft;

  public BoundedPriorityQueue(int maxSize, Comparator<E> comparator) {
    super(comparator);
    this.elementsLeft = maxSize;
  }

  /**
   * @return true if element was added, false otherwise
   * */
  @Override
  public boolean add(E e) {
    if (elementsLeft == 0 && size() == 0) {
      // max size was initiated to zero => just return false
      return false;
    } else if (elementsLeft > 0) {
      // queue isn't full => add element and decrement elementsLeft
      boolean added = super.add(e);
      if (added) {
        elementsLeft--;
      }
      return added;
    } else {
      // there is already 1 or more elements => compare to the least
      int compared = super.comparator().compare(e, this.last());
      if (compared == -1) {
        // new element is larger than the least in queue => pull the least and add new one to queue
        pollLast();
        super.add(e);
        return true;
      } else {
        // new element is less than the least in queue => return false
        return false;
      }
    }
  }

  public List<E> toList() {
    List<E> res = new ArrayList<>();
    for (E e : this)
      res.add(e);
    return res;
  }

  public static void main(String[] args) {

    BoundedPriorityQueue<Integer> queue =
            new BoundedPriorityQueue<>(5,
                    new Comparator<Integer>() {
                      @Override
                      public int compare(Integer o1, Integer o2) {
                        return o1.compareTo(o2);
                      }
                    });

    queue.add(10);
    queue.add(8);
    queue.add(4);
    queue.add(12);
    queue.add(3);
    queue.add(7);
    queue.add(9);
    for (Integer num : queue) {
      System.out.println(num);
    }
  }
}
