#!/usr/bin/env bash
# Re-runs every script in this directory against the pinned Z3 and prints
# per-script wall time (the numbers quoted in the feature matrix evidence).
set -u
Z3="${Z3:-/home/xoruser/msc-4/use-msc2026/tools/z3/bin/z3}"
"$Z3" --version
for f in single-product.smt2 chain-corpus-scale.smt2 qfnra-quadratic.smt2 wide-bound-adversarial.smt2; do
  echo "== $f"
  /usr/bin/time -f "  wall=%es" timeout 60 "$Z3" "$f" 2>&1 | head -8
  echo "  exit=$?"
done
