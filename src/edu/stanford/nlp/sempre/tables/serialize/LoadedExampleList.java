package edu.stanford.nlp.sempre.tables.serialize;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import fig.basic.*;

public class LoadedExampleList implements List<Example> {
  private final String path;
  private String group;
  private int size = -1, currentIndex = -1;
  private Example currentExample;

  private Iterator<LispTree> trees;

  public LoadedExampleList(String path, int maxSize) {
    this.path = path;
    LispTree metadata = resetIterator();
    LogInfo.logs("Metadata: %s", metadata);
    if (!"metadata".equals(metadata.child(0).value))
      LogInfo.fails("Dataset %s does not have metadata", path);
    for (int i = 1; i < metadata.children.size(); i++) {
      LispTree arg = metadata.child(i);
      String label = arg.child(0).value;
      if ("group".equals(label)) {
        group = arg.child(1).value;
      } else if ("size".equals(label)) {
        size = Math.min(Integer.parseInt(arg.child(1).value), maxSize);
      }
    }
    if (group == null) LogInfo.fails("Dataset %s does not specify the group", path);
    if (size < 0) LogInfo.fails("Dataset %s does not specify the size", path);
  }

  /**
   * Reset the LispTree iterator and return the metadata LispTree.
   */
  private LispTree resetIterator() {
    trees = LispTree.proto.parseFromFile(path);
    currentIndex = -1;
    return trees.next();
  }

  @Override public int size() { return size; }
  @Override public boolean isEmpty() { return size == 0; }

  @Override
  public Example get(int index) {
    if (index == currentIndex) {
      return currentExample;
    } else if (index == currentIndex + 1) {
      currentExample = readExample(trees.next());
      currentIndex++;
      return currentExample;
    } else if (index == 0) {
      resetIterator();
      return get(index);
    }
    throw new RuntimeException("Can only get examples " + currentIndex + " or " + (currentIndex + 1));
  }

  private static final Set<String> finalFields = new HashSet<>(Arrays.asList(
      "id", "utterance", "targetFormula", "targetValue", "targetValues", "context"));

  private Example readExample(LispTree tree) {
    Example.Builder b = new Example.Builder();
    if (!"example".equals(tree.child(0).value))
      LogInfo.fails("Not an example: %s", tree);

    // final fields
    for (int i = 1; i < tree.children.size(); i++) {
      LispTree arg = tree.child(i);
      String label = arg.child(0).value;
      if ("id".equals(label)) {
        b.setId(arg.child(1).value);
      } else if ("utterance".equals(label)) {
        b.setUtterance(arg.child(1).value);
      } else if ("targetFormula".equals(label)) {
        b.setTargetFormula(Formulas.fromLispTree(arg.child(1)));
      } else if ("targetValue".equals(label) || "targetValues".equals(label)) {
        if (arg.children.size() != 2)
          throw new RuntimeException("Expect one target value");
        b.setTargetValue(Values.fromLispTree(arg.child(1)));
      } else if ("context".equals(label)) {
        b.setContext(new ContextValue(arg));
      }
    }
    b.setLanguageInfo(new LanguageInfo());

    Example ex = b.createExample();

    // other fields
    for (int i = 1; i < tree.children.size(); i++) {
      LispTree arg = tree.child(i);
      String label = arg.child(0).value;
      if ("tokens".equals(label)) {
        int n = arg.child(1).children.size();
        for (int j = 0; j < n; j++)
          ex.languageInfo.tokens.add(arg.child(1).child(j).value);
      } else if ("lemmaTokens".equals(label)) {
        int n = arg.child(1).children.size();
        for (int j = 0; j < n; j++)
          ex.languageInfo.lemmaTokens.add(arg.child(1).child(j).value);
      } else if ("posTags".equals(label)) {
        int n = arg.child(1).children.size();
        for (int j = 0; j < n; j++)
          ex.languageInfo.posTags.add(arg.child(1).child(j).value);
      } else if ("nerTags".equals(label)) {
        int n = arg.child(1).children.size();
        for (int j = 0; j < n; j++)
          ex.languageInfo.nerTags.add(arg.child(1).child(j).value);
      } else if ("nerValues".equals(label)) {
        int n = arg.child(1).children.size();
        for (int j = 0; j < n; j++) {
          String value = arg.child(1).child(j).value;
          if ("null".equals(value)) value = null;
          ex.languageInfo.nerValues.add(value);
        }
      } else if ("derivations".equals(label)) {
        ex.predDerivations = new ArrayList<>();
        for (int j = 1; j < arg.children.size(); j++)
          ex.predDerivations.add(readDerivation(arg.child(j)));
      } else if (!finalFields.contains(label)) {
        throw new RuntimeException("Invalid example argument: " + arg);
      }
    }

    return ex;
  }

  private Derivation readDerivation(LispTree tree) {
    Derivation.Builder b = new Derivation.Builder()
        .cat(Rule.rootCat).start(-1).end(-1).localFeatureVector(new FeatureVector())
        .rule(Rule.nullRule).children(new ArrayList<Derivation>());
    if (!"derivation".equals(tree.child(0).value))
      LogInfo.fails("Not a derivation: %s", tree);

    for (int i = 1; i < tree.children.size(); i++) {
      LispTree arg = tree.child(i);
      String label = arg.child(0).value;
      if ("formula".equals(label)) {
        b.formula(Formulas.fromLispTree(arg.child(1)));
      } else if ("type".equals(label)) {
        b.type(SemType.fromLispTree(arg.child(1)));
      } else if ("value".equals(label)) {
        b.value(Values.fromLispTree(arg.child(1)));
      } else if (label.endsWith("values")) {
        List<Value> values = new ArrayList<>();
        for (int j = 1; j < arg.children.size(); j++) {
          values.add(Values.fromLispTree(arg.child(j)));
        }
        b.value(new ListValue(values));
      } else {
        throw new RuntimeException("Invalid derivation argument: " + arg);
      }
    }
    return b.createDerivation();
  }

  @Override
  public Iterator<Example> iterator() {
    // TODO Auto-generated method stub
    throw new RuntimeException("Not implemented!");
  }

  @Override public boolean contains(Object o) { throw new RuntimeException("Not implemented!"); }
  @Override public Object[] toArray() { throw new RuntimeException("Not implemented!"); }
  @Override public <T> T[] toArray(T[] a) { throw new RuntimeException("Not implemented!"); }
  @Override public boolean add(Example e) { throw new RuntimeException("Not implemented!"); }
  @Override public boolean remove(Object o) { throw new RuntimeException("Not implemented!"); }
  @Override public boolean containsAll(Collection<?> c) { throw new RuntimeException("Not implemented!"); }
  @Override public boolean addAll(Collection<? extends Example> c) { throw new RuntimeException("Not implemented!"); }
  @Override public boolean addAll(int index, Collection<? extends Example> c) { throw new RuntimeException("Not implemented!"); }
  @Override public boolean removeAll(Collection<?> c) { throw new RuntimeException("Not implemented!"); }
  @Override public boolean retainAll(Collection<?> c) { throw new RuntimeException("Not implemented!"); }
  @Override public void clear() { throw new RuntimeException("Not implemented!"); }
  @Override public Example set(int index, Example element) { throw new RuntimeException("Not implemented!"); }
  @Override public void add(int index, Example element) { throw new RuntimeException("Not implemented!"); }
  @Override public Example remove(int index) { throw new RuntimeException("Not implemented!"); }
  @Override public int indexOf(Object o) { throw new RuntimeException("Not implemented!"); }
  @Override public int lastIndexOf(Object o) { throw new RuntimeException("Not implemented!"); }
  @Override public ListIterator<Example> listIterator() { throw new RuntimeException("Not implemented!"); }
  @Override public ListIterator<Example> listIterator(int index) { throw new RuntimeException("Not implemented!"); }
  @Override public List<Example> subList(int fromIndex, int toIndex) { throw new RuntimeException("Not implemented!"); }
}
