# GCD-Stratified Instrumentation Assessment, Failed v6

## Decision

The v6 mutation run is invalid as product evidence and must not be used to assess GCD usefulness. It successfully demonstrated application and corpus snapshot binding and emitted one terminal record for every census site, but an instrumentation defect prevented every generated mutant from reaching semantic analysis.

## Boundary and Observed Failure

- Manifest profile: `gcd-stratified-v6`
- Frozen census SHA-256: `38223477282fc0b9c673f9e40f9cc1a754cf9bf1ccd8dd75c1969e203faacced`
- Bound application SHA-256: `cbed933da8fa316a1213f606cb1e6c054fa0cf70d967ea1771c500d2e1a8069a`
- Census: 15 specifications, 51 GCD sites, and 45 conforming sites
- Terminal records: 51, comprising 42 invalid and 9 unresolved after two incomplete baselines
- Witness-bearing candidates: 0, not interpretable because no mutant was evaluated

The per-entry corpus snapshot correctly made mounted inputs owner-read-only. `ExperimentRunner` then copied each source into a mutant workspace with `Files.copy(Path, Path)`, which preserved that read-only permission. Replacing a guard in the copied root module consequently failed with `IOException`. The broad failure mapping labeled these records `post-mutation-sany-rejection`, but SANY was never reached.

## Corrective Boundary

The regression is fixed by copying file content through a stream into newly created workspace files, which receive mutable workspace permissions without weakening the mounted input snapshot. A real integration test sets the fixture inputs to owner-read-only and requires all seven fixture mutants to be evaluated. Because the application JAR changed, profile v7 requires a newly frozen application-bound census before any replacement mutation run.

Raw audit evidence is retained in [the v6 census](gcd-stratified-census-v6.json), [the failed JSON report](gcd-stratified-v6-failed.json), and [the failed readable report](gcd-stratified-v6-failed.md).
