---- MODULE NamedAction ----
VARIABLE x

Init == x = 0

Enabled == x = 0

Advance ==
    /\ Enabled
    /\ x' = 1

NamedSpec == Init /\ [][Advance]_x

TypeOK == x \in {0, 1}
====
