package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.solver.*;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

public class ExpressionTranslatorTest {
 @Test public void yearPlausibleIsViolatedByAYearBelowTheConfiguredLowerBound()throws Exception{MClassInvariant inv=findInvariant(compileLibrary(),"yearPlausible");SmtScript s=new SmtScript("QF_LIA");ObjectSlots b=ObjectSlotEncoder.encode(s,List.of(new ClassScope("Book",1,1))).get("Book");AttributeDomain d=new AttributeDomain("Book","year",null,List.of(),new BigDecimal("1455"),null);AttributeValues v=AttributeEncoder.encode(s,b,"year",AttributeType.INTEGER,d);var t=ExpressionTranslator.translate(inv.bodyExpression(),new TranslationContext(Map.of("b",new VariableBinding("Book",0)),Map.of("Book.year",v),Map.of("Book.year",d),Map.of()));s.assertThat(Smt.sym("Book_0_exists"));s.assertThat(t);s.assertThat(Smt.app("<",Smt.sym(v.valueNames().get(0)),Smt.intLit(java.math.BigInteger.valueOf(1455))));assertEquals(SolverOutcome.UNSAT,solve(s).outcome());}
 @Test public void nameAddressFormatOkHoldsForAnyDomainGuardedExistingUser()throws Exception{MClassInvariant inv=findInvariant(compileLibrary(),"nameAddressFormatOk");SmtScript s=new SmtScript("QF_LIA");ObjectSlots u=ObjectSlotEncoder.encode(s,List.of(new ClassScope("User",1,1))).get("User");AttributeDomain nd=new AttributeDomain("User","name",null,List.of("Ada","Bob","Cyd"),null,null),ad=new AttributeDomain("User","address",null,List.of("NY","CA","FL"),null,null);AttributeValues nv=AttributeEncoder.encode(s,u,"name",AttributeType.STRING,nd),av=AttributeEncoder.encode(s,u,"address",AttributeType.STRING,ad);var t=ExpressionTranslator.translate(inv.bodyExpression(),new TranslationContext(Map.of("u",new VariableBinding("User",0)),Map.of("User.name",nv,"User.address",av),Map.of("User.name",nd,"User.address",ad),Map.of()));s.assertThat(Smt.sym("User_0_exists"));s.assertThat(Smt.not(t));assertEquals(SolverOutcome.UNSAT,solve(s).outcome());}
 @Test public void titleFormatOkAlsoTranslatesCorrectlyFromTheRealAst()throws Exception{MClassInvariant inv=findInvariant(compileLibrary(),"titleFormatOk");SmtScript s=new SmtScript("QF_LIA");ObjectSlots b=ObjectSlotEncoder.encode(s,List.of(new ClassScope("Book",1,1))).get("Book");AttributeDomain d=new AttributeDomain("Book","title",null,List.of("DBforDummies","IntrotoAI","PrincsofNW"),null,null);AttributeValues v=AttributeEncoder.encode(s,b,"title",AttributeType.STRING,d);var t=ExpressionTranslator.translate(inv.bodyExpression(),new TranslationContext(Map.of("b",new VariableBinding("Book",0)),Map.of("Book.title",v),Map.of("Book.title",d),Map.of()));s.assertThat(Smt.sym("Book_0_exists"));s.assertThat(t);assertEquals(SolverOutcome.SAT,solve(s).outcome());}
 @Test public void signatureFormatOkAlsoTranslatesCorrectlyFromTheRealAst()throws Exception{MClassInvariant inv=findInvariant(compileLibrary(),"signatureFormatOk");SmtScript s=new SmtScript("QF_LIA");ObjectSlots c=ObjectSlotEncoder.encode(s,List.of(new ClassScope("Copy",1,1))).get("Copy");AttributeDomain d=new AttributeDomain("Copy","signature",null,List.of("DBS42","DBS43","NW21"),null,null);AttributeValues v=AttributeEncoder.encode(s,c,"signature",AttributeType.STRING,d);var t=ExpressionTranslator.translate(inv.bodyExpression(),new TranslationContext(Map.of("c",new VariableBinding("Copy",0)),Map.of("Copy.signature",v),Map.of("Copy.signature",d),Map.of()));s.assertThat(Smt.sym("Copy_0_exists"));s.assertThat(t);assertEquals(SolverOutcome.SAT,solve(s).outcome());}
 @Test public void authSeqFormatOkAlsoTranslatesCorrectlyFromTheRealAst()throws Exception{MClassInvariant inv=findInvariant(compileLibrary(),"authSeqFormatOk");SmtScript s=new SmtScript("QF_LIA");ObjectSlots b=ObjectSlotEncoder.encode(s,List.of(new ClassScope("Book",1,1))).get("Book");AttributeDomain d=new AttributeDomain("Book","auth",null,List.of("Ada","Bob","Cyd"),null,null);AttributeValues v=AttributeEncoder.encode(s,b,"auth",AttributeType.STRING,d);var t=ExpressionTranslator.translate(inv.bodyExpression(),new TranslationContext(Map.of("b",new VariableBinding("Book",0)),Map.of("Book.auth",v),Map.of("Book.auth",d),Map.of()));s.assertThat(Smt.sym("Book_0_exists"));s.assertThat(t);assertEquals(SolverOutcome.SAT,solve(s).outcome());}
 @Test public void anUnsupportedConstructFailsClosedRatherThanBeingSkipped()throws Exception{MClassInvariant inv=findInvariant(compileLibrary(),"noDoubleBorrowings");org.junit.Assert.assertThrows(SmtTranslationException.class,()->ExpressionTranslator.translate(inv.bodyExpression(),new TranslationContext(Map.of(),Map.of(),Map.of(),Map.of())));}
 private static MModel compileLibrary()throws Exception{Path f=Path.of("../benchmark/examples/Library/Library.use");if(!Files.isRegularFile(f))f=Path.of("msc-modelvalidators/benchmark/examples/Library/Library.use");MModel m=USECompiler.compileSpecification(Files.readString(f),"Library",new PrintWriter(System.err),new ModelFactory());return m;}
 private static MClassInvariant findInvariant(MModel m,String n){for(MClassInvariant i:m.classInvariants())if(i.name().equals(n))return i;throw new IllegalStateException("invariant not found: "+n);}
 private static SolverResult solve(SmtScript s){return new SolverProcess(SolverBinary.resolve(),Duration.ofSeconds(30)).run(s.toSmtLib());}
}
