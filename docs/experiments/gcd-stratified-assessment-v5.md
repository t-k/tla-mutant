# GCD-Stratified Experiment Assessment, Hardened v5

## Decision

The hardened v5 run confirms that the 15-specification vertical slice can discover concrete State Invariant improvement candidates while preserving a reviewable preregistration boundary. It still does not justify implementing the full product scope.

The run found nine reproducible witness-bearing `STATE_EXPANDING` GCD survivors across six specifications, six domains, and six family clusters. Six of 15 specifications had at least one expanding site, while ten had at least one state-equal site and five retained at least one unresolved site. Human usefulness review remains `pending`, so this validates candidate discovery and provenance enforcement rather than end-user value.

## Experimental Boundary

- Manifest profile: `gcd-stratified-v5`
- Pinned Examples revision: `ed3cc4c35f7001d7cec08a22dcd698dae7570dc3`
- v5 preregistration commit: `944773a9836a65f5212848d9615fca42da3a2db2`
- Frozen census commit: `545fd965fd48b2fe8593e0e97f39489aee02ea3c`
- Frozen census SHA-256: `66e1b8ddcdd80e9e51383749c2eb1a117d72bdfe2b40fabf126bcba14fcc22c1`
- Exact manifest SHA-256: `41e766b45f821bf46dc6a726a7053ff2a84ea19b5d254441059b7a5d3ca3c8df`
- Exact execution-policy SHA-256: `bd518ab8792604352b1986b3e293c9daf2fbcc5e9f455aaeb162bec1d6cf5e76`
- Exact TLA+ Tools SHA-256: `eabd140a70f49eb9305a3bd3f3df944eddf87e5a90d329789085f8953a80533a`
- Corpus: five controls and ten GCD-enriched specifications
- Static census: 51 unique GCD sites, 45 conforming sites, and a largest family cluster of 9/51 or 17.6%
- Trust boundary: public TLA+ was parsed and executed only inside rootless Podman

The schema-version-2 census embeds exact manifest, policy, and TLA+ Tools identities. The experiment accepts a separately supplied expected census digest before strict parsing and mutation. Census size, digest, and JSON are derived from one bounded no-follow byte snapshot, and the TLA+ Tools bytes actually mounted into the sandbox are materialized from the verified snapshot. A valid or failed census is published only to a new path and cannot replace an existing frozen artifact.

The transition classifier now follows the actual action operand of `[][Next]_vars` and excludes fairness-only actions. Guard conformance rejects negative, conditional, primed, enabled, temporal, and action expressions. A positive compound predicate may conform as one action-level top-level conjunct, while its inner state-only sites do not conform without an action-successor sibling. Direct shared definitions remain excluded from mutation; nested shared value helpers are expanded only to classify the larger guard's syntax.

Profiles v3 and v4 were outcome-free instrumentation attempts that stopped at census conformance before mutation. Their failed census artifacts are retained. Profile v5 retains the same v2 cohort and corrects the classifier boundary identified by those static failures. The final v5 census was run twice to separate write-once paths and was byte-identical.

## Results

### Site-weighted outcomes

| Measure | Result |
| --- | ---: |
| Preregistered specifications | 15 |
| Completed baselines | 13 |
| Census GCD sites | 51 |
| Conforming census sites | 45 |
| GCD results emitted | 42 |
| State Invariant kills, unattributed | 7 |
| State-equal GCD results | 22 |
| State-expanding GCD results | 9 |
| State-incomparable or restricting GCD results | 0 |
| Unresolved GCD sites | 13 |
| Witness-bearing candidates | 9 |
| BRL generated/evaluated | 0/0 |
| UOI generated/evaluated | 7/7 |

### Specification-weighted outcomes

Each outcome counts specifications with at least one site in that bucket, so columns overlap.

| Cohort | Specifications | GCD sites | Specifications with equal | Specifications with expanding | Specifications with unresolved | Specifications with witness |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Control | 5 | 14 | 4 | 2 | 0 | 2 |
| GCD-enriched | 10 | 37 | 6 | 4 | 5 | 4 |
| Overall | 15 | 51 | 10 | 6 | 5 | 6 |

The control cohort produced four expanding sites from 14, while the enriched cohort produced five from 37. This experiment was not designed or powered to establish an uplift in expansion rate. The result supports the feasibility of obtaining useful candidates from a small stratified vertical slice; it does not establish that stratification alone improved the candidate rate.

The experiment was repeated from the same frozen inputs. After excluding diagnostics containing TLC seeds, timestamps, temporary paths, and throughput, the complete JSON was identical: candidate IDs, execution status, oracle outcome, state relation, baseline and mutant state counts, mutant-only witnesses, site-weighted summaries, specification-weighted summaries, and gate status all matched.

## Candidate Review

The candidate interpretation is unchanged from the historical v2 proxy review because the hardened run reproduced the same outcome set.

| Specification | Plausible improvement | Proxy assessment |
| --- | --- | --- |
| `nbacc-ray97` | Add vote-consistency and decision-implies-vote-sent invariants. | Strong |
| `asynchronous-termination-detection` | Register the existing `Safe == terminationDetected => terminated` predicate as a target State Invariant. | Strongest |
| `dining-philosophers` | Derive local phase coherence between `pc`, hunger, and fork ownership. | Promising, domain review required |
| `simple-allocator` | Add `NoRequestAllocationOverlap == \A c \in Clients : unsat[c] \cap alloc[c] = {}`. | Strong |
| `missionaries-and-cannibals` | Investigate a state-only predecessor or capacity invariant. | Weak-to-promising, domain review required |
| `glowing-raccoon-clean` | Add `HotImpliesNoHybrid == tee = "Hot" => hybrid = 0`. | Strong |

## Limitations

- Human usefulness status remains `pending`. A domain owner may reject any proposal as redundant, intentionally omitted, or outside the abstraction boundary.
- `cf1s-folklore` and `bcast-folklore` exceeded the fixed 100,000 canonical-state baseline limit. Their nine sites remain unresolved in every denominator.
- Four completed-entry GCD runs ended in tool errors and remain unresolved.
- Seven State Invariant kills remain unattributed because Type-only reruns are outside this slice.
- BRL still has no eligible candidate, and UOI coverage remains narrow.
- State-set expansion does not establish a harmful behavior, temporal defect, or requirements mismatch.
- The v2 cohort was selected after an outcome-free eligibility repair. The experiment cannot separate cohort stratification from eligibility changes or estimate population prevalence.

## Recommendation

Keep the implementation at vertical-slice scale. The next decision should be a short human review of the four strongest proposals. If at least one is accepted as a useful specification improvement, proceed to Type-only attribution and focused resolution of the four tool-error mutants. Do not implement the remaining long-form product sections yet.

Raw v5 evidence is available in [the frozen census](gcd-stratified-census-v5.json), [the JSON report](gcd-stratified-v5.json), and [the readable report](gcd-stratified-v5.md). The historical result remains available in [the v2 assessment](gcd-stratified-assessment-v2.md).
