# Full-Property Mutation Oracle Design

## Purpose

The existing experiment deliberately evaluates mutants with state invariants only. That mode is useful for discovering state-expanding survivors, but it is not a valid full-property mutation oracle because it removes configured temporal properties and does not classify TLC temporal counterexamples as kills.

This change adds an explicit full-property mode without changing the default or the evidence semantics of the existing state-invariant mode. The new mode is intended to replace repository-owned compatibility runners that preserve the original cfg and classify both safety and temporal counterexamples.

## Scope

The implementation covers the five requirements reported by the firebase-emulator evaluation:

1. Preserve the original cfg, including every `INVARIANT`, `INVARIANTS`, `PROPERTY`, and `PROPERTIES` section.
2. Classify named and generic temporal violations as kills distinct from state-safety kills.
3. Keep timeout, out-of-memory, evaluation failure, process-start failure, malformed output, and other tool failures outside the kill population.
4. Emit enough mutation and oracle evidence to reproduce every candidate and identify the property that killed it when TLC reports one.
5. Bind the report to the root module, cfg, manifest, TLA+ Tools JAR, and tla-mutant application JAR with SHA-256 and byte size.

The change does not add new mutation families, mutate fairness expressions, infer requirements, or alter the selection protocol used by the prospective state-invariant evaluation.

## User-Facing Interface

`experiment` and `corpus-experiment` gain an explicit `--oracle-mode` option with two values:

- `state-invariants`: the existing behavior and the default.
- `full-property`: run the baseline and every mutant with an exact snapshot of the original cfg.

The direct `experiment` command requires `--manifest` in full-property mode because a report that omits the manifest identity would not satisfy the provenance contract. `corpus-experiment` already receives the manifest and propagates its frozen identity automatically.

State-invariant-only arguments are rejected when combined with full-property mode. This avoids a command that claims to preserve the cfg while also requesting a replacement invariant set.

## Oracle Configuration

Configuration materialization is selected by `OracleMode`:

- State-invariant mode continues to use `StateInvariantConfig` and its existing attribution reruns.
- Full-property mode writes an immutable, byte-for-byte snapshot of the original cfg into the result area and uses that same snapshot for the baseline and all mutants.

The baseline must complete with `PASSED`. A baseline state-safety, deadlock, or temporal violation is an invalid experiment baseline and aborts the experiment. A timeout or tool failure also aborts the baseline rather than producing mutant verdicts.

The full-property cfg snapshot is created from bytes already captured for provenance. The execution path and the digest therefore refer to the same content.

## TLC Classification

Classification uses structured `-tool` events, not the Java process exit code by itself.

The ordered classification is:

1. A wall-clock expiry is `TIMEOUT` and `NOT_EVALUATED`.
2. TLC out-of-memory events `1001`, `1002`, and `1003` are `OUT_OF_MEMORY` and `NOT_EVALUATED`.
3. TLC invariant or action-property evaluation failures `2111` and `2113` are `EVALUATION_ERROR` and `NOT_EVALUATED`.
4. Initial-state invariant/property and behavior invariant/action-property violation events `2107`, `2108`, `2110`, and `2112` are `COMPLETED` and `KILLED_STATE_SAFETY`.
5. Temporal property violation event `2116` is `COMPLETED` and either `KILLED_TEMPORAL_NAMED` or `KILLED_TEMPORAL_GENERIC`.
6. Deadlock is `COMPLETED` and `KILLED_DEADLOCK`.
7. TLC success event `2193`, exit code zero, and a complete canonical state file are `COMPLETED` and `PASSED`.
8. Every other combination is `TOOL_ERROR` and `NOT_EVALUATED`.

A non-zero exit code such as the observed value 13 is recorded as a diagnostic but cannot independently create a kill. Invariant, action-property, and temporal property names are extracted only from bounded structured event bodies. If a temporal event does not contain a safe, unambiguous name, the result remains a generic temporal kill.

## Result Model

Full-property reports use a new schema version while legacy state-invariant reports retain their current versions and meaning.

Each full-property report records:

- `oracleMode`
- the five input artifacts and their SHA-256 and byte size
- the existing experiment totals
- one result per generated mutation

Each mutation result records:

- mutation ID
- mutation family/operator
- source module and definition
- source range as `startUtf16Offset` and `endUtf16OffsetExclusive`
- original and replacement text
- original source SHA-256 and mutated source SHA-256
- a deterministic replacement patch containing the source identity, range, expected original text, and replacement text
- execution status and oracle outcome
- killing property name when TLC reports a valid name
- TLC exit code and bounded diagnostic event codes

The range is explicitly named as a UTF-16 offset because Java `String` and the existing `SourceDocument` use UTF-16 code-unit indices. The patch is applied only when the source digest and expected original slice both match; otherwise reproduction fails closed.

The result does not embed an entire mutated module because the digest-bound replacement patch is smaller, deterministic, and sufficient to reconstruct the exact mutated bytes.

## Provenance

Every artifact identity contains lowercase SHA-256 and an exact positive byte size. Full-property execution snapshots inputs before the experiment and uses the snapshots for execution where applicable.

The required roles are:

- `module`: the root TLA+ module used for candidate generation
- `config`: the unchanged cfg used as the oracle
- `manifest`: the caller-provided or corpus manifest
- `tools`: the TLA+ Tools JAR used by the TLC subprocess
- `application`: the tla-mutant JAR containing the runner and canonical state writer

For corpus execution, the outer report binds the manifest, tools, and application artifacts, while each completed entry binds its root module and cfg. This avoids repeating large common metadata while preserving a complete evidence chain.

## Compatibility

- Omitting `--oracle-mode` preserves current state-invariant behavior.
- Existing state-invariant report schema versions and verdict strings remain unchanged.
- Existing callers of `ExperimentRunner`, `TlcRunner`, and `PodmanRunner` continue to compile through compatibility constructors or overloads.
- Temporal outcomes are additive enum values. Existing exhaustive switches must be updated deliberately.
- Corpus manifests remain valid; full-property mode changes execution semantics only when explicitly selected.

## Coverage Ledger

| Obligation | Risk | Verification | Status |
|---|---|---|---|
| FP-01 Original cfg bytes are preserved | A property is silently removed or rewritten | `FullPropertyConfigTest`, `ExperimentRunnerFullPropertyTest` | Covered |
| FP-02 State-invariant mode remains unchanged | Existing prospective evidence changes meaning | `ReportWriterTest`, `ExperimentCommandTest`, `ExperimentIntegrationTest` | Covered |
| FP-03 Events 2107/2108/2110/2112 and 2116 are genuine kills | Configured safety or temporal counterexamples remain unresolved | `TlcOutcomeClassifierTest`, `TlcRunnerIntegrationTest` | Covered with unit and real-TLC overlap |
| FP-04 Named and generic violations are distinct | Property attribution is misleading | `TlcOutcomeClassifierTest`, `TlcRunnerIntegrationTest` | Covered with unit and real-TLC overlap |
| FP-05 Exit code alone cannot kill | Java/TLC startup failures become false kills | `TlcOutcomeClassifierTest` | Covered |
| FP-06 Timeout, OOM, evaluation failure, and tool error are not kills | Mutation score is inflated by infrastructure failures | `TlcOutcomeClassifierTest`, `TlcRunnerIntegrationTest` | Covered |
| FP-07 Baseline temporal failure aborts | Invalid specification is reported as mutant evidence | `ExperimentRunnerFullPropertyTest` | Covered |
| FP-08 Full-property mutation is reproducible | Candidate cannot be inspected outside the run | `MutationEvidenceTest`, `FullPropertyExperimentIntegrationTest` | Covered with unit and real-TLC overlap |
| FP-09 Required input identities are complete | Old evidence survives input changes undetected | `MutationEvidenceTest`, `CorpusExperimentReportTest`, `ExperimentCommandTest` | Covered |
| FP-10 Direct full-property mode requires a manifest | Provenance chain is incomplete | `ExperimentCommandTest` | Covered |
| FP-11 Corpus mode propagates common identities | Sandbox result cannot be tied to frozen inputs | `CorpusExperimentReportTest`, `PodmanCommandTest` | Covered with report-boundary and command-transport overlap |
| FP-12 A real temporal-only mutant is killed | Unit classification works but end-to-end behavior does not | `FullPropertyExperimentIntegrationTest` | Covered by real TLC |
| FP-13 Generic liveness counterexample is killed | Only safety-like temporal formulas work | `TlcRunnerIntegrationTest` | Covered by real TLC |
| FP-14 Output and names remain bounded | Tool output creates oversized or unsafe evidence | `TlcOutcomeClassifierTest`, `TlcOutputCollectorTest` | Covered |

FP-03, FP-05, FP-06, FP-09, and FP-12 are high-risk obligations and receive both focused and integration-level checks where feasible. The current ledger covers the approved five requirements; full fairness-mutation generation remains explicitly out of scope.

## Security Review Focus

The mandatory security review will examine:

- path confinement for manifest, module, cfg, JAR, work, and report inputs
- symlink and time-of-check/time-of-use behavior during snapshots
- bounded file and event-body processing
- patch integrity checks and source-text escaping
- environment scrubbing and subprocess classification
- whether untrusted exit codes or prose can forge a kill

Review findings are recorded as `Must Fix`, `Should Fix`, and `Notes`. Any `Must Fix` blocks completion.

## Acceptance Criteria

The feature is accepted when all of the following hold:

- A full-property run preserves the original cfg and kills a mutant through a configured temporal property.
- Named temporal, generic temporal, state-safety, deadlock, passed, and non-evaluated outcomes are distinguishable in JSON.
- An exit code without a corresponding structured kill event never becomes a kill.
- Every full-property result contains reproducible mutation data and the required input identities.
- Existing state-invariant tests and report semantics remain unchanged.
- Unit, integration, formatting, and available sandbox verification pass, with any unavailable environment-dependent verification reported precisely.
