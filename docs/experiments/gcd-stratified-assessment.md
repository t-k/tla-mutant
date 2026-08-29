# GCD-Stratified Experiment Assessment, Hardened v7

## Decision

The hardened v7 run confirms that the 15-specification vertical slice can discover concrete State Invariant improvement candidates while preserving an application- and corpus-bound preregistration boundary. It still does not justify implementing the full product scope.

The run found nine reproducible witness-bearing `STATE_EXPANDING` GCD survivors across six specifications, six domains, and six family clusters. Six of 15 specifications had at least one expanding site, while ten had at least one state-equal site and five retained at least one unresolved site. Human usefulness review remains `pending`, so this validates candidate discovery and provenance enforcement rather than end-user value.

## Experimental Boundary

- Manifest profile: `gcd-stratified-v7`
- Pinned Examples revision: `ed3cc4c35f7001d7cec08a22dcd698dae7570dc3`
- v7 preregistration commit: `bfeb16e102a5fab188e1a3bbdd8d1400042f364a`
- Frozen census commit: `35448da21f499febcd4cdc2e00a3c0b372d663a5`
- Frozen census SHA-256: `02e6609b70e59be83b21a71027d372ab3c5d99c3fa41afb6ee67af75cfb7cc2a`
- Exact application SHA-256: `c3249a97341f5635ff34af529f4c6d06c489a173a065c5d307b86b3de73613eb`
- Exact manifest SHA-256: `74a138d0dcbae84698ea2cd7e72e8e0dbca703f8d37ce1b770449c05ceeb6b16`
- Exact execution-policy SHA-256: `bd518ab8792604352b1986b3e293c9daf2fbcc5e9f455aaeb162bec1d6cf5e76`
- Exact TLA+ Tools SHA-256: `eabd140a70f49eb9305a3bd3f3df944eddf87e5a90d329789085f8953a80533a`
- Corpus: five controls and ten GCD-enriched specifications
- Static census: 51 unique GCD sites, 45 conforming sites, and a largest family cluster of 9/51 or 17.6%
- Trust boundary: public TLA+ was parsed and executed only inside rootless Podman

The schema-version-3 census embeds exact application, manifest, policy, and TLA+ Tools identities. Every digest-pinned corpus file is reread into a bounded private snapshot, and only those verified bytes are mounted. The experiment requires an externally supplied census digest before strict parsing and mutation. Sandbox stdout remains byte-preserving until strict single-document JSON parsing, output paths reject case, Unicode-normalization, symlink, and hardlink aliases, and valid or failed census publication is write-once.

The transition classifier follows the actual action operand of `[][Next]_vars` and excludes fairness-only actions. Guard conformance rejects negative, conditional, primed, enabled, temporal, and action expressions. A positive compound predicate may conform as one action-level top-level conjunct, while its inner state-only sites do not conform without an action-successor sibling. Direct shared definitions remain excluded from mutation; nested shared value helpers are expanded only to classify the larger guard's syntax.

Profile v6 first established the application- and corpus-bound census but exposed an instrumentation defect during mutation: path-to-path copying preserved owner-read-only permissions in mutant workspaces. Its census and failed mutation artifacts remain versioned and are not product evidence. Profile v7 keeps the same cohort and mutation semantics, copies verified content into newly created mutable scratch files, and has a real read-only-input integration regression. The v7 census was run twice to separate write-once paths and was byte-identical.

## Results

### Site-weighted outcomes

| Measure | Result |
| --- | ---: |
| Preregistered specifications | 15 |
| Completed baselines | 13 |
| Census GCD sites and terminal records | 51/51 |
| Conforming census sites | 45 |
| GCD mutants evaluated from completed baselines | 42 |
| State Invariant kills, unattributed | 7 |
| State-equal GCD results | 22 |
| State-expanding GCD results | 9 |
| State-incomparable or restricting GCD results | 0 |
| Invalid GCD results | 0 |
| Unresolved GCD sites | 13 |
| Witness-bearing candidates | 9 |
| BRL generated/evaluated | 0/0 |
| UOI generated/evaluated | 7/7 |

### Specification-weighted outcomes

Each outcome counts specifications with at least one site in that bucket, so columns overlap.

| Cohort | Specifications | GCD sites | Specifications with equal | Specifications with expanding | Specifications with unresolved | Specifications with witness |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Control | 5 | 14 | 5 | 2 | 0 | 2 |
| GCD-enriched | 10 | 37 | 5 | 4 | 5 | 4 |
| Overall | 15 | 51 | 10 | 6 | 5 | 6 |

The control cohort produced four expanding sites from 14, while the enriched cohort produced five from 37. This experiment was not designed or powered to establish an uplift in expansion rate. The result supports the feasibility of obtaining concrete candidates from a small stratified vertical slice; it does not establish that stratification alone improved the candidate rate.

The experiment was repeated from the same frozen inputs. After excluding failed-baseline diagnostics containing TLC seeds, timestamps, temporary paths, and throughput, the complete semantic JSON was identical: terminal IDs, execution status, oracle outcome, state relation, baseline and mutant state counts, mutant-only witnesses, site-weighted summaries, specification-weighted summaries, and gate status all matched.

## Candidate Review

The candidate interpretation is unchanged from the historical v2 and v5 proxy reviews because v7 reproduced the same outcome set.

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
- `cf1s-folklore` and `bcast-folklore` exceeded the fixed 100,000 canonical-state baseline limit. Their nine sites remain explicit unresolved terminal records.
- Four completed-entry GCD runs ended in tool errors and remain unresolved.
- Seven State Invariant kills remain unattributed because Type-only reruns are outside this slice.
- BRL still has no eligible candidate, and UOI coverage remains narrow.
- State-set expansion does not establish a harmful behavior, temporal defect, or requirements mismatch.
- The v2 cohort was selected after an outcome-free eligibility repair. The experiment cannot separate cohort stratification from eligibility changes or estimate population prevalence.

## Recommendation

Keep the implementation at vertical-slice scale. The next decision should be a short human review of the four strongest proposals. If at least one is accepted as a useful specification improvement, proceed to Type-only attribution and focused resolution of the four tool-error mutants. Do not implement the remaining long-form product sections yet.

Raw v7 evidence is available in [the frozen census](gcd-stratified-census.json), [the JSON report](gcd-stratified.json), and [the readable report](gcd-stratified.md). The failed v6 instrumentation is documented in [its assessment](gcd-stratified-assessment-v6-failed.md), and the hardened historical result remains available in [the v5 assessment](gcd-stratified-assessment-v5.md).
