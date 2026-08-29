# tla-mutant

`tla-mutant` is an experimental vertical slice for mutation analysis of TLA+ State Invariants. It intentionally implements only three mutation families: guard-conjunct deletion (GCD), boundary relaxation (BRL), and update off-by-one (UOI).

The first pinned 10-specification experiment produced no witness-bearing state-expanding survivors. The historical v7 GCD-stratified 15-specification follow-up produced nine witness-bearing state expansions across six specifications, domains, and family clusters while binding every mounted input and requiring an explicit terminal record for every census site. Profile v8 preserves that cohort and runtime semantics while making the bound application JAR byte-reproducible from source. Human review of the proposed invariant improvements remains pending. See the [historical v7 assessment](docs/experiments/gcd-stratified-assessment-v7.md), [failed v6 instrumentation assessment](docs/experiments/gcd-stratified-assessment-v6-failed.md), [historical v5 assessment](docs/experiments/gcd-stratified-assessment-v5.md), and [original assessment](docs/experiments/vertical-slice-assessment.md) before extending the implementation.

## Requirements

- Java 21
- Rootless Podman with cgroup v2
- The pinned container image from `config/execution-policy.json`

The Gradle build downloads the pinned TLA+ Tools artifact and verifies its size and SHA-256 digest.

## Verify

```sh
./gradlew spotlessCheck test integrationTest sandboxTest verifyFrozenApplication
```

## Reproduce the original corpus experiment

```sh
./gradlew fatJar
java -jar build/libs/tla-mutant-all.jar corpus-experiment \
  --manifest corpus/manifest.json \
  --policy config/execution-policy.json \
  --tools-jar .tools/tla2tools-1.8.0.jar \
  --cache-dir .corpus \
  --json-report docs/experiments/vertical-slice.json \
  --markdown-report docs/experiments/vertical-slice.md
```

Public specification contents are fetched as individually pinned blobs and are parsed and executed only inside the rootless Podman boundary.

## Reproduce the GCD-stratified experiment

Freeze the outcome-free census before running mutants:

```sh
java -jar build/libs/tla-mutant-all.jar gcd-stratified-census \
  --manifest corpus/gcd-stratified-manifest.json \
  --policy config/execution-policy.json \
  --tools-jar .tools/tla2tools-1.8.0.jar \
  --cache-dir .corpus \
  --census-report docs/experiments/gcd-stratified-census.json \
  --failed-census-report build/reports/gcd-stratified-census-failed.json
```

Then consume that exact census:

```sh
java -jar build/libs/tla-mutant-all.jar gcd-stratified-experiment \
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
