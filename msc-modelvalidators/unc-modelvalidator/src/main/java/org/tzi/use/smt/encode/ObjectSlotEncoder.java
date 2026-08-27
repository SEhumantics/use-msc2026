package org.tzi.use.smt.encode;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SmtSort;
import org.tzi.use.smt.solver.SmtTerm;

/**
 * Declares bounded candidate object slots with existence-count and symmetry-breaking constraints.
 *
 * <p>Each slot also carries the OBJECT NAME it stands for: the configured identity when the scope
 * predefines one for that index, and otherwise the generated {@code ClassName + index} spelling.
 * Predefined names are assigned to the LEADING slots in order, which is both what {@code
 * ClassConfigurator.generateObjectsTuple} does in the incumbent and what makes the prefix-shaped
 * existence constraint here line up with them: the first {@code min} slots always exist, so the
 * first {@code min} configured identities are always realised.
 */
public final class ObjectSlotEncoder {
  private ObjectSlotEncoder() {}

  public static Map<String, ObjectSlots> encode(SmtScript script, List<ClassScope> scopes) {
    Map<String, ObjectSlots> result = new LinkedHashMap<>();
    for (ClassScope scope : scopes) {
      result.put(scope.className(), encodeOne(script, scope));
    }
    return result;
  }

  private static ObjectSlots encodeOne(SmtScript script, ClassScope scope) {
    if (scope.min() < 0 || scope.max() < scope.min()) {
      throw new IllegalArgumentException(
          "invalid class scope for '"
              + scope.className()
              + "': min="
              + scope.min()
              + ", max="
              + scope.max());
    }
    List<String> slotNames = new ArrayList<>();
    List<String> existsNames = new ArrayList<>();
    List<String> objectNames = new ArrayList<>();
    List<SmtTerm> existsTerms = new ArrayList<>();
    for (int index = 0; index < scope.max(); index++) {
      String slotName = scope.className() + "_" + index;
      String existsName = slotName + "_exists";
      script.declareConst(slotName, SmtSort.INT);
      script.declareConst(existsName, SmtSort.BOOL);
      slotNames.add(slotName);
      existsNames.add(existsName);
      objectNames.add(
          index < scope.objectNames().size()
              ? scope.objectNames().get(index)
              : scope.className() + index);
      existsTerms.add(Smt.sym(existsName));
    }
    assertCount(script, existsTerms, scope.min(), scope.max());
    assertSymmetryBreaking(script, existsTerms);
    return new ObjectSlots(scope.className(), slotNames, existsNames, objectNames);
  }

  private static void assertCount(SmtScript script, List<SmtTerm> existsTerms, int min, int max) {
    SmtTerm count = sumOfBooleans(existsTerms);
    script.assertThat(Smt.app(">=", count, Smt.intLit(BigInteger.valueOf(min))));
    script.assertThat(Smt.app("<=", count, Smt.intLit(BigInteger.valueOf(max))));
  }

  private static SmtTerm sumOfBooleans(List<SmtTerm> existsTerms) {
    List<SmtTerm> asIntegers =
        existsTerms.stream()
            .map(term -> Smt.ite(term, Smt.intLit(BigInteger.ONE), Smt.intLit(BigInteger.ZERO)))
            .toList();
    if (asIntegers.isEmpty()) return Smt.intLit(BigInteger.ZERO);
    SmtTerm total = asIntegers.getFirst();
    for (int i = 1; i < asIntegers.size(); i++) total = Smt.app("+", total, asIntegers.get(i));
    return total;
  }

  private static void assertSymmetryBreaking(SmtScript script, List<SmtTerm> existsTerms) {
    for (int index = 1; index < existsTerms.size(); index++) {
      script.assertThat(Smt.app("=>", existsTerms.get(index), existsTerms.get(index - 1)));
    }
  }
}
