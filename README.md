# tla-mutant

`tla-mutant` is an experimental vertical slice for mutation analysis of TLA+ State Invariants. It intentionally implements only three mutation families: guard-conjunct deletion (GCD), boundary relaxation (BRL), and update off-by-one (UOI).

The first pinned 10-specification experiment produced no witness-bearing state-expanding survivors. The hardened v8 GCD-stratified 15-specification follow-up produced nine reproducible witness-bearing state expansions across six specifications, domains, and family clusters. The preregistered v10 holdout then evaluated eight previously unseen specifications, produced two review-eligible survivors, and yielded one user-delegated `ACCEPT`. Its separately frozen v11 intervention verified that `FaultVoteBoundsWithinCrashed` detects the motivating mutant while the type-only oracle still passes. See the [prospective final assessment](docs/experiments/gcd-prospective-final-assessment.md), [current v8 assessment](docs/experiments/gcd-stratified-assessment.md), [historical v7 assessment](docs/experiments/gcd-stratified-assessment-v7.md), [failed v6 instrumentation assessment](docs/experiments/gcd-stratified-assessment-v6-failed.md), and [original assessment](docs/experiments/vertical-slice-assessment.md). External human or upstream-maintainer acceptance remains separate.

## Requirements

- Java 21
- Rootless Podman with cgroup v2
- The pinned container image from `config/execution-policy.json`

The Gradle build downloads the pinned TLA+ Tools artifact and verifies its size and SHA-256 digest.
The current reproducible application JAR identity is preregistered in `config/current-application.json`. Historical v8 application identity remains part of the frozen v8 census and is reproduced from the corresponding historical commit rather than compared to a changed HEAD.
The application JAR intentionally does not embed TLA+ Tools. Invoke the CLI with both separately verified JARs on the classpath as shown below; `--tools-jar` additionally supplies the exact Tools JAR to child TLC processes.

## Verify

```sh
./gradlew spotlessCheck test integrationTest sandboxTest verifyFrozenApplication
```

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
