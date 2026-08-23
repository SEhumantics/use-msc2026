#!/usr/bin/env python3
"""Suggest candidate feature-matrix IDs for a .use model by scanning its
source text for syntactic constructs that correlate with specific matrix
features -- e.g. a `derive:` clause suggests `attr.derived`, `->forAll(`
suggests `ocl.forAll`, an `associationclass` block suggests
`assoc.association-class`.

WHAT THIS IS: a first-pass suggestion tool, so tagging a new scenario stops
being "manually re-read the whole 126-row matrix and guess which rows
apply" (see docs/kk-modelvalidator-port.md's "Eighth pass" for how that was
done before -- one-by-one by an audit workflow). Run it, review the
candidates, add the confirmed ones to the scenario's `features` entry in
manifest.json (or better: to the relevant feature's satScenarioIds /
unsatScenarioIds / validationOracleScenarioIds in the matrix itself, then
run sync-manifest-features.py).

WHAT THIS IS NOT: ground truth. Syntax presence is necessary but not
sufficient evidence a feature is genuinely exercised -- e.g. `->forAll(`
appearing doesn't confirm the invariant using it is actually *active* and
*checked*, and it says nothing about whether the plugin translates it
*correctly* (a degraded/known-defect feature can have exactly the same
trigger syntax as a supported one; that distinction needs the matrix's own
evidence, not this scanner). It also only covers features with a real
syntactic signal in a .use file -- CLI/properties-file/workflow features
(config.*, cmd.*, invIndep.*, solve.*) and anything requiring runtime
behavior (reconstruction.*, query.mv-vs-plain-ocl-agreement, most prim.*
arithmetic-edge-case features) are out of scope and never suggested here.

Usage: python3 scan-scenario-features.py path/to/Model.use [path/to/Model2.use ...]
"""
import re
import sys
from pathlib import Path

# (feature_id, compiled regex, human-readable trigger description)
# Ordered roughly by matrix area. Patterns are deliberately conservative --
# false negatives (a real usage this scanner misses) are far less costly
# here than false positives (a suggestion that sends a reviewer chasing
# nothing), since this tool's whole job is to narrow the 126-row matrix
# down to a short candidate list, not to replace review.
TRIGGERS = [
    ("class.abstract", re.compile(r"\babstract\s+class\b"), "`abstract class`"),
    ("attr.derived", re.compile(r"^\s*derive\s*:", re.M), "`derive:` attribute clause"),
    ("attr.collection-typed-with-size-bounds",
     re.compile(r":\s*(Set|Bag|Sequence|OrderedSet)\s*\(", re.I),
     "collection-typed attribute declaration"),
    ("assoc.association-class", re.compile(r"\bassociationclass\b", re.I), "`associationclass` block"),
    ("assoc.aggregation", re.compile(r"\baggregation\b"), "`aggregation` role qualifier"),
    ("assoc.composition.forbidden-sharing", re.compile(r"\bcomposition\b"), "`composition` role qualifier"),
    ("assoc.subsets", re.compile(r"\bsubsets\b"), "`subsets` role qualifier"),
    ("assoc.union", re.compile(r"\bunion\b"), "`union` role qualifier"),
    ("assoc.redefines", re.compile(r"\bredefines\b"), "`redefines` role qualifier"),
    ("assoc.qualified-ends", re.compile(r"\bqualifier\b"), "`qualifier` role clause"),
    ("assoc.derived-association-end", re.compile(r"^\s*derived\s*=", re.M), "`derived =` association-end clause"),
    ("enum.literal-domain", re.compile(r"^\s*enum\s+\w+", re.M), "`enum` type declaration"),
    ("enum.literal-expression-in-ocl", re.compile(r"#\w+"), "`#literal` enum-constant expression"),
    ("ocl.forAll", re.compile(r"->\s*forAll\s*\("), "`->forAll(`"),
    ("ocl.exists", re.compile(r"->\s*exists\s*\("), "`->exists(`"),
    ("ocl.select", re.compile(r"->\s*select\s*\("), "`->select(`"),
    ("ocl.reject", re.compile(r"->\s*reject\s*\("), "`->reject(`"),
    ("ocl.collect", re.compile(r"->\s*collect\s*\("), "`->collect(`"),
    ("ocl.collectNested", re.compile(r"->\s*collectNested\s*\("), "`->collectNested(`"),
    ("ocl.sortedBy", re.compile(r"->\s*sortedBy\s*\("), "`->sortedBy(`"),
    ("ocl.any", re.compile(r"->\s*any\s*\("), "`->any(`"),
    ("ocl.one", re.compile(r"->\s*one\s*\("), "`->one(`"),
    ("ocl.iterate", re.compile(r"->\s*iterate\s*\("), "`->iterate(`"),
    ("ocl.closure", re.compile(r"->\s*closure\s*\("), "`->closure(`"),
    ("ocl.isUnique", re.compile(r"->\s*isUnique\s*\("), "`->isUnique(`"),
    ("ocl.let", re.compile(r"\blet\s+\w+\s*(:\s*\w+\s*)?="), "`let ... in` expression"),
    ("ocl.if-then-else", re.compile(r"\bif\b.*\bthen\b.*\belse\b.*\bendif\b", re.S), "`if ... then ... else ... endif`"),
    ("ocl.tuples", re.compile(r"\bTuple\s*\("), "`Tuple(...)` type"),
    ("ocl.tuple-literal", re.compile(r"\bTuple\s*\{"), "`Tuple{...}` literal"),
    ("ocl.oclInState", re.compile(r"\boclInState\s*\("), "`oclInState(`"),
    ("ocl.type-tests-casts", re.compile(r"\boclIsTypeOf\s*\(|\boclIsKindOf\s*\(|\boclAsType\s*\("),
     "`oclIsTypeOf`/`oclIsKindOf`/`oclAsType`"),
    ("ocl.allinstances-class", re.compile(r"\w+\s*\.\s*allInstances\b"), "`Class.allInstances`"),
    ("ocl.selectbykind-selectbytype", re.compile(r"\bselectByKind\s*\(|\bselectByType\s*\("),
     "`selectByKind`/`selectByType`"),
    ("ocl.range-literal", re.compile(r"\{\s*-?\d+\s*\.\.\s*-?\d+\s*\}"), "`{n..m}` range literal"),
    ("collection.set-literal", re.compile(r"\bSet\s*\{"), "`Set{...}` literal"),
    ("collection.bag-literal", re.compile(r"\bBag\s*\{"), "`Bag{...}` literal"),
    ("collection.sequence-literal", re.compile(r"\bSequence\s*\{"), "`Sequence{...}` literal"),
    ("collection.orderedset-literal", re.compile(r"\bOrderedSet\s*\{"), "`OrderedSet{...}` literal"),
    ("collection.flatten", re.compile(r"->\s*flatten\s*\(?"), "`->flatten`"),
    ("type.unlimited-natural", re.compile(r"\bUnlimitedNatural\b"), "`UnlimitedNatural` type"),
    ("ocl.operation-pre-post-contracts", re.compile(r"^\s*(pre|post)\s*:", re.M), "`pre:`/`post:` operation contract"),
    ("ocl.query-operation-inlining", re.compile(r"^\s*\w+\s*\([^)]*\)\s*:\s*\w+\s*=", re.M),
     "query operation declaration (`op(...): T = ...`)"),
    ("ocl.operation-polymorphic-override", re.compile(r"\bredefine\b"), "`redefine` operation clause"),
]

# Purely structural checks that need more than one regex (arity counting,
# self-reference detection) -- handled separately from TRIGGERS.
ASSOC_BLOCK = re.compile(
    r"\bassociation(?:class)?\s+(\w+)\s+between\s*(.*?)\bend\b", re.S | re.I
)
ROLE_LINE = re.compile(r"^\s*(\w+)\s*\[.*?\]\s*(?:role\s+)?(\w+)?", re.M)


def scan_associations(text):
    findings = []
    for m in ASSOC_BLOCK.finditer(text):
        name, body = m.group(1), m.group(2)
        roles = ROLE_LINE.findall(body)
        classes = [r[0] for r in roles]
        arity = len(classes)
        line = text[: m.start()].count("\n") + 1
        if arity == 2:
            findings.append(("assoc.binary", line, f"association `{name}` has 2 ends"))
            if classes[0] == classes[1]:
                findings.append(("assoc.self-referential", line, f"association `{name}` has both ends typed `{classes[0]}`"))
        elif arity >= 3:
            findings.append(("assoc.nary", line, f"association `{name}` has {arity} ends"))
    return findings


def strip_comments(text):
    """Drop `-- ...` line comments (USE/SOIL's only comment syntax) so
    prose in header comments -- which routinely quotes real OCL/USE syntax
    to explain a model, exactly the kind of text this scanner is looking
    for -- doesn't produce false positives."""
    return "\n".join(line.split("--", 1)[0] for line in text.split("\n"))


def scan_file(path):
    raw = path.read_text(encoding="utf-8", errors="replace")
    text = strip_comments(raw)
    findings = []
    for feature_id, pattern, desc in TRIGGERS:
        m = pattern.search(text)
        if m:
            line = text[: m.start()].count("\n") + 1
            findings.append((feature_id, line, desc))
    findings.extend(scan_associations(text))
    findings.sort(key=lambda t: t[1])
    return findings


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    for arg in sys.argv[1:]:
        path = Path(arg)
        print(f"=== {path} ===")
        findings = scan_file(path)
        if not findings:
            print("  (no candidate features matched)")
        seen = set()
        for feature_id, line, desc in findings:
            if feature_id in seen:
                continue
            seen.add(feature_id)
            print(f"  line {line:>4}  {feature_id:<45} {desc}")
        print(f"  {len(seen)} candidate(s) -- review against the actual invariants before tagging.")
        print()


if __name__ == "__main__":
    main()
