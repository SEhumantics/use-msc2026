# RQ4 -- Cost comparison (real-verdict intersection)
Intersection: 46 of 82 rows. KK-trivial: 32. Censored: 4.
| Metric | Z3 | KK DefaultSAT4J |
|---|---|---|
| Median (ms) | 6.2 | 1.9 |
| Q1 (ms) | 3.9 | 0.9 |
| Q3 (ms) | 34.5 | 8.3 |
| IQR (ms) | 30.6 | 7.4 |
| Mean (ms) | 501.0 | 901.7 |
| Min (ms) | 2.3 | 0.3 |
| Max (ms) | 14042.3 | 19762.5 |

Z3 faster on 5/46; KK faster on 41/46. Median ratio (Z3/KK): 3.98.

| Example | Outcome | Z3 med | Z3 min | Z3 max | KK med | KK min | KK max | Ratio |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| AggregationComposition | UNSATISFIABLE | 4.0 | 3.8 | 4.4 | 21.2 | 18.6 | 25.9 | 0.19 |
| AssociationClass | SATISFIABLE | 8.6 | 7.7 | 8.9 | 3.9 | 3.1 | 6.5 | 2.19 |
| AssociationClass-UNSAT | UNSATISFIABLE | 6.2 | 6.1 | 7.4 | 2.2 | 2.1 | 2.2 | 2.85 |
| Casting | SATISFIABLE | 6.8 | 6.4 | 7.5 | 1.4 | 1.1 | 1.7 | 5.01 |
| CivilStatus | SATISFIABLE | 16.3 | 13.3 | 22.5 | 2.5 | 2.4 | 3.1 | 6.43 |
| CivilStatus-UNSAT | UNSATISFIABLE | 8.9 | 8.2 | 9.6 | 2.3 | 1.8 | 5.0 | 3.89 |
| CollectionSemantics | SATISFIABLE | 4.0 | 4.0 | 4.2 | 1.1 | 0.9 | 1.4 | 3.52 |
| CollectionSemantics-UNSAT | UNSATISFIABLE | 2.6 | 2.2 | 2.7 | 0.4 | 0.3 | 0.4 | 7.36 |
| CompanyERSchema | SATISFIABLE | 258.9 | 236.1 | 319.9 | 79.4 | 52.4 | 128.0 | 3.26 |
| CompanyERSchema-UNSAT | UNSATISFIABLE | 300.9 | 258.8 | 311.2 | 12.3 | 11.7 | 13.1 | 24.53 |
| DerivedAttr | SATISFIABLE | 5.0 | 4.8 | 5.6 | 0.9 | 0.9 | 1.2 | 5.22 |
| DerivedAttr-UNSAT | UNSATISFIABLE | 4.2 | 3.8 | 5.2 | 1.1 | 1.0 | 1.3 | 3.74 |
| DerivedFK | SATISFIABLE | 6.8 | 5.6 | 7.7 | 2.0 | 1.2 | 2.5 | 3.46 |
| DerivedFK-UNSAT | UNSATISFIABLE | 3.9 | 3.9 | 4.5 | 0.5 | 0.4 | 0.6 | 8.74 |
| EmployeeInvariants | SATISFIABLE | 7.9 | 6.9 | 27.8 | 3.1 | 2.5 | 3.9 | 2.54 |
| EmployeeInvariants-UNSAT | UNSATISFIABLE | 2.9 | 2.8 | 3.3 | 0.8 | 0.6 | 1.2 | 3.88 |
| Genealogy | SATISFIABLE | 222.9 | 212.9 | 288.2 | 109.4 | 60.8 | 129.0 | 2.04 |
| Genealogy-UNSAT | UNSATISFIABLE | 10.5 | 10.2 | 10.5 | 16.3 | 16.0 | 18.5 | 0.64 |
| Inheritance | SATISFIABLE | 7.0 | 6.7 | 8.3 | 2.3 | 2.0 | 4.3 | 3.01 |
| Inheritance-UNSAT | UNSATISFIABLE | 3.6 | 3.5 | 3.8 | 1.6 | 0.8 | 1.9 | 2.24 |
| IntegerBitwidth-DailyCap | SATISFIABLE | 4.2 | 3.8 | 4.6 | 2.3 | 2.2 | 3.3 | 1.83 |
| Library | SATISFIABLE | 34.5 | 30.4 | 45.4 | 33.9 | 26.4 | 43.5 | 1.02 |
| Library-UNSAT | UNSATISFIABLE | 18.7 | 17.3 | 19.2 | 1.6 | 1.5 | 1.7 | 11.74 |
| MultipleInheritance | SATISFIABLE | 5.5 | 5.3 | 6.4 | 1.2 | 1.0 | 1.6 | 4.54 |
| MultipleInheritance-UNSAT | UNSATISFIABLE | 2.8 | 2.7 | 3.2 | 0.5 | 0.5 | 0.6 | 5.76 |
| NQueens | SATISFIABLE | 14042.3 | 14042.3 | 14042.3 | 18562.1 | 18562.1 | 18562.1 | 0.76 |
| NQueens-UNSAT | UNSATISFIABLE | 126.2 | 113.5 | 143.6 | 2.5 | 2.3 | 12.3 | 50.35 |
| Nary | SATISFIABLE | 5.4 | 5.2 | 5.7 | 0.9 | 0.8 | 1.0 | 6.0 |
| PriceCalc | SATISFIABLE | 5.6 | 5.3 | 6.7 | 1.9 | 1.7 | 2.1 | 2.98 |
| PriceCalc-UNSAT | UNSATISFIABLE | 5.3 | 4.5 | 8.5 | 0.9 | 0.8 | 1.2 | 5.94 |
| RangeLiteral | SATISFIABLE | 6.2 | 5.9 | 7.0 | 1.6 | 1.3 | 1.9 | 3.98 |
| RecursiveTree | SATISFIABLE | 208.7 | 166.2 | 213.2 | 4.3 | 4.0 | 4.4 | 48.05 |
| RecursiveTree-UNSAT | UNSATISFIABLE | 3613.7 | 3521.9 | 3680.6 | 8.3 | 8.0 | 9.5 | 436.04 |
| Redefines | SATISFIABLE | 3.7 | 3.6 | 4.2 | 1.1 | 0.9 | 1.1 | 3.51 |
| Redefines-TranslationGap | UNSATISFIABLE | 3.2 | 2.8 | 3.6 | 0.7 | 0.6 | 0.8 | 4.73 |
| Redefines-UNSAT | UNSATISFIABLE | 5.2 | 4.1 | 5.4 | 0.8 | 0.7 | 0.8 | 6.7 |
| SetAttr | SATISFIABLE | 3.1 | 3.1 | 3.4 | 0.6 | 0.5 | 0.7 | 5.64 |
| SetAttr-UNSAT | UNSATISFIABLE | 2.3 | 2.2 | 2.6 | 0.3 | 0.2 | 0.4 | 7.59 |
| Subsets | SATISFIABLE | 4.7 | 4.3 | 5.2 | 1.5 | 1.3 | 1.8 | 3.06 |
| Subsets-UNSAT | UNSATISFIABLE | 2.7 | 2.5 | 2.8 | 0.4 | 0.4 | 0.7 | 6.36 |
| Sudoku | SATISFIABLE | 665.6 | 620.7 | 710.5 | 2791.1 | 2641.6 | 2940.6 | 0.24 |
| Sudoku-UNSAT | UNSATISFIABLE | 2870.2 | 2870.2 | 2870.2 | 19762.5 | 19762.5 | 19762.5 | 0.15 |
| UnionNav | SATISFIABLE | 3.4 | 3.1 | 3.4 | 1.0 | 0.8 | 1.2 | 3.2 |
| UnionNav-UNSAT | UNSATISFIABLE | 3.8 | 3.3 | 6.1 | 0.8 | 0.7 | 1.1 | 4.71 |
| ZebraPuzzle | SATISFIABLE | 265.5 | 252.0 | 280.7 | 15.4 | 15.0 | 16.6 | 17.2 |
| ZebraPuzzle-UNSAT | UNSATISFIABLE | 240.4 | 211.2 | 261.6 | 15.2 | 15.1 | 16.0 | 15.81 |
