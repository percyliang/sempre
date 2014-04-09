package edu.stanford.nlp.sempre.paraphrase;

public class Interval {
  public int start;
  public int end;

  public Interval(int start, int end) {
    this.start = start;
    this.end = end;
  }

  public void set(int start, int end) {
    this.start = start;
    this.end = end;
  }

  public boolean superset(Interval other) {
    return start<=other.start && end>=other.end;
  }

  public boolean properSuperset(Interval other) {
    return (start<=other.start && end>other.end)  ||
        (start<other.start && end>=other.end);
  }

  public boolean subset(Interval other) {
    return other.superset(this);
  }

  public int length() {
    return end-start;
  }
  
  public double middle() {
    return (((double) start+end)/2)+1;
  }

  public boolean contains(int index) {
    return index>=start && index<end;
  }

  public String toString() {
    return "("+start+","+end+")";
  }

  public boolean equals(Object obj) {
    if(!(obj instanceof Interval))
      return false;
    Interval other = (Interval) obj;
    return start == other.start && end == other.end;
  }
}
