# Census Classpath and Open-Issue Closure Design

## Goal

Close the two reviewed local issues without weakening the deliberate separation between the tla-mutant application JAR and the pinned TLA+ Tools JAR. The packaged `census` command must fail with an actionable classpath diagnostic instead of a raw linkage error, the documented explicit-classpath invocation must be verified against real SANY, and the already completed full-property issue must be moved out of the open queue.

## Current Evidence

At HEAD `ca02a50`, the packaged command below exits with status 1 and a `NoClassDefFoundError` for a TLA+ Tools class:

```sh
java -jar build/libs/tla-mutant-all.jar census \
  --model-dir src/testFixtures/resources/tla/census \
  --module CensusFixture.tla \
  --config CensusFixture.cfg
```

The same application JAR and model succeed when the application and pinned Tools JAR are both placed on the JVM classpath:

```sh
java -cp build/libs/tla-mutant-all.jar:.tools/tla2tools-1.8.0.jar \
  dev.tlamutant.cli.Main census \
  --model-dir src/testFixtures/resources/tla/census \
  --module CensusFixture.tla \
  --config CensusFixture.cfg
```

The root cause is the intentional `compileOnly` TLA+ Tools dependency combined with direct in-process SANY use by `CensusCommand`. The fat JAR expands only the application runtime classpath and does not contain TLA+ Tools. Corpus census execution is unaffected because its Podman command already mounts both JARs and starts Java with an explicit classpath.

The full-property issue's five requirements are present at the current HEAD: exact cfg preservation, named and generic temporal-kill classification, fail-closed tool outcomes, reversible mutation evidence, and complete input identities. Existing unit, real-TLC integration, frozen-artifact, and Podman containment coverage exercise those behaviors.

## Selected Approach

Keep explicit JVM classpath assembly as the supported execution model. Add an early class-availability guard to the direct `census` command and document its exact invocation.

The guard checks for representative SANY semantic classes by name through the application class loader before `GcdCensusAnalyzer` is instantiated. If TLA+ Tools is absent, it throws one bounded, actionable diagnostic that:

- names the affected `census` command;
- explains that the application JAR intentionally excludes TLA+ Tools;
- shows the portable command shape using the current platform's classpath separator;
- does not expose environment values, absolute local paths, or a raw linkage stack rooted in `NoClassDefFoundError`.

When TLA+ Tools is already present, the command follows the existing in-process SANY path without another process, class loader, or mutation of global classpath state.

## Rejected Alternatives

### Re-execute a child JVM from `census --tools-jar`

This would make `java -jar` convenient, but it would introduce arbitrary-JAR execution, snapshot and time-of-check/time-of-use concerns, child-process lifecycle management, output forwarding bounds, interrupt handling, and loaded-origin verification. Those costs do not improve the existing Podman corpus path and are disproportionate to a trusted-input diagnostic defect.

### Embed TLA+ Tools in the application fat JAR

This conflicts with the project's explicit provenance boundary, duplicates the separately pinned Tools artifact, and can make `LoadedArtifactVerifier` observe an origin different from the recorded Tools JAR. It is incompatible with the existing reproducibility and supply-chain model.

### Documentation only

The README already states the general two-JAR requirement, but the raw packaged command still fails with an implementation-level linkage error. Documentation alone would leave the reported usability defect reproducible.

## Components and Responsibilities

### Classpath guard

A small package-private CLI helper owns only the availability check and diagnostic construction. It accepts a class loader for deterministic unit coverage and exposes a production entry point that uses the application class loader. It must not load user-provided paths or modify the running JVM.

### Census command

`CensusCommand.call` invokes the guard before parsing derived edits, creating work directories, or starting SANY. This ordering prevents side effects when the runtime dependency is missing.

### Packaged CLI integration test

A real-process integration test uses the Gradle-provided application and Tools JAR paths. It verifies both sides of the contract:

1. `java -jar ... census` fails promptly with the actionable diagnostic and without `NoClassDefFoundError`.
2. The documented `java -cp application:tools dev.tlamutant.cli.Main census ...` invocation succeeds and emits a schema-version-1 census containing the expected fixture sites.

The test uses no mocks and applies a bounded process wait.

### Corpus oracle-mode parsing test

A focused unit test covers the small gap found during the resolved full-property audit: explicit `--oracle-mode full-property` parsing for `corpus-experiment`, plus the existing `state-invariants` default. It does not duplicate the Podman end-to-end assertion; it protects CLI routing before execution.

### Documentation and issue state

README gains a trusted-local-census section with the exact explicit-classpath command and a warning that direct census parses on the host. Both reviewed issue files move from `docs.local/issues/open` to `docs.local/issues/closed` after verification.

The original census issue also requested a manifest-free local Podman mutation command. Current direct `experiment` support is host-side and full-property mode still requires a manifest, so that request is not silently marked complete. It becomes a separate local open issue covering bounded local-tree snapshotting, rootless Podman execution, provenance, resource limits, and cleanup.

## Error Handling and Security

- Missing Tools classes fail before any model parsing or work-directory creation.
- The diagnostic is static except for the platform classpath separator and remains bounded.
- Existing explicit-classpath execution remains backward compatible.
- No arbitrary JAR is dynamically loaded and no new child process is introduced in production code.
- Public or otherwise untrusted specifications remain restricted to the existing rootless Podman corpus path. The direct `census` command is documented for trusted local input only.
- A Security Specialist review records `Must Fix / Should Fix / Notes`. Any Must Fix blocks integration.

## Coverage Obligations

| ID | Obligation | Risk | Evidence |
|---|---|---:|---|
| O01 | Packaged census without Tools fails before SANY with an actionable diagnostic. | High | Unit guard test and real packaged-process regression test |
| O02 | The failure output does not contain a raw `NoClassDefFoundError`. | High | Real packaged-process regression test |
| O03 | The documented explicit-classpath census succeeds with real SANY and emits valid census JSON. | High | Real packaged-process integration test |
| O04 | Missing Tools causes no work-directory or derived-input side effect. | Medium | Real packaged-process regression test with an absent requested work directory |
| O05 | Existing corpus Podman census behavior is unchanged. | High | Existing `PodmanCommandTest` and `PodmanContainmentTest` |
| O06 | Full-property requirements 1 through 5 remain covered. | High | Existing focused unit and real-TLC integration suites |
| O07 | `corpus-experiment` defaults to state invariants and accepts explicit full-property routing. | Medium | Focused CLI parsing test |
| O08 | The changed application JAR remains byte reproducible and all current validator identities agree. | High | Two clean no-cache builds, digest comparison, and `verifyFrozenApplication` |
| O09 | Open issue state reflects actual repository scope without losing the local Podman request. | Medium | Closed issue moves plus one new scoped open issue |

The ledger intentionally keeps layer overlap for O01 through O03 because the original defect appears only at packaged-process class loading, while the helper unit test gives deterministic diagnostic coverage. Pairwise generation, model checking, and fuzzing are not applicable to this linear dependency-presence branch; real process execution is the authoritative validator.

## Acceptance Criteria

1. The original packaged `java -jar ... census` reproduction no longer emits a raw linkage error and instead explains the supported invocation.
2. The README command succeeds against the real census fixture and pinned TLA+ Tools JAR.
3. No production code dynamically loads a supplied JAR or launches a replacement JVM.
4. All unit, integration, frozen-application, and Podman containment tests pass.
5. Two clean no-cache fat-JAR builds are byte-identical and current identity records are updated consistently.
6. Security review has no Must Fix and clean-context correctness review approves the final diff.
7. Both reviewed issues are removed from the open queue, while the distinct local Podman request remains visible as its own issue.

## Out of Scope

- Making `java -jar ... census --tools-jar ...` dynamically repair its classpath.
- Embedding TLA+ Tools in the application JAR.
- Implementing the new manifest-free local Podman experiment in this change.
- Removing the firebase-emulator repository-owned runner before cross-project result comparison.
