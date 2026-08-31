; QF_NIA product chain at corpus scale: products of bounded attribute
; symbols feeding equalities, the shape a prim.integer-arithmetic
; nonlinear-product slice would emit.
(set-logic QF_NIA)
(declare-const a Int) (declare-const b Int) (declare-const c Int) (declare-const d Int)
(assert (>= a 0)) (assert (<= a 20))
(assert (>= b 0)) (assert (<= b 20))
(assert (>= c 0)) (assert (<= c 20))
(assert (>= d 0)) (assert (<= d 20))
(assert (= (* a b) 36))
(assert (= (* b c) 72))
(assert (= (* c d) 144))
(check-sat)
(get-value (a b c d))
