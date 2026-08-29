# Prospective Assessment Provenance Design

## Goal

Make the prospective closure assessment self-contained as a provenance record. A reader holding only the assessment must be able to identify every artifact consumed by closure validation, including the validator binary, without consulting narrative documentation or reconstructing the command line.

## Current limitation

`ProspectiveClosureAssessment` records the closure decision and per-candidate evidence but no input artifact identities. The receipt chain and final assessment document identify those inputs elsewhere, so the result is reproducible, but the machine assessment alone cannot distinguish an input mix-up from the intended evaluation.

## Chosen approach

Upgrade `ProspectiveClosureAssessment` to schema version 2 and add a canonically ordered `inputArtifacts` list. Each item contains:

- `role`: a stable machine-readable role name
- `sha256`: the lowercase SHA-256 digest of the exact bytes
- `sizeBytes`: the exact byte length

The required roles are:

1. `application`
2. `census-receipt`
3. `dossier`
4. `policy`
5. `review`
6. `tools`
7. `v10-census`
8. `v10-discovery`
9. `v10-discovery-receipt`
10. `v10-experiment-receipt`
11. `v10-manifest`
12. `v10-protocol`
13. `v11-census`
14. `v11-manifest`
15. `v11-protocol`
16. `v11-result`
17. `validation-receipt`
18. `validator-application`

`application` remains the receipt-bound execution JAR that produced the intervention result. `validator-application` identifies the independently built JAR that generated the assessment. `validation-receipt` identifies the intervention experiment receipt supplied to closure validation, and `v11-result` identifies the corrected result being assessed. The remaining roles are the complete input role set bound by the intervention experiment receipt.

The list is sorted lexicographically by role. The assessment constructor rejects a missing role, an unexpected role, duplicate roles, noncanonical ordering, malformed digests, and negative sizes.

## Data flow

`ValidateProspectiveClosureCommand` already obtains bounded snapshots for the validation receipt, every receipt-bound input, and the corrected result. It will additionally snapshot its own validator JAR. It will construct the canonical provenance list from those snapshots and pass it to `ProspectiveClosureValidator.evaluate`. The validator will place the immutable list into the schema version 2 assessment.

The closure decision logic remains unchanged. This change adds provenance to the generated evidence; it does not rerun TLC, alter the holdout, change the selected candidate, or reinterpret the v10/v11 result.

## Failure handling

Assessment construction fails closed when the provenance set is incomplete or malformed. CLI preflight continues to reject receipt or digest mismatches before assessment generation. Output remains write-once, so a failed generation cannot partially overwrite the canonical assessment.

## Compatibility

The canonical v11 assessment will be regenerated as schema version 2 and receive a new digest and size. The previously published schema version 1 digest remains documented as historical evidence. Java callers constructing an assessment must provide the complete provenance list; no compatibility constructor will silently synthesize missing identities.

## Coverage obligations

| ID | Obligation | Verification |
|---|---|---|
| AP01 | The assessment contains exactly the 18 required roles. | Unit test over a valid assessment and canonical generated artifact. |
| AP02 | Every identity uses a lowercase 64-character SHA-256 digest and a nonnegative byte size. | Constructor unit tests. |
| AP03 | Missing, unexpected, duplicate, and noncanonical roles fail closed. | Parameterized or focused unit tests. |
| AP04 | The CLI records identities from the exact bounded snapshots used by preflight and evaluation. | Integration test invoking the real CLI against frozen inputs. |
| AP05 | `application` identifies the execution JAR while `validator-application` identifies the current validator JAR. | Integration assertions against both known digests. |
| AP06 | Closure status and candidate evidence remain unchanged after provenance enrichment. | Regenerated artifact comparison and existing closure tests. |
| AP07 | The application JAR remains byte reproducible and matches `config/current-application.json`. | Two clean no-build-cache builds and `verifyFrozenApplication`. |
| AP08 | Existing unit, integration, sandbox, formatting, and frozen-artifact checks remain green. | Full verification suite. |

## Optional verification techniques

PICT, ACTS, TLA+, Alloy, Dafny, property-based testing, and fuzzing are not applicable to this bounded closed-role schema change. The critical state space is a small explicit set whose failure modes are clearer as direct unit and integration cases.

## Scope exclusions

- Embedding artifact bytes in the assessment
- Signing the assessment or introducing a transparency log
- Rerunning the v10 or v11 mutation experiments
- Generalizing provenance storage for unrelated historical report types
- Fixing the currently unreachable generalized `REJECTED_BY_BASELINE` classification
