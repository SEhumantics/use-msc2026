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
    @Test void uncertaintyQueriesParseAndOrdinaryQueriesRejectThresholds() {
        MModel model=new ModelFactory().createModel("UncertaintyQueries");
        var out=new PrintWriter(new StringWriter());
        assertNotNull(OCLCompiler.compileExpression(model,
            "Sequence{UBoolean(true, 0.9)}->uSelect(x | x)","<uSelect>",out,new VarBindings()));
        assertNotNull(OCLCompiler.compileExpression(model,
            "Sequence{UBoolean(true, 0.9)}->uSelectC(x | x, 0.8)","<uSelectC>",out,new VarBindings()));
        assertNull(OCLCompiler.compileExpression(model,
            "Sequence{1}->select(x | true, 0.8)","<ordinary-threshold>",out,new VarBindings()));
    }
    @Test void malformedUncertaintyLiteralsAreRejected() {
        MModel model=new ModelFactory().createModel("MalformedUncertainty");
        for(String source:new String[]{"UReal(1)","UInteger(1)","UString('x')","UBoolean(true)"}) {
            assertNull(OCLCompiler.compileExpression(model,source,"<malformed>",new PrintWriter(new StringWriter()),new VarBindings()),source);
        }
    }
}
