package org.tzi.use.uml.sys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.URealValue;
import org.tzi.use.uml.ocl.value.Value;

/**
 * A model whose attributes carry uncertainty must still be checkable: the
 * invariant machinery casts the result of an invariant to a plain Boolean, so a
 * Boolean-valued invariant over uncertain attributes has to stay Boolean all
 * the way through the forAll that wraps it.
 */
class UncertainModelValidationTest {

    private static final String SPEC = """
            model M
            class Sensor
            attributes
              reading : UReal
              trusted : SBoolean
              label : UString
            end
            constraints
            context Sensor inv Positive: (self.reading > 0).toBoolean()
            """;

    private MSystem compile() {
        StringWriter err = new StringWriter();
        MModel model = USECompiler.compileSpecification(
                new ByteArrayInputStream(SPEC.getBytes(StandardCharsets.UTF_8)),
                "M.use", new PrintWriter(err), new ModelFactory());
        assertNotNull(model, err.toString());
        return new MSystem(model);
    }

    @Test void uncertainAttributesAreCarriedThroughTheSystemState() throws Exception {
        MSystem system = compile();
        MClass sensor = system.model().getClass("Sensor");
        MObject object = system.state().createObject(sensor, "s1");
        object.state(system.state()).setAttributeValue(sensor.attribute("reading", true),
                new URealValue(5, 0.5));
        Value stored = object.state(system.state()).attributeValue(sensor.attribute("reading", true));
        assertEquals("UReal(5.0, 0.5)", stored.toString());
    }

    @Test void booleanInvariantsOverUncertainAttributesAreCheckable() throws Exception {
        MSystem system = compile();
        MClass sensor = system.model().getClass("Sensor");
        MObject object = system.state().createObject(sensor, "s1");
        object.state(system.state()).setAttributeValue(sensor.attribute("reading", true),
                new URealValue(5, 0.5));
        StringWriter out = new StringWriter();
        assertTrue(system.state().check(new PrintWriter(out), false, false, false, List.of()),
                out.toString());

        object.state(system.state()).setAttributeValue(sensor.attribute("reading", true),
                new URealValue(-5, 0.5));
        StringWriter failing = new StringWriter();
        assertTrue(!system.state().check(new PrintWriter(failing), false, false, false, List.of()),
                failing.toString());
    }

    /** Uncertain values survive a round trip through a SOIL statement. */
    @Test void soilStatementsAssignUncertainAttributes() throws Exception {
        MSystem system = compile();
        for (String statement : new String[] {
                "s1 := new Sensor('s1')",
                "s1.reading := UReal(4, 0.5)",
                "s1.trusted := SBoolean(0.7, 0.1, 0.2, 0.5)",
                "s1.label := UString('probe', 0.8)" }) {
            StringWriter err = new StringWriter();
            org.tzi.use.uml.sys.soil.MStatement compiled =
                org.tzi.use.parser.soil.SoilCompiler.compileStatement(system.model(), system.state(),
                    system.getVariableEnvironment(), statement, "<soil>", new PrintWriter(err), true);
            assertNotNull(compiled, statement + "\n" + err);
            system.execute(compiled);
        }
        MClass sensor = system.model().getClass("Sensor");
        MObject object = system.state().objectByName("s1");
        assertNotNull(object, "object created");
        assertEquals("UReal(4.0, 0.5)",
            object.state(system.state()).attributeValue(sensor.attribute("reading", true)).toString());
        assertEquals("SBoolean(0.7, 0.1, 0.2, 0.5)",
            object.state(system.state()).attributeValue(sensor.attribute("trusted", true)).toString());
        assertEquals("UString('probe', 0.8)",
            object.state(system.state()).attributeValue(sensor.attribute("label", true)).toString());
    }
}
