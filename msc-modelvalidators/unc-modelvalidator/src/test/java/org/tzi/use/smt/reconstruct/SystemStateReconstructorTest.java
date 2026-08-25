package org.tzi.use.smt.reconstruct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.main.Session;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.encode.*;
import org.tzi.use.smt.solver.*;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;

public class SystemStateReconstructorTest {

  /**
   * The overload a live GUI plugin action must use: proves both halves of that contract -- the
   * session's pre-existing state (a stale object created before this call) is gone afterward, and
   * the returned {@link MSystem} is the session's own instance, not a new one.
   */
  @Test
  public void reconstructingIntoAnExistingSessionResetsItAndReusesTheSameSystemNotANewOne()
      throws Exception {
    MModel model = compileLibrary();

    Session session = new Session();
    MSystem originalSystem = new MSystem(model);
    session.setSystem(originalSystem);
    UseSystemApi.create(session).createObjectEx(model.getClass("User"), "StaleUser");
    assertEquals(1, originalSystem.state().objectsOfClass(model.getClass("User")).size());

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots users =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("User", 1, 1))).get("User");
    AttributeDomain nameDomain =
        new AttributeDomain("User", "name", null, List.of("Ada"), null, null);
    AttributeValues nameValues =
        AttributeEncoder.encode(script, users, "name", AttributeType.STRING, nameDomain);
    script.assertThat(Smt.sym("User_0_exists"));
    script.assertThat(
        Smt.eq(Smt.sym(nameValues.valueNames().get(0)), Smt.intLit(java.math.BigInteger.ZERO)));

    SolverResult result =
        new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30)).run(script.toSmtLib());
    assertEquals(SolverOutcome.SAT, result.outcome());
    Map<String, SmtValue> modelValues = SmtModelParser.parse(result.modelText());

    TranslationContext context =
        new TranslationContext(
            Map.of(),
            Map.of("User.name", nameValues),
            Map.of("User.name", nameDomain),
            Map.of("User", users),
            Map.of());

    MSystem returned = SystemStateReconstructor.reconstruct(session, model, context, modelValues);

    assertSame(
        "must reuse the session's own MSystem instance, not construct a new one",
        originalSystem,
        returned);
    MSystemState state = returned.state();
    assertNull(
        "the stale pre-existing object must be gone after reset", state.objectByName("StaleUser"));
    assertEquals(1, state.objectsOfClass(model.getClass("User")).size());
    assertEquals(
        "Ada",
        ((org.tzi.use.uml.ocl.value.StringValue)
                state.objectByName("User0").state(state).attributeValue("name"))
            .value());
  }

  @Test
  public void aSolvedLibraryAssignmentReconstructsIntoARealLiveSystemState() throws Exception {
    MModel model = compileLibrary();
    SmtScript script = new SmtScript("QF_LIA");

    ObjectSlots users =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("User", 1, 1))).get("User");
    ObjectSlots copies =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Copy", 1, 1))).get("Copy");
    // Capacity 2, but only slot 0 will be asserted to exist -- proves a non-existent
    // candidate slot is correctly skipped, not silently instantiated as a phantom object.
    ObjectSlots books =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Book", 1, 2))).get("Book");

    AttributeDomain nameDomain =
        new AttributeDomain("User", "name", null, List.of("Ada"), null, null);
    AttributeDomain addressDomain =
        new AttributeDomain("User", "address", null, List.of("NY"), null, null);
    AttributeDomain sigDomain =
        new AttributeDomain("Copy", "signature", null, List.of("DBS42"), null, null);
    AttributeDomain titleDomain =
        new AttributeDomain("Book", "title", null, List.of("DBforDummies"), null, null);
    AttributeDomain authDomain =
        new AttributeDomain("Book", "auth", null, List.of("Ada"), null, null);
    AttributeDomain yearDomain =
        new AttributeDomain(
            "Book", "year", null, List.of(), new java.math.BigDecimal("1990"), null);

    AttributeValues nameValues =
        AttributeEncoder.encode(script, users, "name", AttributeType.STRING, nameDomain);
    AttributeValues addressValues =
        AttributeEncoder.encode(script, users, "address", AttributeType.STRING, addressDomain);
    AttributeValues sigValues =
        AttributeEncoder.encode(script, copies, "signature", AttributeType.STRING, sigDomain);
    AttributeValues titleValues =
        AttributeEncoder.encode(script, books, "title", AttributeType.STRING, titleDomain);
    AttributeValues authValues =
        AttributeEncoder.encode(script, books, "auth", AttributeType.STRING, authDomain);
    AttributeValues yearValues =
        AttributeEncoder.encode(script, books, "year", AttributeType.INTEGER, yearDomain);

    AssociationLinks borrows =
        AssociationLinkEncoder.encode(
            script,
            "Borrows",
            copies,
            new Multiplicity(0, -1),
            users,
            new Multiplicity(0, 1),
            new AssociationScope("Borrows", 0, -1));
    AssociationLinks belongsTo =
        AssociationLinkEncoder.encode(
            script,
            "BelongsTo",
            copies,
            new Multiplicity(0, -1),
            books,
            new Multiplicity(1, 1),
            new AssociationScope("BelongsTo", 0, -1));

    // Force the intended assignment: slot 0 of every class exists, Book slot 1 does not,
    // and Copy0 is linked to both User0 and Book0.
    script.assertThat(Smt.sym("User_0_exists"));
    script.assertThat(Smt.sym("Copy_0_exists"));
    script.assertThat(Smt.sym("Book_0_exists"));
    script.assertThat(Smt.not(Smt.sym("Book_1_exists")));
    script.assertThat(
        Smt.eq(Smt.sym(nameValues.valueNames().get(0)), Smt.intLit(java.math.BigInteger.ZERO)));
    script.assertThat(
        Smt.eq(Smt.sym(addressValues.valueNames().get(0)), Smt.intLit(java.math.BigInteger.ZERO)));
    script.assertThat(
        Smt.eq(Smt.sym(sigValues.valueNames().get(0)), Smt.intLit(java.math.BigInteger.ZERO)));
    script.assertThat(
        Smt.eq(Smt.sym(titleValues.valueNames().get(0)), Smt.intLit(java.math.BigInteger.ZERO)));
    script.assertThat(
        Smt.eq(Smt.sym(authValues.valueNames().get(0)), Smt.intLit(java.math.BigInteger.ZERO)));
    script.assertThat(
        Smt.eq(
            Smt.sym(yearValues.valueNames().get(0)),
            Smt.intLit(java.math.BigInteger.valueOf(1995))));
    script.assertThat(Smt.sym(borrows.linkNames()[0][0]));
    script.assertThat(Smt.sym(belongsTo.linkNames()[0][0]));

    SolverResult result =
        new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30)).run(script.toSmtLib());
    assertEquals(SolverOutcome.SAT, result.outcome());
    Map<String, SmtValue> modelValues = SmtModelParser.parse(result.modelText());

    TranslationContext context =
        new TranslationContext(
            Map.of(),
            Map.of(
                "User.name",
                nameValues,
                "User.address",
                addressValues,
                "Copy.signature",
                sigValues,
                "Book.title",
                titleValues,
                "Book.auth",
                authValues,
                "Book.year",
                yearValues),
            Map.of(
                "User.name",
                nameDomain,
                "User.address",
                addressDomain,
                "Copy.signature",
                sigDomain,
                "Book.title",
                titleDomain,
                "Book.auth",
                authDomain,
                "Book.year",
                yearDomain),
            Map.of("User", users, "Copy", copies, "Book", books),
            Map.of("Borrows", borrows, "BelongsTo", belongsTo));

    MSystem system = SystemStateReconstructor.reconstruct(model, context, modelValues);
    MSystemState state = system.state();

    assertEquals(1, state.objectsOfClass(model.getClass("User")).size());
    assertEquals(1, state.objectsOfClass(model.getClass("Copy")).size());
    assertEquals(1, state.objectsOfClass(model.getClass("Book")).size());
    assertNull(
        "the non-existent Book slot must not become a phantom object", state.objectByName("Book1"));

    MObject user0 = state.objectByName("User0");
    MObject copy0 = state.objectByName("Copy0");
    MObject book0 = state.objectByName("Book0");
    assertEquals(
        "Ada",
        ((org.tzi.use.uml.ocl.value.StringValue) user0.state(state).attributeValue("name"))
            .value());
    assertEquals(
        1995,
        ((org.tzi.use.uml.ocl.value.IntegerValue) book0.state(state).attributeValue("year"))
            .value());

    MAssociation borrowsAssoc = model.getAssociation("Borrows");
    List<MObject> borrowsOrder =
        borrowsAssoc.associationEnds().stream()
            .map(end -> end.cls().name().equals("User") ? user0 : copy0)
            .toList();
    assertTrue(state.hasLinkBetweenObjects(borrowsAssoc, borrowsOrder.toArray(new MObject[0])));

    MAssociation belongsToAssoc = model.getAssociation("BelongsTo");
    List<MObject> belongsToOrder =
        belongsToAssoc.associationEnds().stream()
            .map(end -> end.cls().name().equals("Book") ? book0 : copy0)
            .toList();
    assertTrue(state.hasLinkBetweenObjects(belongsToAssoc, belongsToOrder.toArray(new MObject[0])));
  }

  private static MModel compileLibrary() throws Exception {
    Path file = Path.of("../benchmark/examples/Library/Library.use");
    if (!Files.isRegularFile(file)) {
      file = Path.of("msc-modelvalidators/benchmark/examples/Library/Library.use");
    }
    String source = Files.readString(file);
    org.tzi.use.uml.mm.ModelFactory factory = new org.tzi.use.uml.mm.ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "Library", err, factory);
    err.flush();
    return model;
  }
}
