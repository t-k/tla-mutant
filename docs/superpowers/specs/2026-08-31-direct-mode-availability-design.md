# Direct-Mode Availability Hardening Design

Date: 2026-08-31

## Purpose

Close the three remaining Security Specialist `Should Fix` findings for direct full-property execution:

1. UOI candidate discovery materializes every semantic application before filtering and repeatedly copies ancestor sets.
2. A direct experiment has a per-TLC timeout but no aggregate deadline across its baseline and mutants.
3. TLC timeout recovery can wait without a bound after a failed final reap or a collector that never reaches EOF.

The change must preserve the default state-invariant behavior, retain every admitted full-property candidate in the report, and leave the existing rootless Podman boundary unchanged.

## Selected Approach

Use bounded in-process primitives rather than introducing a supervisor JVM or one worker JVM per mutant.

An outer supervisor would enforce a hard deadline but would either discard partial evidence or require a new checkpoint protocol. Per-mutant workers would provide stronger isolation at the cost of repeated SANY/JVM startup, a new IPC schema, and a materially larger provenance surface. The selected approach fits the current sequential experiment flow, preserves report compatibility, and has deterministic unit and integration validators.

The candidate tournament is stored under `.codex/candidate-tournaments/direct-mode-availability/`. Its rating is heuristic; the validators in this design remain authoritative.

## Bounded Semantic Traversal

`MutationSupport.applications` will be replaced for UOI generation by an iterative depth-first visitor. The visitor will not build a list of all `OpApplNode` instances.

Each stack frame records one node, its children, and traversal position. One active-path UID set prevents cycles without copying the entire ancestor set per node. A shared semantic node may be revisited after it leaves the active path, preserving the existing path-based traversal behavior.

The production traversal limits are:

- at most 1,000,000 visited semantic nodes per UOI generation
- at most 4,096 active traversal frames
- the existing aggregate full-property candidate cap of 1,024

Exceeding any limit throws `IllegalArgumentException` before another node or candidate is admitted. Tests use package-private smaller budgets to reach the boundaries deterministically.

The visitor invokes UOI filtering as each application is encountered. Candidate generation stops immediately when admitting another candidate would exceed the remaining family budget. Sorting remains a final operation over at most 1,024 candidates, so report ordering and mutation identities remain stable.

## Aggregate Experiment Deadline

The direct `experiment` command will add:

```text
--experiment-timeout-seconds <seconds>
```

The default and maximum are both 3,600 seconds. Callers may choose a smaller positive value. The existing `--timeout-seconds` option remains the limit for one TLC invocation and retains its current maximum of 600 seconds.

`ExperimentRunner` will own a monotonic deadline created at the start of `runFullProperty`. The deadline includes model snapshotting, SANY analysis, candidate generation, the baseline, mutant workspace creation, and mutant evaluation. Blocking SANY work remains in-process, but the deadline is checked immediately after each synchronous phase and before every TLC process launch.

For each TLC invocation, the runner supplies the smaller of:

- the configured per-TLC timeout
- the aggregate deadline's remaining duration

If the deadline expires before the baseline passes, the experiment fails without evaluating mutants because there is no valid comparison baseline.

If the deadline expires after the baseline passes, no additional workspace, SANY analysis, or TLC process is started. Every remaining admitted candidate is still patched in memory and recorded as `TIMEOUT`, `NOT_EVALUATED`, `STATE_UNRESOLVED`, and `unresolved`. Its diagnostic reason includes `experiment-deadline-exhausted`, and its reproducible mutation evidence remains present. Generated, evaluated, and survivor totals continue to be recomputed by schema-v3 validation.

Existing programmatic construction of `ExperimentRunner(TlcRunner, Duration)` receives the same one-hour aggregate default. A new overload accepts the aggregate timeout and a package-private monotonic clock for deterministic tests. State-invariant execution does not consult the aggregate full-property deadline.

## Bounded TLC Process Lifecycle

`TlcRunner` will use an explicit local lifecycle instead of unbounded `Future.get()` and try-with-resources executor closure.

The lifecycle is:

1. Start the exact TLC child and close its stdin.
2. Collect stdout and stderr concurrently under the existing byte limits.
3. Wait for the configured TLC timeout.
4. On timeout, request graceful termination and wait at most two seconds.
5. If still alive, force termination and wait at most five seconds.
6. Drain both collectors for at most five seconds.
7. Close all process streams, cancel unfinished collectors, call `shutdownNow`, and wait at most five seconds for executor termination.
8. Perform strict run-directory deletion and verify exact absence.

A failed final reap, collector drain timeout, collector failure, or executor termination failure produces `TOOL_ERROR/NOT_EVALUATED`. A normal TLC timeout remains `TIMEOUT/NOT_EVALUATED`. No unbounded future or executor close is used.

Interruption preserves the thread interrupt flag after bounded exact-child termination is attempted. Cleanup failure remains observable and can override an otherwise completed result with `TOOL_ERROR/NOT_EVALUATED`.

The stable `PodmanRunner` implementation is not refactored. `TlcRunner` follows its lifecycle conventions locally, avoiding regression risk in the public corpus sandbox.

## CLI and Compatibility

- Omitting `--experiment-timeout-seconds` uses 3,600 seconds.
- `--timeout-seconds` retains its existing per-TLC meaning.
- Values below 1 or above 3,600 are rejected before snapshots or child processes are created.
- The option is accepted by the shared `experiment` command but only affects `full-property` mode.
- Existing JSON schema versions, verdict strings, mutation IDs, and state-invariant behavior remain unchanged.
- Corpus mode remains protected by its existing outer Podman wall-clock limit. The inner aggregate default is defense in depth and does not replace the sandbox deadline.

## Error Handling

- Semantic visit/depth overflow: reject the full-property run before baseline execution.
- Aggregate deadline before baseline completion: fail the experiment with bounded diagnostics.
- Aggregate deadline after baseline completion: emit unresolved evidence for every remaining candidate without spawning new TLC processes.
- Failed reap or collector/executor timeout: return `TOOL_ERROR/NOT_EVALUATED`, close owned handles, and perform strict run-directory cleanup.
- Interrupted execution: restore interruption after bounded cleanup and do not report a kill or survivor.

## Coverage Obligations

| ID | Obligation | Risk | Required verification |
|---|---|---|---|
| DA-01 | UOI visits applications without materializing the full graph | Direct input allocates memory proportional to every application | Source-structure unit test and bounded traversal tests |
| DA-02 | UOI stops at node, depth, and candidate limits | Large/deep AST consumes unbounded CPU or stack | Real SANY fixture with package-private small budgets |
| DA-03 | Existing UOI candidate identities and order are unchanged | Traversal refactor changes experiment semantics | Existing mutator integration tests plus exact candidate assertions |
| DA-04 | Existing per-TLC timeout meaning is preserved | CLI compatibility regression | CLI option validation and fake executable argument/timing tests |
| DA-05 | Aggregate timeout rejects unsafe values before execution | User can disable the direct boundary | CLI preflight tests that assert no output/process creation |
| DA-06 | Baseline uses the remaining aggregate budget | Baseline can exceed the experiment deadline | Deterministic clock/deadline unit test |
| DA-07 | Expiry after baseline spawns no more TLC processes | Direct run continues for hours after deadline | Counting executable integration test |
| DA-08 | Every unstarted candidate remains in the report as unresolved evidence | Deadline biases the denominator or loses reproducibility | Full-property runner report-conservation test |
| DA-09 | Graceful and forced child termination waits are bounded | Failed reap hangs direct execution | Process lifecycle unit and real shell-process test |
| DA-10 | Collector drain and executor shutdown are bounded | Inherited pipe prevents EOF forever | Real shell process retaining stdout/stderr plus elapsed-time assertion |
| DA-11 | Cleanup failure cannot become pass or kill | Residual run files coexist with accepted evidence | Existing and extended cleanup injection tests |
| DA-12 | Real safety, temporal, deadlock, and pass classification remain valid | Lifecycle refactor changes oracle results | Real TLC integration tests |
| DA-13 | Corpus containment remains unchanged | Direct hardening regresses public sandbox execution | Full Podman containment suite |
| DA-14 | Application bytes remain reproducible and frozen | Evidence no longer binds the executed implementation | Two clean no-cache builds and frozen identity verification |

## Security Review

The final Security Specialist review will use `Must Fix / Should Fix / Notes` and verify:

- aggregate and per-process time bounds cannot be bypassed by CLI values
- deadline expiry cannot be reported as a kill or survivor
- process, collector, executor, state output, and run-directory ownership are closed on every path
- semantic traversal budgets are enforced before additional allocation
- public corpus containment and loaded-artifact identity remain intact

Any `Must Fix` blocks integration.

## Acceptance Criteria

The change is complete when:

- UOI no longer materializes all semantic applications or copies ancestor sets per node.
- Node, depth, candidate, per-TLC, aggregate-experiment, collector-drain, executor-shutdown, and child-reap bounds have deterministic tests.
- No TLC child starts after aggregate deadline expiry, while every admitted candidate remains represented in schema-v3 evidence.
- The default state-invariant mode and existing full-property result semantics remain unchanged.
- Unit, integration, formatting, frozen-application, and Podman containment verification pass.
- Correctness review approves and Security Specialist review reports no `Must Fix`.
