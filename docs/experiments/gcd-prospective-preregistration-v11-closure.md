# Corrective v11 Closure Evidence Preregistration

The initial v11 intervention result is preserved with an `UNRESOLVED` assessment because it did not retain the type-only state relation or witness required by the original closure rule. This corrective run is an instrumentation and validation completion after the selected outcome is known; it is not a new holdout, candidate selection, review, predicate, or intervention.

## Frozen correction

- Evidence-retention and closure-validator commit: `897e57f`
- Unresolved initial result commit: `27d3822`
- Corrective application SHA-256: `0c84489df67046b7418cc7dea6f9b794ec17f9de42664049814fe48d02b378dd`
- Corrective application size: 3,148,231 bytes
- Corrective census receipt SHA-256: `12c865261d3a92abf72214aea401f79e50d9778b2c24434babe9c79554e4086e`
- Corrective census receipt size: 2,080 bytes

The correction changes only two evidence surfaces. First, an adopted-target kill now records the type-only state relation and mutant-only witness while the type-only state files still exist. Second, a review-bound closure validator returns `VERIFIED` only when the selected v10 eligible result, v11 baseline count, type-only execution, state count, allowed relation, exact witness, and adopted-target kill all match. Missing or changed evidence returns `UNRESOLVED`.

The frozen v10 protocol, v10 manifest, v10 census, v10 discovery, dossier, review ledger, v11 protocol, v11 manifest, reviewed predicate, source insertion, selected entry, and selected candidate ID remain byte-identical. The original and initial v11 artifacts remain at their existing paths and are not overwritten.

## Corrective execution rule

The corrective census and mutation outputs must use new `-closure` paths. The census must be executed twice with byte-identical results and committed before creating a corrective experiment receipt. The mutation must then execute twice with semantic equality. A positive final assessment is permitted only if `validate-prospective-closure` reconstructs the complete corrected report from its frozen manifest, census, experiment receipt, and embedded experiment, then publishes a write-once assessment whose `closureStatus` is `VERIFIED`, `selected` is 1, `closed` is 1, and `unresolved` is 0.

If the exact v10 witness is not retained as the corrective type-only witness, the result remains unresolved even if state counts match or the full oracle kills the mutant.
