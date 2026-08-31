# QF_NIA/QF_NRA experiment -- prim.integer-arithmetic's nonlinear residual

Real Z3 runs against the pinned binary (`tools/z3/bin/z3`, Z3 5.1.0). Re-run everything with
`./run.sh` (override the binary with `Z3=... ./run.sh`); captured output from the 2026-08-31
run is in `captured-output.txt`.

## What was measured (all wall times from captured-output.txt)

| Script | Logic | Shape | Result | Wall |
|---|---|---|---|---:|
| single-product.smt2 | QF_NIA | x*y = 24, x,y in [2,12] | sat, x=8 y=3 | 0.02 s |
| chain-corpus-scale.smt2 | QF_NIA | product CHAIN a*b=36, b*c=72, c*d=144 over [0,20] | sat | 0.01 s |
| qfnra-quadratic.smt2 | QF_NRA | real quadratic r^2 <= 50 in a band | sat, r=7.0 | 0.01 s |
| wide-bound-adversarial.smt2 | QF_NIA | NON-factorization of prime 999999937, bounds 10^6 | timeout (killed) | 54.8 s |

(An earlier run of the same wide-bound shape at bounds 5*10^8 was killed at 120 s.)

## Disposition

1. Bounded nonlinear products/quadratics are solver-tractable at CORPUS scale (tens of
   milliseconds) under QF_NIA/QF_NRA.
2. The wall is wide-bound REFUTATION: proving a product CANNOT hit a prime over wide bounds
   is exponential and times out. Any slice must therefore carry an explicit solve-time budget.
3. Why it is scoped, not built: the translator refuses nonlinear emission UPSTREAM (a product
   of two non-constant symbols throws at translation time), so a solver-side escalation policy
   alone would never trigger -- a slice must first allow nonlinear emission (FragmentChecker +
   visitStdOp + ledger accounting) and only then re-emit under an escalated logic with the
   budget. That emission-side change is the slice's real work and is deliberately not folded
   into a fix round.
4. prim.integer-round-toString's Real round() half needed NO nonlinear machinery: it landed
   linearly as floor(r+0.5) via SMT-LIB to_int (commit 526b9e94).
