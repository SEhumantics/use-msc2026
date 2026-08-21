-- Checks independence of all 29 class invariants in a 0..3/0..4-object
-- search space. Several FK/business-rule invariants come back "not
-- independent" here -- a real, surprising finding worth a closer look, not
-- just the textbook PK-vs-non-PK story 02-EmployeeInvariants demonstrates.
mv -invIndep CompanyER_invIndep.properties all

quit
