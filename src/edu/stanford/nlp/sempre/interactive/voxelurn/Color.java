package edu.stanford.nlp.sempre.interactive.voxelurn;

public enum Color {
  Red(0), Orange(1), Yellow (2), Green(3), Blue(4), White(6), Black(7),
  Pink(8), Brown(9), Gray(10), Fake(11), None(-5);
  private final int value;
  private static final int MAXCOLOR = 7;
  Color(int value) { this.value = value; }

  public static Color fromString(String color) {
    for(Color c : Color.values())
      if (c.name().equalsIgnoreCase(color)) return c;
    return Color.None;
  }
}