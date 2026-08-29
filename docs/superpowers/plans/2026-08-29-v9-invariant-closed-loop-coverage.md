# v9 Invariant Closed-Loop Coverage Ledger

## Issue Draft

Extend the frozen v8 experiment with a separate v9 intervention that adds the reviewed `NoRequestAllocationOverlap` predicate to SimpleAllocator through a digest-bound derived source edit. Preserve type, legacy-target, and adopted-target oracle identity through the sandbox, classify full-oracle kills with type-only and legacy reruns, and prove that the original Request survivor becomes an adopted-target kill while the baseline remains valid.

## Source Investigation Summary

`CorpusExperimentRunner` currently concatenates type and target invariants before crossing the Podman boundary. `ExperimentRunner` therefore has only one configuration and labels all state-invariant kills `state-invariant-kill-unattributed`. The upstream SimpleAllocator bytes do not define the adopted predicate, while the corpus fetch and snapshot layers correctly require exact upstream bytes and read-only input. v9 needs a bounded derived-source mechanism inside the sandbox and a new census bound to the changed application JAR.

## Code Evidence Log

| Evidence ID | Type | Evidence | Implication |
|---|---|---|---|
| E001 | evidence | `CorpusExperimentRunner` concatenates `typeInvariants` and `targetInvariants`. | Type and target attribution is currently impossible after this boundary. |
| E002 | evidence | `ExperimentRunner` writes one invariant CFG and emits `state-invariant-kill-unattributed`. | Killed mutants need conditional reruns and structured attribution. |
| E003 | evidence | `CorpusFetcher` and `CorpusInputSnapshot` verify and preserve exact manifest bytes. | Derived source must not mutate the cache or mounted input. |
| E004 | evidence | The pinned SimpleAllocator defines `TypeInvariant` and `ResourceMutex` but not `NoRequestAllocationOverlap`. | Merely adding the target name makes the baseline invalid. |
| E005 | evidence | v8 Request mutant `gcd-94424c8f20df1b20ffb7` passes with 1,024 states versus 400 baseline states and witnesses overlap. | This is the preregistered target of the v9 intervention. |
| E006 | evidence | v8 Allocate mutant `gcd-802804f2cb0816da2fa1` is an unattributed state-invariant kill. | It can exercise the expected type-only branch in v9. |
| E007 | evidence | The current frozen application check binds HEAD to the v8 census JAR digest. | Java changes require a new current application lock and v9 census without rewriting v8 history. |
| E008 | inference | Type-only pass does not distinguish the new predicate from existing `ResourceMutex`. | Exact incremental attribution also requires the legacy oracle to pass. |
| E009 | assumption | One manifest-pinned insertion is sufficient to add the reviewed predicate. | v9 deliberately does not support replacement, deletion, or multiple edits per file. |

## Specification Delta

- S01: v8 manifest, census, results, and report remain unchanged.
- S02: v9 can declare one bounded digest-pinned insertion for a manifest-listed TLA+ source.
- S03: The edit is applied only to a private sandbox model copy after upstream verification.
- S04: Type, legacy-target, and adopted-target invariant lists remain distinct through every layer.
- S05: Attribution reruns occur only after a completed full-oracle state-invariant kill.
- S06: Full kill plus type-only pass plus legacy pass is an adopted-target kill.
- S07: Full kill plus type-only kill is a type kill; unresolved reruns remain unresolved attribution.
- S08: Historical unattributed v8 results remain readable and separately counted.
- S09: A new application identity and census are frozen before any v9 mutation run.
- S10: v9 is reported as a post-discovery intervention, not independent usefulness evidence.

## Invariants

- I01: Derived edits never change `.corpus`, the read-only input snapshot, or v8 artifacts.
- I02: A derived file is executable only when both base and derived size/digest checks pass.
- I03: Derived edit metadata cannot name an unlisted path, cross a UTF-8 boundary, contain forbidden controls, or exceed its bound.
- I04: Public TLA+ remains unparsed and unexecuted on the host.
- I05: Oracle list membership cannot be lost, duplicated across categories, or reordered nondeterministically.
- I06: Non-killed mutants never incur attribution reruns.
- I07: A target/type attribution is emitted only from completed oracle evidence; failures are unresolved.
- I08: An adopted-target kill requires the legacy oracle to pass.
- I09: Report buckets conserve evaluated mutation totals and keep historical unattributed kills distinct.
- I10: The v9 census application digest equals the preregistered current application lock.

## Partition Axes and Constraints

| Axis | Values |
|---|---|
| Manifest profile | v1-v8 legacy, v9 without edit, v9 with valid edit, v9 with invalid edit |
| Edit validity | valid, base mismatch, derived mismatch, invalid path, invalid offset, split UTF-8, forbidden control, oversized |
| Full oracle | pass, state-invariant kill, non-completion |
| Type-only oracle | not run, pass, kill, non-completion |
| Legacy oracle | not run, pass, kill, non-completion |
| Attribution | adopted target, legacy target, type, unresolved, historical unattributed, none |
| Result consumer | JSON serialization, JSON compatibility read, Markdown, corpus summary |
| Experiment stage | preregistration, census, mutation run, repeated verification |

Constraints:

- Attribution reruns pair only with a completed full-oracle state-invariant kill.
- Legacy rerun follows only a type-only pass and only when adopted targets exist.
- A v9 edit targets a listed `.tla` source and is insertion-only.
- v8 data has no structured attribution and remains historical unattributed data.

## Coverage Obligations

| ID | Type | Required behavior or risk | Risk | Minimum checks | Preferred verification | Status |
|---|---|---|---|---|---|---|
| O01 | compatibility | Frozen v8 artifacts remain byte-identical and readable. | Critical | 2 | git diff assertion + historical JSON test | verified |
| O02 | security | Validate all edit metadata before sandbox execution. | Critical | 2 | validation table + sandbox rejection | verified |
| O03 | integrity | Verify base and derived bytes and keep source inputs immutable. | Critical | 2 | byte-level unit test + sandbox inspection | verified |
| O04 | security | Transport edits without shell interpretation or host-side TLA parsing. | Critical | 2 | command test + sandbox integration | verified |
| O05 | invariant | Preserve separate type, legacy, and adopted lists end to end. | Critical | 2 | command/CLI test + real TLC fixture | verified |
| O06 | classification | Attribute target, type, and unresolved kills with exclusive rules. | Critical | 2 | reducer matrix + real TLC fixture | verified |
| O07 | efficiency | Do not rerun non-kills and omit nonexistent redundant oracle runs. | High | 2 | unit branch test + output-file evidence | verified |
| O08 | compatibility | Read/render v8 unattributed results without reclassification. | High | 2 | committed JSON read + Markdown test | verified |
| O09 | reporting | New and historical buckets conserve all denominators. | Critical | 2 | pure summary test + v9 report validation | verified |
| O10 | experiment | Derived SimpleAllocator baseline passes all full invariants. | Critical | 1 | pinned v9 run | verified |
| O11 | experiment | Request GCD mutant is killed only after the adopted invariant is enabled. | Critical | 2 | legacy/full comparison + v9 result | verified |
| O12 | experiment | At least one SimpleAllocator kill is classified type-only. | High | 1 | v9 result | not met; Allocate is a legacy-target kill |
| O13 | experiment | Return/non-kill behavior is not perturbed by attribution. | High | 1 | v9 result | verified |
| O14 | reproducibility | Application and census bytes are independently reproducible before mutation. | Critical | 2 | double clean build + double census | verified |
| O15 | lifecycle | Every started container and Gradle/server process is stopped. | Critical | 2 | runner tests + post-task process inspection | verified |
| O16 | validity | Report the intervention's circularity and do not claim out-of-sample validation. | High | 1 | final report review | verified |

## Coverage Ledger

| Obligation | Primary artifact | Secondary artifact | Overlap | Current status | Debt or note |
|---|---|---|---|---|---|
| O01 | historical report compatibility test | frozen-path digest assertions | intentional_overlap | verified | v8 is the comparison boundary. |
| O02-O04 | edit validator/applier tests | Podman command and sandbox tests | layer_overlap | verified | Security review Must Fix findings are resolved. |
| O05-O07 | attribution reducer tests | real TLC attribution fixture | layer_overlap | verified | Invocation counts and generated files are observed without mocks. |
| O08-O09 | report unit tests | committed v8/v9 artifact validation | layer_overlap | verified | The historical bucket remains explicit and v9 totals conserve 51 sites. |
| O10-O13 | focused SimpleAllocator closure check | full v9 corpus result | intentional_overlap | partial | Primary Request closure and Return checks pass; the preregistered SimpleAllocator type-only expectation does not. |
| O14 | reproducible JAR and census commands | frozen identity assertions | layer_overlap | verified | Two clean JAR builds and two byte-identical census runs completed before mutation. |
| O15 | process lifecycle tests | final PID/container audit | layer_overlap | verified | No labeled containers or volumes remained; Podman machine and Gradle daemons were stopped explicitly. |
| O16 | design/report wording review | final human-facing summary | layer_overlap | verified | Generalization remains future work. |

## Test Scenarios

- T001: Every existing v1-v8 manifest validates with no edit fields and unchanged behavior.
- T002: A valid v9 insertion produces exactly the declared bytes and digest in a private destination while leaving the source unchanged.
- T003: Wrong base/derived digest or size, an unlisted/non-TLA path, negative/out-of-range/split offset, NUL/control text, duplicate edit, or oversized text is rejected.
- T004: Podman receives type, legacy-target, adopted-target, and edit JSON as distinct argument-array elements.
- T005: A real finite TLC fixture yields one adopted-target kill, one type kill, and one pass; only killed mutants create attribution outputs.
- T006: A type-only pass and legacy kill is classified as legacy-target kill.
- T007: Type-only or legacy timeout/error becomes unresolved attribution and cannot increase a kill bucket.
- T008: A type-only-only entry avoids redundant reruns; an entry without adopted targets avoids a legacy rerun.
- T009: The committed v8 JSON deserializes, preserves 51 outcomes and seven historical unattributed kills, and renders without invented attribution.
- T010: Report summaries conserve target, type, unresolved, historical unattributed, survivor, invalid, and execution-error buckets.
- T011: The v9 manifest changes only SimpleAllocator provenance and target registration relative to the frozen v8 corpus membership.
- T012: The v9 SimpleAllocator baseline passes the new predicate.
- T013: The Request GCD mutation passes the legacy oracle with the old expanding witness but fails the full oracle and is classified adopted-target.
- T014: The Allocate GCD mutation was expected to fail type-only and be classified type-invariant. This scenario was not met: type-only passed and the legacy target killed it.
- T015: The Return mutation remains non-killed and has no attribution rerun.
- T016: Two clean application builds and two v9 censuses are byte-identical and match the frozen identities.
- T017: Malformed edits fail before public source parsing; successful and failed sandbox runs leave no named container, writable input, or orphaned process.

## Optional Verification Suggestions

- Real SANY/TLC and Podman tests are `executed-by-request` because the user requested the closed-loop experiment.
- Property-based generation is deferred; the bounded edit matrix is small and deterministic table tests provide clearer failures.
- PICT, ACTS, Alloy, Dafny, and fuzzing are not required for this narrow insertion-only profile.

## Questions for the User

None. The user delegated implementation and experiment execution for this bounded v9 loop.

## Coverage Debt

- D001: The v9 experiment is selected using the v8 witness and cannot establish out-of-sample usefulness.
- D002: Capturing the exact TLC invariant name could later replace one rerun, but v9 uses oracle-set comparison for auditable classification.
- D003: General multiple-edit, replacement, deletion, wrapper-module, and fork support remain out of scope.
- D004: The four existing tool errors remain deferred until the closed loop is complete.
