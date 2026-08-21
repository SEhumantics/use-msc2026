-- Model VALIDATION of a hand-built instance -- a distinct concern from
-- model FINDING via mv -validate/Kodkod search (see validate.cmd). Load
-- valid-instance.soil's plain USE SOIL statements (!new/!set/!insert, no
-- solver involved) through the shell's `open' command, then ask USE's own
-- `check -v' to verify every invariant against that hand-built state.
open valid-instance.soil

info state

-- Confirmed by actually running this: all 9 invariants active in this
-- model (User_nameAddressFormatOk, User_nameIsKey, User_noDoubleBorrowings,
-- Copy_signatureFormatOk, Copy_signatureIsKey, Book_titleFormatOk,
-- Book_titleIsKey, Book_authSeqFormatOk, Book_yearPlausible) report OK,
-- and `check -v' finishes with "checking invariants took ... checked 9
-- invariants, 0 failures.".
check -v

quit
