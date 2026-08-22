-- Loading CivilStatus.use logs one expected transformation error for
-- nameCapitalThenSmallLetters (uses String.substring/.size, which this
-- plugin's OCL translator has no operation group for) -- it is silently
-- dropped from the search regardless of the "inactive" setting below; see
-- CivilStatus.properties for the full explanation. That is expected, not a
-- failure of this example.
mv -validate CivilStatus.properties

check -v

quit
