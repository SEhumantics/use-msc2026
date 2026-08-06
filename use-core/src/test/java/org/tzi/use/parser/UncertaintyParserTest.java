package org.tzi.use.parser;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.parser.ocl.OCLCompiler;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.value.VarBindings;

class UncertaintyParserTest {
    @Test void uncertaintyLiteralsCompile() {
        MModel model=new ModelFactory().createModel("UncertaintyParser");
        VarBindings bindings=new VarBindings();
        for(String source:new String[]{"UReal(1, 0.1)","UInteger(1, 0.1)","UString('x', 0.9)","UBoolean(true, 0.8)","SBoolean(0.6, 0.1, 0.3, 0.5)"}) {
            Expression e=OCLCompiler.compileExpression(model,source,"<uncertainty-test>",new PrintWriter(new StringWriter()),bindings);
            assertNotNull(e,source);
        }
    }
}
