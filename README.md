# tla-mutant

`tla-mutant` is an experimental vertical slice for mutation analysis of TLA+ State Invariants. It intentionally implements only three mutation families: guard-conjunct deletion (GCD), boundary relaxation (BRL), and update off-by-one (UOI).

The first pinned 10-specification experiment produced no witness-bearing state-expanding survivors. The hardened v8 GCD-stratified 15-specification follow-up produced nine reproducible witness-bearing state expansions across six specifications, domains, and family clusters while binding every mounted input, requiring an explicit terminal record for every census site, and making the application JAR byte-reproducible from source. A downstream [user-delegated qualitative review](docs/experiments/gcd-stratified-human-review.md) accepted `NoRequestAllocationOverlap` for `simple-allocator` as an adoption-worthy model-quality improvement; external human or upstream-maintainer acceptance remains separate, and the frozen execution report retains its historical `pending` status. See the [current v8 assessment](docs/experiments/gcd-stratified-assessment.md), [historical v7 assessment](docs/experiments/gcd-stratified-assessment-v7.md), [failed v6 instrumentation assessment](docs/experiments/gcd-stratified-assessment-v6-failed.md), and [original assessment](docs/experiments/vertical-slice-assessment.md) before extending the implementation.

## Requirements

- Java 21
- Rootless Podman with cgroup v2
- The pinned container image from `config/execution-policy.json`

The Gradle build downloads the pinned TLA+ Tools artifact and verifies its size and SHA-256 digest.
The current reproducible application JAR identity is preregistered in `config/current-application.json`. Historical v8 application identity remains part of the frozen v8 census and is reproduced from the corresponding historical commit rather than compared to a changed HEAD.

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

## Reproduce the frozen v8 GCD-stratified experiment

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

## Run the preregistered v9 invariant intervention

v9 leaves every v8 artifact unchanged. It applies one base-and-derived-digest-pinned insertion to a private sandbox copy of SimpleAllocator, registers `NoRequestAllocationOverlap` as the adopted target invariant, and preserves type, legacy-target, and adopted-target oracle identity. The census must be frozen and committed before the mutation command is run.

```sh
java -jar build/libs/tla-mutant-all.jar gcd-stratified-census \
  --manifest corpus/gcd-stratified-manifest-v9.json \
  --policy config/execution-policy.json \
  --tools-jar .tools/tla2tools-1.8.0.jar \
  --cache-dir .corpus \
  --census-report docs/experiments/gcd-stratified-census-v9.json \
  --failed-census-report build/reports/gcd-stratified-census-v9-failed.json
```

After reviewing and committing that write-once census, supply its trusted SHA-256 value:

```sh
java -jar build/libs/tla-mutant-all.jar gcd-stratified-experiment \
  --manifest corpus/gcd-stratified-manifest-v9.json \
  --census-report docs/experiments/gcd-stratified-census-v9.json \
  --expected-census-sha256 REVIEWED_V9_CENSUS_SHA256 \
  --policy config/execution-policy.json \
  --tools-jar .tools/tla2tools-1.8.0.jar \
  --cache-dir .corpus \
  --json-report docs/experiments/gcd-stratified-v9.json \
  --markdown-report docs/experiments/gcd-stratified-v9.md
```

This is a post-discovery intervention: the v8 witness was used to select the new invariant. Killing that mutant validates the workflow's closed loop but is not out-of-sample evidence of general usefulness.
