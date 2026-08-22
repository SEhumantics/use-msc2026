# Vendored native SAT solvers

These are the exact files the original plugin's own `modelvalidator -downloadSolvers`
command fetches at runtime from `http://www.db.informatik.uni-bremen.de/kodkod-solvers/1/linux_x86_64.zip`
(redirects to https; still reachable, last-modified 2012-12-22). Vendored here instead
of downloaded live, for the same reproducibility reason `kodkod-2.1.jar` is vendored
under `local-repo/` rather than fetched from Maven Central at build time.

**Not independently vetted for a redistribution license** beyond what the plugin's own
`-downloadSolvers` mechanism already implies a USE user may do (fetch and use them
locally) — treat like any other third-party binary a user would have downloaded
themselves; do not assume a blanket right to redistribute further.

## What actually works, confirmed by running each one

The plugin's own JDK-8-era mechanism for adding these to `java.library.path` at
runtime (`org.tzi.kodkod.helper.LibraryPathHelper`, reflecting into
`ClassLoader.usr_paths`) is broken on any modern JDK — that field no longer exists,
confirmed via `NoSuchFieldException`, and this is unrelated to porting (the helper is
unmodified original source; the same break would hit the pristine original plugin on
any JDK past 8). Fixed here without touching plugin code at all: `java.library.path`
is set at JVM *launch* (the standard, supported mechanism) instead of mutated at
runtime — see `bin/use`/`bin/use.bat` and `examples/run-example.sh`, both of which now
point it at this directory automatically.

Confirmed via `mv -config satsolver := <Name>` against `03-CompanyERSchema`, actually run:

| Name | Works | Notes |
|---|---|---|
| `DefaultSAT4J` | yes | pure Java, no native library, always available |
| `LightSAT4J` | yes | pure Java, no native library, always available |
| `MiniSat` | yes | native, loads and solves correctly |
| `MiniSatProver` | yes | native, loads and solves correctly |
| `Lingeling` | yes | native, loads and solves correctly |
| `Glucose` | **no** | plugin reports "No solver `Glucose` available" — the library's own dependencies resolve cleanly via `ldd` (not a missing-library problem), so this is a native-level incompatibility in the 2012 build itself, not a configuration issue here. Kept vendored for completeness/documentation. |
| `CryptoMiniSat` | **no** | not a valid solver name in Kodkod 2.1's own `SATFactory` at all (only `DefaultSAT4J`/`LightSAT4J`/`MiniSat`/`MiniSatProver`/`Glucose`/`Lingeling` exist as fields) — the plugin's own `CRYPTOMINISAT_NAME` constant is stale relative to this Kodkod version. Kept vendored since it's part of the original bundle. |
| `plingeling` | untested | parallel Lingeling variant, invoked as an external process via `SATFactory.plingeling()`, not JNI — different code path, not exercised here |

Five working solvers (2 pure-Java + 3 native) is what `-config satsolver := <Name>`
can actually select today.
