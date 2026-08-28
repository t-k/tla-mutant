# TLA Mutant Vertical Slice Implementation Plan

> **For Codex:** Use `superpowers:executing-plans` to execute this plan in the isolated `.worktree/vertical-slice` worktree. The Agent DAG decision is a single writer with read-only specialist review; do not split implementation ownership across agents.

**Goal:** Build and run a trustworthy Java vertical slice that applies GCD-01, BRL-01, and UOI-01 to Model-only TLA+ state-machine expressions, executes each mutant with TLC, compares complete reachable state sets, and reports bounded `state-invariant-gap-candidate` evidence for 10–20 pinned public configurations.

**Architecture:** A Java 21 command-line harness uses SANY in-process for semantic analysis and launches each TLC check in a fresh JVM. A custom TLC state writer emits complete, deeply normalized state values; a relation analyzer distinguishes equal, expanding, restricting, incomparable, and unresolved state sets. Repository fixtures may run locally, but every public corpus byte is fetched by digest and parsed or executed only inside a rootless, no-network Podman sandbox.

**Tech Stack:** Java 21, Gradle 8, JUnit Jupiter 5, Jackson 2, Picocli, Spotless, SANY/TLC from pinned TLA+ Tools 1.8.0, rootless Podman.

## Execution Rules

- Work only on branch `feat/vertical-slice` in `.worktree/vertical-slice`; keep ignored `docs.local/` edits and logs in the main checkout.
- Follow red-green-refactor for each behavior and commit after every coherent task.
- Use real SANY/TLC fixtures and real Podman containment tests; do not introduce mocks.
- Keep the TLA+ Tools jar outside the application fat jar. The TLC subprocess classpath contains exactly the application artifact and the verified tools jar.
- Treat syntax, semantic, evaluation, resource, output, canonicalization, and sandbox failures as unresolved or rejected, never as killed or survived.
- Stop and use a clean-context debugging agent before a second attempted fix to the same failure, as required by the repository policy.

### Task 1: Create the reproducible Java build and toolchain lock

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `config/tla-tools.lock.json`
- Create: `config/execution-policy.json`
- Create: `config/standard-modules.txt`
- Create: `src/test/java/dev/tlamutant/build/ToolchainLockTest.java`

**Steps:**

1. Write a failing JUnit test that loads the lock, requires the exact TLA+ Tools 1.8.0 URL, size, and SHA-256, and rejects a changed digest.
2. Generate the Gradle wrapper and configure Java 21, JUnit Jupiter 5.13.1, Jackson 2.22.2, Picocli 4.7.7, Spotless 8.10.0, `integrationTest`, `sandboxTest`, and `tlaTools` configurations.
3. Add `fetchTlaTools` and `verifyTlaTools` tasks. Download to a temporary owner-only file, bound redirects and size, verify SHA-256, and atomically rename to `.tools/tla2tools-1.8.0.jar`.
4. Build an application fat jar that includes application dependencies but excludes the TLA+ Tools jar.
5. Run `./gradlew test --tests dev.tlamutant.build.ToolchainLockTest` and `./gradlew spotlessCheck`.
6. Commit with `build: add reproducible Java toolchain`.

### Task 2: Implement result types and state-set relations

**Files:**
- Create: `src/main/java/dev/tlamutant/result/Result.java`
- Create: `src/main/java/dev/tlamutant/state/CanonicalState.java`
- Create: `src/main/java/dev/tlamutant/state/StateRelation.java`
- Create: `src/main/java/dev/tlamutant/state/StateRelationAnalyzer.java`
- Create: `src/test/java/dev/tlamutant/state/StateRelationAnalyzerTest.java`

**Steps:**

1. Write failing parameterized tests for equal, strict superset, strict subset, incomparable, and unresolved inputs.
2. Add a forced-hash-collision test requiring full canonical-value comparison after digest lookup.
3. Implement sealed success/failure result values and the minimal immutable state records.
4. Implement set-relation analysis with a mutant-only witness for expanding and incomparable outcomes.
5. Run `./gradlew test --tests 'dev.tlamutant.state.*'`.
6. Commit with `feat: classify complete state relations`.

### Task 3: Validate closed manifests, paths, digests, and resource policy

**Files:**
- Create: `src/main/java/dev/tlamutant/security/SafeRelativePath.java`
- Create: `src/main/java/dev/tlamutant/security/Digests.java`
- Create: `src/main/java/dev/tlamutant/config/ExecutionPolicy.java`
- Create: `src/main/java/dev/tlamutant/corpus/CorpusManifest.java`
- Create: `src/main/java/dev/tlamutant/corpus/ManifestValidator.java`
- Create: `src/test/java/dev/tlamutant/security/SafeRelativePathTest.java`
- Create: `src/test/java/dev/tlamutant/corpus/ManifestValidatorTest.java`

**Steps:**

1. Write failing tests for absolute paths, `..`, NUL, option-like names, Unicode/path ambiguity, duplicate normalized paths, case-fold collisions, symlinks, hardlinks, and special files.
2. Write failing tests showing manifest limits may reduce but cannot increase global CPU, memory, PID, file, output, state, and wall-clock limits.
3. Implement portable path parsing, regular-file identity checks, bounded SHA-256 verification, manifest deserialization, and fail-closed policy intersection.
4. Verify that error values contain logical relative paths only.
5. Run `./gradlew test --tests 'dev.tlamutant.security.*' --tests 'dev.tlamutant.corpus.*'`.
6. Commit with `feat: validate closed corpus inputs`.

### Task 4: Build the SANY semantic model and role graph

**Files:**
- Create: `src/main/java/dev/tlamutant/semantic/SanyAnalyzer.java`
- Create: `src/main/java/dev/tlamutant/semantic/SemanticProject.java`
- Create: `src/main/java/dev/tlamutant/semantic/OperatorDefinition.java`
- Create: `src/main/java/dev/tlamutant/semantic/SemanticRole.java`
- Create: `src/main/java/dev/tlamutant/semantic/RoleGraph.java`
- Create: `src/testFixtures/resources/tla/roles/RoleFixture.tla`
- Create: `src/testFixtures/resources/tla/roles/RoleFixture.cfg`
- Create: `src/integrationTest/java/dev/tlamutant/semantic/SanyAnalyzerIntegrationTest.java`

**Steps:**

1. Write failing real-SANY tests for Model-only, Property-only, scope-only, Shared/Coupled, unreachable, and ambiguous instance-origin definitions.
2. Require every exclusion to include a reason and at least one configured-root reference path.
3. Implement a restricted `FilenameToStream` resolver that permits only verified manifest modules or committed standard modules.
4. Parse the CFG roots for `SPECIFICATION` or `INIT`/`NEXT`, `INVARIANT`, constraints, `VIEW`, and `SYMMETRY`; walk semantic operator references and compute role-membership sets.
5. Expose only definitions whose membership is exactly `{model}` as mutation roots; conservatively exclude unresolved INSTANCE identity.
6. Run `./gradlew integrationTest --tests 'dev.tlamutant.semantic.*'`.
7. Commit with `feat: slice model operators by semantic role`.

### Task 5: Implement source-preserving GCD, BRL, and UOI mutation

**Files:**
- Create: `src/main/java/dev/tlamutant/mutation/MutationFamily.java`
- Create: `src/main/java/dev/tlamutant/mutation/MutationCandidate.java`
- Create: `src/main/java/dev/tlamutant/mutation/SourceDocument.java`
- Create: `src/main/java/dev/tlamutant/mutation/SourcePatcher.java`
- Create: `src/main/java/dev/tlamutant/mutation/GcdMutator.java`
- Create: `src/main/java/dev/tlamutant/mutation/BrlMutator.java`
- Create: `src/main/java/dev/tlamutant/mutation/UoiMutator.java`
- Create: `src/testFixtures/resources/tla/mutations/MutationFixture.tla`
- Create: `src/testFixtures/resources/tla/mutations/MutationFixture.cfg`
- Create: `src/test/java/dev/tlamutant/mutation/SourcePatcherTest.java`
- Create: `src/integrationTest/java/dev/tlamutant/mutation/MutatorIntegrationTest.java`

**Steps:**

1. Write failing byte-preservation tests for UTF-8, LF/CRLF, comments, multiline expressions, and adjacent operators.
2. Write failing candidate-table tests: GCD accepts only positive top-level current-state guard conjuncts; BRL accepts only eligible `<` and `>`; UOI accepts only supported integer primed RHS forms.
3. Add negative tests for property, scope, Shared/Coupled, negative polarity, nested/conditional, successor constraint, EXCEPT, unknown domain, and unprimed contexts.
4. Implement semantic candidate selection from SANY nodes and exact location-to-byte-range mapping.
5. Implement stable mutation IDs and a one-range patcher that rejects empty or multiple changes.
6. Copy each fixture to a temporary workspace, apply one patch, and require post-patch SANY success.
7. Run `./gradlew test --tests 'dev.tlamutant.mutation.*'` and `./gradlew integrationTest --tests 'dev.tlamutant.mutation.*'`.
8. Commit with `feat: generate three semantic mutant families`.

### Task 6: Emit deterministic complete TLC states

**Files:**
- Create: `src/main/java/dev/tlamutant/state/CanonicalValue.java`
- Create: `src/main/java/dev/tlamutant/state/TlcValueCanonicalizer.java`
- Create: `src/main/java/dev/tlamutant/state/CanonicalStateWriter.java`
- Create: `src/testFixtures/resources/tla/states/ValueShapes.tla`
- Create: `src/testFixtures/resources/tla/states/ValueShapes.cfg`
- Create: `src/test/java/dev/tlamutant/state/TlcValueCanonicalizerTest.java`
- Create: `src/integrationTest/java/dev/tlamutant/state/CanonicalStateWriterIntegrationTest.java`

**Steps:**

1. Write failing golden tests for integer, Boolean, string, model value, set, tuple, record, function, and nested TLC values.
2. Write a repeated-run test proving equivalent reordered sets, records, and functions yield byte-identical canonical state records.
3. Implement canonical values from deeply normalized TLC values with an explicit kind tag and stable representation. Reject unsupported or lazy values as canonicalization failures.
4. Implement the zero-argument TLC state writer, reading only a validated system-property output path, emitting bounded JSONL, and writing an explicit completion marker on close.
5. Sort variables, retain full canonical values, compute SHA-256 only as an index, and suppress duplicate complete states without relying on TLC fingerprints for equality.
6. Run `./gradlew test --tests 'dev.tlamutant.state.*'` and `./gradlew integrationTest --tests 'dev.tlamutant.state.*'` twice.
7. Commit with `feat: capture canonical TLC state sets`.

### Task 7: Run TLC in fresh processes and reduce tool outcomes

**Files:**
- Create: `src/main/java/dev/tlamutant/tlc/TlcToolEvent.java`
- Create: `src/main/java/dev/tlamutant/tlc/TlcToolEventParser.java`
- Create: `src/main/java/dev/tlamutant/tlc/TlcExecution.java`
- Create: `src/main/java/dev/tlamutant/tlc/TlcOutcome.java`
- Create: `src/main/java/dev/tlamutant/tlc/TlcRunner.java`
- Create: `src/test/java/dev/tlamutant/tlc/TlcToolEventParserTest.java`
- Create: `src/test/java/dev/tlamutant/tlc/TlcRunnerLifecycleTest.java`
- Create: `src/integrationTest/java/dev/tlamutant/tlc/TlcRunnerIntegrationTest.java`

**Steps:**

1. Write failing reducer tests for pass, invariant violation, syntax error, semantic error, evaluation error, timeout, OOM marker, tool error, truncated output, and missing completion marker.
2. Write a controlled-child lifecycle test proving stdout and stderr are drained concurrently, byte limits are independent, timeout sends TERM then exact-PID kill after a bounded grace period, and the process is reaped.
3. Implement an array-only `ProcessBuilder` launch using an absolute verified Java path, exact two-entry classpath, one worker, fixed fingerprint polynomial, dedicated metadata, and removed Java/TLA/proxy/credential environment variables.
4. Parse `-tool` framing and combine message codes, exit status, lifecycle result, and state-writer validation into orthogonal execution/oracle results.
5. Add real TLC integration fixtures for pass, invariant violation, evaluation failure, and bounded timeout.
6. Run `./gradlew test --tests 'dev.tlamutant.tlc.*'` and `./gradlew integrationTest --tests 'dev.tlamutant.tlc.*'`.
7. Commit with `feat: isolate and classify TLC executions`.

### Task 8: Orchestrate the experiment and render safe reports

**Files:**
- Create: `src/main/java/dev/tlamutant/experiment/ExperimentRunner.java`
- Create: `src/main/java/dev/tlamutant/experiment/MutationResult.java`
- Create: `src/main/java/dev/tlamutant/experiment/GapCandidate.java`
- Create: `src/main/java/dev/tlamutant/report/ExperimentReport.java`
- Create: `src/main/java/dev/tlamutant/report/JsonReportWriter.java`
- Create: `src/main/java/dev/tlamutant/report/MarkdownReportWriter.java`
- Create: `src/main/java/dev/tlamutant/cli/Main.java`
- Create: `src/test/java/dev/tlamutant/experiment/ExperimentRunnerTest.java`
- Create: `src/test/java/dev/tlamutant/report/ReportWriterTest.java`
- Create: `src/integrationTest/java/dev/tlamutant/experiment/ExperimentIntegrationTest.java`

**Steps:**

1. Write failing orchestration tests requiring baseline validation, one-site mutants, SANY gating, meaningful versus type-only kills, survivor relations, and witness-bearing candidate verdicts.
2. Write failing report tests for explicit denominators, exclusion reasons, bounded diagnostics, candidate terminology, proxy-review labeling, control/ANSI/bidirectional/NUL removal, raw-HTML neutralization, and code-fence safety.
3. Implement sequential baseline and mutant execution, optional no-type-invariant reruns, and immutable report aggregation.
4. Implement JSON schema-versioned output and escaped Markdown output with relative logical paths only.
5. Add Picocli commands `fixture-experiment`, `sandbox-experiment`, and `validate-manifest`; keep the public interface explicitly experimental.
6. Run `./gradlew test --tests 'dev.tlamutant.experiment.*' --tests 'dev.tlamutant.report.*'` and `./gradlew integrationTest --tests 'dev.tlamutant.experiment.*'`.
7. Commit with `feat: report invariant gap candidates`.

### Task 9: Enforce rootless Podman containment

**Files:**
- Create: `containers/Containerfile`
- Create: `config/seccomp.json`
- Create: `src/main/java/dev/tlamutant/sandbox/PodmanCommand.java`
- Create: `src/main/java/dev/tlamutant/sandbox/PodmanRunner.java`
- Create: `src/test/java/dev/tlamutant/sandbox/PodmanCommandTest.java`
- Create: `src/sandboxTest/java/dev/tlamutant/sandbox/PodmanContainmentTest.java`
- Create: `src/sandboxTest/resources/malicious/`

**Steps:**

1. Write failing command-contract tests for pinned image digest, rootless user namespace, explicit non-root UID/GID, no network, read-only rootfs, read-only input/tool mounts, no devices, cap-drop, no-new-privileges, fixed seccomp, closed stdin, no TTY, tmpfs byte/inode limits, and global resource ceilings.
2. Write real sandbox tests for success, failure, traversal, symlink/special-file input, unlisted module, hostile environment, fake Java/classpath overrides, network attempt, output flood, inode exhaustion, scratch escape, timeout, and ignored termination.
3. Implement exact random container naming, post-start identity verification, active cgroup-limit verification, bounded concurrent output capture, schema validation, atomic host report storage, and exact-container TERM/kill/removal cleanup.
4. Ensure the container has no writable host mount; only the final bounded JSON object crosses stdout.
5. Build and pin the container image by digest, then record the digest in `config/execution-policy.json`.
6. Run `./gradlew sandboxTest` and inspect `podman ps -a` for the exact test-name prefix; require no leftovers.
7. Commit with `feat: sandbox untrusted corpus execution`.

### Task 10: Pin and execute a 10–20 configuration public corpus

**Files:**
- Create: `corpus/manifest.json`
- Create: `src/main/java/dev/tlamutant/corpus/CorpusFetcher.java`
- Create: `src/test/java/dev/tlamutant/corpus/CorpusFetcherTest.java`
- Create: `src/integrationTest/java/dev/tlamutant/corpus/CorpusManifestIntegrationTest.java`
- Modify: `build.gradle.kts`
- Generate ignored: `.corpus/`
- Generate ignored: `.tla-mutant/results/vertical-slice.json`
- Generate ignored: `.tla-mutant/results/vertical-slice.md`

**Steps:**

1. Select 12 initial configurations from at least three domains in the official `tlaplus/Examples` repository; record the full commit, license, logical paths, every transitive non-standard module digest, type invariants, target invariants, and bounded resource limits.
2. Write failing fetch tests for wrong origin, redirect, private/loopback DNS or connected endpoint, size overflow, digest mismatch, partial cache, and atomic recovery.
3. Implement per-file HTTPS blob fetching with a fixed allowlisted URL grammar, public-endpoint checks, bounded response size, digest verification, owner-only temporary files, and atomic rename. Never use Git checkout, archives, LFS, submodules, or hooks.
4. Add `corpusExperiment` to build the fat jar, verify tools and image, fetch inputs, and invoke only `sandbox-experiment`.
5. Run all baselines. Replace unsuitable configurations only with a recorded exclusion reason until 10–20 configurations across at least three domains pass.
6. Run the full mutant experiment, rerun it for reproducibility, and compare candidate IDs and semantic results.
7. Update coverage obligations O18–O20 with evidence and commit corpus code/manifest as `experiment: add pinned public TLA corpus`.

### Task 11: Correct the product specification and publish experiment evidence

**Files:**
- Modify in main checkout: `docs.local/IDEA.md`
- Create in main checkout: `docs.local/logs/2026-08-29/2026-08-29-002-vertical-slice-implementation.md`
- Create: `README.md`
- Create: `docs/experiments/2026-08-29-vertical-slice.md`
- Modify: `docs/superpowers/plans/2026-08-29-vertical-slice-coverage.md`

**Steps:**

1. Replace unbounded Genuine State-Invariant Gap language and slugs with bounded `State-Invariant Gap Candidate` terminology, including exact prerequisites and non-claims.
2. Add Model-only, Property-only, Scope, and Shared/Coupled role slicing; specify that the MVP excludes Shared/Coupled sites and records root paths.
3. Split Property Probe into wiring, contribution, and vacuity; state that conjunct removal is weakening with no standalone kill expectation and generic negation is not an independent score.
4. Document the runnable commands, trust boundary, corpus identity, experiment metrics, candidate witnesses, proxy usefulness review, exclusions, and negative evidence in README and the experiment report.
5. Update every coverage obligation with passing evidence, explicit debt, or a justified deferred status.
6. Add the private Japanese work log with design decisions, security review, commits, commands, results, limitations, and exact artifact paths.
7. Commit public documentation with `docs: publish vertical slice evidence`. The ignored IDEA and work log remain local by repository policy.

### Task 12: Verify, review, and integrate the completed slice

**Files:**
- Modify only files required by verification findings.

**Steps:**

1. Read and follow `superpowers:verification-before-completion`.
2. Run `./gradlew clean spotlessCheck test integrationTest sandboxTest corpusExperiment` from the feature worktree.
3. Run the corpus experiment a second time and compare content-addressed candidate IDs, relations, witnesses, and report consistency.
4. Inspect exact Podman container IDs and relevant child processes; verify no task-owned containers, Java processes, or writable mounts remain.
5. Run `git diff --check`, `git status --short`, and inspect the complete branch diff.
6. Request one clean-context correctness review focused on semantic false positives, state identity, sandbox escape, lifecycle leaks, and missing critical coverage. Address all Must Fix findings and rerun affected verification.
7. Read and follow `superpowers:finishing-a-development-branch`; because the user delegated completion, integrate the verified feature branch into the local main branch without pushing remotely, preserving ignored `docs.local/` files.
8. Record final commit IDs and verification output in the private work log.
