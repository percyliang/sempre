package edu.stanford.nlp.sempre;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import fig.basic.LispTree;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple type system for Formulas.  SemType represents a union over base
 * types, where each base type is either
 *   - entity type, or
 *   - entity type -> base type Example of a 0-ary (for booleans) (type bool)
 *
 * Example of a unary (for Obama)
 *   (union fb:government.politician fb:government.us_president ...)
 *
 * Example of a binary (for born in) [remember, arg1 is the argument, arg0 is
 * the return type]
 *   (-> fb:location.location * fb:people.person)
 *
 * Note: type equality is not implemented, since it's better to use meet() and
 * isSupertypeOf() to exploit the finer lattice structure of the type system.
 *
 * @author Percy Liang
 */
public abstract class SemType {
  // Return whether the type is valid.
  public abstract boolean isValid();

  // Return the meet of |this| and |that|.
  public abstract SemType meet(SemType that);

  // Return whether |this| is a supertype of |that|.
  public abstract boolean isSupertypeOf(SemType that);

  // Treat |this| as a function type and apply it to the argument |that|.
  public abstract SemType apply(SemType that);

  // Return the reversed type: (s -> t) to (t -> s)
  public abstract SemType reverse();

  public abstract LispTree toLispTree();

  @JsonValue
  @Override
  public String toString() { return toLispTree().toString(); }

  @JsonCreator
  public static SemType fromString(String s) {
    return fromLispTree(LispTree.proto.parseFromString(s));
  }

  public static SemType fromLispTree(LispTree tree) {
    if (tree.isLeaf()) return new EntitySemType(tree.value);
    if ("union".equals(tree.child(0).value)) {
      UnionSemType result = new UnionSemType();
      for (int i = 1; i < tree.children.size(); i++)
        result.add(fromLispTree(tree.child(i)));
      return result;
    }
    if ("->".equals(tree.child(0).value)) {
      SemType result = fromLispTree(tree.child(tree.children.size() - 1));
      for (int i = tree.children.size() - 2; i >= 1; i--)
        result = new FuncSemType(fromLispTree(tree.child(i)), result);
      return result;
    }
    throw new RuntimeException("Invalid type: " + tree);
  }

  // Common types 
  public static final SemType bottomType = new UnionSemType();
  public static final SemType stringType = new EntitySemType(FreebaseInfo.TEXT);
  public static final SemType numberType = new EntitySemType(FreebaseInfo.NUMBER);
  public static final SemType dateType = new EntitySemType(FreebaseInfo.DATE);
  public static final SemType entityType = new EntitySemType(FreebaseInfo.ENTITY);
  // Everything (ignore cvt and boolean)
  public static final SemType topType = UnionSemType.create(stringType, numberType, dateType, entityType);
}

class EntitySemType extends SemType {
  public final String name;
  public EntitySemType(String name) { this.name = name; }
  public boolean isValid() { return true; }
  public SemType meet(SemType that) {
    if (that instanceof UnionSemType) return that.meet(this);
    if (that instanceof EntitySemType) {
      String name1 = this.name;
      String name2 = ((EntitySemType) that).name;
      //if (!name1.equals(name2)) return SemType.bottomType;
      //return this;

      // Check this and that against each other's supertypes.
      // Assume a tree-structured lattice.
      FreebaseInfo fbInfo = FreebaseInfo.getSingleton();
      if (fbInfo.getIncludedTypesInclusive(name1).contains(name2))
        return this;
      if (fbInfo.getIncludedTypesInclusive(name2).contains(name1))
        return that;
      return SemType.bottomType;
    }
    return SemType.bottomType;
  }
  public boolean isSupertypeOf(SemType that) {
    if (that instanceof UnionSemType) {
      // Note: it suffices if we're the supertype of one of the baseTypes.
      // This is not a conventional definition of union type.
      for (SemType baseType : ((UnionSemType) that).baseTypes)
        if (isSupertypeOf(baseType))
          return true;
      return false;
    }
    if (that instanceof EntitySemType) {
      String name1 = this.name;
      String name2 = ((EntitySemType) that).name;
      FreebaseInfo fbInfo = FreebaseInfo.getSingleton();
      return fbInfo.getIncludedTypesInclusive(name2).contains(name1);
    }
    return false;
  }
  public SemType apply(SemType that) { return SemType.bottomType; }
  public SemType reverse() { return SemType.bottomType; }
  public LispTree toLispTree() { return LispTree.proto.newLeaf(name); }
}

class FuncSemType extends SemType {
  public final SemType argType;
  public final SemType retType;
  public FuncSemType(SemType argType, SemType retType) {
    this.argType = argType;
    this.retType = retType;
  }
  public boolean isValid() { return true; }

  public SemType meet(SemType that) { return SemType.bottomType; }

  public boolean isSupertypeOf(SemType that) {
    if (that instanceof UnionSemType) {
      for (SemType baseType : ((UnionSemType) that).baseTypes)
        if (isSupertypeOf(baseType))
          return true;
    }
    if (that instanceof FuncSemType) {
      // Return type covaries, arg type contravaries
      // Ignore type checking on the argType (contravariance is too strict)
      //if (!((FuncSemType)that).argType.isSupertypeOf(argType)) return false;
      return retType.isSupertypeOf(((FuncSemType) that).retType);
    }
    return false;
  }

  public SemType apply(SemType that) {
    // Currently, we insist that |that| must be a subtype of |argType|.
    // This allows us to rule out allowing passing an arbitrary location to
    // something expecting a city.
    // However, this might be too strong for queries like "lived in something",
    // where "something" maps onto fb:common.topic.
    // But we assume it is rare in natural language queries to modify with
    // something so vague.
    //if (argType.meet(baseType).isValid()) return retType;
    if (argType.isSupertypeOf(that)) return retType;
    return SemType.bottomType;
  }
  public SemType reverse() { return new FuncSemType(retType, argType); }
  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("->");
    tree.addChild(argType.toLispTree());
    tree.addChild(retType.toLispTree());
    return tree;
  }
}

class UnionSemType extends SemType {
  public final List<SemType> baseTypes;
  public UnionSemType() { this.baseTypes = new ArrayList<SemType>(); }
  public boolean isValid() { return baseTypes.size() > 0; }

  public SemType meet(SemType that) {
    UnionSemType result = new UnionSemType();
    for (SemType baseType : baseTypes)
      result.add(baseType.meet(that));
    return result.simplify();
  }

  public boolean isSupertypeOf(SemType that) {
    // Compute whether there exists a baseType in this that covers (is a supertype of) that.
    // TODO: this is technically incorrect because we assume that one baseType can cover |that|.
    for (SemType baseType : baseTypes)
      if (baseType.isSupertypeOf(that))
        return true;
    return false;
  }

  public SemType apply(SemType that) {
    UnionSemType result = new UnionSemType();
    for (SemType baseType : baseTypes)
      result.add(baseType.apply(that));
    return result.simplify();
  }

  public SemType reverse() {
    UnionSemType result = new UnionSemType();
    for (SemType baseType : baseTypes)
      result.add(baseType.reverse());
    return result.simplify();
  }

  public LispTree toLispTree() {
    LispTree result = LispTree.proto.newList();
    result.addChild("union");
    for (SemType baseType : baseTypes)
      result.addChild(baseType.toLispTree());
    return result;
  }

  public SemType simplify() {
    if (baseTypes.size() == 0) return SemType.bottomType;
    if (baseTypes.size() == 1) return baseTypes.get(0);
    return this;
  }

  public UnionSemType add(SemType baseType) {
    if (baseType.isValid()) baseTypes.add(baseType);
    return this;
  }

  public static UnionSemType create(SemType... baseTypes) {
    UnionSemType result = new UnionSemType();
    for (SemType baseType : baseTypes)
      result.add(baseType);
    return result;
  }
}
