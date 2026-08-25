package org.tzi.use.smt.encode;
import java.util.Map;
import org.tzi.use.smt.config.AttributeDomain;
public record TranslationContext(Map<String,VariableBinding> variables,Map<String,AttributeValues> attributes,Map<String,AttributeDomain> domains){
 public AttributeValues attributeValues(String c,String a){return require(attributes,c,a,"attribute values");}
 public AttributeDomain attributeDomain(String c,String a){return require(domains,c,a,"attribute domain");}
 public VariableBinding binding(String n){VariableBinding b=variables.get(n);if(b==null)throw new SmtTranslationException("unbound OCL variable '"+n+"'");return b;}
 private static <T>T require(Map<String,T>m,String c,String a,String w){T v=m.get(c+"."+a);if(v==null)throw new SmtTranslationException("no "+w+" registered for "+c+"."+a);return v;}
}
