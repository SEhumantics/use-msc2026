-- Checks each invariant's independence: can it be violated in isolation
-- while every other invariant still holds? PositiveSalary should come back
-- independent; NotBelowMinusOne should come back dependent, since it is a
-- logical consequence of PositiveSalary.
mv -invIndep Employee.properties all

quit
