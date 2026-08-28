---- MODULE RoleFixture ----
EXTENDS Integers
VARIABLE x

vars == <<x>>
Init == x = 0
Bound == x < 3
Step == x' = x + 1
Shared == x <= 3
PropertyHelper == x >= 0
ScopeHelper == x >= 0
Unreachable == x = 42

Next == /\ Bound
        /\ Step
        /\ Shared

Safety == PropertyHelper /\ Shared
StateConstraint == ScopeHelper
Spec == Init /\ [][Next]_vars

====
