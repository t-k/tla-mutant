---- MODULE GcdShapes ----
EXTENDS Integers

CONSTANT C
VARIABLE x

Init == x = 0

Positive == x >= 0

Conditional == IF x = 0 THEN TRUE ELSE FALSE

Nested == x >= 0 /\ x < 10

Primed == x' = x + 1

Core == /\ Positive
        /\ x < 10
        /\ ~Positive
        /\ Conditional
        /\ Nested
        /\ ENABLED Primed
        /\ Primed

ParamAction(p) == /\ p = C
                  /\ x' = x

Next == Core \/ \E p \in {C} : ParamAction(p)

TypeOK == x \in Int

====
