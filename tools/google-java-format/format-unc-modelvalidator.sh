#!/usr/bin/env bash
# Reformats every .java file in unc-modelvalidator with google-java-format, in place.
# Run before committing. CI/plan review checks formatting by running this and diffing.
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
