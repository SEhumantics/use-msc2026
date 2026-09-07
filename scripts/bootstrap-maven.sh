#!/usr/bin/env bash
# One-time ONLINE dependency bootstrap for an isolated Maven repository.
#
# The run/test scripts execute Maven OFFLINE (-o) so a measured run can never silently pull
# different plugin or dependency versions than the pinned set. Offline only works once the
# repository has been populated; this script does exactly that, online, and nothing else:
#
#   scripts/bootstrap-maven.sh /path/to/repo     (or set MSC_M2_REPO and run without arguments)
#
# After it finishes, point the run scripts at the same directory:
#
#   MSC_M2_REPO=/path/to/repo scripts/run-tests.sh --ours-only
#   MSC_M2_REPO=/path/to/repo scripts/run-experiments.sh <output-dir>
#
# Every script that sees MSC_M2_REPO adds -Dmaven.repo.local to each Maven call, so an
# unbootstrapped or unrelated repository fails fast with an explicit message instead of
# silently falling back to ~/.m2. Requires network access and Maven 3.9+ (for MAVEN_ARGS).
set -euo pipefail
cd "$(dirname "$0")/.."

REPO="${1:-${MSC_M2_REPO:-}}"
if [[ -z "$REPO" ]]; then
  echo "usage: scripts/bootstrap-maven.sh <maven-repo-dir>   (or set MSC_M2_REPO)" >&2
  exit 2
fi
mkdir -p "$REPO"
REPO="$(cd "$REPO" && pwd)"

echo "== bootstrapping Maven repository at $REPO (online; one-time; ~15-20 min with tests) =="
# FULL reactor install, tests included: the upstream-oracle floor gates demand the default
# test population and are deliberately unskippable, and the KK suite's 462 pinned failures do
# not fail the build (see docs/kk-modelvalidator-port.md). Installing (not just packaging)
# puts every artifact, including parent POMs, where later OFFLINE reactor subsets resolve them.
mvn -Dmaven.repo.local="$REPO" install dependency:go-offline dependency:resolve dependency:resolve-plugins

echo
echo "== bootstrap complete =="
echo "Offline runs from this repository:"
echo "  MSC_M2_REPO=$REPO scripts/run-tests.sh --ours-only"
echo "  MSC_M2_REPO=$REPO scripts/run-experiments.sh <output-dir>"
