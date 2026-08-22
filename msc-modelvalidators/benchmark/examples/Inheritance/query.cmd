-- The query mechanism must be enabled BEFORE the search, so extra relations
-- needed to answer OCL queries get built into the relational model.
mv ? enable

mv -validate Vehicle.properties

-- Polymorphic navigation: Vehicle.allInstances() implicitly contains every
-- concrete subclass instance (both the Car and the Truck found above),
-- demonstrating that the superclass collection is populated from its
-- subclasses without any explicit union.
mv ? Vehicle.allInstances()->size()
mv ? Vehicle.allInstances()->forAll(v | v.wheels > 0)

-- oclIsTypeOf distinguishes exactly which concrete subclass each Vehicle
-- really is.
mv ? Vehicle.allInstances()->select(v | v.oclIsTypeOf(Car))->size()
mv ? Vehicle.allInstances()->select(v | v.oclIsTypeOf(Truck))->size()

-- oclAsType downcasts a polymorphically-typed Vehicle back to Truck so its
-- subclass-only attribute (payloadCapacity) can be navigated.
mv ? Vehicle.allInstances()->select(v | v.oclIsTypeOf(Truck))->collect(v | v.oclAsType(Truck).payloadCapacity)->asSet()

quit
