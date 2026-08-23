# KK-ModelValidator examples

Runnable, verified examples of the Kodkod-based model validator plugin, covering
the plugin's command surface beyond a single "does it solve" smoke test. Each
example is a `.use` model plus one or more `.cmd` USE shell scripts; every
script here has actually been executed against a built distribution, not just
written by inspection.

Ships in the distribution under `examples/KK-ModelValidator/` alongside the
main USE examples under `examples/`.

## Running an example

`lib/plugins/KK-ModelValidator-1.0.jar` must be present (it is, by default, in
any build produced by `mvn clean verify`/`mvn package` from this reactor).

From a USE distribution root (`use-7.5.0/`, i.e. the directory containing `lib/`
and `examples/`):

```bash
java -jar lib/use-gui.jar -nogui examples/KK-ModelValidator/Library/Library.use \
    examples/KK-ModelValidator/Library/validate.cmd
```

Or use the bundled runner, which finds the `.use` model in a directory and
runs one (or every) `.cmd` script against it — useful for automation, since it
does not require knowing which model file goes with which script:

```bash
bash examples/KK-ModelValidator/run-example.sh lib/use-gui.jar \
    examples/KK-ModelValidator/Library
```

**Important, discovered empirically, not documented anywhere upstream:** only
the short alias (`mv ...`) is recognized as a shell command when it comes from
a `.cmd` script file fed to `-nogui`. The long form (`modelvalidator ...`)
parses fine when a command string is passed directly to `Shell.execute(...)`
(e.g. from a JUnit test, see `EndToEndValidationTest`), but is **not**
recognized by the script-file command grammar and fails with a parse error
that looks like a model-compilation error (`missing 'model' at 'modelvalidator'`).
Every `.cmd` file here uses `mv`, confirmed working.

**Choosing a SAT solver:** `mv -config satsolver := <Name>` before `-validate` selects the backend.
`DefaultSAT4J`/`LightSAT4J` (pure Java) always work. `MiniSat`/`MiniSatProver`/`Lingeling` (native,
generally faster) also work out of the box in this distribution — `bin/use` and this directory's
`run-example.sh` both point `-Djava.library.path` at the vendored solvers in
`lib/plugins/modelValidatorPlugin/x64` automatically, no extra flags needed. See
`msc-modelvalidators/kk-modelvalidator/vendored-solvers/README.md` for exactly which solvers work and why (`Glucose`/
`CryptoMiniSat` don't, for reasons unrelated to this port) and `docs/kk-modelvalidator-port.md`'s "Sixth
pass" for the full story of why this was broken and how it was fixed without touching any plugin code.

## Library

The plugin's own historical Library/User/Copy/Book test fixture (same model
used by `EndToEndValidationTest`), demonstrating the single most common
workflow: `-config` to set the SAT solver/bitwidth, then `-validate` against a
`.properties` file. Confirmed output: `SATISFIABLE`, 9 objects (3 Users, 3
Copies, 3 Books), 6 links.

- `validate.cmd` — configure + validate + `info state`

## EmployeeInvariants

A minimal, purpose-built model (not from upstream) with two invariants where
one is a logical consequence of the other (`self.salary > 0` implies
`self.salary > -1`), chosen specifically to give the less-common commands
something meaningful to demonstrate:

- `invIndep.cmd` — `mv -invIndep Employee.properties all`. Confirmed output:
  `PositiveSalary: Independent`, `NotBelowMinusOne: Not independent for given
  properties` — i.e. the tool correctly detects the implication.
  (Note: the upstream PDF guide says the invariant-name argument to
  `-invIndep` is optional, defaulting to checking everything; the actual
  implementation in this build rejects the command without an explicit `all`
  or `className::invName` argument. Documented drift, not a bug introduced by
  this port — the same behavior reproduces against the plugin's own
  `InvariantIndepChecker` source unchanged from upstream.)
- `scrollingAll.cmd` — `mv -scrollingAll Employee.properties`. Enumerates
  every solution in the bounded space (1-2 Employees, salary in {1,2,3}) until
  `UNSATISFIABLE`. Confirmed output: `Found 9 solutions`.
- `query.cmd` — enables the query mechanism *before* searching (required),
  validates, then evaluates two OCL queries directly against the relational
  solution. Confirmed output: `Employee.allInstances()->size()` → `[[1]]`,
  `Employee.allInstances()->forAll(e | e.salary > 0)` → `[[true]]`.
- `scrolling.cmd` — single-step `mv -scrolling`/`next`/`previous`/`show(n)`,
  as opposed to `scrollingAll.cmd`'s "find everything at once". Confirmed
  real forward/backward/jump stepping by tracking `salary` across steps:
  `{1} → {3} → {2} → {3} → {1} → {2}` for initial/next/next/previous/previous/
  show(3) — not just the first solution repeated. **Discovered empirically:**
  the plugin's `mv ?` query cache does *not* track `previous`/`show(n)` — it
  only ever reflects the last *searched* solution, since those two commands
  call `createObjectDiagram(...)` without refreshing the query evaluator —
  which is why this script uses plain USE `?` instead of `mv ?` to show state
  after each step.

## CompanyERSchema

The classic entity-relationship COMPANY schema (Employee, Department,
Dependent, Project, ProjectWork, Supplier, Part/Component bill-of-materials,
ProjectPart, SupplierProjectPart), sourced essentially verbatim from
`chen_pk_fk.use`/`.properties` in an artifact archive published by the model
validator's own creators (University of Bremen DBIS group — see attribution
note below). Every association here is `derived` from string-valued
foreign-key attributes rather than a first-class navigable link, and the
constraints re-derive primary-/foreign-key integrity purely in OCL. Compiled
with zero syntax changes against USE 7.5.0.

- `validate.cmd` — the default `[small]` section (2-3 objects/class, deliberately
  rescaled down from the source's own `[one]`/`[three]`/`[six]`/`[twelve]`/`[sixteen]`
  sections, several of which are either UNSATISFIABLE for this model or take
  far too long — see the header comment in `CompanyER.properties` for exactly
  why). Confirmed: `SATISFIABLE`, `check -v` → all 29 invariants OK.
- `invIndep.cmd` — `mv -invIndep CompanyER_invIndep.properties all`. Confirmed:
  most of the 29 invariants come back `Independent`, but 8 come back
  `Not independent for given properties` — 3 FK invariants
  (`Employee::dname_foreign_key_Department` and two others) and 5
  business-rule invariants (`salary_positive`, `budget_positive`,
  `age_reasonable`, `cost_positive`, and `ProjectBudget_greater_PartCost`).
  A genuinely interesting, not-fully-explained finding worth a closer look,
  not just a textbook "PK implies nothing" demo.
- `query.cmd` — confirmed `Employee.allInstances()->size()` → `[[2]]`, an FK
  consistency check → `[[true]]`.
- `invIndepSingle.cmd` — `mv -invIndep CompanyER_invIndep.properties
  Employee::dname_foreign_key_Department` (a single named invariant, not
  `all`). Confirmed identical per-invariant result to the full sweep above
  (`Not independent for given properties`), at roughly half the wall-clock
  time (~1.6s vs ~3.4s) — useful when only one invariant's independence is in
  question.
- **Confirmed does *not* scale**: `-scrollingAll` was tried against `[small]`
  and killed after 60s without finishing — too many symmetric foreign-key
  reassignments to enumerate exhaustively at this class count. No
  scrollingAll-friendly config was produced for this example (would need most
  classes bounded to 0 objects, as `EmployeeInvariants` does).

## CivilStatus

Martin Gogolla's "civstat" tutorial model (a 2010 teaching example, predates
Kodkod entirely) — a self-referential Person/Marriage model with
birth/marry/divorce/death operations and civil-status invariants. Reproduced
essentially unchanged; only `.properties` files are new (the original archive
had none).

- `validate.cmd` — confirmed `SATISFIABLE`, `check -v` → all 5 invariants OK.
  Loading the model logs one *expected* error —
  `Cannot transform invariant 'Person::nameCapitalThenSmallLetters'. OCL
  operation substring is not supported` — because this plugin's OCL→Kodkod
  translator has no operation group for `String.substring`/`.size` at all (not
  a port regression: confirmed by grepping `org.tzi.kodkod.ocl.operation` for
  every operation group that exists). The invariant is silently dropped from
  every search regardless of its `active`/`inactive` setting; kept in the
  `.use` file rather than deleted, per this repo's own discipline of not
  quietly removing things that don't work.
- `invIndep.cmd` — confirmed `nameIsUnique`/`femaleHasNoWife`/`maleHasNoHusband`
  all `Independent` of each other (a genuinely different story, among those
  three, from `EmployeeInvariants`, where one invariant *is* redundant) —
  but the fourth active invariant here, `attributesDefined`, *does* come
  back `Not independent`, the same dependent-invariant pattern
  `EmployeeInvariants` demonstrates.
- `scrollingAll.cmd` — the default bounds (2-4 Persons, unrestricted
  civstat/gender/alive) do **not** finish enumerating in 60s (confirmed by
  actually running it); a separate, much smaller `[scrolling]` section (exactly
  2 Persons, 2 names) is used instead. Confirmed: `Found 384 solutions`.
- `query.cmd` — confirmed `Person.allInstances()->size()` → `[[2]]`, two role
  consistency checks → `[[true]]`, `[[true]]`. **Discovered empirically, not
  documented anywhere upstream:** comparing an attribute to a specific enum
  *literal* (e.g. `p.civstat = #married`) in a query can throw
  `kodkod.engine.fol2sat.UnboundLeafException: Unbound relation` if that
  literal's atom wasn't actually allocated in the particular solution found —
  the query mechanism only builds relations for values reachable in that run,
  not every enum literal the model declares. Comparing attributes to each
  other, or to a value already forced active by an invariant (like `#female`/
  `#male` here), is safe; the two queries above were rewritten to avoid this
  after hitting it firsthand.

## Genealogy

The Person/Parenthood ("Corleone family") example from Gogolla, Hilken & Doan,
*"Achieving Model Quality through Model Validation, Verification and
Exploration"* (COMLAN). Unlike `CompanyERSchema`, **no working `.use` file
exists anywhere in the source archive** — only PDF/EPS renderings of the
resulting diagrams survive. `Genealogy.use` and `corleone*.properties` are
therefore a from-scratch reconstruction assembled from verbatim OCL invariant
bodies and bound tables quoted in the paper's own LaTeX source
(`sections/preliminaries.tex`, `sections/use-cases.tex`) — every invariant
body is a direct quote, but the file structure, naming, and packaging
(all supplementary invariants folded into one file as inactive-by-default,
rather than loaded on demand via `constraints -load`, as the paper does) are
this port's own choices. Real calendar years (1891–1953) were replaced with
small synthetic relative years (0, 10, 20, …) to keep solve times to a few
seconds — the paper's own numbers took 5.7–16s per its reported timings.

- `validate.cmd` (`consistency` section) — confirmed `SATISFIABLE`. `check -v`
  re-validates *every* invariant in the file regardless of which ones this
  section's properties activated for the search — confirmed `balancedBinaryTree`
  comes back `FAILED` here (expected: inactive in `consistency`, and this
  solution's tree isn't a balanced binary one) while the three base invariants
  (`acyclicParenthood`/`nameUnique`/`parentOlderChild`) come back `OK`.
- `invIndep.cmd` — confirmed reproduces the paper's own reported finding:
  `acyclicParenthood` comes back `Not independent` (it's a logical consequence
  of `parentOlderChild` — every parent-child edge strictly increases `yearB`
  by 15+, so a cycle back to the same person becomes impossible), while
  `nameUnique`/`parentOlderChild` come back `Independent`.
- `scrollingAll.cmd` (`scrolling` section, 3 Person/2 links) — confirmed
  `Found 6 solutions`, matching the paper's own stated expectation exactly.
- `constraintImplication.cmd` — `implication_check` (wide population): confirmed
  `UNSATISFIABLE`, i.e. no counterexample to `grandparentOlderGrandchild`
  exists there — it really is implied. `implication_sanity` (narrow
  population): confirmed `SATISFIABLE`, the paper's own smaller cross-check.
- `partial-state.soil` + `partialCompletion.cmd` — the paper's own "partial
  solution completion" use case: a hand-built 2-object/1-link family (Vito,
  Michael) is loaded via SOIL, then `mv -config automaticDiagramExtraction :=
  on` before `-validate` lets the search *grow* that existing state (to 4
  objects/2 links) instead of starting from nothing. Confirmed: the search
  succeeds, and the two hand-built objects and their link survive into the
  completed solution unchanged (checked via direct OCL queries against their
  attribute values). Every other example in this suite sets
  `automaticDiagramExtraction := off`, so this is the one place that option is
  actually demonstrated turned on.
- `descLevels.ct` + `classifyingTerms.cmd` — the paper's "partitioning with
  classifying terms" use case (`-scrollingCT`/`-scrollingAllCT`, deliberately
  left out of the original port pass as too syntactically uncertain to
  attempt — now implemented and verified). `descLevel0/1/2` are new
  class-scope OCL operations on `Person` in `Genealogy.use`, partitioning
  Persons by descendant depth. `-scrollingCT` against `[consistency]` (6
  Person) pages through distinct solutions with term-value triples
  `(4,2,0)` → `(4,1,1)` → `(3,3,0)`, confirmed by direct re-run, not just
  reported; `-scrollingAllCT` against `[scrolling]` (3 Person) finds exactly
  2 term-distinct equivalence classes among the 6 raw solutions
  `scrollingAll.cmd` already finds — `(1,2,0)` and `(2,1,0)` — also
  independently re-confirmed.

## AssociationClass

A small model (`CompanyEmployment.use`, not from upstream) built specifically
to exercise **association classes** — `Employment` is declared
`associationclass ... between Person[0..*] role employee Company[0..1] role
employer attributes salary:Integer, startDate:Integer end`, giving the
`Employment` *link* its own attributes and identity, queryable and countable
like an ordinary class while still navigable as a link from either endpoint.
This is a real, implemented plugin feature (`IAssociationClass`/
`AssociationClass` in the plugin's own source) that had never been exercised
at any level anywhere in this plugin's history — not one example, not one
unit test — before this.

- `validate.cmd` — confirmed `SATISFIABLE`; `check -v` → all 5 invariants OK,
  and `info state` confirms an `Employment` object is correctly counted both
  as an `Employment` class instance and as an `Employment` association link
  simultaneously (`Employment_min` is 1, not 0, specifically so every SAT
  witness demonstrates this rather than possibly landing on zero Employment
  objects — see `CompanyEmployment.properties` for why that was a real gap).
- `invIndep.cmd` — confirmed a genuinely different flavor of "not independent"
  than any prior example: `EmployeeAndEmployerAlwaysLinked` and
  `AtMostOneEmployer` come back `Dependent` — not because the solver searched
  and failed (the "Not independent for given properties" wording elsewhere in
  this suite), but because `InvariantIndepChecker` rejects their negation as
  *trivially* unsatisfiable before the SAT solver ever runs (they restate
  structural facts — every association-class instance has both ends bound by
  construction; the `Company[0..1]` end multiplicity is already enforced by
  the association declaration itself). `PositiveSalary`/`StartDateNotNegative`/
  `SalaryBelowEmployerBudget` (the three invariants with real, individually
  violable OCL semantics) all come back plain `Independent`.
- `query.cmd` — confirmed `Employment.allInstances()->size()` → `[[1]]`, a
  cross-attribute FK-style check (`e.salary < e.employer.budget`, reaching
  from the link's own attribute through to an endpoint's attribute) → `[[true]]`.

## Inheritance

A minimal, purpose-built model (not from upstream): an `abstract class
Vehicle` superclass (attributes `licensePlate`, `wheels`) with two concrete
subclasses, `Car < Vehicle` (attribute `numDoors`) and `Truck < Vehicle`
(attribute `payloadCapacity`), built to exercise generalization/inheritance
specifically — a superclass-level invariant, a subclass-specific invariant
on each subclass, and polymorphic navigation over the superclass collection.

- `validate.cmd` — exactly 1 Car and 1 Truck (`Vehicle_min/max = 0` since
  Vehicle itself is abstract and has no direct instances). Confirmed:
  `SATISFIABLE`; `check -v` → all 3 invariants (`Vehicle::PositiveWheels`,
  `Car::ReasonableDoors`, `Truck::PositivePayload`) OK, 0 failures.
- `query.cmd` — confirmed `Vehicle.allInstances()->size()` → `[[2]]` (the
  Car and the Truck together, i.e. the superclass collection is
  transparently populated from both subclasses with no explicit union);
  `Vehicle.allInstances()->forAll(v | v.wheels > 0)` → `[[true]]`;
  `oclIsTypeOf(Car)`/`oclIsTypeOf(Truck)` counts → `[[1]]`/`[[1]]`; and an
  `oclAsType(Truck).payloadCapacity` downcast after filtering by
  `oclIsTypeOf(Truck)` → `[[5]]` (one of the three configured payload
  values), confirming the subclass-only attribute is reachable through a
  polymorphically-typed `Vehicle` collection.
- All three invariants were confirmed non-vacuous by separately forcing each
  one's governing attribute domain outside its allowed range (e.g.
  `Car_numDoors = Set{1,6}`, `Vehicle_wheels = Set{0,-2}`,
  `Truck_payloadCapacity = Set{-3}`) and observing `TRIVIALLY_UNSATISFIABLE`
  in each case — i.e. the solver is genuinely enforcing them, not just
  finding domains that happen to already comply.
- **Discovered empirically, not documented anywhere upstream:** an attribute
  declared on a *superclass* must be bounded in the `.properties` file under
  the declaring class's own name (`Vehicle_licensePlate`, `Vehicle_wheels`),
  never under a subclass's name. `Car_wheels`/`Truck_wheels` are silently
  accepted but have no effect. The consequence is subtler than a clean
  failure: a wrongly-scoped *Integer* attribute (`wheels`) silently falls
  back to the type-wide `Integer_min`/`Integer_max` range instead of its
  intended enumerated domain and can still come back `SATISFIABLE` — just
  quietly under-tested, no error at all. A wrongly-scoped *String* attribute
  with no type-wide fallback configured (`licensePlate`) does genuinely fail,
  but with a plain `UNSATISFIABLE`, not a `TRIVIALLY_` one, and no proof-node
  text of any kind (an earlier version of this note wrongly claimed both).
  See the header comment in `Vehicle.properties` for the side-by-side
  confirmation.

## AggregationComposition

A small filesystem-flavored model (`FileSystem.use`, not from upstream) built
around a whole-part composition (`Folder` containing `Folder`s and `File`s)
specifically to exercise the plugin's `aggregationcyclefreeness`/
`forbiddensharing` config-file toggles (documented in the plugin's own PDF
guide, never previously exercised anywhere). Two `.properties` files,
identical model structure, differing only in these two toggles:

- `validateDefault.cmd`/`FileSystem_default.properties` (toggles at their
  default, `on`/`on`) — a forced 2-hop composition cycle (`[cycle]` section)
  and a forced doubly-owned file (`[sharing]` section) both confirmed
  `UNSATISFIABLE`: Kodkod finds a candidate, the object-diagram
  reconstruction step logs `Warning: Insert has resulted in a cycle in the
  part-whole hierarchy` (or the analogous shared-ownership warning), the
  plugin treats that as an error and retries, and since every value here is
  pinned by concrete `Set{}` literals there is no cycle-free/unshared
  alternative to retry into.
- `validateOff.cmd`/`FileSystem_off.properties` (both toggles set `off`) — the
  *identical* forced structures now confirmed `SATISFIABLE` on the first try,
  with the cycle/shared ownership genuinely present in the reconstructed
  diagram (confirmed via direct query, e.g. `mv ? f2.primaryParent = f1` and
  `mv ? f1.altParent = f2` both `true` for the same two Folders).

**A real, previously-unknown plugin limitation surfaced while building this,
independently re-confirmed:** the toggle has **no effect at all** for the
single most natural case a modeler would reach for — one self-referential
composition association applied to itself (e.g. a single `Folder contains
Folder`), or two different classes composing the exact same class directly.
Reading the source confirms why: cycle/sharing freeness for that shape is
compiled into an *unconditional* Kodkod-level SAT constraint
(`Association.cycleFreenessDefinitions()`/`Class.forbiddingSharingDefinition()`),
which the config toggle never reaches — the toggle is only consulted later,
in a separate object-diagram-level check
(`ObjectDiagramCreator.hasDiagramErrors()`) that the Kodkod solve has already
foreclosed. The shipped example uses two *different* associations (for the
cycle) and an inheritance-widened part class (for sharing) specifically
because those are the only shapes, in this plugin build, where the toggle has
any observable effect via `-validate`.

## CollectionSemantics

A small model (`CollectionSemantics.use`, not from upstream) built to make an
existing, easy-to-miss limitation impossible to miss: this plugin's
OCL→Kodkod translator has no real Bag/Sequence support. A `->collect()` that
should produce a Bag (duplicates included) is implemented as an ordinary
Kodkod relational join instead — the duplicate is structurally lost in the
relation itself, not just mislabeled — confirmed by reading
`SetOperationGroup.collect`/`QueryExpressionVisitor.collectTypeCheck` in the
plugin's own source, not inferred from behavior alone.

- `demonstrate.cmd` — a 3-`Song`/2-possible-`album`-value model guarantees (by
  pigeonhole) a duplicate album value among the reconstructed songs. Plain USE
  OCL against the reconstructed object diagram (`? Song.allInstances().album`)
  correctly returns a `Bag` of size 3 with a genuine duplicate; the plugin's
  own `mv ?` query mechanism, asked the identical question against the
  identical solution, logs `WARN: Collect operation ... results in
  unsupported type 'Bag'. It will be interpreted as 'Set'.` and returns a
  2-element relation instead. The clearest single demonstration: `?
  Song.allInstances()->collect(s|s.album)->size() =
  Song.allInstances()->size()` evaluates `true` via plain OCL and `false` via
  `mv ?`, on the same solution, every run.
- `NOTES.md` — the full write-up, with exact source line citations.

## MultipleInheritance

`MultipleInheritance.use` preserves `use-core`'s own bundled diamond
hierarchy exactly (`B`/`C < A`; `D < B, C, E`) — a UML feature with zero
coverage anywhere in this plugin's history before this example. The original
had no attributes/operations at all, so one attribute per class plus three
invariants were added on top purely to give Kodkod a real bounded search
space; the inheritance structure itself is untouched.

- `validate.cmd` — confirmed `SATISFIABLE` in ~5–11ms, `check -v` → all 3
  invariants OK.
- `query.cmd` — navigates from the single `D` instance across all three of
  its direct superclasses (`B`, `C`, `E`) plus the shared grandparent `A` at
  the diamond's root, and confirms `oclIsKindOf` all `true` — direct proof
  the two inheritance paths to `A` aren't double-counted or confused.
- `valid-instance.soil`/`invalid-instance.soil` — confirmed `check -v`
  reports exactly `D::LevelsDiffer` FAILED for the deliberately-violating
  instance, others OK.
- **Discovered empirically**: `String.size()` has no Kodkod encoding (same
  gap family as `CivilStatus`'s `substring` finding) — silently dropped
  from the search and rejected outright by the query mechanism. Rewrote the
  affected invariant/query to use `<>` instead, which is supported.

## Subsets

`Subsets.use` is `use-core`'s own bundled
`examples/Others/Subsets/twoSubsets.use`, ported verbatim (zero syntax
changes): `A`/`B` linked by `ab` (both ends declared `union`); `C<A`/`D<B`
linked by `cd` (`c subsets a`, `d subsets b`); `E<A`/`F<B` linked by `ef`
(`e subsets a`, `f subsets b`). Per UML/OCL, a `union` end is *derived* as
the union of every end that `subsets` it, so every `cd`/`ef` link must also
be an `ab` link — a UML/OCL feature with zero coverage anywhere in this
plugin's history before this example.

- `validate.cmd` — configure + validate the `[demo]` section + `info state`
  + `check -v`. Confirmed `SATISFIABLE`, 4 objects, `check -v` reports
  `checked 0 invariants ..., 0 failures` (the ported model has no class
  invariants of its own) with no structural error either.
- `query.cmd` — confirms, by direct plain-OCL query against the found
  instance, that the subsets containment genuinely holds: `c1.d = c1.b`
  and `e1.f = e1.b` both evaluate `true`, even against a second section
  (`[boundIgnored]`) that explicitly sets `ab_min=ab_max=0` to try to force
  `ab` empty against `cd`'s own non-empty link.

**Three things discovered empirically while building this, none documented
anywhere upstream, all confirmed by actually running the commands above:**

1. The plugin's Kodkod-side translator never reads `subsets` at all —
   `grep -rn subset` over its entire `src/main/java` returns zero hits. It
   does ship a dedicated `org.tzi.kodkod.model.impl.UnionAssociation` class
   for special-casing `union` ends, and a matching
   `handleUnionAssociationNavigation` case in its OCL→Kodkod translator —
   but `grep -rn "new UnionAssociation"` over the same tree *also* returns
   zero hits: nothing in the plugin ever constructs one. So `ab` compiles
   as a completely ordinary, independent association, exactly like `cd`/
   `ef`, with no SAT-level constraint tying its content to theirs.
   Confirmed directly: bounding `ab` to exactly 2 tuples in `[demo]` lets
   the solver pick 2 tuples completely disjoint from `cd`'s/`ef`'s own
   links (`mv ? c1.b` / `mv ? e1.b` — which read that raw relation
   directly — print the swapped `f1`/`d1` instead of `query.cmd`'s
   plain-`?` answer of `d1`/`f1`, on the very same solution).
2. And yet the containment *does* hold under ordinary inspection (plain
   OCL `?`, `check -v`, any real class invariant): USE core's own
   general-purpose OCL evaluator — a code path entirely separate from the
   Kodkod plugin — correctly implements `union` navigation as the live
   derived union of `cd` and `ef`, never consulting `ab`'s own solved
   Kodkod relation. Confirmed: no `ab_min`/`ab_max` bound, including the
   deliberately contradictory `ab_min=ab_max=0` in `[boundIgnored]`, can
   make plain `? c1.b` disagree with `cd`'s own link.
3. `info state`'s printed link count for `ab` matches *neither* view above:
   it reports the size of the **set union** of `ab`'s own raw solved
   relation together with `cd`'s and `ef`'s current links, silently
   merging finding 1's "wrong" raw pairing with finding 2's correct derived
   links instead of picking one view consistently. Confirmed arithmetic:
   `[demo]`'s raw `ab` relation (size 2, disjoint from `cd`+`ef`'s own 2
   links) reports `info state: ab: 4`; `[boundIgnored]`'s raw `ab` relation
   (forced to size 0, with only `cd` contributing 1 link) reports
   `ab: 1`. `info state`'s count for a `union`-declared association is not
   reliable evidence of its actual content — query it with plain OCL `?`.

See `Subsets.properties`' header comment for the full derivation of all
three findings, including the exact commands run.

## RecursiveTree

`Tree.use` is `use-core`'s own bundled tree/DAG example, ported to
demonstrate *user-defined recursive OCL operations* — distinct from
`Genealogy`'s recursion, which uses the built-in `closure()`. Its own
`AcyclicParentship` invariant calls a self-recursive helper operation
(`childPlus2` → `childPlusOnNodeSet`).

- **Discovered empirically**: the plugin's `OperationExpressionVisitor`/
  `OperationStack` detect any self-calling operation (directly or
  transitively) and reject it at transform time
  (`TransformationException("... is recursive and thereby cannot be
  transformed.")`) — confirmed live, the plugin drops just that one
  invariant and continues rather than aborting.
- **A second, independent finding, in the untouched upstream invariant
  itself**: `childPlus2()` seeds its own accumulator with `Set{self}`, so
  `self` is trivially always a member of the result — meaning the *original*
  `AcyclicParentship` invariant evaluates `false` for every `TreeNode` in
  every instance, cyclic or not. Confirmed against unmodified upstream
  `Tree.use` + both bundled `.cmd` scripts and a fresh from-scratch acyclic
  instance — all three fail identically. Not a plugin bug; an authoring bug
  in the example USE itself ships, predating this port.
- Given both findings, this example keeps the original invariant declared
  (permanently inactive except in a dedicated probe section) and adds a
  working `AcyclicParentshipClosure` (`self.child->closure(child)->excludes(self)`)
  that Kodkod can actually solve. `validate.cmd` confirms `SATISFIABLE` with
  the closure-based invariant OK and the original recursive one FAILED
  (expected, matching `Genealogy`'s "`check -v` re-validates every
  declared invariant regardless of active/inactive" behavior); a
  `[forcedCycle]` section confirms the corrected invariant genuinely
  rejects a forced 3-cycle (`TRIVIALLY_UNSATISFIABLE`), not just passing
  vacuously.

## Redefines

`Redefines.use` ports `use-core`'s own bundled association-end redefinition
example (`CD`'s ends `redefines` `AB`'s ends) — zero prior coverage.
Confirmed positive: navigating the *superclass-declared* role on a subclass
instance correctly resolves through the redefining association under plain
USE OCL, reaching attributes that exist only on the redefining type.

- **A genuine soundness gap, confirmed live, not fixed (kept for
  comparison, per this project's own discipline of documenting rather than
  patching Kodkod behavior)**: the plugin's Kodkod translator has zero
  special-casing for `redefines` anywhere in its source. Consequence: an
  invariant written via the superclass-declared role is evaluated over an
  *empty* relation during Kodkod's search — silently vacuously true — so
  `-validate` reports `SATISFIABLE` even when `check -v` on the identical
  reconstructed state reports that very invariant `FAILED`. The plugin's own
  log even prints `Invariant '...' is not fulfilled in generated system
  state` without changing the reported outcome. Isolated in
  `Redefines.properties`'s `[translationGap]` section, documented rather
  than silently worked around.
- `mv ? c.b` (the plugin's own relational query) also returns empty for the
  redefined role, while plain OCL `?` and `.d` both resolve correctly —
  same family of gap as `Subsets`' `subsets` finding.

## Sudoku

`Sudoku.use` ports `use-core`'s own bundled Sudoku model (previously solved
only via ASSL, never through Kodkod) — a deliberate **performance** stress
test. Getting it running surfaced two previously-undiscovered plugin bugs,
neither worked around by patching the plugin, only by choosing a
model/config shape that avoids triggering them:

1. `MultiplicityTransformator.transform` parses a fixed-cardinality
   multiplicity by raw string length and blindly splits on `".."` —
   `Field[81]` (two digits) crashes with `ArrayIndexOutOfBoundsException`;
   single-digit fixed multiplicities (`Row[9]`) parse fine. Worked around
   with `Field[*]` plus exact `.properties` bounds instead of a fixed `[81]`.
2. `AttributeConfigurator.lowerBound` looks up a named object atom as
   `<bare-name>`, but `ClassConfigurator` always names atoms
   `<Class>_<object>` — a mismatch with no fallback (unlike
   `AssociationConfigurator`, which has one), throwing `No such atom in the
   universe`. Confirmed this breaks pinning *any* specific object's
   attribute by name via `.properties`, regardless of model. Worked around
   by leaving `Row.index`/`Column.index` solver-chosen (range + uniqueness
   constrained) and encoding Sudoku givens as coordinate-conditioned
   invariants instead of direct per-object bindings.
- A full 9×9 board (bitwidth 12 and 8) was actually run and left executing
  6m18s/2m30s/2m30s without terminating — confirmed genuinely intractable
  here, not assumed. Fell back to a 6×6 board (10 givens, independently
  verified via a standalone backtracking solver to have a unique solution).
  Confirmed `SATISFIABLE`, ~2.5–5.0s solving time, `check -v` → all 18
  invariants OK.

## NQueens

Authored from scratch — the classic N-Queens puzzle, chosen specifically as
a solver-choice-sensitive performance stress test. Structural
Row/Col-bijection encoding (row/column distinctness enforced by population
counts + `[1]-[1]` multiplicities) leaves one arithmetic invariant,
`Queen::noAttack`, checking both diagonals.

- `[small]` (N=8, 92 real solutions exist): confirmed `SATISFIABLE`,
  ~300–410ms.
- `[large]` (N=17): confirmed `SATISFIABLE`, ~19.4–23.3s solving time across
  repeated runs (N=18 was also tried and came back SATISFIABLE at ~30.5s —
  right at the edge, so N=17 was kept as the shipped "large" section).
- `check -v` re-confirms all 3 invariants OK in every run;
  `query.cmd` confirms row/column distinctness and no shared diagonals via
  `mv ?`.
- **A methodological finding worth keeping distinct from a plugin bug**: an
  initial, more "obvious" encoding (raw `row`/`col : Integer` attributes)
  reproducibly came back UNSATISFIABLE at every N tried, even though a
  hand-built instance satisfying the identical OCL invariant validated fine
  via `check -v`. Traced to the model author's own `Integer_min`/`Integer_max`
  bounds being sized for the row/col domain but too narrow for the *derived*
  diagonal sum — a configuration mistake, not a plugin defect. Kept as a
  documented lesson in `NQueens.use`'s own header rather than erased.

## GraphColoring

Authored from scratch — classic k-coloring, built as a deliberately hard
instance: 40 regions, 67 edges, constructed via the same "flat graph" method
Culberson's DIMACS benchmarks use (partition into 3 hidden color classes,
edges only *between* classes) so a 3-coloring provably exists by
construction while staying hidden from the solver.

- Confirmed `SATISFIABLE` at bitwidth 8+ (genuinely non-trivial solving,
  not a bounds-mismatch shortcut — but the exact time depends heavily on
  which solver: from well under a second on the fastest backend to over
  ten seconds on the slowest, a 20x+ spread (varies somewhat run to run;
  don't rely on a specific multiplier quoted here) that's the largest
  solver-choice difference seen anywhere in this suite — see
  `src/main/resources/latest-results.json` (or generate a fresh report
  via `scripts/run-benchmark.sh`) for the actual current per-solver
  numbers). The identical graph with only 2 colors available
  comes back UNSATISFIABLE in ~77ms, confirming 3 is the tight chromatic
  number, not a loose bound.
- **A real, previously-unknown plugin bug, confirmed and not worked
  around by patching Kodkod**: at bitwidth 4/5/6, the solver reports
  UNSATISFIABLE for this *exact* instance in ~15–25ms — a false negative,
  since the flat-graph construction is a constructive proof a 3-coloring
  exists, independent of integer bitwidth (colors only ever need values
  1..3). Only bitwidth≥8 gives the correct, genuinely-searched
  SATISFIABLE result. `GraphColoring.properties` pins bitwidth 8+
  explicitly and documents this.
- **A second finding**: `mv ? Region.allInstances()->size()` (the plugin's
  own query mechanism) returns `[]` empty instead of the correct count,
  specifically for object-typed collections — confirmed working correctly
  on primitive-valued collections in the same session. Plain OCL `?` has no
  such limitation and is used for the actual correctness check
  (`query.cmd`'s central `properColoring` re-check returns `[[true]]`).

## ZebraPuzzle

A dedicated **performance** stress test (`ZebraPuzzle.use`, not from
upstream, no prior encoding anywhere in this plugin's history): the classic
"Life International" (17 December 1962) Zebra Puzzle / "Einstein's riddle" —
5 houses in a row, each with a distinct color, nationality, drink, tobacco
brand, and pet, constrained by the canonical 14 interlocking clues (a 15th,
"there are five houses", is just the population bound). Modeled as a single
`House` class with an `Integer position` (1..5) plus five enum-typed
attributes, all-different expressed as six pairwise `forAll` invariants (one
per attribute), and every clue as a direct OCL invariant on `self` referring
to `House.allInstances`. Deliberately faithful to the well-known clue list
rather than a weaker invented variant, since fidelity is the entire point of
using it as a stress test: 5 interacting bijections over 5 houses each
(120^5 raw combinations before any clue is applied) with only local,
adjacency-based constraints to prune the search — famously hard for a naive/
brute-force enumerator, which is exactly why it is worth having here.

- `validate.cmd` — `DefaultSAT4J`, bitwidth 4 (position and its `+/-1`
  neighbors only ever need to represent 0..6). Confirmed: `SATISFIABLE`,
  `check -v` → all 22 invariants (1 position-range + 6 all-different + 14
  named clues + 1 derived Clue16 corollary) OK.
  Actual measured solver time (DefaultSAT4J, this machine, averaged over
  several runs): USE→Kodkod translation ~90–200ms, Kodkod→SAT translation
  ~120–350ms, **SAT solving time itself ~65–120ms** — i.e. despite the
  puzzle's reputation for defeating naive solvers, a real SAT solver handles
  it essentially instantly once properly encoded as a bounded relational
  search; the "famously hard" property is about brute-force enumeration, not
  about SAT-based constraint solving.
- `query.cmd` — the real correctness check the puzzle is built for, not
  just "did it solve": confirms the two headline answers the Zebra Puzzle is
  famous for (`House.allInstances()->any(h | h.drink = #Water).nationality`
  → `Nationality::Norwegian`, `...pet = #Zebra).nationality` →
  `Nationality::Japanese`), and dumps the full 5-house solution, confirmed to
  match the canonical published 1962 solution **exactly**, attribute for
  attribute (Yellow/Norwegian/Water/Kools/Fox, Blue/Ukrainian/Tea/
  Chesterfields/Horse, Red/Englishman/Milk/OldGold/Snails, Ivory/Spaniard/
  OrangeJuice/LuckyStrike/Dog, Green/Japanese/Coffee/Parliaments/Zebra), not
  merely the two headline facts in isolation.
  **Discovered empirically, not documented anywhere upstream** (a sharper,
  root-caused instance of the enum-literal pattern already noted in
  `CivilStatus`): the plugin's `mv ?` relational query mechanism only
  builds a Kodkod relation for an enum literal that is referenced *by name*
  somewhere in the model's own invariants. `Water` (Drink) and `Zebra` (Pet)
  are, by the puzzle's own construction, the *only* literals in their
  category never named by any clue (every other value is pinned to a
  house/nationality directly; Water and Zebra are determined purely by
  elimination — which is the whole reason the puzzle asks about them).
  Querying `h.drink = #Water` or `h.pet = #Zebra` via `mv ?` throws
  `kodkod.engine.fol2sat.UnboundLeafException: Unbound relation`, confirmed
  every run, while the identical comparison against any other, clue-named
  literal (`#Blue`, `#Parliaments`, `#Red`, `#OldGold`, ... all individually
  confirmed) resolves fine — ruling out a simpler "last enum literal" or
  "not-forced-true" explanation. Plain USE `?` against the reconstructed
  object diagram has no such limitation and is used for the actual
  correctness check instead.

## SOIL-based validation tests (14 domains)

Every example above tests model **finding** — does Kodkod's bounded search
produce *some* satisfying instance? A separate, complementary question is
model **validation**: given an instance nobody searched for, does the plugin
correctly accept a valid one and correctly reject an invalid one? 14 of the
17 domains now have a `valid-instance.soil`/`.cmd` pair (hand-builds a small,
deliberately-valid instance via plain USE SOIL `!create`/`!set`/`!insert`
statements — no Kodkod involved at all — then runs `check -v` and confirms
every applicable invariant reports `OK`) and an `invalid-instance.soil`/`.cmd`
pair (an otherwise-identical instance deliberately violating exactly **one**
named invariant, confirmed via `check -v` reporting `FAILED` for that
invariant alone and `OK` for every other one — a surgical, not incidental,
violation) — the original 8 (`Library` through `AggregationComposition`),
plus `MultipleInheritance` and `Redefines`, and 4 puzzle scenarios added
later (`ZebraPuzzle`, `GraphColoring`, `NQueens`, `Sudoku`). Every one of
these 28 scripts was actually executed and its output compared against the
intended result, not written by inspection.

One finding specific to this approach: `CivilStatus`'s
`nameCapitalThenSmallLetters` invariant — silently dropped from every Kodkod
search because the translator has no `String.substring` support (see
`CivilStatus`'s own section above) — genuinely *is* evaluated, correctly,
by `check -v` in the SOIL-based tests, since that path uses USE's own base
OCL interpreter rather than going through the Kodkod translation layer at
all. SOIL-based validation can therefore exercise strictly more of a model's
invariants than search-based finding can, for any model that hits a
translation gap like this one.

**Attribution and licensing caveat (applies to `CompanyERSchema` and
`Genealogy`):** both are sourced from `COMLAN-274-Gogolla.zip`, downloaded
from the University of Bremen DBIS group's own `publications/intern/` web
directory — publicly reachable, authored by the plugin's own creators, but
**not accompanied by a license file or a formal citable release**. Treat
anything ported from it as "source-available for academic citation," not as
freely redistributable, and cite the COMLAN paper (Gogolla, Hilken, Doan) or
Gogolla's 2010 "How to Check UML and OCL Models with USE" tutorial (for
`CivilStatus`) explicitly wherever these examples are used or published.

## Why not just the Library example

The plugin's own trunk source (`.git/reference-repositories/use-plugins/ModelValidator/`,
all branches) ships exactly one worked example internally (`test2/t002.*`,
the Library model used in `Library`) — there is no larger example corpus to
port from the plugin's own history. Everything else here exists specifically
to exercise commands and model features Library alone doesn't demonstrate:
`-invIndep` (including a single targeted invariant, `CompanyERSchema`),
`-scrollingAll`, single-step `-scrolling` (`EmployeeInvariants`), the query
mechanism, `-scrollingCT`/`-scrollingAllCT` classifying terms and
partial-solution completion (`Genealogy`), derived associations
(`CompanyERSchema`), enum types and self-referential associations
(`CivilStatus`), recursive associations (`Genealogy`), association classes
(`AssociationClass`), inheritance/polymorphism (`Inheritance`),
aggregation/composition cycle- and sharing-freeness
(`AggregationComposition`), and Bag/Sequence translation limits
(`CollectionSemantics`) — plus,
across every domain, SOIL-based validation of hand-built instances as a
check distinct from Kodkod-driven search.
