# tla-mutant

`tla-mutant` is an experimental vertical slice for mutation analysis of TLA+ State Invariants. It intentionally implements only three mutation families: guard-conjunct deletion (GCD), boundary relaxation (BRL), and update off-by-one (UOI).

The first pinned 10-specification experiment produced no witness-bearing state-expanding survivors. A prospectively GCD-stratified 15-specification follow-up produced nine reproducible witness-bearing state expansions across six specifications, domains, and family clusters. This supports continuing the bounded GCD-focused experiment, but human review of the proposed invariant improvements remains pending and two large baselines exceeded the fixed state limit. See the [original assessment](docs/experiments/vertical-slice-assessment.md) and [stratified assessment](docs/experiments/gcd-stratified-assessment.md) before extending the implementation.

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
  --census-report docs/experiments/gcd-stratified-census.json
```

Then consume that exact census:

```sh
java -jar build/libs/tla-mutant-all.jar gcd-stratified-experiment \
  --manifest corpus/gcd-stratified-manifest.json \
  --census-report docs/experiments/gcd-stratified-census.json \
  --policy config/execution-policy.json \
  --tools-jar .tools/tla2tools-1.8.0.jar \
  --cache-dir .corpus \
  --json-report docs/experiments/gcd-stratified.json \
  --markdown-report docs/experiments/gcd-stratified.md
```

The current frozen run returns exit code 2 after writing both reports because two preregistered baselines exceed the fixed canonical-state limit. Their nine GCD sites remain unresolved in the denominator.
