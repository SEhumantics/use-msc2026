# RQ4 -- Cost comparison (real-verdict intersection, 46 of 82 rows)
| Example | Outcome | Z3 (ms) | KK DefaultSAT4J (ms) | Z3/KK ratio |
|---|---|---:|---:|---:|
| AggregationComposition | UNSATISFIABLE | 11.8 | 31.3 | 0.38 |
| AssociationClass | SATISFIABLE | 18.3 | 11.8 | 1.55 |
| AssociationClass-UNSAT | UNSATISFIABLE | 7.2 | 3.3 | 2.15 |
| Casting | SATISFIABLE | 12.7 | 3.8 | 3.33 |
| CivilStatus | SATISFIABLE | 16.8 | 6.9 | 2.44 |
| CivilStatus-UNSAT | UNSATISFIABLE | 11.3 | 3.5 | 3.22 |
| CollectionSemantics | SATISFIABLE | 8.1 | 4.6 | 1.76 |
| CollectionSemantics-UNSAT | UNSATISFIABLE | 3.2 | 0.9 | 3.71 |
| CompanyERSchema | SATISFIABLE | 373.9 | 118.1 | 3.17 |
| CompanyERSchema-UNSAT | UNSATISFIABLE | 344.7 | 21.4 | 16.14 |
| DerivedAttr | SATISFIABLE | 8.0 | 2.3 | 3.56 |
| DerivedAttr-UNSAT | UNSATISFIABLE | 4.8 | 2.5 | 1.9 |
| DerivedFK | SATISFIABLE | 7.1 | 2.8 | 2.5 |
| DerivedFK-UNSAT | UNSATISFIABLE | 3.7 | 2.7 | 1.38 |
| EmployeeInvariants | SATISFIABLE | 15.9 | 6.3 | 2.54 |
| EmployeeInvariants-UNSAT | UNSATISFIABLE | 5.0 | 1.3 | 3.71 |
| Genealogy | SATISFIABLE | 317.1 | 118.5 | 2.68 |
| Genealogy-UNSAT | UNSATISFIABLE | 13.6 | 23.5 | 0.58 |
| Inheritance | SATISFIABLE | 11.1 | 6.5 | 1.7 |
| Inheritance-UNSAT | UNSATISFIABLE | 4.7 | 1.0 | 4.73 |
| IntegerBitwidth-DailyCap | SATISFIABLE | 5.3 | 3.5 | 1.53 |
| Library | SATISFIABLE | 148.8 | 186.1 | 0.8 |
| Library-UNSAT | UNSATISFIABLE | 27.9 | 4.8 | 5.8 |
| MultipleInheritance | SATISFIABLE | 6.9 | 4.0 | 1.73 |
| MultipleInheritance-UNSAT | UNSATISFIABLE | 3.6 | 0.8 | 4.37 |
| NQueens | SATISFIABLE | 13719.6 | 17139.1 | 0.8 |
| NQueens-UNSAT | UNSATISFIABLE | 115.1 | 17.2 | 6.7 |
| Nary | SATISFIABLE | 8.0 | 5.0 | 1.59 |
| PriceCalc | SATISFIABLE | 5.4 | 8.0 | 0.68 |
| PriceCalc-UNSAT | UNSATISFIABLE | 4.0 | 1.2 | 3.38 |
| RangeLiteral | SATISFIABLE | 12.8 | 4.2 | 3.03 |
| RecursiveTree | SATISFIABLE | 168.3 | 6.6 | 25.44 |
| RecursiveTree-UNSAT | UNSATISFIABLE | 3399.4 | 6.7 | 504.1 |
| Redefines | SATISFIABLE | 9.8 | 6.1 | 1.6 |
| Redefines-TranslationGap | UNSATISFIABLE | 5.8 | 7.8 | 0.74 |
| Redefines-UNSAT | UNSATISFIABLE | 4.8 | 0.7 | 6.59 |
| SetAttr | SATISFIABLE | 10.4 | 2.4 | 4.34 |
| SetAttr-UNSAT | UNSATISFIABLE | 16.4 | 0.6 | 26.31 |
| Subsets | SATISFIABLE | 5.1 | 4.1 | 1.25 |
| Subsets-UNSAT | UNSATISFIABLE | 3.4 | 1.1 | 3.18 |
| Sudoku | SATISFIABLE | 665.1 | 2712.9 | 0.25 |
| Sudoku-UNSAT | UNSATISFIABLE | 2803.7 | 17479.6 | 0.16 |
| UnionNav | SATISFIABLE | 3.8 | 2.9 | 1.3 |
| UnionNav-UNSAT | UNSATISFIABLE | 2.8 | 1.5 | 1.82 |
| ZebraPuzzle | SATISFIABLE | 245.1 | 25.8 | 9.5 |
| ZebraPuzzle-UNSAT | UNSATISFIABLE | 243.7 | 18.1 | 13.48 |

**Summary**: Z3 mean 496.6ms, median 10.7ms; KK DefaultSAT4J mean 826.6ms, median 4.7ms. Z3 faster on 8/46; KK faster on 38/46.

**Out of intersection**: 32 KK-trivial rows (SMT answered, KK silently dropped the invariants), 4 censored rows (ERROR on one or both sides). Total: 82 = 82.
