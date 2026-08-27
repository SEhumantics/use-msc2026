package org.tzi.use.smt.finder;

import java.util.List;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.Scenario;
import org.tzi.use.smt.config.ScenarioProfile;

/**
 * What bounded this solve, carried on every {@link ModelFinderResult} so a verdict can never be
 * read without it.
 *
 * <p>This exists because of the proposal's correctness claim 7, which is a design decision and not
 * a gap to be closed:
 *
 * <blockquote>
 * "Qualified bounded completeness: exact encodings are complete only for the configured scopes and
 * value domains. Conservative quantile intervals may omit assignments inside their boundary band,
 * so UNSAT is never presented as an unbounded or numerically exact theorem."
 * </blockquote>
 *
 * <p>and which 7 closes by ruling out the obvious way to try to escape it: "a successful
 * post-validation justifies a delivered witness; it CANNOT strengthen the deliberately qualified
 * meaning of UNSAT". So this is NOT a second oracle and no amount of re-checking would make it one.
 * It is the machine-readable form of the qualification itself, attached to the answer instead of
 * living in a document read separately from it.
 *
 * <p>The failure mode it forecloses is concrete and easy to fall into: a caller reads {@code
 * satisfiable() == false} and reports "no such model exists". With this attached the only thing
 * that verdict can honestly be rendered as is "no such model exists WITHIN these scopes, these
 * domains, this scenario policy, and this numerical policy".
 *
 * @param scenarioLabels every configured measurement scenario the profile was actually quantified
 *     over. Empty for a free-uncertainty EXISTS solve, where the solver chose the scenario itself
 *     rather than being held to a configured set.
 */
public record BoundedCompletenessQualification(
    List<ClassScope> classScopes,
    List<AssociationScope> associationScopes,
    List<AttributeDomain> attributeDomains,
    ScenarioProfile profile,
    List<String> scenarioLabels,
    String numericalPolicy) {

  /**
   * The bisection width {@code URealThresholdBoundary} encloses USE's own CDF transition to.
   * Declared here so a refutation's stated numerical policy and the encoding that produced it
   * cannot drift apart silently -- {@code TypeAndDefinednessPreservationTest} asserts they are the
   * same number.
   */
  public static final double QUANTILE_ENCLOSURE_WIDTH = 1.0e-8;

  /** The decimal precision reconstruction rounds a solved rational to. */
  public static final int RECONSTRUCTION_DECIMAL_SCALE = 10;

  /**
   * The numerical policy every U-type threshold in this slice is encoded under, in one sentence.
   *
   * <p>USE's own normal CDF is a Zelen and Severo rational-polynomial approximation and its inverse
   * a bisection search; neither is expressible to Z3. The boundary is therefore computed in Java
   * from the SAME approximation the evaluator uses, enclosed to {@link #QUANTILE_ENCLOSURE_WIDTH},
   * and rounded OUTWARD by polarity so a rounded boundary can never manufacture a witness -- which
   * is exactly why a refutation inside that band is not a refutation of anything.
   *
   * <p>{@code UInteger} is covered by the SAME sentence, not by a second policy: USE widens a
   * UInteger comparison to UReal before evaluating it, so the boundary is literally the same one.
   * The Int sort of the representative then narrows the admissible witnesses to the integers on the
   * satisfying side of that boundary, which is a restriction of the search space rather than a
   * different numerical policy.
   */
  public static final String NUMERICAL_POLICY =
      "UReal and UInteger confidence thresholds are encoded as linear inequalities against a"
          + " boundary computed in Java from USE's own CDF approximation, enclosed to a"
          + " standardized width of "
          + QUANTILE_ENCLOSURE_WIDTH
          + " and rounded outward by polarity, with solved rationals reconstructed at "
          + RECONSTRUCTION_DECIMAL_SCALE
          + " decimal places";

  public BoundedCompletenessQualification {
    classScopes = List.copyOf(classScopes);
    associationScopes = List.copyOf(associationScopes);
    attributeDomains = List.copyOf(attributeDomains);
    scenarioLabels = List.copyOf(scenarioLabels);
  }

  /** The qualification of one solve, taken from the configuration that actually bounded it. */
  public static BoundedCompletenessQualification of(
      AnalysisConfiguration config, ScenarioProfile profile, List<Scenario> scenarios) {
    return new BoundedCompletenessQualification(
        config.classScopes(),
        config.associationScopes(),
        config.attributeDomains(),
        profile,
        scenarios == null ? List.of() : scenarios.stream().map(Scenario::label).toList(),
        NUMERICAL_POLICY);
  }

  /**
   * The qualification as one sentence, for a report or a log line.
   *
   * <p>Deliberately phrased as a bound and not as a conclusion. A caller that prints this next to
   * an UNSAT cannot accidentally publish an unbounded claim.
   */
  public String statement() {
    return "this result holds only within the configured bounds: class scopes "
        + render(
            classScopes, scope -> scope.className() + "[" + scope.min() + ".." + scope.max() + "]")
        + "; association scopes "
        + render(
            associationScopes,
            scope -> scope.associationName() + "[" + scope.min() + ".." + scope.max() + "]")
        + "; attribute domains "
        + render(attributeDomains, BoundedCompletenessQualification::renderDomain)
        + "; scenario policy "
        + profile.name()
        + " over "
        + (scenarioLabels.isEmpty()
            ? "a solver-chosen measurement scenario"
            : scenarioLabels.size() + " configured scenario(s) " + scenarioLabels)
        + "; numerical policy: "
        + numericalPolicy
        + ". A refutation is a statement about these bounds, not a theorem about the model.";
  }

  private static String renderDomain(AttributeDomain domain) {
    StringBuilder rendered =
        new StringBuilder(domain.className()).append('.').append(domain.attributeName());
    if (domain.component() != null) {
      rendered.append('_').append(domain.component());
    }
    rendered.append('=');
    if (!domain.enumeratedValues().isEmpty()) {
      return rendered.append(domain.enumeratedValues()).toString();
    }
    return rendered
        .append('[')
        .append(domain.lowerBound())
        .append("..")
        .append(domain.upperBound())
        .append(']')
        .toString();
  }

  private static <T> String render(List<T> items, java.util.function.Function<T, String> renderer) {
    return items.isEmpty() ? "(none configured)" : items.stream().map(renderer).toList().toString();
  }
}
