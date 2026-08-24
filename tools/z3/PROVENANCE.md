# Vendored solver: Z3 5.1.0

| Field | Value |
|---|---|
| Version | 5.1.0 |
| Released | 2026-08-16 |
| Source | https://github.com/Z3Prover/z3/releases/download/z3-5.1.0/z3-5.1.0-x64-glibc-2.39.zip |
| Archive SHA-256 | `f47be8d27d3230e823bf1eeede2fe0abaca55bb78d0b59974370e6689a92284a` |
| Binary SHA-256 | `b4e0b3483ce37817230b20d6cad48390eb6a3aefde1d93342ad6dc763f24bc23` |
| Binary size | 36,403,784 bytes |
| Platform | Linux x86-64, dynamically linked, requires glibc >= 2.39 |
| Vendored on | 2026-08-24 |
| Recorded fallback | 4.16.0 — archive `7288c49a5bd6dbafd7b0b0d1f65956b91672da24b08f09242919af159be3418e`, binary `e583c4186a45e72411fa2cb2048401eed03f0f8e5f24694676a8f6271a50b765` |

Vendored rather than resolved from `PATH` so experimental results cannot change with an unrelated
toolchain upgrade. The OPAM-provided Z3 4.13.0 was deliberately not used.

The recorded satisfiable, unsatisfiable, and malformed cases were byte-identical on Z3 4.13.0, 4.16.0,
and 5.1.0, including exact rational output. If a regression appears, use `tools/fetch-z3.sh 4.16.0` and
re-run every recorded result.
