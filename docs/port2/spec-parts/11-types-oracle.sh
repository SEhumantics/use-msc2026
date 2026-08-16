#!/usr/bin/env bash
# 11-types-oracle.sh — rebuilds the executable conformance oracle used by 11-types.md.
#
# READ-ONLY with respect to both repositories: it only copies sources out of the
# reference fork / use-core into $WORK (default: a fresh temp dir) and compiles
# them there. It never invokes Maven and never writes into any target/ directory.
#
# Usage:  bash docs/port2/spec-parts/11-types-oracle.sh [workdir]
set -euo pipefail

REPO=/home/xoruser/msc-4/use-msc2026
FORK="$REPO/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use"
BASE="$REPO/use-core/src/main/java/org/tzi/use"
WORK="${1:-$(mktemp -d)}"

echo "workdir: $WORK"
rm -rf "$WORK/fork" "$WORK/base"
mkdir -p "$WORK/fork/src/org/tzi/use/uml/ocl/type" "$WORK/fork/src/org/tzi/use/util" "$WORK/fork/out"
mkdir -p "$WORK/base/src/org/tzi/use/uml/ocl/type" "$WORK/base/src/org/tzi/use/util" "$WORK/base/out"

# ---- verbatim sources ------------------------------------------------------
cp "$FORK/util/BufferedToString.java" "$WORK/fork/src/org/tzi/use/util/"
for f in Type TypeImpl BasicType BooleanType IntegerType RealType StringType \
         UnlimitedNaturalType OclAnyType VoidType UniqueLeastCommonSupertypeDeterminator \
         UncertainType UncertainBooleanType UBooleanType UIntegerType URealType \
         UStringType SBooleanType; do
  cp "$FORK/uml/ocl/type/$f.java" "$WORK/fork/src/org/tzi/use/uml/ocl/type/"
done

cp "$BASE/util/BufferedToString.java" "$WORK/base/src/org/tzi/use/util/"
for f in Type TypeImpl BasicType BooleanType IntegerType RealType StringType \
         UnlimitedNaturalType OclAnyType VoidType UniqueLeastCommonSupertypeDeterminator; do
  cp "$BASE/uml/ocl/type/$f.java" "$WORK/base/src/org/tzi/use/uml/ocl/type/"
done

# ---- reduced TypeFactory (fork) -------------------------------------------
# Identical field declarations, static-block content and mk* bodies to the fork
# original; only the collection / enum / tuple / message factories are dropped,
# because no conformsTo() or allSupertypes() body of a simple type calls them.
cat > "$WORK/fork/src/org/tzi/use/uml/ocl/type/TypeFactory.java" <<'EOF'
package org.tzi.use.uml.ocl.type;
import java.util.HashMap; import java.util.Map;
public final class TypeFactory {
    private static final Map<String, Type> buildInTypesMap = new HashMap<String, Type>();
    private static final IntegerType integerType = new IntegerType();
    private static final UnlimitedNaturalType unlimitedNaturalType = new UnlimitedNaturalType();
    private static final RealType realType = new RealType();
    private static final URealType uRealType = new URealType();
    private static final StringType stringType = new StringType();
    private static final UStringType uStringType = new UStringType();
    private static final UBooleanType uBooleanType = new UBooleanType();
    private static final BooleanType booleanType = new BooleanType();
    private static final OclAnyType oclAnyType = new OclAnyType();
    private static final VoidType voidType = new VoidType();
    private static final UIntegerType uIntegerType = new UIntegerType();
    private static final SBooleanType sBooleanType = new SBooleanType();
    static {
        buildInTypesMap.put("Integer", integerType);
        buildInTypesMap.put("UInteger", uIntegerType);
        buildInTypesMap.put("UnlimitedNatural", unlimitedNaturalType);
        buildInTypesMap.put("String", stringType);
        buildInTypesMap.put("UString", uStringType);
        buildInTypesMap.put("SBoolean", sBooleanType);
        buildInTypesMap.put("UBoolean", uBooleanType);
        buildInTypesMap.put("Boolean", booleanType);
        buildInTypesMap.put("UReal", uRealType);
        buildInTypesMap.put("Real", realType);
        buildInTypesMap.put("OclAny", oclAnyType);
        buildInTypesMap.put("OclVoid", voidType);
    }
    private TypeFactory() {}
    public static IntegerType mkInteger() { return integerType; }
    public static UIntegerType mkUInteger() { return uIntegerType; }
    public static UnlimitedNaturalType mkUnlimitedNatural() { return unlimitedNaturalType; }
    public static RealType mkReal() { return realType; }
    public static Type mkUReal() { return uRealType; }
    public static StringType mkString() { return stringType; }
    public static UStringType mkUString() { return uStringType; }
    public static BooleanType mkBoolean() { return booleanType; }
    public static UBooleanType mkUBoolean() { return uBooleanType; }
    public static SBooleanType mkSBoolean() { return sBooleanType; }
    public static OclAnyType mkOclAny() { return oclAnyType; }
    public static VoidType mkVoidType() { return voidType; }
    public static Type mkSimpleType(String n) {
        return buildInTypesMap.containsKey(n) ? buildInTypesMap.get(n) : null;
    }
}
EOF

# ---- reduced TypeFactory (7.5.0 baseline) ---------------------------------
cat > "$WORK/base/src/org/tzi/use/uml/ocl/type/TypeFactory.java" <<'EOF'
package org.tzi.use.uml.ocl.type;
import java.util.HashMap; import java.util.Map;
public final class TypeFactory {
    private static final Map<String, Type> buildInTypesMap = new HashMap<String, Type>();
    private static final IntegerType integerType = new IntegerType();
    private static final UnlimitedNaturalType unlimitedNaturalType = new UnlimitedNaturalType();
    private static final RealType realType = new RealType();
    private static final StringType stringType = new StringType();
    private static final BooleanType booleanType = new BooleanType();
    private static final OclAnyType oclAnyType = new OclAnyType();
    private static final VoidType voidType = new VoidType();
    static {
        buildInTypesMap.put("Integer", integerType);
        buildInTypesMap.put("UnlimitedNatural", unlimitedNaturalType);
        buildInTypesMap.put("String", stringType);
        buildInTypesMap.put("Boolean", booleanType);
        buildInTypesMap.put("Real", realType);
        buildInTypesMap.put("OclAny", oclAnyType);
        buildInTypesMap.put("OclVoid", voidType);
    }
    private TypeFactory() {}
    public static IntegerType mkInteger() { return integerType; }
    public static UnlimitedNaturalType mkUnlimitedNatural() { return unlimitedNaturalType; }
    public static RealType mkReal() { return realType; }
    public static StringType mkString() { return stringType; }
    public static BooleanType mkBoolean() { return booleanType; }
    public static OclAnyType mkOclAny() { return oclAnyType; }
    public static VoidType mkVoidType() { return voidType; }
    public static Type mkSimpleType(String n) { return buildInTypesMap.get(n); }
}
EOF

# ---- drivers ---------------------------------------------------------------
cat > "$WORK/fork/src/Dump.java" <<'EOF'
import org.tzi.use.uml.ocl.type.*;
import java.util.*;
public class Dump {
  static LinkedHashMap<String,Type> types() {
    LinkedHashMap<String,Type> t = new LinkedHashMap<>();
    t.put("UBoolean", TypeFactory.mkUBoolean());
    t.put("UInteger", TypeFactory.mkUInteger());
    t.put("UReal", TypeFactory.mkUReal());
    t.put("UString", TypeFactory.mkUString());
    t.put("SBoolean", TypeFactory.mkSBoolean());
    t.put("Boolean", TypeFactory.mkBoolean());
    t.put("Integer", TypeFactory.mkInteger());
    t.put("Real", TypeFactory.mkReal());
    t.put("String", TypeFactory.mkString());
    t.put("OclVoid", TypeFactory.mkVoidType());
    t.put("OclAny", TypeFactory.mkOclAny());
    t.put("UnlimitedNatural", TypeFactory.mkUnlimitedNatural());
    return t;
  }
  public static void main(String[] a) {
    LinkedHashMap<String,Type> ts = types(); int yes = 0, n = 0;
    for (Map.Entry<String,Type> x : ts.entrySet())
      for (Map.Entry<String,Type> y : ts.entrySet()) {
        boolean r = x.getValue().conformsTo(y.getValue()); n++; if (r) yes++;
        System.out.println("CONF " + x.getKey() + "," + y.getKey() + "," + r);
      }
    System.out.println("# conformsTo true = " + yes + " of " + n);
    for (Map.Entry<String,Type> x : ts.entrySet()) {
      try { List<String> l = new ArrayList<>();
            for (Type q : x.getValue().allSupertypes()) l.add(q.toString());
            Collections.sort(l); System.out.println("SUP " + x.getKey() + " " + l); }
      catch (Throwable e) { System.out.println("SUP " + x.getKey() + " THROWS " + e.getClass().getSimpleName()); }
    }
    for (Map.Entry<String,Type> x : ts.entrySet())
      for (Map.Entry<String,Type> y : ts.entrySet()) {
        String r; try { Type z = x.getValue().getLeastCommonSupertype(y.getValue());
                        r = (z == null ? "null" : z.toString()); }
                  catch (Throwable e) { r = "EXC:" + e.getClass().getSimpleName(); }
        System.out.println("LCS " + x.getKey() + "," + y.getKey() + "," + r);
      }
  }
}
EOF
# the 7.5.0 driver is the fork driver minus the five uncertainty rows
grep -v 'TypeFactory\.mk\(UBoolean\|UInteger\|UReal\|UString\|SBoolean\)()' \
    "$WORK/fork/src/Dump.java" > "$WORK/base/src/Dump.java"

javac -nowarn -d "$WORK/fork/out" $(find "$WORK/fork/src" -name '*.java')
javac -nowarn -d "$WORK/base/out" $(find "$WORK/base/src" -name '*.java')

echo "===== FORK (USE-Uncertainty) ====="
java -cp "$WORK/fork/out" Dump | tee "$WORK/fork.txt" | grep -E '^(# |SUP )'
echo "===== 7.5.0 BASELINE ====="
java -cp "$WORK/base/out" Dump | tee "$WORK/base.txt" | grep -E '^(# |SUP )'
echo "===== classic 7x7 block: fork vs 7.5.0 ====="
C='Boolean|Integer|Real|String|OclVoid|OclAny|UnlimitedNatural'
grep -E "^(CONF|LCS) ($C)," "$WORK/fork.txt" | awk -F, -v c="$C" '$2 ~ "^("c")$"' | sort > "$WORK/f7"
grep -E "^(CONF|LCS) ($C)," "$WORK/base.txt" | awk -F, -v c="$C" '$2 ~ "^("c")$"' | sort > "$WORK/b7"
diff "$WORK/b7" "$WORK/f7" && echo "IDENTICAL — the uncertainty extension changes no classic-type cell"
