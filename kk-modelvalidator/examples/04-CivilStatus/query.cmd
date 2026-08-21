mv ? enable
mv -validate CivilStatus.properties

mv ? Person.allInstances()->size()

-- NOTE, discovered by actually running this: comparing against a specific
-- enum LITERAL (e.g. `p.civstat = #married`) in a query can throw
-- kodkod.engine.fol2sat.UnboundLeafException: Unbound relation if that
-- literal's atom was never allocated in the particular solution found (the
-- query mechanism only builds relations for values actually reachable in
-- this run, not all declared enum literals) -- comparing attributes to each
-- other, or checking a Boolean/gender value already used elsewhere in an
-- active invariant, is safe. These two mirror femaleHasNoWife/
-- maleHasNoHusband exactly, so both must evaluate to [[true]].
mv ? Person.allInstances()->forAll(p | p.gender = #female implies p.wife.isUndefined)
mv ? Person.allInstances()->forAll(p | p.gender = #male implies p.husband.isUndefined)

quit
