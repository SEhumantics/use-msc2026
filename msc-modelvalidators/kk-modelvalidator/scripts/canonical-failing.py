#!/usr/bin/env python3
# Prints the exact "KIND\tclassname#testname" set of currently-failing/erroring tests from a
# surefire-reports directory (default target/surefire-reports, or argv[1]), one per line, sorted --
# the canonical form floor-check.sh diffs against scripts/known-failing-tests.txt. Deliberately only
# reads TEST-*.xml (the per-class reports with real <testcase> children), not the 5 JUnit3-style
# *TestSuite.xml wrapper reports, which report 0/0/0/0 at their own root and have no <testcase>
# children of their own to double-count.
import re, glob, os, sys

reports_dir = sys.argv[1] if len(sys.argv) > 1 else "target/surefire-reports"
out = set()
for fn in sorted(glob.glob(os.path.join(reports_dir, "TEST-*.xml"))):
    content = open(fn, encoding="utf-8", errors="replace").read()
    m_cls = re.search(r'<testsuite\b[^>]*\bname="([^"]+)"', content)
    cls = m_cls.group(1) if m_cls else os.path.basename(fn)
    blocks = re.findall(r'<testcase\b.*?(?:/>|</testcase>)', content, re.S)
    for b in blocks:
        m = re.search(r'name="([^"]+)"', b)
        if not m:
            continue
        name = m.group(1)
        if '<failure' in b:
            out.add(f"FAILURE\t{cls}#{name}")
        elif '<error' in b:
            out.add(f"ERROR\t{cls}#{name}")

for line in sorted(out):
    print(line)
