#!/usr/bin/env bash
# Runs one or all .cmd scripts in a KK-ModelValidator example directory against
# a built USE distribution, headless. Intended for both manual use and
# automation (CI, or an agent driving the plugin non-interactively).
#
# Usage:
#   run-example.sh <path-to-use-gui.jar> <example-dir> [script.cmd]
#
# Examples:
#   run-example.sh ../../use-assembly/target/.../lib/use-gui.jar 01-Library
#   run-example.sh ../../use-assembly/target/.../lib/use-gui.jar 02-EmployeeInvariants invIndep.cmd
set -euo pipefail

if [ "$#" -lt 2 ]; then
    echo "usage: $0 <path-to-use-gui.jar> <example-dir> [script.cmd]" >&2
    exit 1
fi

USE_GUI_JAR="$1"
EXAMPLE_DIR="$2"
SCRIPT="${3:-}"

if [ ! -f "$USE_GUI_JAR" ]; then
    echo "Cannot find use-gui.jar at [$USE_GUI_JAR]" >&2
    exit 1
fi

MODEL="$(find "$EXAMPLE_DIR" -maxdepth 1 -name '*.use' | head -1)"
if [ -z "$MODEL" ]; then
    echo "No .use model found in [$EXAMPLE_DIR]" >&2
    exit 1
fi

# Native SAT solvers (MiniSat/MiniSatProver/Lingeling; see
# kk-modelvalidator/vendored-solvers/README.md) live alongside the plugin jar at
# lib/plugins/modelValidatorPlugin/x64 in a built distribution. Passing this
# unconditionally is harmless even if the directory doesn't exist yet.
SOLVER_LIB_DIR="$(dirname "$USE_GUI_JAR")/plugins/modelValidatorPlugin/x64"

run_one() {
    local cmd="$1"
    echo "=== $(basename "$cmd") ==="
    java -Djava.library.path="$SOLVER_LIB_DIR" -jar "$USE_GUI_JAR" -nogui "$MODEL" "$cmd"
    echo
}

if [ -n "$SCRIPT" ]; then
    run_one "$EXAMPLE_DIR/$SCRIPT"
else
    for cmd in "$EXAMPLE_DIR"/*.cmd; do
        run_one "$cmd"
    done
fi
