# tla-mutant

`tla-mutant` is an experimental vertical slice for mutation analysis of TLA+ State Invariants. It intentionally implements only three mutation families: guard-conjunct deletion (GCD), boundary relaxation (BRL), and update off-by-one (UOI).

The first pinned 10-specification experiment produced no witness-bearing state-expanding survivors. The historical v2 GCD-stratified 15-specification follow-up produced nine reproducible witness-bearing state expansions across six specifications, domains, and family clusters. This supports continuing the bounded GCD-focused experiment, but human review of the proposed invariant improvements remains pending and two large baselines exceeded the fixed state limit. See the [original assessment](docs/experiments/vertical-slice-assessment.md) and [v2 stratified assessment](docs/experiments/gcd-stratified-assessment-v2.md) before extending the implementation.

## Requirements

- Java 21
- Rootless Podman with cgroup v2
- The pinned container image from `config/execution-policy.json`

The Gradle build downloads the pinned TLA+ Tools artifact and verifies its size and SHA-256 digest.

## Verify

```sh
./gradlew spotlessCheck test integrationTest sandboxTest
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
  --expected-census-sha256 66e1b8ddcdd80e9e51383749c2eb1a117d72bdfe2b40fabf126bcba14fcc22c1 \
  --policy config/execution-policy.json \
  --tools-jar .tools/tla2tools-1.8.0.jar \
  --cache-dir .corpus \
  --json-report docs/experiments/gcd-stratified.json \
  --markdown-report docs/experiments/gcd-stratified.md
```

The expected census digest must come from the reviewed frozen-census commit, not from an unreviewed file calculated immediately before execution. The v5 census above has 51 sites, 45 conforming sites, at least one conforming site in every enriched entry, and SHA-256 `66e1b8ddcdd80e9e51383749c2eb1a117d72bdfe2b40fabf126bcba14fcc22c1`; a second write-once run was byte-identical. Frozen census paths are write-once, so use a new output path for a repeat run. The historical v2 run returned exit code 2 because two preregistered baselines exceeded the fixed canonical-state limit; its artifacts use the `-v2` suffix.
