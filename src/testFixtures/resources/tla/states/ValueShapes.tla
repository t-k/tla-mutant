---- MODULE ValueShapes ----
EXTENDS Integers
CONSTANT MV
VARIABLES i, b, s, m, set, tuple, record, function

vars == <<i, b, s, m, set, tuple, record, function>>

Init == /\ i = 1
        /\ b = TRUE
        /\ s = "A"
        /\ m = MV
        /\ set = {2, 1}
        /\ tuple = <<2, 1>>
        /\ record = [b |-> 2, a |-> 1]
        /\ function = [n \in {2, 1} |-> n]

Next == UNCHANGED vars
Safety == TRUE

====
