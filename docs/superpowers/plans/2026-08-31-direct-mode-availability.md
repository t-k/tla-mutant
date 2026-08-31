# Direct-Mode Availability Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bound direct full-property semantic traversal, total experiment duration, and TLC process cleanup without changing the state-invariant path or schema-v3 result semantics.

**Architecture:** Keep execution in one JVM. Introduce an iterative semantic visitor with explicit budgets, a monotonic aggregate deadline owned by `ExperimentRunner`, and an explicit bounded TLC child-process lifecycle. The main thread is the only source writer; read-only findings are reduced through the Agent DAG before implementation.

**Tech Stack:** Java 21, Gradle, JUnit 5, TLA+ Tools 1.8.0, rootless Podman

---

## Constraints and coverage ledger

The accepted design is `docs/superpowers/specs/2026-08-31-direct-mode-availability-design.md`. Existing public constructors, state-invariant behavior, schema version 3, mutation IDs, and verdict strings remain compatible. No mock framework is used; tests use real SANY models, real shell child processes, and real TLC where appropriate.

| ID | Required behavior | Primary RED test | Independent verification |
|---|---|---|---|
| DA-01 | UOI streams semantic applications instead of materializing the graph | `MutationSupportSourceTest.uoiTraversalDoesNotReintroduceWholeGraphMaterialization` | Real SANY bounded traversal tests |
| DA-02 | Node, depth, and candidate budgets fail before another admission | `MutatorIntegrationTest.rejectsUoiTraversalBeyondNodeAndDepthBudgets` | Existing bounded-family integration test |
| DA-03 | UOI identities and order remain stable | Extend `generatesOnlyEligibleGcdBrlAndUoiCandidates` with exact IDs | Full mutation integration suite |
| DA-04 | `--timeout-seconds` remains the per-TLC bound | `ExperimentCommandTest.preservesPerTlcTimeoutAndParsesAggregateTimeout` | Fake child observes a shortened deadline-derived TLC timeout |
| DA-05 | Aggregate CLI values outside 1..3,600 fail before execution | `ExperimentCommandTest.rejectsUnsafeAggregateExperimentTimeouts` | In-process command preflight creates no work directory |
| DA-06 | Baseline receives `min(per-TLC, remaining)` | `FullPropertyDeadlineTest.capsAnInvocationAtTheRemainingBudget` | Counting fake child deadline test |
| DA-07 | Expiry after baseline starts no workspace, SANY, or TLC work | `ExperimentRunnerFullPropertyTest.stopsLaunchingMutantsAfterTheAggregateDeadline` | Launch-count file remains at one |
| DA-08 | Every admitted unstarted candidate remains unresolved with evidence | Same report-conservation test | Schema-v3 report constructor validation |
| DA-09 | Graceful and forced child reap waits are bounded | `TlcRunnerTest.forceTerminatesAChildThatIgnoresTerm` | Exact-child PID is absent after the test |
| DA-10 | Collector and executor waits are bounded when a descendant retains pipes | `TlcRunnerTest.doesNotWaitForInheritedPipesAfterTheChildEnds` | Elapsed-time bound and no live exact PID |
| DA-11 | Cleanup failure is never a pass or kill | Existing `cleanupFailureMakesAnOtherwiseCompletedRunUnresolved` | Extend lifecycle failure diagnostics assertions |
| DA-12 | Real pass, safety, temporal, and deadlock classifications remain stable | Existing `TlcRunnerIntegrationTest` and full-property integration tests | Full `integrationTest` task |
| DA-13 | Corpus sandbox containment remains unchanged | Existing `PodmanContainmentTest` | Full `sandboxTest` task and lifecycle audit |
| DA-14 | Application bytes remain reproducible and frozen | Existing archive/freeze tests | Two clean, uncached fat-JAR builds with identical digest and size |

PICT, ACTS, Alloy, Dafny, and fuzzing are not selected for this change. The boundary space is small and ordered, so explicit limit tables, deterministic monotonic-clock tests, real process tests, real TLC tests, and the Podman containment suite are the authoritative validators.

## Task 1: Stream and bound UOI semantic traversal

**Files:**

- Create: `src/test/java/dev/tlamutant/mutation/MutationSupportSourceTest.java`
- Modify: `src/integrationTest/java/dev/tlamutant/mutation/MutatorIntegrationTest.java`
- Modify: `src/main/java/dev/tlamutant/mutation/MutationSupport.java`
- Modify: `src/main/java/dev/tlamutant/mutation/UoiMutator.java`

- [ ] **Step 1: Add RED tests for visitor shape and exact mutation identity**

Add a source-structure guard that requires a callback-based API and rejects `List<OpApplNode>` materialization and recursive per-node ancestor copying. Extend the real SANY test to assert the exact ordered UOI candidate IDs as a compatibility fixture.

Run:

```bash
./gradlew test --tests dev.tlamutant.mutation.MutationSupportSourceTest integrationTest --tests dev.tlamutant.mutation.MutatorIntegrationTest --no-build-cache --rerun-tasks --console=plain
```

Expected: compilation fails because the visitor and traversal-budget API do not exist.

- [ ] **Step 2: Add RED tests for node and depth boundaries**

Use package-private small limits with a real SANY fixture. Verify that exactly-at-limit traversal succeeds and the first node or frame beyond the limit throws `IllegalArgumentException`. Retain the existing candidate-overflow assertion.

- [ ] **Step 3: Implement iterative active-path DFS**

Add package-private immutable limits with production defaults of 1,000,000 visited nodes and 4,096 active frames. Each frame owns one `SemanticNode`, its child array, and the next child index. Keep one active UID set, remove the UID when a frame exits, and invoke a `Consumer<OpApplNode>` as each application is first entered.

Update `UoiMutator` to filter and admit candidates inside the callback. Stop traversal as soon as the next candidate would exceed the supplied family capacity. Preserve the final source-offset/replacement sort.

- [ ] **Step 4: Run GREEN verification and inspect allocation shape**

```bash
./gradlew spotlessApply test --tests dev.tlamutant.mutation.MutationSupportSourceTest integrationTest --tests dev.tlamutant.mutation.MutatorIntegrationTest --no-build-cache --rerun-tasks --console=plain
rg -n "List<OpApplNode>|new HashSet.*ancestors|applications\(" src/main/java/dev/tlamutant/mutation
```

Expected: tests pass; the source scan finds no whole-graph application list or per-node ancestor-set copy.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/tlamutant/mutation src/test/java/dev/tlamutant/mutation src/integrationTest/java/dev/tlamutant/mutation/MutatorIntegrationTest.java
git commit -m "fix: bound UOI semantic traversal"
```

## Task 2: Add a monotonic aggregate deadline primitive

**Files:**

- Create: `src/main/java/dev/tlamutant/experiment/FullPropertyDeadline.java`
- Create: `src/test/java/dev/tlamutant/experiment/FullPropertyDeadlineTest.java`

- [ ] **Step 1: Add RED boundary tests**

Drive the deadline with a package-private `LongSupplier` nanosecond clock. Cover: positive duration validation, initial remaining duration, elapsed subtraction, saturation at zero, invocation timeout capped by the per-TLC limit, and overflow-safe one-hour construction.

Run:

```bash
./gradlew test --tests dev.tlamutant.experiment.FullPropertyDeadlineTest --no-build-cache --rerun-tasks --console=plain
```

Expected: compilation fails because `FullPropertyDeadline` does not exist.

- [ ] **Step 2: Implement the smallest deadline API**

The package-private value owns a monotonic start value and total nanoseconds. Expose `expired()` and `remainingCappedBy(Duration perInvocation)`. Reject nonpositive values. Return at least one nanosecond only when time genuinely remains; never convert an expired deadline into a launchable timeout.

- [ ] **Step 3: Run GREEN and commit**

```bash
./gradlew spotlessApply test --tests dev.tlamutant.experiment.FullPropertyDeadlineTest --no-build-cache --rerun-tasks --console=plain
git add src/main/java/dev/tlamutant/experiment/FullPropertyDeadline.java src/test/java/dev/tlamutant/experiment/FullPropertyDeadlineTest.java
git commit -m "feat: add full-property aggregate deadline"
```

## Task 3: Enforce the aggregate deadline while conserving report evidence

**Files:**

- Modify: `src/main/java/dev/tlamutant/experiment/ExperimentRunner.java`
- Modify: `src/test/java/dev/tlamutant/experiment/ExperimentRunnerFullPropertyTest.java`

- [ ] **Step 1: Add a RED launch-count and report-conservation test**

Extend the existing real fake-Java fixture so each launch appends to a count file. Advance a deterministic clock when the baseline child runs. Construct the runner with a small aggregate timeout and clock seam. Assert:

- baseline launches once and passes
- no mutant child launches after expiry
- no mutant workspace remains
- `generatedMutants == results.size()`
- `evaluatedMutants == 0`
- every result is `TIMEOUT/NOT_EVALUATED/STATE_UNRESOLVED/unresolved`
- every diagnostic is `experiment-deadline-exhausted`
- every result has reversible mutation evidence

Run:

```bash
./gradlew test --tests dev.tlamutant.experiment.ExperimentRunnerFullPropertyTest.stopsLaunchingMutantsAfterTheAggregateDeadline --no-build-cache --rerun-tasks --console=plain
```

Expected: compilation fails because aggregate timeout and clock construction are unavailable.

- [ ] **Step 2: Add RED baseline-expiry tests**

Cover expiry after snapshot/SANY/candidate generation and expiry while the baseline consumes its remaining budget. Both must fail the experiment before mutant evaluation with bounded diagnostics.

- [ ] **Step 3: Wire the deadline into full-property execution only**

Keep `ExperimentRunner(TlcRunner, Duration)` and give it a one-hour full-property aggregate default. Add a public overload accepting the aggregate timeout and a package-private overload accepting the monotonic clock. Create the deadline at `runFullProperty` entry. Check it after snapshot, input validation, SANY, and candidate generation, and before every TLC launch.

Pass `min(timeout, deadline.remaining())` to baseline and mutant TLC calls. After a passing baseline, check the deadline before workspace creation. For each unstarted candidate, apply its patch in memory and classify a synthetic `TlcExecution(TIMEOUT, NOT_EVALUATED, ...)` with the explicit deadline diagnostic while retaining evidence.

- [ ] **Step 4: Run GREEN and regression tests**

```bash
./gradlew spotlessApply test --tests dev.tlamutant.experiment.FullPropertyDeadlineTest --tests dev.tlamutant.experiment.ExperimentRunnerFullPropertyTest --no-build-cache --rerun-tasks --console=plain
```

Expected: all deadline, conservation, baseline, and prior full-property tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/tlamutant/experiment src/test/java/dev/tlamutant/experiment
git commit -m "fix: enforce full-property experiment deadline"
```

## Task 4: Expose and validate the aggregate CLI limit

**Files:**

- Modify: `src/main/java/dev/tlamutant/cli/Main.java`
- Modify: `src/test/java/dev/tlamutant/cli/ExperimentCommandTest.java`

- [ ] **Step 1: Add RED parsing and limit tests**

Verify the default is 3,600; explicit values 1 and 3,600 pass in full-property mode; 0 and 3,601 fail; state-invariant mode remains valid and ignores the aggregate value at execution; and `--timeout-seconds` still defaults to 180 with maximum 600 for full-property mode.

Run:

```bash
./gradlew test --tests dev.tlamutant.cli.ExperimentCommandTest --no-build-cache --rerun-tasks --console=plain
```

Expected: compilation or assertions fail because the aggregate option is absent.

- [ ] **Step 2: Add and propagate `--experiment-timeout-seconds`**

Declare the option with default `3600`. Validate 1..3,600 for full-property direct execution before directory creation or snapshots. Pass it to the new `ExperimentRunner` overload only in the full-property branch. Do not change corpus command transport or Podman limits.

- [ ] **Step 3: Run GREEN and commit**

```bash
./gradlew spotlessApply test --tests dev.tlamutant.cli.ExperimentCommandTest --no-build-cache --rerun-tasks --console=plain
git add src/main/java/dev/tlamutant/cli/Main.java src/test/java/dev/tlamutant/cli/ExperimentCommandTest.java
git commit -m "feat: expose full-property experiment timeout"
```

## Task 5: Bound TLC child, collector, and executor cleanup

**Files:**

- Modify: `src/main/java/dev/tlamutant/tlc/TlcRunner.java`
- Modify: `src/test/java/dev/tlamutant/tlc/TlcRunnerTest.java`
- Modify: `src/integrationTest/java/dev/tlamutant/tlc/TlcRunnerIntegrationTest.java`

- [ ] **Step 1: Add RED real-process lifecycle tests**

Use executable shell fixtures, not mocks. Add package-private lifecycle durations so tests use millisecond-scale bounds while production uses graceful 2 seconds, forced 5 seconds, collector drain 5 seconds, and executor termination 5 seconds.

Add tests for:

- a child that traps `TERM` and requires force termination
- a child that exits while a descendant retains stdout/stderr, preventing EOF
- a collector drain timeout returning `TOOL_ERROR/NOT_EVALUATED`
- a failed final reap returning `TOOL_ERROR/NOT_EVALUATED`
- interrupt restoration after bounded cleanup

Record exact PIDs in temporary files, assert elapsed upper bounds with tolerance, and verify owned exact PIDs are absent before test completion.

Run:

```bash
./gradlew test --tests dev.tlamutant.tlc.TlcRunnerTest --no-build-cache --rerun-tasks --console=plain
```

Expected: lifecycle tests fail because collector waits and final reap are unbounded or unchecked.

- [ ] **Step 2: Replace try-with executor closure with explicit lifecycle**

Close child stdin immediately. Keep explicit `Process`, `ExecutorService`, and collector futures. Wait for the TLC timeout, then exact-child graceful and forced windows. Treat a still-live child after the forced window as a tool error. Drain each collector with the configured bound. Close all process streams, cancel unfinished futures, call `shutdownNow`, and await termination with the configured bound. Do not call unbounded `Future.get()` or `ExecutorService.close()`.

Accumulate lifecycle failures into bounded stderr diagnostics. A normal successfully reaped TLC timeout remains `TIMEOUT/NOT_EVALUATED`; failed reap, collector failure/timeout, or executor failure becomes `TOOL_ERROR/NOT_EVALUATED`. Restore interruption only after bounded exact-child cleanup has been attempted. Always run strict run-directory cleanup, which may also override the result with a tool error.

- [ ] **Step 3: Run GREEN and scan for unbounded waits**

```bash
./gradlew spotlessApply test --tests dev.tlamutant.tlc.TlcRunnerTest --no-build-cache --rerun-tasks --console=plain
rg -n "Future<|\.get\(\)|try \(var executor|process\.waitFor\(\)" src/main/java/dev/tlamutant/tlc/TlcRunner.java
```

Expected: lifecycle tests pass; the scan finds futures but no unbounded `get`, executor close, or process wait.

- [ ] **Step 4: Close the real TLC classification gap and run regressions**

Extend `TlcRunnerIntegrationTest` with real named-temporal and deadlock fixtures in its temporary directory. Together with the existing pass, safety, and generic-temporal cases, assert all full-property classifications after the lifecycle refactor.

```bash
./gradlew integrationTest --tests dev.tlamutant.tlc.TlcRunnerIntegrationTest --tests dev.tlamutant.experiment.FullPropertyExperimentIntegrationTest --no-build-cache --rerun-tasks --console=plain
```

Expected: pass for real pass, state-safety, named-temporal, generic-temporal, and deadlock outcomes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/tlamutant/tlc/TlcRunner.java src/test/java/dev/tlamutant/tlc/TlcRunnerTest.java src/integrationTest/java/dev/tlamutant/tlc/TlcRunnerIntegrationTest.java
git commit -m "fix: bound TLC process cleanup"
```

## Task 6: Document direct-mode limits and freeze application identity

**Files:**

- Modify: `README.md`
- Modify: `config/current-application.json`
- Modify: `docs/experiments/gcd-prospective-closure-assessment-v11.json`
- Modify: `src/integrationTest/java/dev/tlamutant/prospective/HistoricalArtifactFreezeIntegrationTest.java`

- [ ] **Step 1: Document the two timeout scopes**

Explain that `--timeout-seconds` limits one TLC invocation and `--experiment-timeout-seconds` limits the full direct full-property run, defaults to one hour, and preserves unresolved evidence for admitted candidates after baseline expiry.

- [ ] **Step 2: Build twice from clean uncached inputs**

```bash
./gradlew clean fatJar --no-build-cache --rerun-tasks --console=plain
shasum -a 256 build/libs/tla-mutant-all.jar
stat -f '%z' build/libs/tla-mutant-all.jar
./gradlew clean fatJar --no-build-cache --rerun-tasks --console=plain
shasum -a 256 build/libs/tla-mutant-all.jar
stat -f '%z' build/libs/tla-mutant-all.jar
```

Expected: both digest and size pairs are identical.

- [ ] **Step 3: Update the frozen identities with `apply_patch`**

Update `config/current-application.json`, the v11 assessment validator application identity, and the corresponding frozen assessment digest asserted by `HistoricalArtifactFreezeIntegrationTest`. Do not alter historical source identities.

- [ ] **Step 4: Verify and commit**

```bash
./gradlew spotlessApply test integrationTest verifyFrozenApplication --no-build-cache --rerun-tasks --console=plain
git diff --check
git add README.md config/current-application.json docs/experiments/gcd-prospective-closure-assessment-v11.json src/integrationTest/java/dev/tlamutant/prospective/HistoricalArtifactFreezeIntegrationTest.java
git commit -m "docs: record direct-mode availability bounds"
```

## Task 7: Full verification, containment, reviews, and integration

**Files:**

- Create: `docs/security/2026-08-31-direct-mode-availability-review.md`
- Create on main checkout: `docs.local/logs/2026-08-31/2026-08-31-002-direct-mode-availability.md`

- [ ] **Step 1: Run the full fresh suite**

```bash
./gradlew spotlessCheck test integrationTest verifyFrozenApplication --no-build-cache --rerun-tasks --console=plain
git diff --check
```

- [ ] **Step 2: Run Podman containment with lifecycle-safe startup and shutdown**

Start the configured Podman machine, run `sandboxTest`, inspect its JUnit XML for zero failures, stop Gradle, and stop the same Podman machine in one shell scope. Verify no task-owned container, volume, `workerd`, Gradle, or child shell process remains.

- [ ] **Step 3: Run clean-context correctness and Security Specialist reviews**

Give reviewers only the original request, acceptance criteria, final diff, and fresh test results. The security report must use `Must Fix / Should Fix / Notes` and end with `GO` or `NO-GO`. Any Must Fix blocks integration.

- [ ] **Step 4: Resolve findings and rerun affected validators**

Use TDD for every correction and commit each coherent fix. Update the review artifact with resolution evidence.

- [ ] **Step 5: Record the Japanese local work log**

Use `date -Idate`, choose the next daily sequence under the main checkout's `docs.local/logs/2026-08-31/`, and record scope, commits, test commands/results, JAR identity, review verdicts, and process cleanup. This ignored log is not committed.

- [ ] **Step 6: Finish the branch safely**

Run `git status --short`, verify only intended changes exist, fast-forward the local main branch without pushing, run `./gradlew --stop`, and retain the user-owned `.worktree` directory unless explicitly asked to remove it.
