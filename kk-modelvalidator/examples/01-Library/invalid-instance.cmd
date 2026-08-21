-- Model VALIDATION of a hand-built instance that deliberately breaks ONE
-- named invariant, Book_yearPlausible (context b:Book inv yearPlausible:
-- 1455<=b.year), for the DBforDummies object (year := 1400). Load
-- invalid-instance.soil through `open', then run `check -v' and confirm
-- exactly that one invariant is reported FAILED while every other active
-- invariant (User_nameAddressFormatOk, User_nameIsKey,
-- User_noDoubleBorrowings, Copy_signatureFormatOk, Copy_signatureIsKey,
-- Book_titleFormatOk, Book_titleIsKey, Book_authSeqFormatOk) still reports
-- OK.
open invalid-instance.soil

info state

-- Confirmed by actually running this: `check -v' reports
-- "checking invariant (4) `Book::yearPlausible': FAILED." with
-- DBforDummies as the sole object in Book.allInstances->select(not
-- yearPlausible) below, and finishes "checked 9 invariants, 1 failure.";
-- every one of the other 8 invariants reports OK/true in the same run.
check -v

? Book.allInstances->select(b | not (1455<=b.year))

quit
