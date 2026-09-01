# RQ4 -- Cost comparison (real-verdict intersection)
Intersection: 46 of 82 rows. KK-trivial: 32. Censored: 4.
| Metric | Z3 | KK DefaultSAT4J |
|---|---|---|
| Median (ms) | 6.3 | 2.3 |
| Q1 (ms) | 4.4 | 0.9 |
| Q3 (ms) | 37.6 | 6.5 |
| IQR (ms) | 33.2 | 5.6 |
| Mean (ms) | 487.1 | 840.5 |
| Min (ms) | 2.2 | 0.3 |
| Max (ms) | 13465.3 | 17830.6 |

Z3 faster on 5/46; KK faster on 41/46. Median ratio (Z3/KK): 4.23.

| Example | Outcome | Z3 med | Z3 min | Z3 max | KK med | KK min | KK max | Ratio |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| AggregationComposition | UNSATISFIABLE | 7.9 | 7.3 | 9.2 | 17.1 | 16.2 | 20.0 | 0.46 |
| AssociationClass | SATISFIABLE | 8.5 | 7.9 | 40.2 | 5.2 | 4.8 | 5.9 | 1.64 |
| AssociationClass-UNSAT | UNSATISFIABLE | 8.1 | 7.5 | 8.9 | 2.5 | 2.4 | 3.5 | 3.22 |
| Casting | SATISFIABLE | 4.1 | 4.0 | 5.6 | 0.8 | 0.7 | 0.9 | 5.09 |
| CivilStatus | SATISFIABLE | 13.3 | 11.8 | 16.8 | 3.5 | 3.3 | 3.9 | 3.81 |
| CivilStatus-UNSAT | UNSATISFIABLE | 9.4 | 9.3 | 10.8 | 1.7 | 1.4 | 3.2 | 5.65 |
| CollectionSemantics | SATISFIABLE | 14.8 | 6.6 | 18.2 | 2.3 | 2.1 | 2.5 | 6.28 |
| CollectionSemantics-UNSAT | UNSATISFIABLE | 6.0 | 5.1 | 6.8 | 0.5 | 0.4 | 0.6 | 11.44 |
| CompanyERSchema | SATISFIABLE | 271.3 | 252.8 | 320.1 | 58.5 | 56.7 | 100.0 | 4.64 |
| CompanyERSchema-UNSAT | UNSATISFIABLE | 295.3 | 246.2 | 313.6 | 12.5 | 11.9 | 12.8 | 23.58 |
| DerivedAttr | SATISFIABLE | 5.6 | 5.2 | 14.1 | 1.3 | 1.2 | 1.4 | 4.19 |
| DerivedAttr-UNSAT | UNSATISFIABLE | 4.4 | 3.7 | 9.6 | 1.1 | 1.0 | 1.4 | 3.9 |
| DerivedFK | SATISFIABLE | 4.7 | 4.6 | 4.8 | 0.9 | 0.8 | 1.0 | 5.15 |
| DerivedFK-UNSAT | UNSATISFIABLE | 2.9 | 2.6 | 3.2 | 0.4 | 0.3 | 0.5 | 7.64 |
| EmployeeInvariants | SATISFIABLE | 6.0 | 5.2 | 7.2 | 3.1 | 2.6 | 4.8 | 1.92 |
| EmployeeInvariants-UNSAT | UNSATISFIABLE | 2.9 | 2.7 | 3.1 | 0.7 | 0.7 | 0.9 | 4.23 |
| Genealogy | SATISFIABLE | 245.3 | 217.9 | 262.3 | 99.9 | 70.7 | 132.1 | 2.46 |
| Genealogy-UNSAT | UNSATISFIABLE | 12.6 | 12.3 | 17.8 | 22.7 | 16.8 | 32.8 | 0.55 |
| Inheritance | SATISFIABLE | 5.5 | 5.2 | 6.0 | 2.1 | 1.5 | 2.8 | 2.67 |
| Inheritance-UNSAT | UNSATISFIABLE | 6.2 | 5.1 | 7.0 | 1.0 | 0.9 | 1.5 | 6.07 |
| IntegerBitwidth-DailyCap | SATISFIABLE | 6.1 | 5.6 | 7.6 | 2.4 | 2.3 | 3.8 | 2.53 |
| Library | SATISFIABLE | 37.6 | 34.0 | 83.6 | 20.6 | 15.1 | 30.1 | 1.83 |
| Library-UNSAT | UNSATISFIABLE | 21.3 | 20.1 | 25.5 | 3.3 | 2.9 | 4.0 | 6.52 |
| MultipleInheritance | SATISFIABLE | 9.5 | 9.4 | 9.8 | 2.3 | 2.0 | 2.7 | 4.21 |
| MultipleInheritance-UNSAT | UNSATISFIABLE | 2.8 | 2.7 | 3.2 | 0.9 | 0.6 | 1.1 | 3.03 |
| NQueens | SATISFIABLE | 13465.3 | 13465.3 | 13465.3 | 17796.1 | 17796.1 | 17796.1 | 0.76 |
| NQueens-UNSAT | UNSATISFIABLE | 115.1 | 112.4 | 121.9 | 2.3 | 2.2 | 2.5 | 51.15 |
| Nary | SATISFIABLE | 5.7 | 4.6 | 6.3 | 2.3 | 1.5 | 8.3 | 2.5 |
| PriceCalc | SATISFIABLE | 4.8 | 4.3 | 5.5 | 1.4 | 1.3 | 1.4 | 3.48 |
| PriceCalc-UNSAT | UNSATISFIABLE | 3.5 | 3.3 | 3.6 | 0.8 | 0.7 | 1.0 | 4.12 |
| RangeLiteral | SATISFIABLE | 5.1 | 3.7 | 6.2 | 1.1 | 0.9 | 1.2 | 4.66 |
| RecursiveTree | SATISFIABLE | 188.0 | 166.1 | 199.9 | 3.4 | 3.3 | 3.7 | 55.78 |
| RecursiveTree-UNSAT | UNSATISFIABLE | 3430.0 | 3423.9 | 3585.5 | 6.5 | 6.5 | 7.1 | 524.63 |
| Redefines | SATISFIABLE | 7.4 | 6.6 | 9.1 | 1.3 | 1.3 | 1.4 | 5.58 |
| Redefines-TranslationGap | UNSATISFIABLE | 6.3 | 5.0 | 6.9 | 1.4 | 1.2 | 1.7 | 4.36 |
| Redefines-UNSAT | UNSATISFIABLE | 4.6 | 4.3 | 4.9 | 0.5 | 0.5 | 0.9 | 8.84 |
| SetAttr | SATISFIABLE | 4.2 | 3.4 | 6.4 | 0.8 | 0.7 | 1.0 | 5.44 |
| SetAttr-UNSAT | UNSATISFIABLE | 2.4 | 2.3 | 2.7 | 0.3 | 0.3 | 0.5 | 8.01 |
| Subsets | SATISFIABLE | 4.3 | 4.0 | 4.8 | 1.7 | 1.5 | 2.0 | 2.6 |
| Subsets-UNSAT | UNSATISFIABLE | 2.6 | 2.5 | 2.8 | 0.5 | 0.4 | 0.6 | 5.64 |
| Sudoku | SATISFIABLE | 797.0 | 787.8 | 806.2 | 2702.6 | 2526.4 | 2878.9 | 0.29 |
| Sudoku-UNSAT | UNSATISFIABLE | 2880.7 | 2880.7 | 2880.7 | 17830.6 | 17830.6 | 17830.6 | 0.16 |
| UnionNav | SATISFIABLE | 2.6 | 2.5 | 2.8 | 0.7 | 0.6 | 0.8 | 3.99 |
| UnionNav-UNSAT | UNSATISFIABLE | 2.2 | 2.1 | 2.4 | 0.6 | 0.6 | 0.7 | 3.51 |
| ZebraPuzzle | SATISFIABLE | 251.6 | 244.7 | 257.0 | 22.5 | 19.9 | 25.6 | 11.19 |
| ZebraPuzzle-UNSAT | UNSATISFIABLE | 211.8 | 200.5 | 257.7 | 17.1 | 15.5 | 20.9 | 12.36 |
