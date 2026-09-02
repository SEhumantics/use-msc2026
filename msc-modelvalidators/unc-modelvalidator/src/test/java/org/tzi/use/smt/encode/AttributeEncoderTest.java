package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.Test;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SmtTerm;
import org.tzi.use.smt.solver.SolverBinary;
import org.tzi.use.smt.solver.SolverOutcome;
import org.tzi.use.smt.solver.SolverProcess;
import org.tzi.use.smt.solver.SolverResult;

public class AttributeEncoderTest {
  @Test
  public void uRealValuesAndUncertaintiesAreIndependentlyConstrained() {
    SmtScript s = new SmtScript("QF_LIRA");
    ObjectSlots reading =
        ObjectSlotEncoder.encode(s, List.of(new ClassScope("Reading", 1, 1))).get("Reading");
    AttributeDomain valueDomain =
        new AttributeDomain("Reading", "measurement", "value", List.of("0.31"), null, null);
    AttributeDomain uncertaintyDomain =
        new AttributeDomain("Reading", "measurement", "uncertainty", List.of("0.02"), null, null);

    AttributeValues values =
        AttributeEncoder.encodeUType(
            s, reading, "measurement", AttributeType.UREAL, valueDomain, uncertaintyDomain);

    assertEquals(List.of("Reading_0_measurement_value"), values.valueNames());
    assertEquals(List.of("Reading_0_measurement_uncertainty"), values.uncertaintyNames());
    s.assertThat(Smt.sym("Reading_0_exists"));
    SmtTerm wrongValue =
        Smt.not(
            Smt.eq(Smt.sym(values.valueNames().getFirst()), Smt.realLit(new BigDecimal("0.31"))));
    SmtTerm wrongUncertainty =
        Smt.not(
            Smt.eq(
                Smt.sym(values.uncertaintyNames().getFirst()),
                Smt.realLit(new BigDecimal("0.02"))));
    s.assertThat(Smt.or(List.of(wrongValue, wrongUncertainty)));

    assertEquals(SolverOutcome.UNSAT, solve(s).outcome());
  }

  @Test
  public void declaresOneValueConstantPerCandidateSlot() {
    SmtScript s = new SmtScript("QF_LIA");
    ObjectSlots b = slots(s, 3, 3);
    AttributeDomain d =
        new AttributeDomain(
            "Book", "title", null, List.of("DBforDummies", "IntrotoAI", "PrincsofNW"), null, null);
    AttributeValues v = AttributeEncoder.encode(s, b, "title", AttributeType.STRING, d);
    assertEquals(3, v.valueNames().size());
  }

  @Test
  public void stringValuesAreConstrainedToValidCandidateIndicesWhenTheOwnerExists() {
    SmtScript s = new SmtScript("QF_LIA");
    ObjectSlots b = slots(s, 1, 1);
    AttributeValues v =
        AttributeEncoder.encode(
            s,
            b,
            "title",
            AttributeType.STRING,
            new AttributeDomain("Book", "title", null, List.of("One", "Two"), null, null));
    s.assertThat(Smt.sym("Book_0_exists"));
    s.assertThat(
        Smt.eq(Smt.sym(v.valueNames().get(0)), Smt.intLit(java.math.BigInteger.valueOf(5))));
    assertEquals(SolverOutcome.UNSAT, solve(s).outcome());
  }

  @Test
  public void integerValuesRespectConfiguredBoundsWhenTheOwnerExists() {
    SmtScript s = new SmtScript("QF_LIA");
    ObjectSlots b = slots(s, 1, 1);
    AttributeValues v =
        AttributeEncoder.encode(
            s,
            b,
            "year",
            AttributeType.INTEGER,
            new AttributeDomain(
                "Book", "year", null, List.of(), new java.math.BigDecimal("1455"), null));
    s.assertThat(Smt.sym("Book_0_exists"));
    s.assertThat(
        Smt.app(
            "<", Smt.sym(v.valueNames().get(0)), Smt.intLit(java.math.BigInteger.valueOf(1455))));
    assertEquals(SolverOutcome.UNSAT, solve(s).outcome());
  }

  @Test
  public void aNonExistentOwnersValueIsUnconstrainedRatherThanForcedIntoTheDomain() {
    SmtScript s = new SmtScript("QF_LIA");
    ObjectSlots b = slots(s, 0, 1);
    AttributeValues v =
        AttributeEncoder.encode(
            s,
            b,
            "title",
            AttributeType.STRING,
            new AttributeDomain("Book", "title", null, List.of("One", "Two"), null, null));
    s.assertThat(Smt.not(Smt.sym("Book_0_exists")));
    s.assertThat(
        Smt.eq(Smt.sym(v.valueNames().get(0)), Smt.intLit(java.math.BigInteger.valueOf(999))));
    assertEquals(SolverOutcome.SAT, solve(s).outcome());
  }

  @Test
  public void emptyStringDomainFailsClosedWithClassAndAttributeNamed() {
    SmtScript s = new SmtScript("QF_LIA");
    SmtTranslationException e =
        assertThrows(
            SmtTranslationException.class,
            () ->
                AttributeEncoder.encode(
                    s,
                    slots(s, 1, 1),
                    "title",
                    AttributeType.STRING,
                    new AttributeDomain("Book", "title", null, List.of(), null, null)));
    assertTrue(e.getMessage().contains("Book.title"));
  }

  @Test
  public void malformedIntegerCandidateFailsClosedNamingTheOffendingValue() {
    SmtScript s = new SmtScript("QF_LIA");
    SmtTranslationException e =
        assertThrows(
            SmtTranslationException.class,
            () ->
                AttributeEncoder.encode(
                    s,
                    slots(s, 1, 1),
                    "year",
                    AttributeType.INTEGER,
                    new AttributeDomain("Book", "year", null, List.of("1994.0"), null, null)));
    assertTrue(e.getMessage().contains("Book.year"));
    assertTrue(e.getMessage().contains("1994.0"));
  }

  @Test
  public void malformedRealCandidateFailsClosedNamingTheOffendingValue() {
    SmtScript s = new SmtScript("QF_LIA");
    SmtTranslationException e =
        assertThrows(
            SmtTranslationException.class,
            () ->
                AttributeEncoder.encode(
                    s,
                    slots(s, 1, 1),
                    "price",
                    AttributeType.REAL,
                    new AttributeDomain("Book", "price", null, List.of("nine-ninety"), null, null)));
    assertTrue(e.getMessage().contains("Book.price"));
    assertTrue(e.getMessage().contains("nine-ninety"));
  }

  @Test
  public void nonIntegerConfiguredBoundOnAnIntegerAttributeFailsClosed() {
    SmtScript s = new SmtScript("QF_LIA");
    SmtTranslationException e =
        assertThrows(
            SmtTranslationException.class,
            () ->
                AttributeEncoder.encode(
                    s,
                    slots(s, 1, 1),
                    "year",
                    AttributeType.INTEGER,
                    new AttributeDomain(
                        "Book", "year", null, List.of(), new BigDecimal("1994.5"), null)));
    assertTrue(e.getMessage().contains("Book.year"));
  }

  @Test
  public void aBareMinMaxDomainOnAURealAttributeFailsClosedInsteadOfRawIllegalArgument() {
    SmtScript s = new SmtScript("QF_LIRA");
    ObjectSlots reading = slots(s, 1, 1, "Reading");
    SmtTranslationException e =
        assertThrows(
            SmtTranslationException.class,
            () ->
                AttributeEncoder.encode(
                    s,
                    reading,
                    "measurement",
                    AttributeType.UREAL,
                    new AttributeDomain(
                        "Reading", "measurement", null, List.of(), BigDecimal.ZERO, BigDecimal.TEN)));
    assertTrue(e.getMessage().contains("Reading.measurement"));
  }

  @Test
  public void aBareMinMaxDomainOnAUBooleanAttributeFailsClosedInsteadOfRawIllegalArgument() {
    SmtScript s = new SmtScript("QF_LIRA");
    ObjectSlots flag = slots(s, 1, 1, "Flag");
    SmtTranslationException e =
        assertThrows(
            SmtTranslationException.class,
            () ->
                AttributeEncoder.encode(
                    s,
                    flag,
                    "state",
                    AttributeType.UBOOLEAN,
                    new AttributeDomain("Flag", "state", null, List.of("true"), null, null)));
    assertTrue(e.getMessage().contains("Flag.state"));
  }

  @Test
  public void aBareMinMaxDomainOnAUStringAttributeFailsClosedInsteadOfRawIllegalArgument() {
    SmtScript s = new SmtScript("QF_LIRA");
    ObjectSlots note = slots(s, 1, 1, "Note");
    SmtTranslationException e =
        assertThrows(
            SmtTranslationException.class,
            () ->
                AttributeEncoder.encode(
                    s,
                    note,
                    "text",
                    AttributeType.USTRING,
                    new AttributeDomain("Note", "text", null, List.of("hello"), null, null)));
    assertTrue(e.getMessage().contains("Note.text"));
  }

  @Test
  public void aNonLiteralBooleanCandidateFailsClosedNamingTheOffendingValue() {
    SmtScript s = new SmtScript("QF_LIA");
    SmtTranslationException e =
        assertThrows(
            SmtTranslationException.class,
            () ->
                AttributeEncoder.encode(
                    s,
                    slots(s, 1, 1, "Widget"),
                    "flag",
                    AttributeType.BOOLEAN,
                    new AttributeDomain("Widget", "flag", null, List.of("maybe"), null, null)));
    assertTrue(e.getMessage().contains("Widget.flag"));
    assertTrue(e.getMessage().contains("maybe"));
  }

  @Test
  public void anEmptySetIntegerPoolFailsClosedWithClassAndAttributeNamed() {
    SmtScript s = new SmtScript("QF_LIA");
    SmtTranslationException e =
        assertThrows(
            SmtTranslationException.class,
            () ->
                AttributeEncoder.encode(
                    s,
                    slots(s, 1, 1, "Basket"),
                    "codes",
                    AttributeType.SET_INTEGER,
                    new AttributeDomain("Basket", "codes", null, List.of(), null, null)));
    assertTrue(e.getMessage().contains("Basket.codes"));
  }

  @Test
  public void aMalformedSetIntegerPoolElementFailsClosedNamingTheOffendingValue() {
    SmtScript s = new SmtScript("QF_LIA");
    SmtTranslationException e =
        assertThrows(
            SmtTranslationException.class,
            () ->
                AttributeEncoder.encode(
                    s,
                    slots(s, 1, 1, "Basket"),
                    "codes",
                    AttributeType.SET_INTEGER,
                    new AttributeDomain(
                        "Basket", "codes", null, List.of("1", "notAnInteger"), null, null)));
    assertTrue(e.getMessage().contains("Basket.codes"));
    assertTrue(e.getMessage().contains("notAnInteger"));
  }

  @Test
  public void naiveExistentialWithoutExistenceGuardFalselyFindsATitleCollision() {
    SmtScript s = new SmtScript("QF_LIA");
    ObjectSlots b = slots(s, 1, 2);
    AttributeValues v =
        AttributeEncoder.encode(
            s,
            b,
            "title",
            AttributeType.STRING,
            new AttributeDomain("Book", "title", null, List.of("One", "Two"), null, null));
    s.assertThat(Smt.sym("Book_0_exists"));
    s.assertThat(Smt.not(Smt.sym("Book_1_exists")));
    s.assertThat(Smt.eq(Smt.sym(v.valueNames().get(0)), Smt.sym(v.valueNames().get(1))));
    assertEquals(SolverOutcome.SAT, solve(s).outcome());
  }

  @Test
  public void existenceGuardedExistentialCorrectlyFindsNoCollisionForOneRealBook() {
    SmtScript s = new SmtScript("QF_LIA");
    ObjectSlots b = slots(s, 1, 2);
    AttributeValues v =
        AttributeEncoder.encode(
            s,
            b,
            "title",
            AttributeType.STRING,
            new AttributeDomain("Book", "title", null, List.of("One", "Two"), null, null));
    s.assertThat(Smt.sym("Book_0_exists"));
    s.assertThat(Smt.not(Smt.sym("Book_1_exists")));
    var both = Smt.and(List.of(Smt.sym("Book_0_exists"), Smt.sym("Book_1_exists")));
    var same = Smt.eq(Smt.sym(v.valueNames().get(0)), Smt.sym(v.valueNames().get(1)));
    s.assertThat(Smt.and(List.of(both, same)));
    assertEquals(SolverOutcome.UNSAT, solve(s).outcome());
  }

  private static ObjectSlots slots(SmtScript s, int min, int max) {
    return slots(s, min, max, "Book");
  }

  private static ObjectSlots slots(SmtScript s, int min, int max, String className) {
    return ObjectSlotEncoder.encode(s, List.of(new ClassScope(className, min, max)))
        .get(className);
  }

  private static SolverResult solve(SmtScript s) {
    return new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30)).run(s.toSmtLib());
  }
}
