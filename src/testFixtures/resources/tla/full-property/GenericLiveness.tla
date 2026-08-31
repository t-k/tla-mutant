---- MODULE GenericLiveness ----
VARIABLE x

Init == x = 0
Next == x' = x

Spec == Init /\ [][Next]_x
TypeOK == x \in {0, 1}
EventuallyOne == <> (x = 1)
EventuallyTwo == <> (x = 2)

====
