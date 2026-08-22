-- Model VALIDATION of a hand-built instance that deliberately violates ONE
-- named invariant, to confirm `check -v' actually catches it (not just that
-- the state is malformed).
open invalid-instance.soil

info state

-- Expect Car::ReasonableDoors to report FAILED (car1.numDoors = 1, outside
-- [2,5]) while Vehicle::PositiveWheels and Truck::PositivePayload still
-- report OK.
check -v

quit
