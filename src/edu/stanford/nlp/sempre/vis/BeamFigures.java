package edu.stanford.nlp.sempre.vis;

import com.google.common.base.Joiner;
import com.google.common.primitives.Ints;
import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.Example;
import edu.stanford.nlp.sempre.Vis;
import fig.basic.IOUtils;
import fig.basic.IntTriple;
import fig.basic.LogInfo;
import fig.basic.MapUtils;
import fig.exec.Execution;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** @author Roy Frostig */
public class BeamFigures {
  private final List<String> execPaths;

  public BeamFigures(List<String> execPaths) {
    this.execPaths = execPaths;
  }

  private void logFileList(List<File> files) {
    LogInfo.begin_track("Files");
    for (File file : files)
      LogInfo.logs("%s", file);
    LogInfo.end_track();
  }

  /** @param files Examples file from each iteration in turn. */
  private void writeBeamDistributions(List<File> files, String group) {
    String basePath = "beamfigs-dists-" + group + ".vis";

    LogInfo.logs("Example files: %s", files);

    List<int[]> countsPerIter = new ArrayList<int[]>();
    int iter = 0, e = 0, beamSize = 0;
    for (File file : files) {
      LogInfo.logs("Counting histogram %d.%s", iter, group);
      e = 0;
      int[] counts = new int[0];
      for (Example example : Vis.getExamples(file)) {
        final List<Derivation> beam = example.getPredDerivations();
        beamSize = Math.max(beamSize, beam.size());
        if (beamSize > counts.length)
          counts = Utils.resize(counts, beamSize);
        for (int i = 0; i < beam.size(); i++)
          if (beam.get(i).getCompatibility() == 1.0d)
            counts[i]++;
        e++;
      }
      countsPerIter.add(counts);
      iter++;
    }

    LogInfo.logs("Writing " + basePath);
    String outPath = Execution.getFile(basePath);
    PrintWriter out = IOUtils.openOutHard(outPath);

    out.printf("examples %d\n", e);
    out.printf("iters %d\n", iter);
    out.printf("beamsize %d\n", beamSize);
    for (int[] counts : countsPerIter)
      out.println(Joiner.on(' ').join(Ints.asList(counts)));

    LogInfo.logs("Done");

    out.flush();
    out.close();
  }

  /** @param files Examples file from each iteration in turn. */
  private void writeExampleMetaAndBeamItemsAndMaps(List<File> files,
                                                   String group) {
    String basePathMeta = "beamfigs-meta-" + group + ".vis";
    String basePathCorBmp = "beamfigs-corbmp-" + group + ".vis";
    String basePathItems = "beamfigs-items-" + group + ".vis";

    LogInfo.logs("Example files: %s", files);

    List<IntTriple> correctPoints = new ArrayList<IntTriple>();
    List<String> exampleMetadata = new ArrayList<String>();

    int iter = 0, e = 0, beamSize = 0;
    for (File file : files) {
      LogInfo.logs("Processing %d.%s", iter, group);
      e = 0;
      for (Example example : Vis.getExamples(file)) {
        final List<Derivation> beam = example.getPredDerivations();
        beamSize = Math.max(beamSize, beam.size());
        for (int i = 0; i < beam.size(); i++)
          if (beam.get(i).getCompatibility() == 1.0d)
            correctPoints.add(new IntTriple(iter, e, i));
        if (iter == 0) {
          exampleMetadata.add(
              String.format(
                  "%d %d %s",
                  beam.size(),
                  example.getTokens().size(),
                  Joiner.on(' ').join(example.getTokens())));
        } else {
          exampleMetadata.add("" + beam.size());
        }
        e++;
      }
      iter++;
    }

    LogInfo.logs("Writing " + basePathCorBmp);
    String outPath = Execution.getFile(basePathCorBmp);
    PrintWriter out = IOUtils.openOutHard(outPath);

    out.printf("examples %d\n", e);
    out.printf("iters %d\n", iter);
    out.printf("beamsize %d\n", beamSize);
    for (IntTriple pt : correctPoints)
      out.printf("%d %d %d\n", pt.first, pt.second, pt.third);

    out.flush();
    out.close();
    LogInfo.logs("Done");

    LogInfo.logs("Writing " + basePathMeta);
    outPath = Execution.getFile(basePathMeta);
    out = IOUtils.openOutHard(outPath);

    out.printf("examples %d\n", e);
    out.printf("iters %d\n", iter);
    out.printf("beamsize %d\n", beamSize);

    for (String s : exampleMetadata)
      out.println(s);

    out.flush();
    out.close();
    LogInfo.logs("Done");

    LogInfo.logs("Writing " + basePathItems);
    outPath = Execution.getFile(basePathItems);
    out = IOUtils.openOutHard(outPath);

    out.printf("examples %d\n", e);
    out.printf("iters %d\n", iter);
    out.printf("beamsize %d\n", beamSize);

    for (File file : files) {
      LogInfo.logs("Processing examples from %s", file);
      for (Example example : Vis.getExamples(file)) {
        final List<Derivation> beam = example.getPredDerivations();
        for (Derivation deriv : beam)
          out.printf(
              "%.5f %.5f %.5f\n",
              deriv.getCompatibility(),
              deriv.getScore(),
              deriv.getProb());
      }
    }

    out.flush();
    out.close();
    LogInfo.logs("Done");
  }

  /** @param files Example file from each iteration in turn. */
  private void writeBeamDeltas(List<File> files, String group) {
    String basePath = "beamfigs-deltas-" + group + ".vis";

    LogInfo.begin_track("Collecting deltas for all examples");
    LogInfo.logs("Example files: %s", files);

    List<List<List<Integer>>> deltasPerIterPerExample = new ArrayList<List<List<Integer>>>();
    int e = 0, iter = 0, beamSize = 0;
    for (List<Example> row : Vis.zipExamples(files)) {
      LogInfo.logs("example " + e);
      final Example targetExample = row.get(row.size() - 1);
      final Map<Derivation, Integer> targetBeamPositions = Utils.indicesOf(targetExample.getPredDerivations());
      beamSize = targetBeamPositions.size();

      List<List<Integer>> deltasPerIter = new ArrayList<List<Integer>>();
      for (iter = 0; iter < row.size(); iter++) {
        List<Integer> deltas = new ArrayList<Integer>();
        List<Derivation> beam = row.get(iter).getPredDerivations();
        for (int i = 0; i < beam.size(); i++) {
          int targetPos = MapUtils.get(targetBeamPositions, beam.get(i), targetBeamPositions.size());
          deltas.add(i - targetPos);
        }
        deltasPerIter.add(deltas);
      }
      deltasPerIterPerExample.add(deltasPerIter);
      e++;
    }

    String outPath = Execution.getFile(basePath);
    PrintWriter out = IOUtils.openOutHard(outPath);
    LogInfo.logs("Writing " + basePath);

    out.printf("examples %d\n", e);
    out.printf("iters %d\n", iter);
    out.printf("beamsize %d\n", beamSize);
    for (List<List<Integer>> deltasPerIter : deltasPerIterPerExample)
      for (List<Integer> deltas : deltasPerIter)
        out.println(Joiner.on(' ').join(deltas));

    LogInfo.logs("Done");
    out.flush();
    out.close();
  }

  public void write(String execPath) {
    for (String group : new String[]{"train", "dev"}) {
      writeBeamDistributions(Vis.getExecIterFiles(execPath, group), group);
      writeExampleMetaAndBeamItemsAndMaps(Vis.getExecIterFiles(execPath, group), group);
      writeBeamDeltas(Vis.getExecIterFiles(execPath, group), group);
    }
  }

  public void writeAll() {
    for (String execPath : execPaths)
      write(execPath);
  }
}
