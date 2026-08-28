---- MODULE MutationFixture ----
EXTENDS Integers
VARIABLES x, y

Init == x = 0 /\ y = 1
GuardA == x < 3
GuardB == y > 0
Shared == x <= 10
UpdateX == x' = x + 1
UpdateY == y' = 0
UnknownUpdate == x' = "unknown"

Next == /\ GuardA
        /\ GuardB
        /\ Shared
        /\ UpdateX
        /\ UpdateY

Safety == Shared /\ x >= 0
ScopeOnly == x <= 20

====
