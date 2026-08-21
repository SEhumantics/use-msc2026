-- Validates the class hierarchy: Vehicle (abstract superclass) with Car and
-- Truck subclasses, exactly 1 instance of each concrete subclass.
mv -validate Vehicle.properties

-- Re-checks all class invariants against the reconstructed state directly
-- (independent confirmation, on top of the plugin's own outcome line).
check -v

quit
