---- MODULE MutationFixture ----
EXTENDS Integers
VARIABLES x, y, z

Init == x = 0 /\ y = 1 /\ z = 0
GuardA == x < 3
GuardB == y > 0
Shared == x <= 10
UpdateX == x' = x + 1
UpdateY == y' = 0
NestedComparison == IF x > 0 THEN 0 ELSE 0
UpdateZ == z' = NestedComparison
UnknownUpdate == x' = "unknown"

Next == /\ GuardA
        /\ GuardB
        /\ Shared
        /\ UpdateX
        /\ UpdateY
        /\ UpdateZ

Safety == Shared /\ x >= 0
ScopeOnly == x <= 20

====
