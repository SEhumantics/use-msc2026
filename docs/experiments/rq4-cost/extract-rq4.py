#!/usr/bin/env python3
"""
RQ4 cost-extraction script.

Reads a BenchmarkRunner results.json, applies the real-verdict intersection
rule (matching ParityTable.java), and produces studyC-rq4-cost.json and
studyC-rq4-cost.md artifacts with per-example timing comparisons.

Usage:
  python3 extract-rq4.py <results.json> [output_dir]

The partition (from DESIGN_BRIEF_RQ4_COST.md + ParityTable.java):
  intersection  : SMT-Z3 reaches SAT/UNSAT AND all 5 KK backends reach SAT/UNSAT
  kk_trivial    : SMT real, but at least one KK backend reports TRIVIALLY_*
  censored      : either side reports ERROR
"""
import json, os, sys, statistics
from collections import defaultdict

KK_SOLVERS = ['DefaultSAT4J', 'LightSAT4J', 'MiniSat', 'MiniSatProver', 'Lingeling']
KK_REAL = {'SATISFIABLE', 'UNSATISFIABLE'}
SMT_REAL = {'SATISFIABLE', 'UNSATISFIABLE'}

def quartiles(sorted_vals):
    n = len(sorted_vals)
    q1 = sorted_vals[n // 4] if n >= 4 else sorted_vals[0]
    q3 = sorted_vals[(3 * n) // 4] if n >= 4 else sorted_vals[-1]
    return q1, q3

def iqr(sorted_vals):
    q1, q3 = quartiles(sorted_vals)
    return q3 - q1

def main():
    if len(sys.argv) < 2:
        print("usage: extract-rq4.py <results.json> [output_dir]")
        sys.exit(1)
    results_path = sys.argv[1]
    out_dir = sys.argv[2] if len(sys.argv) > 2 else '.'
    os.makedirs(out_dir, exist_ok=True)

    data = json.load(open(results_path))
    by_example = defaultdict(dict)
    for r in data:
        by_example[r['exampleId']][r['solver']] = r

    intersection = []
    kk_trivial = []
    censored = []

    for eid in sorted(by_example):
        row = by_example[eid]
        kk_outs = [row[s]['outcome'] for s in KK_SOLVERS if s in row]
        smt = row.get('SMT-Z3', {}).get('outcome', 'MISSING')
        if 'ERROR' in kk_outs or smt == 'ERROR':
            censored.append(eid)
        elif all(o in KK_REAL for o in kk_outs) and smt in SMT_REAL:
            intersection.append(eid)
        elif smt in SMT_REAL:
            kk_trivial.append(eid)
        else:
            censored.append(eid)

    # Per-example timing extraction
    rows = []
    for eid in sorted(intersection):
        r = by_example[eid]
        z3 = r['SMT-Z3']
        kk = r['DefaultSAT4J']
        z3_ms = z3['medianWallMs']
        kk_ms = kk['medianWallMs']
        ratio = round(z3_ms / kk_ms, 2) if kk_ms > 0 else None
        rows.append({
            'exampleId': eid,
            'smtOutcome': z3['outcome'],
            'kkOutcome': kk['outcome'],
            'z3MedianWallMs': round(z3_ms, 1),
            'z3MinWallMs': round(z3.get('minWallMs', z3_ms), 1),
            'z3MaxWallMs': round(z3.get('maxWallMs', z3_ms), 1),
            'kkMedianWallMs': round(kk_ms, 1),
            'kkMinWallMs': round(kk.get('minWallMs', kk_ms), 1),
            'kkMaxWallMs': round(kk.get('maxWallMs', kk_ms), 1),
            'ratio': ratio,
        })

    # Summary with median/IQR
    z3_vals = sorted(r['z3MedianWallMs'] for r in rows)
    kk_vals = sorted(r['kkMedianWallMs'] for r in rows)
    ratios = sorted(r['ratio'] for r in rows if r['ratio'] is not None)
    smt_faster = sum(1 for r in rows
                     if r['z3MedianWallMs'] < r['kkMedianWallMs'])
    kk_faster = len(rows) - smt_faster

    def summarize(vals):
        return {
            'median': round(median := vals[len(vals)//2], 1),
            'q1': quartiles(vals)[0],
            'q3': quartiles(vals)[1],
            'iqr': quartiles(vals)[1] - quartiles(vals)[0],
            'mean': round(sum(vals) / len(vals), 1),
            'min': vals[0],
            'max': vals[-1],
        }

    summary = {
        'intersectionSize': len(rows),
        'z3': summarize(z3_vals),
        'kkDefaultSAT4J': summarize(kk_vals),
        'smtFasterOn': smt_faster,
        'kkFasterOn': kk_faster,
        'medianRatio': round(ratios[len(ratios)//2], 2),
        'ratioIqr': [ratios[len(ratios)//4], ratios[3*len(ratios)//4]],
    }

    out = {
        'study': 'RQ4-cost',
        'source': results_path,
        'methodology': {
            'partition': 'intersection (both real) / kk_trivial (SMT real, KK TRIVIALLY_*) / censored (ERROR)',
            'kkSolverReported': 'DefaultSAT4J (all 5 backends must agree on SAT/UNSAT for intersection membership)',
            'censoring': 'ERROR rows reported as censored counts, not dropped',
        },
        'partition': {
            'total': len(by_example),
            'intersection': len(intersection),
            'kkTrivial': len(kk_trivial),
            'censored': len(censored),
        },
        'summary': summary,
        'rows': rows,
        'outOfIntersection': {
            'kkTrivial': sorted(kk_trivial),
            'censored': sorted(censored),
        },
    }

    json_path = os.path.join(out_dir, 'studyC-rq4-cost.json')
    json.dump(out, open(json_path, 'w'), indent=2)

    md_path = os.path.join(out_dir, 'studyC-rq4-cost.md')
    md = ['# RQ4 -- Cost comparison (real-verdict intersection)\n']
    md.append(f'Intersection: {len(rows)} of {len(by_example)} rows. '
              f'KK-trivial: {len(kk_trivial)}. Censored: {len(censored)}.\n')
    md.append(f'| Metric | Z3 | KK DefaultSAT4J |\n|---|---|---|\n')
    for k, label in [('median', 'Median (ms)'), ('q1', 'Q1 (ms)'), ('q3', 'Q3 (ms)'),
                     ('iqr', 'IQR (ms)'), ('mean', 'Mean (ms)'), ('min', 'Min (ms)'), ('max', 'Max (ms)')]:
        md.append(f'| {label} | {summary["z3"][k]} | {summary["kkDefaultSAT4J"][k]} |\n')
    md.append(f'\nZ3 faster on {smt_faster}/{len(rows)}; KK faster on {kk_faster}/{len(rows)}. '
              f'Median ratio (Z3/KK): {summary["medianRatio"]}.\n')
    md.append(f'\n| Example | Outcome | Z3 med | Z3 min | Z3 max | KK med | KK min | KK max | Ratio |\n')
    md.append('|---|---|---:|---:|---:|---:|---:|---:|---:|\n')
    for r in sorted(rows, key=lambda x: x['exampleId']):
        md.append(f"| {r['exampleId']} | {r['smtOutcome']} | {r['z3MedianWallMs']} | "
                  f"{r['z3MinWallMs']} | {r['z3MaxWallMs']} | {r['kkMedianWallMs']} | "
                  f"{r['kkMinWallMs']} | {r['kkMaxWallMs']} | {r['ratio']} |\n")
    open(md_path, 'w').write(''.join(md))

    print(f'RQ4 extraction complete: {len(rows)} intersection rows, '
          f'{len(kk_trivial)} KK-trivial, {len(censored)} censored')
    print(f'Artifacts: {json_path}, {md_path}')

if __name__ == '__main__':
    main()
