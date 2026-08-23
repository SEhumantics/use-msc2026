package org.tzi.msc.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MLink;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystemState;

/**
 * Canonicalizes a reconstructed solution into a content-based digest, deliberately ignoring
 * object identity/order: different solvers legitimately find different-but-equally-valid
 * satisfying assignments (e.g. different concrete attribute values), so comparing digests across
 * solvers answers "did they find a solution with the same shape", not "the identical object
 * graph" -- the latter is not expected to hold and is not a correctness requirement.
 *
 * <p>Every object gets a per-object signature built from its own (declared + inherited) attribute
 * values -- not just a per-class, per-attribute column of values -- because link endpoints need a
 * content-based (not identity-based) way to say *which* object a link connects to. A column-wise
 * digest (sort each attribute's values independently) cannot distinguish, e.g., {(Ada,30),
 * (Bob,25)} from {(Ada,25),(Bob,30)}; the per-object tuple can. Association links are then digested
 * as a sorted multiset of endpoint-signature tuples per association, so two solutions with
 * identical attribute values but different link topology (a real distinguishing case for
 * associations, association classes, subsetting/redefinition, and aggregation/composition) no
 * longer collide to the same digest.
 */
final class WitnessDigest {
	private WitnessDigest() {
	}

	static String digest(MModel model, MSystemState state) {
		List<String> parts = new ArrayList<>();
		Map<MObject, String> signatures = new HashMap<>();

		// getClassesIncludingImports()/getAssociationsIncludingImports(), not the plain classes()/
		// associations() accessors: a model can pull in classes/associations from another .use file via
		// USE's `import` statement (MModel keeps those in a separate importedModels list, never merged
		// into its own fClasses/fAssociations), and an object/link touching one would otherwise be
		// missing from `signatures` entirely and fall back to the "?" placeholder below -- silently
		// collapsing witnesses that actually differ into the same digest.
		List<MClass> classes = new ArrayList<>(model.getClassesIncludingImports());
		classes.sort((a, b) -> a.name().compareTo(b.name()));
		for (MClass cls : classes) {
			List<MObject> objs = new ArrayList<>(state.objectsOfClass(cls));
			parts.add(cls.name() + "#count=" + objs.size());

			List<MAttribute> attrs = new ArrayList<>(cls.allAttributes());
			attrs.sort((a, b) -> a.name().compareTo(b.name()));
			for (MObject obj : objs) {
				List<String> attrParts = new ArrayList<>();
				for (MAttribute attr : attrs) {
					Value v = obj.state(state).attributeValue(attr.name());
					attrParts.add(attr.name() + "=" + (v == null ? "null" : v.toString()));
				}
				signatures.put(obj, cls.name() + "{" + String.join(",", attrParts) + "}");
			}
			List<String> objSignatures = objs.stream().map(signatures::get).sorted().collect(Collectors.toList());
			parts.add(cls.name() + ".objects=" + objSignatures);
		}

		List<MAssociation> associations = new ArrayList<>(model.getAssociationsIncludingImports());
		associations.sort((a, b) -> a.name().compareTo(b.name()));
		for (MAssociation assoc : associations) {
			Set<MLink> links = state.linksOfAssociation(assoc).links();
			List<String> linkTuples = new ArrayList<>();
			for (MLink link : links) {
				List<String> endSignatures = link.linkedObjects().stream()
						.map(o -> signatures.getOrDefault(o, "?"))
						.collect(Collectors.toList());
				linkTuples.add("(" + String.join("|", endSignatures) + ")");
			}
			Collections.sort(linkTuples);
			parts.add(assoc.name() + "#count=" + links.size());
			parts.add(assoc.name() + ".links=" + linkTuples);
		}

		return String.join(";", parts);
	}
}
