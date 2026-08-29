---- MODULE AttributionFixture ----
EXTENDS Integers
VARIABLE x

Init == x = 0

TargetStep == /\ x = 3
              /\ x' = 2

TypeStep == /\ x = 3
            /\ x' = 4

PassStep == /\ x = 3
            /\ x' = 0

Next == TargetStep \/ TypeStep \/ PassStep

TypeOK == x \in 0..3
LegacyOK == x >= 0
AdoptedOK == x # 2

====
