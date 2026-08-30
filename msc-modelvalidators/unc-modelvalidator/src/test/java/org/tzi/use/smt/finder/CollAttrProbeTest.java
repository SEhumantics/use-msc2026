package org.tzi.use.smt.finder;

import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import java.io.PrintWriter;

public class CollAttrProbeTest {
  static void probe(String label, String spec) {
    java.io.StringWriter buffer = new java.io.StringWriter();
    PrintWriter err = new PrintWriter(buffer, true);
    String modelName = "M" + label.replaceAll("[^A-Za-z0-9]", "");
    MModel model = USECompiler.compileSpecification(
        spec.replace("model P", "model " + modelName), modelName, err, new ModelFactory());
    err.flush();
    if (model == null) { System.out.println("### " + label + ": FAIL -- " + buffer.toString().trim()); return; }
    StringBuilder out = new StringBuilder("### " + label + ": OK.");
    for (org.tzi.use.uml.mm.MClass c : model.classes())
      for (org.tzi.use.uml.mm.MAttribute a : c.attributes())
        out.append(" attr ").append(a.name()).append(":").append(a.type()).append(";");
    for (org.tzi.use.uml.mm.MClassInvariant inv : model.classInvariants(true))
      out.append(" inv ").append(inv.name()).append("=(").append(inv.bodyExpression().type()).append(") ").append(inv.bodyExpression()).append(";");
    System.out.println(out);
  }

  @Test
  public void probeSetAttributeSupport() {
    String head = "model P\nclass X\nattributes\n";
    String tail = "end\nconstraints\ncontext x : X inv A:\n%BODY%\n";
    probe("int-body-cmp", head + "  i : Integer\n" + tail.replace("%BODY%", "x.i > 0"));
    probe("set-attr-decl", head + "  tags : Set(Integer)\n  i : Integer\n" + tail.replace("%BODY%", "x.i > 0"));
    probe("set-attr-isEmpty", head + "  tags : Set(Integer)\n  i : Integer\n" + tail.replace("%BODY%", "x.tags->isEmpty()"));
    probe("set-attr-size", head + "  tags : Set(Integer)\n  i : Integer\n" + tail.replace("%BODY%", "x.tags->size() = 2"));
    probe("set-attr-includes-lit", head + "  tags : Set(Integer)\n  i : Integer\n" + tail.replace("%BODY%", "x.tags->includes(1)"));
    probe("set-attr-includes-attr", head + "  tags : Set(Integer)\n  i : Integer\n" + tail.replace("%BODY%", "x.tags->includes(x.i)"));
    probe("setstr-includes-str", head + "  tags : Set(String)\n  s : String\n" + tail.replace("%BODY%", "x.tags->includes(\"u\")"));
    probe("set-eq-literal", head + "  tags : Set(Integer)\n  i : Integer\n" + tail.replace("%BODY%", "x.tags = Set{1,2}"));
    probe("set-forAll", head + "  tags : Set(Integer)\n  i : Integer\n" + tail.replace("%BODY%", "x.tags->forAll(t | t > 0)"));
  }
}
