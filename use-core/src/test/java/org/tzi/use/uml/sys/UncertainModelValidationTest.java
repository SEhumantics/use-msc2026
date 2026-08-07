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
}
