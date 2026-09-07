package org.tzi.msc.benchmark;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.smt.config.Scenario;
import org.tzi.use.smt.finder.ModelFinderResult;
import org.tzi.use.smt.finder.ResultClassification;
import org.tzi.use.smt.finder.SmtModelFinder;
import org.tzi.use.smt.solver.SolverProcess;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Regenerates the Robot Battle witness evidence the paper's object diagram reports: the banded
 * case study (the published model plus one added not-very-fast requirement) solved under COVER
 * with both speed representatives admitted, where every one of the eight scenarios is witnessed
 * and UNIFORM is refuted.
 *
 * <p>The dump is the GENERATED source of the figure: for every configured scenario it records the
 * measurement quality the scenario fixes, and for every witnessed scenario the reconstructed
 * snapshot's object identities, links and representative attribute values, exactly as
 * reconstruction produced them. The figure in the paper is laid out by hand but every value it
 shows comes from this dump, so the caption can honestly say the diagram is a tool witness.
 *
 * <p>Usage: {@code RobotBattleWitness <output.json>} (run with the benchmark classpath).
 */
public final class RobotBattleWitness {

  private static final String MODEL =
      """
      model RobotBattle
      class Robot
      attributes
        speed : UReal
        lastMovement : UInteger
      end
      class UnidentifiedObject
      attributes
        id : UString
        speed : UReal
      end
      class Mark
      attributes
        hitsTarget : UBoolean
      end
      association Engagement between
        Robot[0..1] role robot
        UnidentifiedObject[0..1] role target
      end
      association Decision between
        Mark[0..1] role source
        UnidentifiedObject[0..1] role about
      end
      constraints
      context r : Robot inv reliablyFast:
        (r.speed > 0.30).toBooleanC(0.95)
      context r : Robot inv movedRecently:
        (r.lastMovement > 8).toBooleanC(0.95)
      context u : UnidentifiedObject inv identified:
        (u.id = 'U-77').toBooleanC(0.7)
      context u : UnidentifiedObject inv recentlyMoved:
        (u.speed > 0.5).toBooleanC(0.6)
      context m : Mark inv hitConfirmed:
        m.hitsTarget.toBooleanC(0.8)
      """;

  private static final String BANDED_MODEL = MODEL
      + "context r : Robot inv notVeryFast:\n"
      + "  not ((r.speed > 0.36).toBooleanC(0.95))\n";

  private RobotBattleWitness() {}

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      System.err.println("usage: RobotBattleWitness <output.json>");
      System.exit(1);
      return;
    }
    StringWriter buffer = new StringWriter();
    PrintWriter err = new PrintWriter(buffer, true);
    MModel model =
        USECompiler.compileSpecification(BANDED_MODEL, "RobotBattleBanded", err, new org.tzi.use.uml.mm.ModelFactory());
    if (model == null) {
      throw new IllegalStateException("banded fixture did not compile:\n" + buffer);
    }
    ConfigurationVocabulary vocabulary = ConfigurationVocabulary.fromModel(model);

    List<AttributeDomain> domains = List.of(
        new AttributeDomain("Robot", "speed", "value", List.of("0.35", "0.42"), null, null),
        new AttributeDomain("Robot", "speed", "uncertainty", List.of("0.02", "0.06"), null, null),
        new AttributeDomain("Robot", "lastMovement", "value", List.of("20"), null, null),
        new AttributeDomain("Robot", "lastMovement", "uncertainty", List.of("1", "3"), null, null),
        new AttributeDomain("UnidentifiedObject", "id", "value", List.of("U-77"), null, null),
        new AttributeDomain("UnidentifiedObject", "id", "confidence", List.of("0.85"), null, null),
        new AttributeDomain("UnidentifiedObject", "speed", "value", List.of("0.8"), null, null),
        new AttributeDomain("UnidentifiedObject", "speed", "uncertainty", List.of("0.1", "0.5"),
            null, null),
        new AttributeDomain("Mark", "hitsTarget", "probability", List.of("0.9"), null, null));
    List<ClassScope> scopes = List.of(
        new ClassScope("Robot", 1, 1, List.of("r1")),
        new ClassScope("UnidentifiedObject", 1, 1, List.of("u1")),
        new ClassScope("Mark", 1, 1, List.of("m1")));
    List<AssociationScope> associations = List.of(
        new AssociationScope("Engagement", 1, 1, List.of(List.of("r1", "u1"))),
        new AssociationScope("Decision", 1, 1, List.of(List.of("m1", "u1"))));
    Set<String> everyInvariant = Set.of(
        "Robot::reliablyFast", "Robot::movedRecently", "Robot::notVeryFast",
        "Mark::hitConfirmed",
        "UnidentifiedObject::identified", "UnidentifiedObject::recentlyMoved");

    JsonObject root = new JsonObject();
    root.addProperty("model", "RobotBattleBanded");
    root.addProperty("policy", "cover");
    JsonArray scenarioArray = new JsonArray();
    try (SolverProcess process =
        SolverProcess.persistent(org.tzi.use.smt.solver.SolverBinary.resolve(), Duration.ofSeconds(60))) {
      ModelFinderResult cover = SmtModelFinder.find(model, new AnalysisConfiguration(
          scopes, associations, domains, everyInvariant,
          QueryParser.parse("cover satisfy", vocabulary), Duration.ofSeconds(60), 1), process);
      root.addProperty("coverClassification",
          ResultClassification.of(cover, everyInvariant).name());
      for (var report : cover.scenarios()) {
        scenarioArray.add(scenarioJson(model, report));
      }

      ModelFinderResult uniform = SmtModelFinder.find(model, new AnalysisConfiguration(
          scopes, associations, domains, everyInvariant,
          QueryParser.parse("uniform satisfy", vocabulary), Duration.ofSeconds(60), 1), process);
      root.addProperty("uniformClassification",
          ResultClassification.of(uniform, everyInvariant).name());
    }

    root.add("scenarios", scenarioArray);
    Files.writeString(Path.of(args[0]),
        new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
    System.out.println("wrote " + args[0]);
  }

  private static JsonObject scenarioJson(MModel model, org.tzi.use.smt.finder.ScenarioReport report) {
    JsonObject json = new JsonObject();
    Scenario scenario = report.scenario();
    json.addProperty("index", scenario.index());
    JsonArray bindings = new JsonArray();
    for (var binding : scenario.bindings()) {
      bindings.add(binding.className() + "." + binding.attributeName()
          + "_" + binding.component() + " = " + binding.value().toPlainString());
    }
    json.add("bindings", bindings);
    json.addProperty("outcome", report.outcome().name());
    if (report.system() == null) {
      return json;
    }
    MSystemState state = report.system().state();
    JsonArray objects = new JsonArray();
    for (String className : List.of("Robot", "UnidentifiedObject", "Mark")) {
      MClass cls = model.getClass(className);
      for (MObject object : state.objectsOfClass(cls)) {
        JsonObject objectJson = new JsonObject();
        objectJson.addProperty("name", object.name());
        objectJson.addProperty("class", className);
        JsonArray attributes = new JsonArray();
        for (var attribute : cls.attributes()) {
          Object value = object.state(state).attributeValue(attribute);
          attributes.add(attribute.name() + " = " + value);
        }
        objectJson.add("attributes", attributes);
        objects.add(objectJson);
      }
    }
    json.add("objects", objects);
    JsonArray links = new JsonArray();
    for (String assocName : List.of("Engagement", "Decision")) {
      MAssociation assoc = model.getAssociation(assocName);
      for (var link : state.linksOfAssociation(assoc).links()) {
        JsonArray linkJson = new JsonArray();
        linkJson.add(assocName);
        JsonArray ends = new JsonArray();
        for (MObject end : link.linkedObjects()) {
          ends.add(end.name());
        }
        linkJson.add(ends);
        links.add(linkJson);
      }
    }
    json.add("links", links);
    return json;
  }
}
