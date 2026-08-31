; The wall: NON-factorization of a prime over wide bounds -- proving NO
; factorization exists requires excluding every candidate pair.
(set-logic QF_NIA)
(declare-const x Int) (declare-const y Int)
(assert (>= x 2)) (assert (<= x 1000000))
(assert (>= y 2)) (assert (<= y 1000000))
(assert (= (* x y) 999999937))
(check-sat)
