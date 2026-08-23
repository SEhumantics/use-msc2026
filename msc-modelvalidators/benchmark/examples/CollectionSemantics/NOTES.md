# Confirmed limitation: `->collect()` results are always interpreted as `Set`, never `Bag`/`Sequence`

## What triggers it

Kodkod (the relational model finder this plugin translates OCL into) has
no native representation for a multiset (`Bag`) or an ordered collection
(`Sequence`) -- every Kodkod relation is a set of tuples, with duplicates
and ordering both structurally impossible to represent.

Per the OCL standard, `Collection->collect(x | expr)` never filters, only
maps each source element through `expr`, so its result must have the
*same size* as its source and must preserve duplicates: `collect()` over
a `Set` or `Bag` source produces a `Bag`; over an `OrderedSet` or
`Sequence` source it produces a `Sequence`.

This plugin's translator
(`org.tzi.use.kodkod.transform.ocl.QueryExpressionVisitor`, method
`collectTypeCheck`, lines 73-82 of
`kk-modelvalidator/src/main/java/org/tzi/use/kodkod/transform/ocl/QueryExpressionVisitor.java`)
detects exactly this situation and logs a `WARN` -- but then proceeds to
build the collect as a genuine Kodkod **Set** anyway
(`org.tzi.kodkod.ocl.operation.SetOperationGroup.collect`, which registers
`"collect"`/`"collectNested"` as set-returning operations and implements
them as an ordinary relational join, lines 31-32 and 100-127 of
`kk-modelvalidator/src/main/java/org/tzi/kodkod/ocl/operation/SetOperationGroup.java`).
So this is not merely a mislabeled type or a display quirk: the
relation the solver actually builds and reasons over has already lost
the duplicate tuples by the time any invariant or query sees it.

The exact log line (confirmed, reproduced verbatim below from an actual
run against this example, only the timestamp elided):

```
WARN: Collect operation `Song.allInstances->collect(s : Song | s.album)' results in unsupported type `Bag'. It will be interpreted as `Set'.
```

It fires for **every** `->collect()`/`->collectNested()` whose source
collection is a `Set`, `Bag`, `OrderedSet`, or `Sequence` -- i.e.
essentially every real-world use of `collect`, since those are the OCL
collection types `.allInstances()` and every plain association-end
navigation produce. It fires whether the `collect` sits inside an
invariant, an operation body, or (as exercised here) an ad-hoc `mv ?`
query -- all three go through the same `DefaultExpressionVisitor` /
`QueryExpressionVisitor` translation path.

## The scenario in this example

`CollectionSemantics.use` defines a `Playlist` with exactly 3 `Song`s
(`Playlist [1] role playlist` / `Song [3] role songs` in the `Contains`
association), and `CollectionSemantics.properties` restricts each
`Song.album : Integer` to only **2** possible values (`Song_album =
Set{1,2}`). With 3 Songs and only 2 possible album values, the
pigeonhole principle guarantees every solution the solver finds has at
least one repeated album value -- there is no way to avoid it.

- **Expected (true Bag semantics):** `Song.allInstances()->collect(s |
  s.album)` has size 3 (one entry per Song, duplicate kept), so
  `Song.allInstances()->collect(s | s.album)->size() =
  Song.allInstances()->size()` should be `true` (3 = 3).
- **Actual (this plugin, via `mv ?`):** the collect is reinterpreted as
  a `Set`, collapsing the duplicate. Its size is always exactly 2 (the
  number of *distinct* album values actually used), never 3, so the
  same equality query evaluates to `false`.

## Confirmed, reproducible output

Run against the actual built plugin (`java -jar
.../use-7.5.0/lib/use-gui.jar -nogui CollectionSemantics.use
demonstrate.cmd`), enabling the query mechanism, validating, then
comparing the **same OCL expression** evaluated two different ways
against the **same reconstructed solution**:

| Evaluation path | Expression | Result |
|---|---|---|
| Plain USE OCL (`?`), i.e. the real reconstructed object diagram, genuine `Bag` support | `Song.allInstances().album` | `Bag{1,2,2}` (or `Bag{1,1,2}` -- see below) |
| Plain USE OCL (`?`) | `Song.allInstances()->collect(s\|s.album)->size() = Song.allInstances()->size()` | `true` |
| This plugin's relational query mechanism (`mv ?`) | `Song.allInstances()->collect(s\|s.album)` | `[[1], [2]]` -- a 2-tuple relation, i.e. `Set{1,2}` |
| This plugin's relational query mechanism (`mv ?`) | `Song.allInstances()->collect(s\|s.album)->size()` | `[[2]]`, **not** `[[3]]` |
| This plugin's relational query mechanism (`mv ?`) | `Song.allInstances()->collect(s\|s.album)->size() = Song.allInstances()->size()` | `false` |

Every one of these five rows was actually executed and observed (see
`demonstrate.cmd` and `query.cmd` in this directory, both fully
commented, runnable as-is).

**Note on the specific duplicated value:** DefaultSAT4J is not
guaranteed to return the same satisfying witness on every run; repeated
invocations of this example were observed to return either `Bag{1,2,2}`
or `Bag{1,1,2}` (confirmed by running `query`/`demonstrate` several
times in a row). Which value repeats varies; that there *is* a repeat,
and that the plugin's `->collect()` always drops it, does not -- the
`Set` result was observed to have size 2 (never 3) on every run.

## Why this matters

Any OCL invariant or query that relies on `->collect()` (or `->sum()`
over a `Bag`/`Sequence`, which triggers a sibling warning -- see
`StandardOperationVisitor.printSumWarning`, not exercised in this
example) preserving multiplicities will silently get a wrong answer from
this plugin whenever the source data actually contains duplicates after
the `collect`. There is no configuration flag or workaround; the fix
would require Kodkod-level multiset support, which does not exist
upstream in this plugin's current translation architecture.
