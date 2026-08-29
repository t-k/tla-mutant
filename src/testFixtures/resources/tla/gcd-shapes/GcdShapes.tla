---- MODULE GcdShapes ----
EXTENDS Integers

VARIABLE x

Init == x = 0

Positive == x >= 0

Conditional == IF x = 0 THEN TRUE ELSE FALSE

Nested == x >= 0 /\ x < 10

Primed == x' = x + 1

Next == /\ Positive
        /\ x < 10
        /\ ~Positive
        /\ Conditional
        /\ Nested
        /\ ENABLED Primed
        /\ Primed

TypeOK == x \in Int

====
