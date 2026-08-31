---- MODULE NamedTemporal ----
VARIABLE x

Init == x = 0
Next == x' = 1

Spec == Init /\ [][Next]_x
TypeOK == x \in {0, 1}
AlwaysZero == [] (x = 0)

====
