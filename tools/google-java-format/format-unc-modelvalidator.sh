#!/usr/bin/env bash
# Reformats every .java file in unc-modelvalidator with google-java-format, in place.
#
# CONVENTION, NOT AN ENFORCED CHECK (corrected 2026-09-02): this line used to claim
# "CI/plan review checks formatting by running this and diffing". No such check exists
# anywhere -- .github/workflows/maven.yml runs a plain `mvn verify`, no pom declares a
# spotless/checkstyle/fmt plugin, and there are no git hooks. google-java-format style is
# an unenforced project convention that this script exists to help you follow by hand.
#
# NOTE BEFORE RUNNING: the tree is not currently format-clean, so a blanket run rewrites a
# large majority of the module's files and buries a real change in the diff. Prefer
# formatting only the files you touched:
#   java -jar tools/google-java-format/google-java-format-1.36.1-all-deps.jar --replace <files...>
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${here}/../.." && pwd)"
jar="${here}/google-java-format-1.36.1-all-deps.jar"

mapfile -t files < <(find "${repo_root}/msc-modelvalidators/unc-modelvalidator/src" -name "*.java")
if [ "${#files[@]}" -eq 0 ]; then
  echo "no .java files found under unc-modelvalidator/src" >&2
  exit 1
fi
java -jar "${jar}" --replace "${files[@]}"
echo "reformatted ${#files[@]} files"
