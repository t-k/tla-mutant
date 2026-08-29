---- MODULE FairSpec ----
EXTENDS Integers

VARIABLE x

vars == <<x>>

Init == x = 0

NextGuard == x < 3

NextUpdate == x' = x + 1

Next == /\ NextGuard
        /\ NextUpdate

FairGuard == x >= 0

FairOnly == /\ FairGuard
            /\ x' = x

Spec == Init /\ [][Next]_vars /\ WF_vars(FairOnly)

TypeOK == x \in Int

====
