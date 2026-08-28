---- MODULE Violation ----
EXTENDS Integers
VARIABLE x
Init == x = 0
Next == x' = x + 1
Safety == x = 0
====
