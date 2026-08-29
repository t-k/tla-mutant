---- MODULE CensusFixture ----
EXTENDS Integers

VARIABLES x, y

Init == x = 0 /\ y = 0

Guard == x < 2

Update == x' = x + 1 /\ UNCHANGED y

Action == /\ Guard
          /\ Update

StateOnly == /\ x < 2
             /\ y = 0

StateOnlyAction == /\ StateOnly
                   /\ UNCHANGED <<x, y>>

Next == Action \/ StateOnlyAction

TypeOK == x \in Int /\ y \in Int

====
