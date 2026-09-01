# tla-mutant

`tla-mutant` mutates a TLA+ model and runs TLC to find behaviors that the model can reach without violating its current State Invariants. A surviving mutant can expose a missing or overly weak invariant, but it is a review candidate, not an automatic defect report.

The current implementation is an experimental CLI with three mutation families: guard-conjunct deletion (GCD), boundary relaxation (BRL), and update off-by-one (UOI). It supports a State Invariant oracle for finding state-expanding candidates and a full-property oracle for checking every property configured in the original cfg.

## What it checks

- **GCD** replaces one eligible positive current-state guard conjunct with `TRUE`, which can admit a successor that the original guard excluded.
- **BRL** changes an eligible `<` guard to `<=`, or `>` to `>=`, at a current-state boundary.
- **UOI** changes a supported integer update on the right-hand side of a primed equality by one.

The oracle determines how each mutant is judged:

- `state-invariants` asks whether the configured State Invariants reject the mutant and records qualified state-expansion evidence. This is the default mode.
- `full-property` leaves the cfg unchanged and lets configured invariants, action properties, temporal properties, fairness, constraints, and deadlock checking contribute to the verdict.

Neither mode decides whether a survivor is a domain defect. A person must inspect the mutation and its witness in the context of the model.

## Quick start

Requirements:

- Java 21
- Rootless Podman with cgroup v2 for downloaded or otherwise untrusted specifications
- The pinned container image from `config/execution-policy.json` for corpus execution

Provision the reviewed TLA+ Tools JAR from the repository, verify both JAR identities, and build the CLI:

```sh
./gradlew verifyTlaTools verifyFrozenApplication fatJar
```

The direct `census` command parses the model with SANY in the host JVM. Every JAR on its classpath executes with the current user's privileges, so use only the two verified JARs and a trusted local specification.

```sh
java -cp build/libs/tla-mutant-all.jar:.tools/tla2tools-1.8.0.jar dev.tlamutant.cli.Main census \
  --model-dir path/to/model \
  --module Spec.tla \
  --config Spec.cfg \
  > build/census.json
```

Downloaded, public, or otherwise untrusted specifications belong in the corpus commands, which parse and execute them inside rootless Podman.

## Reading the result

- `KILLED`: the selected oracle rejected the mutant.
- `STATE_EXPANDING`: the mutant reached a state outside the baseline reachable-state set while the selected State Invariants still passed.
- `SURVIVED`: the selected oracle did not kill the mutant, but the run did not establish a qualified state-expansion witness.
- `UNRESOLVED`: the runner could not produce a trustworthy verdict because of a timeout, resource limit, invalid execution evidence, or another execution failure.

For a state-expanding survivor, inspect the source patch and mutant-only witness, write the smallest domain-specific invariant that excludes the unwanted state, and rerun with that invariant enabled. `tla-mutant` does not generate or approve the invariant automatically.

## Evidence so far

The [v8 GCD-stratified run](docs/experiments/gcd-stratified-assessment.md) found nine reproducible witness-bearing state expansions across six specifications. The preregistered v10 holdout fixed eight previously unevaluated specifications before mutation; two completed survivors met the review rule. A user-delegated review accepted one invariant proposal and marked the other `NEEDS_STRENGTHENING`. In the separately frozen v11 intervention, the accepted invariant killed its motivating mutant while the type-only oracle still passed with 30,943 explored states. The [prospective final assessment](docs/experiments/gcd-prospective-final-assessment.md) records the cohort, intervention, and limitations.

The [historical v7 assessment](docs/experiments/gcd-stratified-assessment-v7.md), [failed v6 instrumentation assessment](docs/experiments/gcd-stratified-assessment-v6-failed.md), and [original 10-specification assessment](docs/experiments/vertical-slice-assessment.md) remain available for audit.

## Limits

- A surviving mutant is a review candidate, not proof of a specification defect.
- The accepted proposal came from a user-delegated review, not an independent domain owner or upstream maintainer.
- One successful prospective intervention does not estimate how often the method will find useful invariants in other specifications.
- TLC provides finite-state evidence for the configured model. It is not an unbounded inductive proof.

## Security and reproducibility

- The application JAR is fixed by `config/current-application.json` at 3,221,295 bytes and SHA-256 `80e20e15f832c38e8562c8080962c7b5a7d603d2de0e9acbc2576217a6f189d1`.
- TLA+ Tools is fixed by `config/tla-tools.lock.json` at 4,487,757 bytes and SHA-256 `eabd140a70f49eb9305a3bd3f3df944eddf87e5a90d329789085f8953a80533a`. The exact unsigned JAR is tracked because the upstream v1.8.0 prerelease URL is rolling. See the [provenance note](vendor/tla-tools/1.8.0-9787e65/README.md) and [security review](docs/security/2026-09-02-vendored-tla-tools-pin-review.md).
- The application JAR does not embed TLA+ Tools. Direct commands need both verified JARs on the classpath, and commands that launch child TLC processes also take `--tools-jar`.
- Direct commands execute trusted input on the host. Corpus commands fetch individually pinned public blobs and keep SANY and TLC inside the rootless Podman boundary.
- Frozen manifests and receipts bind input identities, execution policy, tool JAR, and application JAR. Per-run and aggregate deadlines, state and file bounds, strict output parsing, and exact cleanup checks turn incomplete evidence into `UNRESOLVED` instead of a survivor.
- Historical application identities remain part of their frozen census or receipt. Reproduce them from the corresponding historical commit rather than comparing them with the current application JAR.

## Run a full-property experiment

The default `state-invariants` oracle preserves the historical behavior: it evaluates mutants against the configured State Invariants and is intended to discover state-expanding gap candidates. Select `full-property` when the original cfg must remain byte-for-byte unchanged and configured invariants, action properties, temporal properties, fairness conditions, state constraints, and deadlock checking must all participate in the verdict.

For one local model, provide a manifest whose identity defines the experiment context:

```sh
java -cp build/libs/tla-mutant-all.jar:.tools/tla2tools-1.8.0.jar dev.tlamutant.cli.Main experiment \
  --model-dir path/to/model \
  --module Spec.tla \
  --config Spec.cfg \
  --tools-jar .tools/tla2tools-1.8.0.jar \
  --work-dir build/full-property-work \
  --oracle-mode full-property \
  --manifest path/to/frozen-manifest.json \
  --timeout-seconds 180 \
  --experiment-timeout-seconds 3600 \
  --json-report build/full-property-report.json
```

For a pinned corpus, add `--oracle-mode full-property` to `corpus-experiment`. The corpus runner propagates the frozen manifest, TLA+ Tools JAR, and application JAR identities into the outer report and every completed entry report.

A full-property baseline must pass before any mutant is evaluated. `--timeout-seconds` limits one TLC invocation and retains its 180-second default and 600-second maximum. `--experiment-timeout-seconds` limits the complete direct full-property run, including snapshots, SANY analysis, candidate generation, the baseline, and mutants; its default and maximum are 3,600 seconds. If that aggregate deadline expires after a passing baseline, no more mutant workspaces or TLC processes are started, but every admitted candidate remains in the report as `TIMEOUT/NOT_EVALUATED` with reproducible mutation evidence. Structured TLC safety, temporal, and deadlock violation events produce `full-property-state-safety-kill`, `full-property-temporal-kill`, and `full-property-deadlock-kill`; timeouts, out-of-memory conditions, evaluation errors, incomplete state output, and unexplained nonzero exits remain `unresolved`. Every candidate records its original and mutated source digests, UTF-16 source range, reversible patch, TLC exit code, significant structured event codes, and the violated property name when TLC reports one unambiguously. Legacy invocations that omit `--oracle-mode` retain their existing report shape and semantics.

Full-property execution fails closed. It rejects malformed UTF-8, incomplete or mismatched TLC tool frames, mixed terminal outcomes, truncated output, escaped module/config paths, and run-directory cleanup failures. Direct runs snapshot the complete model tree into private read-only inputs, bind every copied file identity into the report, and enforce file-count, directory-count, depth, path-length, per-file, aggregate-byte, state-output, candidate-count, and wall-clock bounds. Corpus runs retain the existing rootless Podman boundary in addition to these checks.

## Reproduce the original corpus experiment

```sh
./gradlew fatJar
java -cp build/libs/tla-mutant-all.jar:.tools/tla2tools-1.8.0.jar dev.tlamutant.cli.Main corpus-experiment \
  --manifest corpus/manifest.json \
  --policy config/execution-policy.json \
  --tools-jar .tools/tla2tools-1.8.0.jar \
  --cache-dir .corpus \
  --json-report docs/experiments/vertical-slice.json \
  --markdown-report docs/experiments/vertical-slice.md
```

Public specification contents are fetched as individually pinned blobs and are parsed and executed only inside the rootless Podman boundary.

## Reproduce the frozen v8 GCD-stratified experiment

Freeze the outcome-free census before running mutants:

```sh
java -cp build/libs/tla-mutant-all.jar:.tools/tla2tools-1.8.0.jar dev.tlamutant.cli.Main gcd-stratified-census \
  --manifest corpus/gcd-stratified-manifest.json \
  --policy config/execution-policy.json \
  --tools-jar .tools/tla2tools-1.8.0.jar \
  --cache-dir .corpus \
  --census-report docs/experiments/gcd-stratified-census.json \
  --failed-census-report build/reports/gcd-stratified-census-failed.json
```

Then consume that exact census:

```sh
java -cp build/libs/tla-mutant-all.jar:.tools/tla2tools-1.8.0.jar dev.tlamutant.cli.Main gcd-stratified-experiment \
  --manifest corpus/gcd-stratified-manifest.json \
  --census-report docs/experiments/gcd-stratified-census.json \
  --expected-census-sha256 6de7c400622557a545a82c3db0d29b20f304ab2e0c47ec29eddfea2e9a72df95 \
  --policy config/execution-policy.json \
  --tools-jar .tools/tla2tools-1.8.0.jar \
  --cache-dir .corpus \
  --json-report docs/experiments/gcd-stratified.json \
  --markdown-report docs/experiments/gcd-stratified.md
```

The expected census digest must come from the reviewed frozen-census commit, not from an unreviewed file calculated immediately before execution. The v8 census has 51 sites, 45 conforming sites, application SHA-256 `5c17b77c22fa33dac205cc54d35794c5c09cf9ea6e46ce17f3cdb84f8c569cec`, and census SHA-256 `6de7c400622557a545a82c3db0d29b20f304ab2e0c47ec29eddfea2e9a72df95`. Two no-build-cache clean application builds and two write-once census runs were byte-identical. Frozen census paths are write-once, so use a new output path for a repeat run. The historical v7 census uses the `-v7` suffix, the failed v6 instrumentation uses the `-v6` suffix, and earlier historical artifacts remain versioned.

## Run the preregistered v9 invariant intervention

v9 leaves every v8 artifact unchanged. It applies one base-and-derived-digest-pinned insertion to a private sandbox copy of SimpleAllocator, registers `NoRequestAllocationOverlap` as the adopted target invariant, and preserves type, legacy-target, and adopted-target oracle identity. The census must be frozen and committed before the mutation command is run.

```sh
java -cp build/libs/tla-mutant-all.jar:.tools/tla2tools-1.8.0.jar dev.tlamutant.cli.Main gcd-stratified-census \
  --manifest corpus/gcd-stratified-manifest-v9.json \
  --policy config/execution-policy.json \
  --tools-jar .tools/tla2tools-1.8.0.jar \
  --cache-dir .corpus \
  --census-report docs/experiments/gcd-stratified-census-v9.json \
  --failed-census-report build/reports/gcd-stratified-census-v9-failed.json
```

The reviewed v9 census is committed with SHA-256 `cd714aecfec926c3250922ec52e9dc6caef944c1300ff2cafaf9911c2f7a66cb`:

```sh
java -cp build/libs/tla-mutant-all.jar:.tools/tla2tools-1.8.0.jar dev.tlamutant.cli.Main gcd-stratified-experiment \
  --manifest corpus/gcd-stratified-manifest-v9.json \
  --census-report docs/experiments/gcd-stratified-census-v9.json \
  --expected-census-sha256 cd714aecfec926c3250922ec52e9dc6caef944c1300ff2cafaf9911c2f7a66cb \
  --policy config/execution-policy.json \
  --tools-jar .tools/tla2tools-1.8.0.jar \
  --cache-dir .corpus \
  --json-report docs/experiments/gcd-stratified-v9.json \
  --markdown-report docs/experiments/gcd-stratified-v9.md
```

This is a post-discovery intervention: the v8 witness was used to select the new invariant. Killing that mutant validates the workflow's closed loop but is not out-of-sample evidence of general usefulness.

See the [v9 preregistration](docs/experiments/gcd-stratified-v9-preregistration.md), [generated report](docs/experiments/gcd-stratified-v9.md), and [assessment](docs/experiments/gcd-stratified-assessment-v9.md).

## Run the preregistered v10 prospective evaluation

v10 freezes eight previously unevaluated specifications before inspecting their GCD outcomes. The cohort contains six standard-risk entries and two high-risk observations that remain in the denominator without replacement if they fail. The historical source registry rejects both reused repository paths and reused source bytes, while the protocol fixes the eligibility rule, complete review rubric, and three-intervention cap. The discovery receipt binds the registry, protocol, manifest, execution policy, TLA+ Tools JAR, and reproducible application JAR.

Freeze the failure-preserving census only from the commit containing all outcome-free inputs:

```sh
java -cp build/libs/tla-mutant-all.jar:.tools/tla2tools-1.8.0.jar dev.tlamutant.cli.Main prospective-census \
  --registry corpus/historical-source-registry-v10.json \
  --protocol config/prospective-protocol-v10.json \
  --manifest corpus/gcd-prospective-manifest-v10.json \
  --receipt docs/experiments/gcd-prospective-receipt-v10.json \
  --policy config/execution-policy.json \
  --tools-jar .tools/tla2tools-1.8.0.jar \
  --cache-dir .corpus-v10 \
  --census-report docs/experiments/gcd-prospective-census-v10.json \
  --failed-census-report build/reports/gcd-prospective-census-v10-failed.json
```

The census must be committed before any v10 mutation starts. Failed and zero-site entries are retained without replacement. After census, a separate experiment receipt binds the unchanged discovery receipt and the exact census bytes; the mutation command requires both receipts. Discovery outcomes, review decisions, and any accepted-predicate intervention are published in later commits so Git ancestry preserves the prospective boundary. User-delegated agent review is not external human or upstream-maintainer acceptance.

The completed v10 run found two eligible `nbacg-guer01` survivors. The complete review accepted `FaultVoteBoundsWithinCrashed` for candidate `gcd-3c1ee8b4429ee7af9c2f` and classified the other proposal as `NEEDS_STRENGTHENING`. The review-bound v11 census retained all eight specifications and all 60 sites. The initial intervention result was preserved as unresolved because it omitted the type-only relation and witness. A separately frozen corrective run retained `STATE_EXPANDING`, reproduced the exact v10 witness, and attributed the selected kill to the adopted invariant after its type-only run passed with 30,943 states. The schema version 2 machine assessment embeds the role, SHA-256 digest, and byte size of all 18 validation inputs, including the separate execution and validator JARs. The [v11 preregistration](docs/experiments/gcd-prospective-preregistration-v11.md), [corrective preregistration](docs/experiments/gcd-prospective-preregistration-v11-closure.md), [corrected generated report](docs/experiments/gcd-prospective-v11-closure.md), [machine assessment](docs/experiments/gcd-prospective-closure-assessment-v11.json), and [final assessment](docs/experiments/gcd-prospective-final-assessment.md) contain the exact provenance, denominators, and limitations.
