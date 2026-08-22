-- Checks whether femaleHasNoWife/maleHasNoHusband (constraining the two
-- disjoint role-slots of the same Marriage association) and nameIsUnique
-- are genuinely independent of each other -- a different story from
-- EmployeeInvariants, where one invariant IS a logical consequence of
-- the other.
mv -invIndep CivilStatus_invIndep.properties all

quit
