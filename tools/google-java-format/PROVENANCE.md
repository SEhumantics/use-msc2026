# Vendored formatter: google-java-format 1.36.1

| Field | Value |
|---|---|
| Version | 1.36.1 |
| Source | https://github.com/google/google-java-format/releases/download/v1.36.1/google-java-format-1.36.1-all-deps.jar |
| Binary SHA-256 | `25b400f003089d23cc5320cdaf1a16cabee19b8aa3434d0ff021b3d9f42154b4` |
| Vendored on | 2026-08-25 |

Vendored so a consistent, deterministic formatting pass is always available, matching the reasoning for
vendoring Z3 (`tools/z3/PROVENANCE.md`): don't depend on whatever happens to be on a given machine.

Run `tools/google-java-format/format-unc-modelvalidator.sh` before every commit to `unc-modelvalidator`.
Applies Google Java Style (2-space indent, 100-column, standard brace placement) to every file under
`msc-modelvalidators/unc-modelvalidator/src`. Purely cosmetic — verified after the first full-module
reformat (2026-08-25) that it changes no semantics: the full test suite (62/62) and full reactor build
passed identically before and after.
