package edu.stanford.nlp.sempre.interactive.voxelurn;

public class Color {
  enum BasicColor {
    Red(0), Orange(1), Yellow(2), Green(3), Blue(4), White(6), Black(7), Pink(8), Brown(9), Gray(10), Fake(11), None(
        -5);
    private final int value;

    BasicColor(int value) {
      this.value = value;
    }

    public BasicColor fromString(String color) {
      for (BasicColor c : BasicColor.values())
        if (c.name().equalsIgnoreCase(color))
          return c;
      return BasicColor.None;
    }
  };

  public static Color Fake = new Color(BasicColor.Fake.toString());
  String colorName;
  boolean isCode = false;

  public Color(String name) {
    colorName = name.toLowerCase();
    if (name.startsWith("0x") || name.startsWith("#"))
      isCode = true;
  }

  public static Color fromString(String color) {
    return new Color(color.toLowerCase());
  }

  @Override
  public String toString() {
    return colorName.toLowerCase();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((colorName == null) ? 0 : colorName.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Color other = (Color) obj;
    if (colorName == null) {
      if (other.colorName != null)
        return false;
    } else if (!colorName.equalsIgnoreCase(other.colorName))
      return false;
    return true;
  }
}
