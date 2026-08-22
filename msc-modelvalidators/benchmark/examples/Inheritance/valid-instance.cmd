-- Model VALIDATION of a hand-built instance -- a distinct concern from model
-- FINDING via `mv -validate' (Kodkod search): here the object diagram is
-- constructed by hand (valid-instance.soil, plain USE SOIL statements) and
-- then simply checked, with no search involved at all.
open valid-instance.soil

info state

-- Expect every invariant to report OK: car1/truck1 were built to satisfy
-- Vehicle::PositiveWheels, Car::ReasonableDoors and Truck::PositivePayload
-- simultaneously.
check -v

quit
