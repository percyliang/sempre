package edu.stanford.nlp.sempre;

import fig.basic.*;

import java.util.*;

/**
 * Performs type inference: given a Formula, return a SemType.
 *
 * Use a TypeLookup class to look up types of entities and properties.
 * - The default NullTypeLookup returns the most generic types.
 * - If the Freebase schema is loaded, FreebaseTypeLookup will give
 *   the type information from the schema.
 * - Furthermore, if EntityLexicon is loaded, EntityLexicon will give
 *   even more refined types.
 *
 * Note that we just return an upper bound on the type.
 * Doesn't have to be perfect, since this is just used to prune out bad combinations.
 *
 * @author Percy Liang
 */
public final class TypeInference {
  private TypeInference() { }

  public static class Options {
    @Option(gloss = "Verbosity level") public int verbose = 1;
    @Option(gloss = "Class for looking up types") public String typeLookup = "NullTypeLookup";
  }
  public static Options opts = new Options();

  private static TypeLookup typeLookup;
  public static TypeLookup getTypeLookup() {
    if (typeLookup == null)
      typeLookup = (TypeLookup) Utils.newInstanceHard(SempreUtils.resolveClassName(opts.typeLookup));
    return typeLookup;
  }
  public static void setTypeLookup(TypeLookup typeLookup) {  // Kind of hacky, only used in tests
    TypeInference.typeLookup = typeLookup;
  }

  // For computing type of (call ...) expressions.
  private static Map<String, CallTypeInfo> callTypeInfos;
  public static void addCallTypeInfo(CallTypeInfo info) {
    if (callTypeInfos.containsKey(info.func))
      throw new RuntimeException("Already contains " + info.func);
    callTypeInfos.put(info.func, info);
  }
  private static void initCallTypeInfo() {
    if (callTypeInfos != null) return;
    callTypeInfos = new HashMap<String, CallTypeInfo>();
    addCallTypeInfo(new CallTypeInfo("Math.cos", ListUtils.newList(SemType.floatType), SemType.floatType));
    addCallTypeInfo(new CallTypeInfo(".concat", ListUtils.newList(SemType.stringType, SemType.stringType), SemType.stringType));
    addCallTypeInfo(new CallTypeInfo(".length", ListUtils.newList(SemType.stringType), SemType.intType));
    addCallTypeInfo(new CallTypeInfo(".toString", ListUtils.newList(SemType.anyType), SemType.stringType));
    // This is just a placeholder now...need to have a more systematic way of
    // putting types in (see JavaExecutor).
  }

  private static final ValueFormula<NameValue> typeFormula = new ValueFormula<NameValue>(new NameValue(CanonicalNames.TYPE));

  private static final Set<Formula> comparisonFormulas = new HashSet<>(Arrays.asList(
      new ValueFormula<NameValue>(new NameValue("<")),
      new ValueFormula<NameValue>(new NameValue(">")),
      new ValueFormula<NameValue>(new NameValue("<=")),
      new ValueFormula<NameValue>(new NameValue(">="))));

  @SuppressWarnings("serial")
  private static class TypeException extends Exception { }

  private static class Env {
    private final TypeLookup typeLookup;
    private final boolean allowFreeVariable;      // Don't throw an error if there is an unbound variable.
    private final ImmutableAssocList<String, Ref<SemType>> list;
    private Env(ImmutableAssocList<String, Ref<SemType>> list, TypeLookup typeLookup, boolean allowFreeVariable) {
      this.list = list;
      this.typeLookup = typeLookup;
      this.allowFreeVariable = allowFreeVariable;
    }
    public Env(TypeLookup typeLookup, boolean allowFreeVariable) {
      this(ImmutableAssocList.emptyList, typeLookup, allowFreeVariable);
    }

    public Env addVar(String var) {
      return new Env(list.prepend(var, new Ref<SemType>(SemType.topType)), typeLookup, allowFreeVariable);
    }
    public SemType updateType(String var, SemType type) {
      Ref<SemType> ref = list.get(var);
      if (ref == null) {
        if (!allowFreeVariable)
          throw new RuntimeException("Free variable not defined: " + var);
        else {
          // This does not save the new type to the list
          ref = new Ref<SemType>(SemType.topType);
        }
      }
      SemType newType = ref.value.meet(type);
      if (!newType.isValid() && opts.verbose >= 2)
        LogInfo.warnings("Invalid type from [%s MEET %s]", ref.value, type);
      ref.value = newType;
      return newType;
    }

    @Override
    public String toString() {
      // Used for debugging, so no need to be efficient.
      String answer = typeLookup.getClass().getSimpleName() + " {";
      ImmutableAssocList<String, Ref<SemType>> now = list;
      while (!now.isEmpty()) {
        answer += now.key + ": " + now.value;
        now = now.next;
      }
      return answer + "}";
    }
  }

  // Use the default typeLookup
  public static SemType inferType(Formula formula) {
    return inferType(formula, getTypeLookup(), false);
  }
  public static SemType inferType(Formula formula, boolean allowFreeVariable) {
    return inferType(formula, getTypeLookup(), allowFreeVariable);
  }
  public static SemType inferType(Formula formula, TypeLookup typeLookup) {
    return inferType(formula, typeLookup, false);
  }

  public static SemType inferType(Formula formula, TypeLookup typeLookup, boolean allowFreeVariable) {
    SemType type;
    try {
      type = inferType(formula, new Env(typeLookup, allowFreeVariable), SemType.topType);
    } catch (TypeException e) {
      type = SemType.bottomType;
    }
    if (opts.verbose >= 2) LogInfo.logs("TypeInference: %s => %s", formula, type);
    return type;
  }

  private static SemType check(SemType type) throws TypeException {
    if (!type.isValid()) throw new TypeException();
    return type;
  }

  // Return the type of |formula| (|type| is an upper bound on the type).
  // |env| specifies the mapping form variables to their types.  This should be updated.
  private static SemType inferType(Formula formula, Env env, SemType type) throws TypeException {
    if (opts.verbose >= 5)
      LogInfo.logs("TypeInference.inferType(%s, %s, %s)", formula, env, type);
    if (formula instanceof VariableFormula) {
      return check(env.updateType(((VariableFormula) formula).name, type));

    } else if (formula instanceof ValueFormula) {
      Value value = ((ValueFormula<?>) formula).value;
      if (value instanceof NumberValue) return check(type.meet(SemType.numberType));
      else if (value instanceof StringValue) return check(type.meet(SemType.stringType));
      else if (value instanceof DateValue) return check(type.meet(SemType.dateType));
      else if (value instanceof TimeValue) return check(type.meet(SemType.timeType));
      else if (value instanceof NameValue) {
        String id = ((NameValue) value).id;

        if (CanonicalNames.isUnary(id)) {  // Unary
          SemType unaryType = env.typeLookup.getEntityType(id);
          if (unaryType == null)
            unaryType = SemType.entityType;
          type = check(type.meet(unaryType));
        } else {  // Binary
          // Careful of the reversal.
          SemType propertyType = null;
          if (CanonicalNames.SPECIAL_SEMTYPES.containsKey(id)) {
            propertyType = CanonicalNames.SPECIAL_SEMTYPES.get(id);
          } else if (!CanonicalNames.isReverseProperty(id)) {
            propertyType = env.typeLookup.getPropertyType(id);
          } else {
            propertyType = env.typeLookup.getPropertyType(CanonicalNames.reverseProperty(id));
            if (propertyType != null) propertyType = propertyType.reverse();
          }
          if (propertyType == null)
            propertyType = SemType.anyAnyFunc;  // Don't know
          type = check(type.meet(propertyType));
        }
        return type;
      } else {
        throw new RuntimeException("Unhandled value: " + value);
      }

    } else if (formula instanceof JoinFormula) {
      JoinFormula join = (JoinFormula) formula;

      // Special case: (fb:type.object.type fb:people.person) => fb:people.person
      if (typeFormula.equals(join.relation) && join.child instanceof ValueFormula)
        return check(type.meet(SemType.newAtomicSemType(Formulas.getString(join.child))));

      // Special case: (<= (number 5)) => same type as (number 5)
      if (comparisonFormulas.contains(join.relation))
        return check(type.meet(inferType(join.child, env, SemType.numberOrDateType)));

      SemType relationType = inferType(join.relation, env, new FuncSemType(SemType.topType, type)); // Relation
      SemType childType = inferType(join.child, env, relationType.getArgType()); // Child
      relationType = inferType(join.relation, env, new FuncSemType(childType, type)); // Relation again
      return check(relationType.getRetType());

    } else if (formula instanceof MergeFormula) {
      MergeFormula merge = (MergeFormula) formula;
      type = check(type.meet(SemType.anyType));  // Must be not higher-order
      type = inferType(merge.child1, env, type);
      type = inferType(merge.child2, env, type);
      return type;

    } else if (formula instanceof MarkFormula) {
      MarkFormula mark = (MarkFormula) formula;
      env = env.addVar(mark.var);
      type = check(type.meet(SemType.anyType));  // Must be not higher-order
      type = inferType(mark.body, env, type);
      type = check(env.updateType(mark.var, type));
      return type;

    } else if (formula instanceof LambdaFormula) {
      LambdaFormula lambda = (LambdaFormula) formula;
      env = env.addVar(lambda.var);
      SemType bodyType = inferType(lambda.body, env, type.getRetType());
      SemType varType = check(env.updateType(lambda.var, type.getArgType()));
      return new FuncSemType(varType, bodyType);

    } else if (formula instanceof NotFormula) {
      NotFormula not = (NotFormula) formula;
      type = check(type.meet(SemType.anyType));  // Must be not higher-order
      return inferType(not.child, env, type);

    } else if (formula instanceof AggregateFormula) {
      AggregateFormula aggregate = (AggregateFormula) formula;
      SemType childType = inferType(aggregate.child, env, SemType.anyType);
      if (aggregate.mode == AggregateFormula.Mode.count)
        return check(SemType.numberType.meet(type));
      else
        return check(SemType.numberOrDateType.meet(type).meet(childType));

    } else if (formula instanceof ArithmeticFormula) {
      ArithmeticFormula arith = (ArithmeticFormula) formula;
      // TODO(pliang): allow date + duration
      type = inferType(arith.child1, env, type);
      type = inferType(arith.child2, env, type);
      return check(type.meet(SemType.numberOrDateType));

    } else if (formula instanceof ReverseFormula) {
      ReverseFormula reverse = (ReverseFormula) formula;
      SemType reverseType = inferType(reverse.child, env, type.reverse());
      return check(reverseType.reverse());

    } else if (formula instanceof SuperlativeFormula) {
      SuperlativeFormula superlative = (SuperlativeFormula) formula;
      inferType(superlative.rank, env, SemType.numberType);
      inferType(superlative.count, env, SemType.numberType);
      type = check(type.meet(SemType.anyType));  // Must be not higher-order
      type = inferType(superlative.head, env, type); // Head
      SemType relationType = inferType(superlative.relation, env, new FuncSemType(SemType.numberOrDateType, type)); // Relation
      type = inferType(superlative.head, env, relationType.getRetType()); // Head again
      return type;

    } else if (formula instanceof CallFormula) {
      initCallTypeInfo();
      CallFormula call = (CallFormula) formula;
      if (!(call.func instanceof ValueFormula)) return SemType.bottomType;
      Value value = ((ValueFormula<?>) call.func).value;
      if (!(value instanceof NameValue)) return SemType.bottomType;
      String func = ((NameValue) value).id;

      CallTypeInfo info = callTypeInfos.get(func);
      if (info == null) return SemType.anyType;  // Don't know

      if (info.argTypes.size() != call.args.size()) return SemType.bottomType;
      for (int i = 0; i < info.argTypes.size(); i++)
        inferType(call.args.get(i), env, info.argTypes.get(i));
      return check(type.meet(info.retType));
    } else if (formula instanceof ActionFormula) {
      initCallTypeInfo();
      return SemType.anyType;
    } else {
      throw new RuntimeException("Can't infer type of formula: " + formula);
    }
  }
}
