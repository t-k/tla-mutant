# TLA Mutant Vertical Slice Design

## Goal

Build a research-grade vertical slice that mutates TLA+ models with GCD-01, BRL-01, and UOI-01, executes the mutants with TLC, and distinguishes state-expanding survivors from equivalent, restricting, incomparable, and unresolved mutants. Run the slice on 10–20 pinned public specifications and produce evidence suitable for judging whether the surviving mutants suggest useful invariant improvements.

This slice validates the product thesis. It is not the final Rust CLI described in `docs.local/IDEA.md`.

## Scope

The slice includes:

- TLA+ modules checked by TLC with one or more `.cfg` files.
- State invariants only. Temporal safety, liveness, fairness mutation, deadlock sensitivity, and transition-graph comparison are excluded.
- GCD-01, BRL-01, and UOI-01 only.
- Model-only mutation sites. Property-only, scope-only, Shared/Coupled, unreachable, and ambiguous sites are excluded.
- One semantic mutation per mutant and one source patch per mutant.
- Real SANY analysis and real TLC integration tests. Mocks are not used.
- JSON and Markdown experiment output.
- A pinned public corpus containing 10–20 configurations from at least three specification domains.

The slice excludes HTML, SARIF, CI baseline comparison, parallel TLC execution, PlusCal-source mutation, SYMMETRY, VIEW, state/action constraints, refinements, and a stable public CLI compatibility promise.

## Semantic Corrections to the Product Specification

### Gap terminology

The tool reports `state-invariant-gap-candidate`, displayed as `State-Invariant Gap Candidate`.

The verdict means that, under a pinned CFG, toolchain, and completed reduction-free exploration:

1. the mutant satisfies every configured non-type state invariant;
2. the mutant has at least one reachable complete state value that the baseline does not have; and
3. the audit is resolved without timeout, out-of-memory termination, truncated state output, or canonicalization failure.

The verdict does not assert that the new state violates a natural-language requirement, represents a realistic implementation defect, or remains reachable outside the finite scope. Human or requirement-backed review is required before calling it a defect.

### Semantic-role slicing

Every operator definition receives a role-membership set derived from the SANY reference graph:

- `model`: reachable from `SPECIFICATION`, or from configured `INIT` and `NEXT` roots;
- `property`: reachable from configured `INVARIANT` roots;
- `scope`: reachable from configured state constraints, action constraints, `VIEW`, or `SYMMETRY` roots.

A definition is eligible for model mutation only when its membership is exactly `{model}`. Definitions with two or more roles are `Shared/Coupled` and excluded. Definitions reached only by a property are reserved for Property Probe. Definitions with no configured-root reachability are excluded as unreachable. Ambiguous INSTANCE/source-operator identity is excluded rather than guessed.

The report records the exclusion reason and at least one root-to-definition reference path.

### Property Probe taxonomy

Property Probe is split into three concepts:

- `wiring`: attach a temporary `FALSE` invariant and require TLC to reject a model with at least one initial state. This checks that the property is connected, not that it is useful.
- `contribution`: compare the kill set of a fixed model-mutant corpus with and without a property or conjunct. Weakening `A /\ B` to `A` is expected to pass the baseline and has no standalone kill expectation.
- `vacuity`: use operator-specific activation or strengthening probes with an explicitly defined semantic relation and expected outcome.

Generic invariant negation is not an independent quality score. The vertical slice implements only the wiring probe; contribution and vacuity remain specified but deferred.

## Selected Architecture

The experiment uses Java 21 with Gradle and JUnit 5.

```text
CLI
  -> project/config loader
  -> SANY semantic analyzer
  -> configured-root role graph
  -> GCD/BRL/UOI candidate generator
  -> isolated source patch
  -> SANY validity gate
  -> fresh TLC subprocess
  -> canonical state JSONL writer
  -> outcome and state-set analyzer
  -> JSON/Markdown report
```

SANY analysis runs in the harness JVM. Each TLC execution runs in a fresh JVM subprocess because TLC is not safely reusable for repeated independent model checks in one JVM and must be isolated for timeouts and failures. Repository-owned test fixtures may run locally. Public corpus analysis and TLC execution run only inside the sandbox described below.

TLA+ Tools is downloaded from a pinned release URL into `.tools/`, verified against a committed SHA-256 value, and loaded as a compile/test dependency. The exact TLC revision, jar digest, Java version, configuration hash, and module hashes are recorded in every experiment report.

## Components

### Semantic analyzer

The analyzer loads the root module through SANY, requires syntax, semantic, and level checks to pass, resolves configured roots, and builds a directed operator-reference graph. It preserves source module identity and exact source locations.

The result is a read-only semantic model containing definitions, levels, roles, reference paths, and source ranges. Failure to resolve a root, source module, instantiated source operator, or exact range makes the affected site ineligible.

### Mutator engine

GCD-01 replaces one eligible top-level state-level guard conjunct with `TRUE`. This is semantically equivalent to deleting that conjunct while preserving the prefix-conjunction source structure. It does not mutate primed assignments, action-level successor constraints, negative contexts, conditional branches, or non-model-only definitions.

BRL-01 replaces `<` with `<=` or `>` with `>=` only in an eligible positive current-state guard expression. It does not target `<=`, `>=`, `EXCEPT`, primed assignments, property expressions, or scope expressions.

UOI-01 mutates the right-hand side of an eligible primed equality only when integer arithmetic is syntactically established by a numeral or an arithmetic operator. The first supported rewrites are integer-literal `n` to `n + 1` and `n - 1`, and `x + 1` to `x + 2`. Unknown numeric domains are excluded.

Every generated mutant has a stable ID derived from the module, definition, mutator, normalized original expression, normalized replacement, and source context. The patcher copies the model directory to a temporary workspace, changes exactly one source range, verifies a non-empty one-site diff, and reruns SANY before TLC.

### TLC runner

The runner executes baseline and mutant configurations with one TLC worker, a fixed fingerprint polynomial, a dedicated metadata directory, and a bounded timeout. It parses TLC tool-mode message codes and the process exit status. Human-facing prose is retained for diagnostics but is not the primary verdict source.

Execution and oracle results remain separate. Syntax errors, semantic errors, evaluation errors, timeouts, out-of-memory failures, and tool failures are never counted as killed or survived.

Configured type invariants are identified in the corpus manifest. When the complete invariant set kills a mutant first through a type invariant, the runner repeats the configuration without type invariants to distinguish a meaningful state-invariant kill from a type-only kill.

### Canonical state writer

A custom TLC `IStateWriter` writes every observed complete state to JSONL. The writer derives a canonical representation from sorted variable names and deeply normalized TLC values, stores the complete canonical value, and computes a SHA-256 state identity. TLC's 64-bit fingerprint is diagnostic metadata only and never establishes equality.

The writer must prove deterministic handling for integers, booleans, strings, model values, sets, tuples, records, functions, and nested combinations before the corpus experiment begins. If a value cannot be represented without ambiguity, the run is unresolved.

### State relation analyzer

For a completed baseline and surviving mutant under the same configuration, the analyzer compares the sets of SHA-256 identities while retaining the full canonical values for collision verification.

It returns exactly one of:

- `state-equal`;
- `state-expanding`;
- `state-restricting`;
- `state-incomparable`;
- `state-unresolved`.

A surviving `state-expanding` or `state-incomparable` mutant becomes a `state-invariant-gap-candidate`. `state-equal` and `state-restricting` mutants are not invariant gaps.

### Corpus experiment

The committed corpus manifest records an allowlisted repository URL, full commit object ID, source tree identity, license, module path, CFG path, every required source-file path and SHA-256 digest, domain, type invariants, target invariants, and resource limits. Corpus sources are fetched into ignored `.corpus/` storage and are not vendored.

The initial corpus starts with 12 configurations and may grow to at most 20 when a mutator family lacks enough eligible sites. Every configuration must pass its baseline before mutation. Configurations requiring SYMMETRY, VIEW, state/action constraints, unavailable external modules, or impractical state spaces are excluded with a recorded reason.

The experiment reports:

- generated and applicable mutants by family;
- SANY-valid and TLC-evaluable ratios;
- meaningful kills, type-only kills, survivors, and unresolved outcomes;
- survivor state relations;
- runtime and state counts;
- every candidate's source diff and new-state witness;
- manual-review classification and rationale.

Candidate review uses three labels:

- `actionable-invariant-improvement`: a concrete invariant addition or correction can be stated and the reviewed requirement context supports it;
- `permitted-but-undistinguished`: the state is acceptable, although current invariants do not distinguish it;
- `mutation-or-scope-artifact`: the mutation is unrealistic, the CFG is unsuitable, or the evidence is insufficient.

Automated or agent review is explicitly labeled as a proxy and never reported as human validation. The experiment is a go when it produces at least five actionable candidates from at least three specifications, or when it produces a clear negative result with resolved evidence showing why this mutation strategy is not useful.

## Error Handling and Safety

### Trust boundary

Public corpus content is untrusted. It is never parsed or executed directly on the host. Only repository-owned test fixtures may use the local runner. Public corpus parsing, mutation, and TLC execution use a rootless Podman container with a pinned image digest.

The sandbox has:

- no network;
- a read-only root filesystem;
- read-only mounts for verified corpus input, the harness artifact, and the pinned TLA+ Tools jar;
- an in-memory size-limited scratch filesystem for all mutable data;
- an empty HOME and a minimal explicit environment;
- all Linux capabilities dropped and `no-new-privileges` enabled;
- CPU, memory, PID, open-file, file-size, file-count, total-output, state-count, and wall-clock limits;
- a fixed seccomp profile, an explicit non-root UID/GID and user namespace, no TTY, closed stdin, and no host devices;
- no host credential, agent socket, source checkout, package cache, or user-home mount.

Manifest resource values may only reduce the versioned global execution-policy limits. Missing limits, an attempted increase, an unsupported rootless cgroup controller, or failure to verify the active limits fails closed before corpus parsing. The scratch tmpfs has both byte and inode limits.

The sandbox writes no host-mounted output. It emits one bounded final JSON result through stdout after internal validation. The host captures stdout and stderr concurrently with independent byte limits, validates the JSON schema, removes control and terminal escape sequences from diagnostics, and atomically stores the accepted result. Limit violations are `unresolved-resource-limit`, not kill or survive.

The host assigns a random fixed-format container name. On timeout or cancellation it verifies that exact container identity, sends TERM through `podman stop`, waits a bounded grace period, sends `podman kill` only to the same verified container if necessary, waits for exit, and verifies removal. It never performs broad process-name termination.

### Closed module resolution

The input manifest enumerates every allowed non-standard `.tla` and `.cfg` file. Paths must be normalized relative paths using a restricted portable character set. Absolute paths, `..`, NUL, leading option markers, duplicate normalized names, and case-folding collisions are rejected.

Corpus files are downloaded as individual blobs from an allowlisted HTTPS origin derived from the pinned repository and commit. The URL grammar and origin are fixed by the execution policy; user-controlled schemes, authorities, ports, queries, and fragments are impossible. DNS and connected endpoints must not be loopback, link-local, private, or otherwise non-public addresses. Redirects, Git hooks, checkout filters, Git LFS expansion, submodules, and archive extraction are not used. Each response has a byte limit and must match the committed SHA-256 digest before an atomic rename. Existing cached files are reverified before use.

The corpus store and mutation workspace accept regular files only. Symlinks, hardlinks, sockets, devices, FIFOs, and pre-existing output targets are rejected. Mutation workspaces are unpredictable owner-only directories created beneath the sandbox scratch root. Input-derived names are mapped to fixed-length hashes rather than used as output paths.

SANY uses a restricted module resolver. A resolved module must be either a manifest-listed regular file below the immutable input root or a module on a committed standard-module allowlist loaded from the verified TLA+ Tools jar. The analyzer records the transitive module path and SHA-256 digest set. Ambiguous resolution, resolution outside the input root, an unlisted standard module, or disagreement between the analyzed and executed dependency set rejects the run.

### Java and TLC launch contract

The public-corpus Java runtime is part of the pinned container image. The harness invokes Java and Podman with argument arrays, never through a shell. The runtime path, version, image digest, harness digest, and TLA+ Tools digest are verified and recorded.

The TLC classpath contains exactly the verified TLA+ Tools jar and the harness state-writer artifact. Corpus jars, class files, Java overrides, build scripts, and arbitrary external classpaths are neither searched nor loaded. `CLASSPATH`, `TLA_LIBRARY`, `JAVA_TOOL_OPTIONS`, `_JAVA_OPTIONS`, `JDK_JAVA_OPTIONS`, language-specific package variables, and proxy/credential variables are removed from the child environment.

TLC metadata, generated mutants, state JSONL, and diagnostics remain beneath the scratch root. Output paths must be new, empty, and free of links before execution and must contain only expected regular files afterward. Partial JSONL, output truncation, an unclosed writer, or a missing completion marker makes the audit unresolved.

### Fetch and report integrity

The toolchain download uses an allowlisted HTTPS URL, no redirect, an expected size bound, SHA-256 verification before atomic rename, and owner-only permissions. The build dependency and runtime jar must have the same digest. The container image registry and repository are allowlisted, and every pull must resolve to the committed image digest or fail closed.

The experiment result records the execution-policy version, sandbox arguments, resource limits, container image digest, Java identity, harness digest, TLA+ Tools digest, source tree identity, transitive module hashes, CFG hash, and every generated mutant hash. A mismatch rejects reproduction rather than silently producing a new result.

JSON is produced by a standard serializer. Markdown rendering escapes control characters, ANSI escapes, bidirectional Unicode controls, NUL, raw HTML, and dynamic code-fence termination. Reports contain relative logical paths and bounded diagnostic excerpts, not absolute host paths, usernames, environment values, credentials, or complete command lines.

Security refusals are distinct from mutation results:

- `rejected-input`;
- `rejected-module-resolution`;
- `rejected-integrity`;
- `sandbox-violation`;
- `unresolved-resource-limit`.

The slice still does not claim to make arbitrary hostile TLA+ safe. It implements defense in depth for the pinned experiment corpus and refuses inputs outside that closed profile.

## Test Strategy

Development follows strict red-green-refactor TDD. No production behavior is added before a failing test demonstrates it. Tests use real SANY and TLC fixtures; mocks are prohibited.

High-risk obligations have at least two layers of evidence:

- semantic slicing: focused semantic-graph tests plus real SANY fixtures;
- mutator correctness: candidate-selection tests plus exact golden patches and post-patch SANY validation;
- TLC classification: tool-message reduction tests plus real TLC pass/violation/error fixtures;
- state canonicalization: value-shape golden tests plus repeated real TLC runs;
- state relations: pure set-relation tests plus equal/expanding/restricting/incomparable TLC fixtures;
- corpus reproducibility: manifest validation plus the pinned corpus experiment.
- sandbox containment: malicious-path, network-attempt, scratch-escape, special-file, fake-Java, environment-injection, Java-override/classpath-injection, module-resolution, output-flood, tmpfs-inode-exhaustion, ignored-termination, timeout, and success/failure container-cleanup tests plus post-run inspection.

Timeout behavior uses a deterministic controlled child process for unit-level lifecycle testing and at least one bounded TLC integration scenario for end-to-end classification. Out-of-memory behavior may remain coverage debt if it cannot be reproduced safely and deterministically.

The required verification commands are:

```text
./gradlew spotlessCheck test
./gradlew integrationTest
./gradlew sandboxTest
./gradlew corpusExperiment
```

## Acceptance Criteria

- The three semantic corrections are reflected consistently throughout `docs.local/IDEA.md`.
- Shared/Coupled sites never enter the mutation execution set in the golden fixture corpus.
- All three mutator families generate at least one valid mutant.
- One-site mutation rate is 100%, empty-diff rate is 0%, SANY-valid rate is at least 95%, and TLC evaluation-error rate is at most 2% for the experiment corpus.
- Complete-state identities are stable across repeated runs and canonical ordering differences.
- State relation fixtures resolve to their expected relation without using TLC fingerprints as equality evidence.
- At least 10 pinned public configurations from at least three domains complete baseline validation.
- Public corpus execution never parses or executes corpus content on the host and passes the sandbox containment suite.
- Every resolved non-standard module is manifest-listed, remains below the immutable input root, and matches its committed SHA-256 digest.
- Every corpus container is removed after success, failure, timeout, or cancellation, and no child process or writable host mount remains.
- State, diagnostic, file, process, memory, CPU, and wall-clock limits terminate safely and produce an unresolved or rejected verdict rather than a kill or survive verdict.
- Every generated result is reproducible or explicitly unresolved with a reason.
- The final report distinguishes automated proxy review from human validation and does not call a candidate a genuine defect without external evidence.
