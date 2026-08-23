-- Checks whether femaleHasNoWife/maleHasNoHusband (constraining the two
-- disjoint role-slots of the same Marriage association) and nameIsUnique
-- are genuinely independent of each other -- among those three, a
-- different story from EmployeeInvariants, where one invariant IS a
-- logical consequence of the other. This properties file also has a
-- fourth invariant active, Person::attributesDefined, which DOES come
-- back "Not independent for given properties" here (confirmed by
-- actually running this) -- CivilStatus.properties' own header explains
-- why (it already forces every attribute to be defined), so this is the
-- same dependent-invariant pattern EmployeeInvariants demonstrates, just
-- on a fourth invariant this comment doesn't discuss, not something the
-- three named invariants above are exempt from.
mv -invIndep CivilStatus_invIndep.properties all

quit
