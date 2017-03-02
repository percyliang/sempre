package edu.stanford.nlp.sempre.interactive.blocks;

import com.google.common.collect.Lists;
import fig.basic.*;
import edu.stanford.nlp.sempre.*;
import java.util.*;
import java.util.stream.IntStream;
/**
 *
 * @author Sida Wang
 */
public final class StacksWorld {
  public static class Options {
    @Option(gloss = "Verbosity")
    public int verbose = 0;
    @Option(gloss = "When performing a join with getProperty, do we want to deduplicate?")
    public boolean joinDedup = true;
    @Option(gloss = "operating entirely in lowercase")
    public boolean ignoreCase = true;
  }
  public static Options opts = new Options();

  public static final String COLOR = "COLOR";

  private static final Random random = new Random(1);

  public enum Color {
    Cyan(0), Brown(1), Red (2), Orange(3), Yellow(4), None(5);
    private final int value;
    private static final int MAXCOLOR = 4;
    private Color(int value) { this.value = value; }
    public int intValue() { return this.value; }
    public static NumberValue fromInt(int i) { return new NumberValue(i, COLOR); }
    public static NumberValue randomColor() { 
      int i = random.nextInt(5);
      return new NumberValue(i, COLOR); 
    }
    public static NumberValue randomColor(int max) { 
      int i = random.nextInt(max);
      return new NumberValue(i, COLOR); 
    }
    public static NumberValue randomColor(int[] colors) { 
      int i = colors[random.nextInt(colors.length)];
      return new NumberValue(i, COLOR); 
    }
    public static List<NumberValue> allColors() {
      List<NumberValue> allColors = new ArrayList<>();
      for (int i=0; i<MAXCOLOR; i++)
        allColors.add(Color.fromInt(i));
      return allColors;
    }

    public NumberValue toNumVal() { return new NumberValue(this.value, COLOR); }
  }

  // give me distinct colors
  public static int[] randColors(int limColor, int numColor) {
    int[] allcolors = IntStream.range(0,limColor).toArray();
    int[] somecolors = new int[numColor];
    for (int i=0; i<numColor; i++) {
      int next = i+random.nextInt(limColor-i);
      int tmp = allcolors[i];
      allcolors[i] = allcolors[next];
      allcolors[next] = tmp;
      somecolors[i] = allcolors[i];
    }
    return somecolors;
  }

  // various tricks for generating another random wall
  private static boolean wallIsInvalid(List<List<NumberValue>> wall) {
    return false;
  }
  private static String abcToWallString(String abcstr, int[] rcolors) {
    for (int i=0; i<rcolors.length; i++)
      abcstr = abcstr.replace((char)('a'+i), Character.forDigit(rcolors[i],10));
    return abcstr;
  }

  public static String getLevel(String level) {
    List<List<NumberValue>> wall = null;
    List<List<NumberValue>> target = null;
    int countRandom = 0;
    while (target == null || wallToString(wall).equals(wallToString(target))) {
      if (countRandom++ > 10) {
        throw new RuntimeException("too many random tries without success");
      }
      else if (level.startsWith("remove") ) // basic colors and location
      {
        int[] rcolors = randColors(4,4);
        wall = randomWall(new int[]{5,6,7}, new int[]{1}, rcolors);
        List<BooleanValue> set;
        int p = random.nextInt(3);
        ContextValue context = getContextFromWall(wall);
        
        set = getTopColor(Color.randomColor(rcolors), context);
        
        
        target = removeTop(set, context);
        
      }
      else if (level.startsWith("baby") ) // basic colors and location
      {
        int[] rcolors = null;
        boolean flip = false;
        if (level.equals("babybasic")) {
          rcolors = randColors(4,3);
          wall = randomWall(new int[]{2,3}, new int[]{1}, rcolors);
          flip = false;
        }
        else if (level.equals("babystep")) {
          rcolors = randColors(4,2);
          wall = randomWall(new int[]{4,5,6}, new int[]{1}, rcolors);
          flip = false;
        } else  if (level.equals("babynot")){
          rcolors = randColors(4,2);
          wall = randomWall(new int[]{3,4,5,6}, new int[]{1}, rcolors);
          flip = true;
        } else  if (level.equals("babystack")){
          rcolors = randColors(4,3);
          wall = randomWall(new int[]{2,3,4}, new int[]{1, 2}, rcolors);
          flip = random.nextBoolean();
        }
   
        ContextValue context = getContextFromWall(wall);
        int p = random.nextInt(3);
        List<BooleanValue> set;
        if (p==0)
           set = getTopColor(Color.randomColor(rcolors), context);
        else if (p==1)
           set = leftMost(getAll(context), new NumberValue(1));
        else //if (p==2)
           set = rightMost(getAll(context), new NumberValue(1));
        if (flip) {
          set = complement(set);
        }
        p = random.nextInt(2);
        if (p==0)
          target = removeTop(set, context);
        else
          target = stackOnTop(set, Color.randomColor(rcolors), context);
      }
      
      else if (level.equals("pattern")) { //great wall
        int[] rcolors = randColors(4,3);
        wall = randomWall(new int[]{3,4,5,6}, new int[]{2}, rcolors);
        int start = random.nextInt(3);
        if (start == 0)  {
          for (int s=0; s<wall.size(); s++) {
            wall.get(s).set(0, Color.fromInt(rcolors[0]));
            wall.get(s).set(1, Color.fromInt(rcolors[s%2]));
          } 
        } else if (start == 1) {
          for (int s=0; s<wall.size(); s++)
          {
            wall.get(s).set(0, Color.fromInt(rcolors[s%2]));
            wall.get(s).set(1, Color.fromInt(rcolors[s%2]));
          } 
        } else {
          for (int s=0; s<wall.size(); s++)
          {
            wall.get(s).set(0, Color.fromInt(rcolors[s%2]));
            wall.get(s).set(1, Color.fromInt(rcolors[(s+1)%2]));
          } 
        }
        ContextValue context = getContextFromWall(wall);
        int action = random.nextInt(4);
        if (action == 0) {
            target = stackOnTop(
            getTopColor(Color.fromInt(rcolors[random.nextInt(2)]), context),
            Color.randomColor(rcolors), context);
        } else if (action == 1) {
            target = removeTop(
            getTopColor(Color.fromInt(rcolors[random.nextInt(2)]), context),
            context);
        } else {
          target = removeTop(
              getTopColor(Color.fromInt(rcolors[1]), context), context);
          context = getContextFromWall(target);
          target = stackOnTop(
              getTopColor(Color.fromInt(rcolors[0]), context),
              Color.randomColor(rcolors), context);
        }
      }
      else if (level.equals("littlehouse")) // basic colors and location
      {
        int[] rcolors = randColors(4,3);
        wall = stringToWall(abcToWallString(
            "[[c,c],[c,a,a],[c,a,a],[c,a],[c,c]]", rcolors));
        target = stringToWall(abcToWallString(
                 "[[c,c,c],"
                + "[c,a,a,c],"
                + "[c,a,a,a,c],"
                + "[c,a,a,c],"
                + "[c,c,c]]", rcolors));
      }
      else if (level.equals("logic")) { // action on color combos
        int[] rcolors = randColors(4,3);
        int[] colors2 = new int[]{rcolors[0], rcolors[1]};
        wall = randomWall(new int[]{5,6,7,8}, new int[]{2}, colors2);
        ContextValue context = getContextFromWall(wall);

        List<BooleanValue> set1 = getColor(Color.fromInt(rcolors[0]), context);
        List<BooleanValue> set2 = getColor(Color.fromInt(rcolors[1]), context);
        List<BooleanValue> randomset = randSetOperation(set1, set2);
        int action = random.nextInt(2);
        if (action == 0)
          target = stackOnTop(randomset, Color.randomColor(rcolors), context);
        if (action == 1)
          target = removeTop(randomset, context);
      }
      else if (level.equals("bigrandom")) { // big and random
        int[] rcolors = randColors(4,4);
        wall = randomWall(new int[]{2,3,4}, new int[]{1,2,3}, rcolors);
        target = randomWall(new int[]{2,3,4}, new int[]{0,1}, rcolors);
      }
      else if (level.equals("checker")) { //checkerboard
        int[] color3 = randColors(4,3);
        wall = randomWall(new int[]{4}, new int[]{2}, color3);
        for (int s=0; s<wall.size(); s++)
        {
          for (int j=0; j<wall.get(0).size(); j++)
            wall.get(s).set(j, Color.fromInt(color3[(s+j)%2]));
        }
        target = randomWall(new int[]{4}, new int[]{4}, color3);
        for (int s=0; s<target.size(); s++)
        {
          for (int j=0; j<target.get(0).size(); j++)
            target.get(s).set(j, Color.fromInt(color3[(s+j)%2]));
        }
      } 
      else if (level.equals("bottle")) { 
        int[] rcolors = randColors(4,3);
        wall = stringToWall(abcToWallString("[[b],[a],[b]]", rcolors));
        target = stringToWall(abcToWallString("[[b,a,b],[a,b,a,b],[b,a,b]]", rcolors));
      }
      else if (level.equals("triangle")) { 
        int[] rcolors = randColors(4,4);
        wall = stringToWall(abcToWallString("[[a],[a],[a],[a]]", rcolors));
        target = stringToWall(abcToWallString("[[a,b,c,d],[a,b,c],[a,b],[a]]", rcolors));
      }
      else if (level.equals("fork")) { 
        int[] rcolors = randColors(4,3);
        wall = stringToWall(abcToWallString("[[a],[b],[c],[b],[a]]", rcolors));
        target = stringToWall(abcToWallString("[[a,a],[b],[c,a,a],[b],[a,a]]", rcolors));
      } 
      else if (level.equals("ship")) { 
        int[] rcolors = randColors(4,3);
        wall = stringToWall(abcToWallString("[[c],[c,a],[c,a,a]]", rcolors));
        target = stringToWall(abcToWallString("[[c,b,a,a],[c,a,b,a],[c,a,a,b]]", rcolors));
      } else if (level.equals("heart")) { 
        wall = stringToWall("[[0],[0],[],[0],[0]]");
        target = stringToWall("[[0,0,2],[0,2,2,2],[2,2,2],[0,2,2,2],[0,0,2]]");
      } 
      else {
        throw new RuntimeException("invalid level: " + level);
      }
    }
    return wallToString(wall) + "|" + wallToString(target);
  }

  private static List<List<NumberValue>> randomWall(int[] stacks, int[] heights, int[] allowedColors) {
    List<List<NumberValue>> wall = new ArrayList<>();
    int numStack = stacks[random.nextInt(stacks.length)];
    for (int s = 0; s < numStack; s++)
    {
      int height = heights[random.nextInt(heights.length)];
      List<NumberValue> stack = new ArrayList<>();
      for (int h = 0; h < height; h++)
        stack.add(Color.randomColor(allowedColors));
      wall.add(stack);
    }
    return wall;
  }

  private StacksWorld() { }

  public static boolean colorsEqual(NumberValue c1, NumberValue c2)
  {
    if (!c1.unit.equals(COLOR) || !c2.unit.equals(COLOR)) return false;
    int c1v = (int) c1.value; int c2v = (int) c2.value;
    if (c1v != c2v) return false;
    return true;
  }

  // wallString consists of [[1,2,3],[2,1,2],[2,3,4]]
  public static List<List<NumberValue>> getWallFromContext(ContextValue context) {
    NaiveKnowledgeGraph graph = (NaiveKnowledgeGraph)context.graph;
    String wallString = ((StringValue)graph.triples.get(0).e1).value;
    return stringToWall(wallString);
  }
  //wallString consists of [[1,2,3],[2,1,2],[2,3,4]]
  public static ContextValue getContextFromWall(List<List<NumberValue>> wall) {
    String wallString = wallToString(wall);
    LispTree tree = LispTree.proto.parseFromString(
        "(context (graph NaiveKnowledgeGraph ((string " + wallString + ") (name b) (name c))))");
    return new ContextValue(tree);
  }

  public static List<List<NumberValue>> stringToWall(String wallString) {
    List<List<NumberValue>> wall = new ArrayList<>();
    List<List<Integer>> intwall = Json.readValueHard(wallString, List.class);
    //throw new RuntimeException(a.toString()+a.get(1).toString());

    for (List<Integer> intstack : intwall) {
      List<NumberValue> stack = new ArrayList<>();
      for (Integer intcube : intstack) {
        stack.add(Color.fromInt(intcube.intValue()));
      }
      wall.add(stack);
    }
    return wall;
  }

  public static String wallToString(List<List<NumberValue>> wall) {
    List<List<Integer>> intwall = new ArrayList<>();
    for (List<NumberValue> stack : wall) {
      List<Integer> intstack = new ArrayList<>();
      for (NumberValue cube : stack) {
        intstack.add((int)cube.value);
      }
      intwall.add(intstack);
    }
    return Json.writeValueAsStringHard(intwall);
  }

  // well, here are all final actions that one can take, which returns a wall
  // considering returning a List< List<List<NumberValue>> > here, of the original and the new
  public static List<List<NumberValue>> removeTop(List<BooleanValue> cubeset, ContextValue context) {
    return removeTop(getWallFromContext(context), cubeset, Color.None.toNumVal());
  }
  private static List<List<NumberValue>> removeTop(List<List<NumberValue>> wall, List<BooleanValue> cubeset, NumberValue color) {
    List<List<NumberValue>> newwall = new ArrayList<>();
    for (int s = 0; s < cubeset.size(); s++) {
      List<NumberValue> newstack = new ArrayList<>(wall.get(s));
      if (cubeset.get(s).value == true && newstack.size()>0) {
        newstack.remove(newstack.size()-1);
      }
      newwall.add(newstack);
    }
    return newwall;
  }

  // debatable if this should be a complete removal or not
  public static List<List<NumberValue>> removeAll(List<BooleanValue> cubeset, ContextValue context) {
    return removeAll(getWallFromContext(context), cubeset, Color.None.toNumVal());
  }
  private static List<List<NumberValue>> removeAll(List<List<NumberValue>> wall, List<BooleanValue> cubeset, NumberValue color) {
    List<List<NumberValue>> newwall = new ArrayList<>();
    for (int s = 0; s < cubeset.size(); s++) {
      List<NumberValue> newstack = new ArrayList<>(wall.get(s));
      if (cubeset.get(s).value == true) {
        newwall.add(new ArrayList<NumberValue>());
      } else {
        newwall.add(newstack);
      }
    }
    return newwall;
  }

  public static List<List<NumberValue>> stackOnTop(List<BooleanValue> cubeset, NumberValue color, ContextValue context) {
    return stackOnTop(getWallFromContext(context), cubeset, color);
  }
  private static List<List<NumberValue>> stackOnTop(List<List<NumberValue>> wall, List<BooleanValue> cubeset, NumberValue color) {
    List<List<NumberValue>> newwall = new ArrayList<>();
    for (int s = 0; s < cubeset.size(); s++) {
      List<NumberValue> newstack = new ArrayList<>(wall.get(s));
      if (cubeset.get(s).value == true) {
        newstack.add(color);
      }
      newwall.add(newstack);
    }
    return newwall;
  }

  public static List<List<NumberValue>> doubleTop(List<BooleanValue> cubeset, ContextValue context) {
    return doubleTop(getWallFromContext(context), cubeset);
  }
  private static List<List<NumberValue>> doubleTop(List<List<NumberValue>> wall, List<BooleanValue> cubeset) {
    List<List<NumberValue>> newwall = new ArrayList<>();
    for (int s = 0; s < cubeset.size(); s++) {
      List<NumberValue> newstack = new ArrayList<>(wall.get(s));
      if (cubeset.get(s).value == true && newstack.size()>0) {
        newstack.add(newstack.get(newstack.size()-1));
      }
      newwall.add(newstack);
    }
    return newwall;
  }

  public static List<BooleanValue> getColor(NumberValue color, ContextValue context) {
    List<BooleanValue> cubeset = new ArrayList<>();
    for (List<NumberValue> stack : getWallFromContext(context)) {
      boolean addStack = false;
      for (NumberValue cube : stack) {
        if (colorsEqual(cube, color) ) {
          addStack = true;
        }
      }
      cubeset.add(new BooleanValue(addStack));
    }
    return cubeset;
  }
  public static List<BooleanValue> getTopColor(NumberValue color, ContextValue context) {
    List<BooleanValue> cubeset = new ArrayList<>();
    for (List<NumberValue> stack : getWallFromContext(context)) {
      boolean addStack = false;
      if (stack.size() > 0) {
        NumberValue cube = stack.get(stack.size()-1);
        if (colorsEqual(cube, color) ) {
          addStack = true;
        }
      }
      cubeset.add(new BooleanValue(addStack));
    }
    return cubeset;
  }
  public static List<BooleanValue> getAll(ContextValue context) {
    List<List<NumberValue>>  wall = getWallFromContext(context);
    List<BooleanValue> retset = new ArrayList<>(wall.size());
    for (int s = 0; s < wall.size(); s++) {
      retset.add(new BooleanValue(true));
    }
    return retset;
  }
  
  public static List<BooleanValue> getNonEmpty(ContextValue context) {
    List<List<NumberValue>>  wall = getWallFromContext(context);
    List<BooleanValue> retset = new ArrayList<>(wall.size());
    for (int s = 0; s < wall.size(); s++) {
      if (wall.get(s).size() > 0)
        retset.add(new BooleanValue(true));
      else retset.add(new BooleanValue(false));
    }
    return retset;
  }
  // get greater or greater or equal then
  public static List<BooleanValue> getGEQ(NumberValue minheight, ContextValue context) {
    List<List<NumberValue>>  wall = getWallFromContext(context);
    List<BooleanValue> retset = new ArrayList<>(wall.size());
    for (int s = 0; s < wall.size(); s++) {
      if (wall.get(s).size() >= Math.round(minheight.value) )
        retset.add(new BooleanValue(true));
      else
        retset.add(new BooleanValue(false));
    }
    return retset;
  }

  // Begin UnarySetOp
  public static List<BooleanValue> complement(List<BooleanValue> set) {
    return setOperation(set, set, "complement");
  }
  public static List<BooleanValue> exceptLeftMost1(List<BooleanValue> set) {
    return setDifference(set, leftMost(set, new NumberValue(1)));
  }
  public static List<BooleanValue> leftMost1(List<BooleanValue> set) {
    return leftMost(set, new NumberValue(1, "COUNT"));
  }
  public static List<BooleanValue> rightMost1(List<BooleanValue> set) {
    return rightMost(set, new NumberValue(1, "COUNT"));
  }
  public static List<BooleanValue> leftMost(List<BooleanValue> set, NumberValue count) {
    List<BooleanValue> retset = new ArrayList<>(set.size());
    int countr = 0;
    for (int s = 0; s < set.size(); s++) {
      if (set.get(s).value && countr < Math.round(count.value)) {
        retset.add(new BooleanValue(set.get(s).value));
        countr++;
      } else {
        retset.add(new BooleanValue(false));
      }
    }
    return retset;
  }
  public static List<BooleanValue> exceptRightMost1(List<BooleanValue> set) {
    return setDifference(set, rightMost(set, new NumberValue(1)));
  }
  public static List<BooleanValue> rightMost(List<BooleanValue> set, NumberValue count) {
    return reverseStacks(leftMost(reverseStacks(set), count));
  }

  private static List<BooleanValue> reverseStacks(List<BooleanValue> set) {
    List<BooleanValue> retset = new ArrayList<>(set);
    Collections.reverse(retset);
    return retset;
  }

  // Begin BinarySetOp
  public static List<BooleanValue> setUnion(List<BooleanValue> set1, List<BooleanValue> set2) {
    return setOperation(set1, set2, "union");
  }
  public static List<BooleanValue> setIntersection(List<BooleanValue> set1, List<BooleanValue> set2) {
    return setOperation(set1, set2, "intersection");
  }
  public static List<BooleanValue> setDifference(List<BooleanValue> set1, List<BooleanValue> set2) {
    return setOperation(set1, set2, "difference");
  }

  private static <E> E randomE(List<E> list) {
    int index = random.nextInt(list.size());
    return list.get(index);
  }
  private static  List<BooleanValue> randSetOperation(List<BooleanValue> set1, List<BooleanValue> set2) {
    String op = randomE(Lists.newArrayList("intersection", "union", "difference"));
    return setOperation(set1, set2, op);
  }
  public static List<BooleanValue> setOperation(List<BooleanValue> set1, List<BooleanValue> set2, String op) {
    if (set1.size() != set2.size()) {
      throw new RuntimeException("Sets have different sizes while aggregating"); 
    }
    List<BooleanValue> retstack = new ArrayList<>();
    for (int s = 0; s < set1.size(); s++) {
      boolean val1 = set1.get(s).value;
      boolean val2 = set2.get(s).value;

      boolean opval;
      if ( op == "union" )
        opval = val1 || val2;
      else if (op == "intersection")
        opval = val1 && val2;
      else if (op == "difference")
        opval = val1 && !val2;
      else if (op == "complement")
        opval = !val1;
      else
        opval = val1;

      retstack.add(new BooleanValue(opval));
    }
    return retstack;
  }


}
