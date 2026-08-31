---- MODULE FullPropertyMutation ----
EXTENDS Naturals

VARIABLE x

Init == x = 0

Advance == /\ x = 0
           /\ x' = x + 1

Reset == /\ x = 1
         /\ x' = 0

Next == Advance \/ Reset
Spec == Init /\ [][Next]_x /\ WF_x(Reset)

TypeOK == x \in 0..2
StateConstraint == x <= 2
ReturnsToZero == [] (x /= 0 => <> (x = 0))

====
