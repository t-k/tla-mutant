# GCD-Stratified Experiment Assessment

## Decision

The GCD-stratified vertical slice is meaningful enough to continue as a bounded experiment. It should not yet expand into the full product described in the long-form idea document.

The frozen run found nine reproducible witness-bearing `STATE_EXPANDING` GCD survivors across six specifications, six domains, and six family clusters. This clears the preregistered automated gate by a wide margin. A proxy review found four candidates that translate directly into plausible State Invariant improvements. Human acceptance remains pending by design, so this result validates candidate discovery, not end-user usefulness or the full product hypothesis.

## Experimental Boundary

- Manifest profile: `gcd-stratified-v2`
- Pinned Examples revision: `ed3cc4c35f7001d7cec08a22dcd698dae7570dc3`
- Versioned preregistration commit: `548b6a70690da240df236caa3f86035afca0b280`
- Frozen census commit: `c9a1ff9`
- Frozen census SHA-256: `9780bc2eea85fcf5376fe391d6e5e521bf95adb314bcc65913a83bf7747f08e6`
- Corpus: five controls and ten GCD-enriched specifications
- Static census: 51 unique GCD sites; largest family cluster 9/51, or 17.6%
- Trust boundary: public TLA+ was parsed and executed only inside rootless Podman

The first preregistration failed before mutation. Its audit artifact records one wrapper-source error, six zero-site entries, and a 14-site family cluster. A root-only RED fixture then exposed a separate false negative caused by requiring the literal action name `Next`. The minimal repair recognized the first action-level hop below a configured `SPECIFICATION`; it did not add multi-module source or patch routing. Two outcome-free discovery censuses selected the v2 replacements. No mutation outcome existed before the v2 manifest and final census were committed.

## Results

| Measure | Result |
| --- | ---: |
| Preregistered specifications | 15 |
| Completed baselines | 13 |
| Census GCD sites | 51 |
| GCD results emitted | 42 |
| State Invariant kills, unattributed | 7 |
| State-equal GCD results | 22 |
| State-expanding GCD results | 9 |
| State-incomparable or restricting GCD results | 0 |
| Unresolved GCD sites | 13 |
| Witness-bearing candidates | 9 |
| BRL generated/evaluated | 0/0 |
| UOI generated/evaluated | 7/7 |

The 13 unresolved sites consist of four tool-error mutant runs and all nine sites from two specifications whose baselines exceeded the 100,000 canonical-state limit. `cf1s-folklore` and `bcast-folklore` remain in every denominator; they were not replaced or silently dropped. Exit code 2 records these incomplete entries after the JSON and Markdown reports are atomically written.

The control cohort produced four expanding sites from 14, while the enriched cohort produced five from 37. This experiment was not designed or powered to establish an uplift in expansion rate, and the nonliteral-action eligibility repair changed the executable candidate universe after v1. The result therefore supports the feasibility of finding useful candidates in a stratified vertical slice; it does not causally establish that stratification alone improved the rate.

The experiment was repeated. Candidate IDs, execution status, oracle outcome, state relation, baseline and mutant state counts, mutant-only witnesses, summaries, and gate status matched exactly. Diagnostics containing TLC seeds, timestamps, temporary paths, and throughput were excluded from semantic comparison.

## Proxy Invariant Review

This review proposes concrete follow-up invariants but does not substitute for a domain owner's judgment.

| Specification | Evidence from mutant-only witness | Plausible improvement | Proxy assessment |
| --- | --- | --- | --- |
| `nbacc-ray97` | A process can contribute both `YES` and `NO` messages, or reach `ABORT` without its vote in `sent`. | Add vote-consistency and decision-implies-vote-sent invariants. | Strong: the witnesses map to protocol phase and message-authorship rules absent from `TypeOK`. |
| `asynchronous-termination-detection` | `terminationDetected = TRUE` while every node is active. | Register the source's existing `Safe == terminationDetected => terminated` as a target State Invariant. | Strongest: the mutation exposes an already documented safety property omitted from the configured invariant oracle. |
| `dining-philosophers` | Removing `Loop` or `Think` program-counter guards combines fork, hunger, and control states that the baseline cannot reach. | Derive a local phase-coherence invariant relating `pc`, `hungry`, and fork ownership. | Promising but needs domain review; the witness does not identify one uniquely compelling invariant. |
| `simple-allocator` | The same client has `r1` in both `alloc` and `unsat`. | Add `NoRequestAllocationOverlap == \A c \in Clients : unsat[c] \cap alloc[c] = {}`. | Strong: the predicate is inductive under the original actions and captures the comment-level request lifecycle. |
| `missionaries-and-cannibals` | A safe partition of people becomes reachable through a boat move impossible under the one-or-two passenger capacity. | Add a reachability invariant that captures capacity-induced parity or legal predecessor structure. | Weak-to-promising: the anomaly is real, but a clear state-only invariant is not immediate. |
| `glowing-raccoon-clean` | `tee = "Hot"` and `hybrid = 1` occur together after cooling without the required extension step. | Add `HotImpliesNoHybrid == tee = "Hot" => hybrid = 0`. | Strong: the invariant expresses phase coherence not covered by type, non-negativity, or mass preservation. |

Four of six affected specifications therefore produce a concrete, compact improvement candidate under proxy review. The most actionable result is not the raw count of nine survivors; it is that distinct domains yield recognizable missing state relationships rather than only scope artifacts.

## Limitations

- Human usefulness status remains `pending`. A domain owner may reject a proposal as redundant, intentionally omitted, or outside the specification's abstraction boundary.
- Two enriched baselines exceeded the preregistered state limit. Raising limits after observing results would be a new experiment version, not a completion of this run.
- Seven State Invariant kills remain unattributed because Type-only reruns are outside this slice.
- Four completed-entry GCD runs ended in tool errors and remain unresolved.
- BRL still has no eligible candidate in this corpus, and UOI coverage is narrow. These families do not yet justify general conclusions.
- Static GCD conformance enriches for action shape, not for mutation outcome. The observed expansion rate is not a prevalence estimate for public TLA+ specifications.
- The eligibility repair and versioned corpus reselection are co-interventions. The current evidence cannot separate their contributions to candidate discovery.
- State-set expansion does not establish a harmful behavior, temporal defect, or requirements mismatch.

## Recommendation

Keep the implementation at the vertical-slice scale. The next highest-value step is a short human review of the four strong proposals, starting with the existing `Safe` predicate in `asynchronous-termination-detection` and `NoRequestAllocationOverlap` in `simple-allocator`. If at least one is accepted as a useful specification improvement, proceed to Type-only attribution and focused resolution of the four tool-error mutants. Do not implement the remaining full-product sections yet.

Raw evidence is available in [the frozen census](gcd-stratified-census.json), [the JSON report](gcd-stratified.json), and [the readable report](gcd-stratified.md).
