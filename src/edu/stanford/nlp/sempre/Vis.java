package edu.stanford.nlp.sempre;

import com.google.common.base.Joiner;
import edu.stanford.nlp.sempre.vis.BeamFigures;
import edu.stanford.nlp.sempre.vis.ConfusionMatrices;
import edu.stanford.nlp.sempre.vis.ExampleDerivations;
import edu.stanford.nlp.sempre.vis.Utils;
import fig.basic.IOUtils;
import fig.basic.LispTree;
import fig.basic.LogInfo;
import fig.basic.Option;
import fig.exec.Execution;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.PrintWriter;
import java.util.*;

/**
 * Entry point for debugging and vis tools for the semantic parser.
 *
 * @author Roy Frostig
 */
public class Vis implements Runnable {
  public static final class Options {
    @Option public List<String> execPaths = null;
    @Option public int topN = 10;
    @Option public int iter = -1;
    @Option public String group = null;

    @Option public String command = "";

    @Option public String in = null;
    @Option public int width = 100;

    @Option public boolean useJson = true;
  }

  public static final Options opts = new Options();

  public static void main(String[] args) {
    Execution.run(args, "Main", new Vis(), Master.getOptionsParser());
  }

  // -- Commands --

  private void requireExecPaths() {
    if (opts.execPaths == null)
      throw new RuntimeException("Need -execPaths");
    if (opts.execPaths.isEmpty())
      return;
  }

  private void cmp() {
    requireExecPaths();
    if (opts.iter >= 0 && opts.group != null)
      new ExampleDerivations(opts.execPaths, opts.topN).write(opts.iter, opts.group);
    else
      new ExampleDerivations(opts.execPaths, opts.topN).writeAll();
  }

  private void confuse() {
    requireExecPaths();
    new ConfusionMatrices(opts.execPaths).logsAll();
  }

  private void beamfigs() {
    requireExecPaths();
    new BeamFigures(opts.execPaths).writeAll();
  }

  private void lispTreePrettyPrint() {
    String s = "";
    if (opts.in == null) {
      Scanner stdin = new Scanner(System.in);
      while (stdin.hasNextLine())
        s += stdin.nextLine();
      stdin.close();
    } else {
      s = Joiner.on("").join(IOUtils.readLinesHard(opts.in));
    }
    LogInfo.log(LispTree.proto.parseFromString(s).toStringWrap(opts.width));
  }

  public void run() {
    Execution.putOutput("vis", true);

    if ("cmp".equals(opts.command)) {
      cmp();
    } else if ("confuse".equals(opts.command)) {
      confuse();
    } else if ("beamfigs".equals(opts.command)) {
      beamfigs();
    } else if ("ltpp".equals(opts.command)) {
      lispTreePrettyPrint();
    }
  }

  @SuppressWarnings({"deprecation"})
  public static void writeExamples(int iter, String group,
                                   List<Example> examples,
                                   boolean outputPredDerivations) {
    String basePath = "preds-iter" + iter + "-" + group + ".examples";
    String outPath = Execution.getFile(basePath);
    if (outPath == null || examples.size() == 0) return;
    LogInfo.begin_track("Writing examples to %s", basePath);
    PrintWriter out = IOUtils.openOutHard(outPath);
    if (opts.useJson) {
      Json.writeValueHard(
          out,
          Collections.singletonMap("examples", examples),
          outputPredDerivations ? Example.JsonViews.WithDerivations.class : Object.class);
    } else {
      // Deprecation warnings come through here.
      for (Example ex : examples) {
        if (outputPredDerivations)
          out.println(ex.toLispTree(true).toString());
        else
          out.println(ex.toLispTree(false).toStringWrap(0, 1000));
      }
    }
    out.close();
    LogInfo.end_track();
  }

  public static List<File> getFilesPerExec(List<String> execPaths, final int iter, final String group) {
    List<File> files = new ArrayList<File>();
    for (String execPath : execPaths) {
      List<File> lsFiles = IOUtils.getFilesUnder(
          execPath, new FileFilter() {
        public boolean accept(File f) {
          if (f.isDirectory())
            return true;
          return f.getName().startsWith("preds-iter" + iter + "-" + group);
        }
      });
      for (int i = 0; i < lsFiles.size(); i++) {
        if (lsFiles.get(i).isDirectory()) {
          lsFiles.remove(i);
          i--;
        }
      }
      if (lsFiles.isEmpty())
        return null;
      sortFiles(lsFiles);
      files.add(lsFiles.get(0));
    }
    return files;
  }

  public static File getExecFile(String execPath, final int iter, final String group) {
    List<File> files = getFilesPerExec(Collections.singletonList(execPath), iter, group);
    if (files == null)
      return null;
    return files.get(0);
  }

  public static List<File> getExecIterFiles(String execPath, final String group) {
    List<File> files = new ArrayList<File>();
    for (int iter = 0; ; iter++) {
      File file = Vis.getExecFile(execPath, iter, group);
      if (file == null)
        break;
      files.add(file);
    }
    return files;
  }

  /**
   * @param files List of ".examples" files.
   * @return Iterable over rows of examples, each row taking the subsequent
   *         example from each file.
   */
  @SuppressWarnings({"deprecation"})
  public static Iterable<List<Example>> zipExamples(final List<File> files) {
    // Wish list: lazy IO in this language.
    return new edu.stanford.nlp.sempre.vis.Utils.SimpleGenerator<List<Example>>() {
      List<BufferedReader> readers = null;

      private void closeFiles() {
        if (readers != null)
          for (BufferedReader in : readers)
            edu.stanford.nlp.sempre.vis.Utils.closeHard(in);
      }

      private void openFiles() {
        readers = new ArrayList<BufferedReader>();
        for (File file : files)
          readers.add(IOUtils.openInHard(file));
      }

      @Override
      protected List<Example> computeNext() {
        if (readers == null)
          openFiles();
        List<Example> row = new ArrayList<Example>(readers.size());
        for (BufferedReader in : readers) {
          String line = edu.stanford.nlp.sempre.vis.Utils.readLineHard(in);
          if (line == null) {
            closeFiles();
            return null;
          }
          LispTree tree = LispTree.proto.parseFromString(line);
          // Deprecation warnings come through here.
          Example ex = Example.fromLispTree(tree);
          row.add(ex);
        }
        return row;
      }
    };
  }

  public static Iterable<Example> getExamples(final File file) {
    final Iterator<List<Example>> examples = zipExamples(Collections.singletonList(file)).iterator();
    return new Utils.SimpleGenerator<Example>() {
      @Override
      protected Example computeNext() {
        return examples.hasNext() ? examples.next().get(0) : null;
      }
    };
  }

  private static void sortFiles(List<File> files) {
    Collections.sort(
        files, new Comparator<File>() {
      @Override
      public int compare(File a, File b) {
        return a.getName().compareTo(b.getName());
      }
    });
  }
}
