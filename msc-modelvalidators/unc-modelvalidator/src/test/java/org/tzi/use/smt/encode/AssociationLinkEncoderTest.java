package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import java.time.Duration;
import java.util.List;
import org.junit.Test;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.solver.*;

public class AssociationLinkEncoderTest {
 @Test public void declaresOneLinkBooleanPerCandidatePair(){SmtScript s=new SmtScript("QF_LIA");ObjectSlots c=slots(s,"Copy",2),b=slots(s,"Book",2);AssociationLinks l=AssociationLinkEncoder.encode(s,"BelongsTo",c,new Multiplicity(0,-1),b,new Multiplicity(1,1),new AssociationScope("BelongsTo",0,-1));assertEquals(2,l.linkNames().length);assertEquals(2,l.linkNames()[0].length);}
 @Test public void everyCopyGettingExactlyOneBookIsSatisfiable(){assertEquals(SolverOutcome.SAT,solveBelongsTo(2,2,x->{}).outcome());}
 @Test public void forcingOneCopyToTwoBooksViolatesTheBookEndsUpperBound(){assertEquals(SolverOutcome.UNSAT,solveBelongsTo(2,2,x->{x.script().assertThat(Smt.sym(x.links().linkNames()[0][0]));x.script().assertThat(Smt.sym(x.links().linkNames()[0][1]));}).outcome());}
 @Test public void sharingOneEndpointIsLegalWhenItsMultiplicityAllowsIt(){assertEquals(SolverOutcome.SAT,solveBelongsTo(2,2,x->{x.script().assertThat(Smt.sym(x.links().linkNames()[0][0]));x.script().assertThat(Smt.sym(x.links().linkNames()[1][0]));}).outcome());}
 @Test public void exceedingAnEndpointsMultiplicityUpperBoundIsRejected(){SmtScript s=new SmtScript("QF_LIA");ObjectSlots c=slots(s,"Copy",3),b=slots(s,"Book",1);AssociationLinks l=AssociationLinkEncoder.encode(s,"BelongsTo",c,new Multiplicity(0,2),b,new Multiplicity(1,1),new AssociationScope("BelongsTo",0,-1));for(int i=0;i<3;i++)s.assertThat(Smt.sym(l.linkNames()[i][0]));assertEquals(SolverOutcome.UNSAT,solve(s).outcome());}
 @Test public void aLinkToANonExistentSlotIsForcedFalse(){SmtScript s=new SmtScript("QF_LIA");ObjectSlots c=slots(s,"Copy",1,0),b=slots(s,"Book",1,0);AssociationLinks l=AssociationLinkEncoder.encode(s,"BelongsTo",c,new Multiplicity(0,-1),b,new Multiplicity(0,1),new AssociationScope("BelongsTo",0,-1));s.assertThat(Smt.not(Smt.sym("Copy_0_exists")));s.assertThat(Smt.sym(l.linkNames()[0][0]));assertEquals(SolverOutcome.UNSAT,solve(s).outcome());}
 @Test public void aggregateLinkCountRespectsTheAssociationScopeFromConfiguration(){SmtScript s=new SmtScript("QF_LIA");AssociationLinkEncoder.encode(s,"BelongsTo",slots(s,"Copy",2),new Multiplicity(0,-1),slots(s,"Book",2),new Multiplicity(1,1),new AssociationScope("BelongsTo",0,1));assertEquals(SolverOutcome.UNSAT,solve(s).outcome());}
 private record Scripted(SmtScript script,AssociationLinks links){}
 private static ObjectSlots slots(SmtScript s,String n,int count){return slots(s,n,count,count);} private static ObjectSlots slots(SmtScript s,String n,int max,int min){return ObjectSlotEncoder.encode(s,List.of(new ClassScope(n,min,max))).get(n);}
 private static SolverResult solveBelongsTo(int cs,int bs,java.util.function.Consumer<Scripted> extra){SmtScript s=new SmtScript("QF_LIA");AssociationLinks l=AssociationLinkEncoder.encode(s,"BelongsTo",slots(s,"Copy",cs),new Multiplicity(0,-1),slots(s,"Book",bs),new Multiplicity(1,1),new AssociationScope("BelongsTo",0,-1));extra.accept(new Scripted(s,l));return solve(s);} private static SolverResult solve(SmtScript s){return new SolverProcess(SolverBinary.resolve(),Duration.ofSeconds(30)).run(s.toSmtLib());}
}
