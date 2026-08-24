#!/usr/bin/env bash
# Re-fetch and verify the vendored Z3. Run from anywhere; paths are resolved from this script.
set -euo pipefail

VERSION="${1:-5.1.0}"
ARCHIVE="z3-${VERSION}-x64-glibc-2.39.zip"
URL="https://github.com/Z3Prover/z3/releases/download/z3-${VERSION}/${ARCHIVE}"

case "${VERSION}" in
  5.1.0)
    ARCHIVE_SHA256="f47be8d27d3230e823bf1eeede2fe0abaca55bb78d0b59974370e6689a92284a"
    BINARY_SHA256="b4e0b3483ce37817230b20d6cad48390eb6a3aefde1d93342ad6dc763f24bc23"
    ;;
  4.16.0)
    ARCHIVE_SHA256="7288c49a5bd6dbafd7b0b0d1f65956b91672da24b08f09242919af159be3418e"
    BINARY_SHA256="e583c4186a45e72411fa2cb2048401eed03f0f8e5f24694676a8f6271a50b765"
    ;;
  *)
    echo "No recorded checksum for Z3 ${VERSION}; refusing to install an unverified binary." >&2
    exit 1
    ;;
esac

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
target="${here}/z3/bin/z3"
workdir="$(mktemp -d)"
trap 'rm -rf "${workdir}"' EXIT

echo "Fetching ${URL}"
curl -sSL -o "${workdir}/${ARCHIVE}" "${URL}"
echo "${ARCHIVE_SHA256}  ${workdir}/${ARCHIVE}" | sha256sum -c -

unzip -o -q -d "${workdir}/x" "${workdir}/${ARCHIVE}"
mkdir -p "$(dirname "${target}")"
cp "${workdir}/x/z3-${VERSION}-x64-glibc-2.39/bin/z3" "${target}"
chmod +x "${target}"
echo "${BINARY_SHA256}  ${target}" | sha256sum -c -

"${target}" --version
echo "Vendored Z3 ${VERSION} verified at ${target}"
