package org.tzi.use.smt.encode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SmtSort;
import org.tzi.use.smt.solver.SmtTerm;

/** Encodes always-declared attribute values whose domain constraints are guarded by owner existence. */
public final class AttributeEncoder {
    private AttributeEncoder() { }
    public static AttributeValues encode(SmtScript script,ObjectSlots owner,String attributeName,AttributeType type,AttributeDomain domain){
        List<String> names=new ArrayList<>();
        for(int i=0;i<owner.capacity();i++){String name=owner.className()+"_"+i+"_"+attributeName;script.declareConst(name,sort(type));names.add(name);SmtTerm exists=Smt.sym(owner.existsNames().get(i)),value=Smt.sym(name);switch(type){case STRING->guardString(script,exists,value,domain,owner.className(),attributeName);case INTEGER->guardInteger(script,exists,value,domain,owner.className(),attributeName);case REAL->guardRange(script,exists,value,domain,false);case BOOLEAN->{}}}
        return new AttributeValues(owner.className(),attributeName,names);
    }
    private static SmtSort sort(AttributeType type){return switch(type){case STRING,INTEGER->SmtSort.INT;case REAL->SmtSort.REAL;case BOOLEAN->SmtSort.BOOL;};}
    private static void guardString(SmtScript s,SmtTerm exists,SmtTerm value,AttributeDomain d,String cls,String attr){if(d.enumeratedValues().isEmpty())throw new IllegalArgumentException("string attribute '"+cls+"."+attr+"' has no configured candidate values");List<SmtTerm> options=new ArrayList<>();for(int i=0;i<d.enumeratedValues().size();i++)options.add(Smt.eq(value,Smt.intLit(BigInteger.valueOf(i))));s.assertThat(Smt.app("=>",exists,Smt.or(options)));}
    private static void guardInteger(SmtScript s,SmtTerm exists,SmtTerm value,AttributeDomain d,String cls,String attr){if(!d.enumeratedValues().isEmpty()){List<SmtTerm> options=new ArrayList<>();for(String candidate:d.enumeratedValues())options.add(Smt.eq(value,Smt.intLit(parseInteger(candidate,cls,attr))));s.assertThat(Smt.app("=>",exists,Smt.or(options)));return;}guardRange(s,exists,value,d,true);}
    private static void guardRange(SmtScript s,SmtTerm exists,SmtTerm value,AttributeDomain d,boolean integer){List<SmtTerm> bounds=new ArrayList<>();if(d.lowerBound()!=null)bounds.add(Smt.app(">=",value,literal(d.lowerBound(),integer)));if(d.upperBound()!=null)bounds.add(Smt.app("<=",value,literal(d.upperBound(),integer)));if(!bounds.isEmpty())s.assertThat(Smt.app("=>",exists,Smt.and(bounds)));}
    private static SmtTerm literal(BigDecimal d,boolean integer){return integer?Smt.intLit(d.toBigIntegerExact()):Smt.realLit(d);}
    private static BigInteger parseInteger(String candidate,String cls,String attr){try{return new BigInteger(candidate);}catch(NumberFormatException e){throw new IllegalArgumentException("invalid integer candidate '"+candidate+"' for attribute '"+cls+"."+attr+"'",e);}}
}
