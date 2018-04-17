package edu.stanford.nlp.sempre.tables.serialize;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;

import edu.stanford.nlp.sempre.*;
import fig.basic.*;

/**
 * Lazily read and construct examples from a dump file.
 *
 * The process is fast if the examples are read sequentially.
 *
 * @author ppasupat
 */
public class LazyLoadedExampleList implements List<Example> {
  public static class Options {
    @Option(gloss = "whether to ensure thread safety (makes things slower)")
    public boolean threadSafe = false;
  }
  public static Options opts = new Options();

  private final List<String> paths;
  private final List<Integer> sizes;
  private final List<Integer> offsets;
  private final List<Integer> exampleIndexToPathIndex;
  private final int size;
  // Whether each file contains only a single example (faster)
  private final boolean single;

  private LazyLoadedExampleListIterator defaultIterator;

  public LazyLoadedExampleList(String path, int maxSize) {
    this(Collections.singletonList(path), maxSize);
  }

  public LazyLoadedExampleList(List<String> paths, int maxSize) {
    this(paths, maxSize, false);
  }

  public LazyLoadedExampleList(List<String> paths, int maxSize, boolean single) {
    this.paths = new ArrayList<>(paths);
    this.single = single;
    // Combined the number of examples from all files
    this.sizes = new ArrayList<>();
    this.offsets = new ArrayList<>();
    this.exampleIndexToPathIndex = new ArrayList<>();
    int size = 0;
    for (int pathIndex = 0; pathIndex < paths.size(); pathIndex++) {
      String path = paths.get(pathIndex);
      if (single) {
        sizes.add(1);
        exampleIndexToPathIndex.add(pathIndex);
        offsets.add(size);
        size++;
      } else {
        int thisSize = readSizeFromMetadata(LispTree.proto.parseFromFile(path).next());
        sizes.add(thisSize);
        for (int i = 0; i < thisSize; i++)
          exampleIndexToPathIndex.add(pathIndex);
        offsets.add(size);
        size += thisSize;
      }
    }
    this.size = Math.min(size, maxSize);
    LogInfo.logs("(LazyLoadedExampleList) Dataset size: %d", this.size);
    defaultIterator = new LazyLoadedExampleListIterator();
  }

  public List<String> getPaths() { return paths; }

  @Override public int size() { return size; }
  @Override public boolean isEmpty() { return size == 0; }

  // ============================================================
  // Iterator
  // ============================================================

  public class LazyLoadedExampleListIterator implements Iterator<Example> {
    Iterator<LispTree> trees = null;
    private int currentPathIndex = -1, currentIndex = -1;
    private Example currentExample = null;

    @Override
    public boolean hasNext() {
      return currentIndex + 1 < size;   // size could be affected by MaxExampleForGroup
    }

    @Override
    public Example next() {
      currentIndex++;
      while (trees == null || !trees.hasNext()) {
        trees = LispTree.proto.parseFromFile(paths.get(++currentPathIndex));
        trees.next();     // Skip metadata
      }
      return currentExample = readExample(trees.next());
    }

    public Example seek(int index) {
      if (index < 0 || index >= size)
        throw new IndexOutOfBoundsException("Array size: " + size + "; No index " + index);
      int pathIndex = exampleIndexToPathIndex.get(index);
      if (pathIndex != currentPathIndex || currentIndex > index) {
        currentPathIndex = pathIndex;
        trees = LispTree.proto.parseFromFile(paths.get(currentPathIndex));
        trees.next();     // Skip metadata
        currentIndex = offsets.get(pathIndex) - 1;
      }
      while (currentIndex < index) {
        currentIndex++;
        LispTree tree = trees.next();
        if (currentIndex == index)
          currentExample = readExample(tree);
      }
      return currentExample;
    }

    public int getCurrentIndex() { return currentIndex; }
    public Example getCurrentExample() { return currentExample; }
  }

  @Override
  public Iterator<Example> iterator() {
    return new LazyLoadedExampleListIterator();
  }

  @Override
  public Example get(int index) {
    if (opts.threadSafe)
      return new LazyLoadedExampleListIterator().seek(index);
    return defaultIterator.seek(index);
  }

  public List<Example> loadAll() {
    List<Example> examples = new ArrayList<>();
    Iterator<Example> itr = iterator();
    while (itr.hasNext())
      examples.add(itr.next());
    return examples;
  }

  public List<String> getAllIds() {
    List<String> ids = new ArrayList<>();
    for (String path : paths) {
      if (single) {
        Matcher matcher = SerializedDataset.GZ_PATTERN.matcher(new File(path).getName());
        matcher.matches();
        ids.add(matcher.group(3));
      } else {
        Iterator<LispTree> trees = LispTree.proto.parseFromFile(path);
        while (trees.hasNext()) {
          LispTree tree = trees.next();
          String exampleId = getExampleId(tree);
          if (exampleId != null) ids.add(exampleId);
        }
      }
    }
    return ids;
  }

  // ============================================================
  // Read Metadata
  // ============================================================

  private int readSizeFromMetadata(LispTree tree) {
    if (!"metadata".equals(tree.child(0).value))
      throw new RuntimeException("Not metadata: " + tree);
    for (int i = 1; i < tree.children.size(); i++) {
      LispTree arg = tree.child(i);
      if ("size".equals(arg.child(0).value))
        return Integer.parseInt(arg.child(1).value);
    }
    throw new RuntimeException("Size not specified: " + tree);
  }

  // ============================================================
  // LispTree --> Example
  // ============================================================

  private static final Set<String> finalFields = new HashSet<>(Arrays.asList(
      "id", "utterance", "targetFormula", "targetValue", "targetValues", "context"));

  private String getExampleId(LispTree tree) {
    if (!"example".equals(tree.child(0).value)) return null;
    for (int i = 1; i < tree.children.size(); i++) {
      LispTree arg = tree.child(i);
      if ("id".equals(arg.child(0).value)) {
        return arg.child(1).value;
      }
    }
    // The ID is missing. Throw an error.
    String treeS = tree.toString();
    treeS = treeS.substring(0, Math.min(140, treeS.length()));
    throw new RuntimeException("Example does not have an ID: " + treeS);
  }

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

  public static final String SERIALIZED_ROOT = "$SERIALIZED_ROOT";

  private Derivation readDerivation(LispTree tree) {
    Derivation.Builder b = new Derivation.Builder()
    .cat(SERIALIZED_ROOT).start(-1).end(-1).localFeatureVector(new FeatureVector())
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
      } else if ("canonicalUtterance".equals(label)) {
        b.canonicalUtterance(arg.child(1).value);
      } else {
        throw new RuntimeException("Invalid derivation argument: " + arg);
      }
    }
    return b.createDerivation();
  }

  // ============================================================
  // Unimplemented methods
  // ============================================================

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
