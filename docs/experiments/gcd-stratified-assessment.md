# GCD-Stratified Experiment Assessment, Hardened v8

## Decision

The hardened v8 run confirms that the 15-specification vertical slice can discover concrete State Invariant improvement candidates with a source-reconstructable, application- and corpus-bound preregistration boundary. It still does not justify implementing the full product scope.

The run found nine reproducible witness-bearing `STATE_EXPANDING` GCD survivors across six specifications, six domains, and six family clusters. Six of 15 specifications had at least one expanding site, ten had at least one state-equal site, and five retained at least one unresolved site. Human usefulness review remains `pending`, so this validates candidate discovery and provenance rather than end-user value.

## Experimental Boundary

- Manifest profile: `gcd-stratified-v8`
- Pinned Examples revision: `ed3cc4c35f7001d7cec08a22dcd698dae7570dc3`
- v8 preregistration commit: `1296d0f1485915376ff8a64b51efbbff91565437`
- Frozen census commit: `ef8bf42269b5822936ea41bff2b2099ca8aad784`
- Frozen census SHA-256: `6de7c400622557a545a82c3db0d29b20f304ab2e0c47ec29eddfea2e9a72df95`
- Exact application SHA-256: `5c17b77c22fa33dac205cc54d35794c5c09cf9ea6e46ce17f3cdb84f8c569cec`
- Exact manifest SHA-256: `1caeb7734ea814e0d9d2e007ae0711f7fa9dcc332e2cce50b7fb780b08e5ec7e`
- Exact execution-policy SHA-256: `bd518ab8792604352b1986b3e293c9daf2fbcc5e9f455aaeb162bec1d6cf5e76`
- Exact TLA+ Tools SHA-256: `eabd140a70f49eb9305a3bd3f3df944eddf87e5a90d329789085f8953a80533a`
- Corpus: five controls and ten GCD-enriched specifications
- Static census: 51 unique GCD sites, 45 conforming sites, and a largest family cluster of 9/51 or 17.6%
- Trust boundary: public TLA+ was parsed and executed only inside rootless Podman

Every archive task fixes entry timestamps, ordering, file mode, and directory mode. Two isolated no-build-cache clean builds produced byte-identical fat JARs with the application digest above, and `verifyFrozenApplication` compares the current fat JAR directly with the committed census identity. Two write-once census runs were also byte-identical.

The schema-version-3 census embeds exact application, manifest, policy, and TLA+ Tools identities. Every digest-pinned corpus file is reread into a bounded private snapshot, and only those verified bytes are mounted. The experiment requires an externally supplied census digest before strict parsing and mutation. Sandbox stdout remains byte-preserving until strict single-document JSON parsing, output paths reject case, Unicode-normalization, symlink, and hardlink aliases, and valid or failed census publication is write-once.

Profile v6 exposed a read-only mutant-workspace instrumentation defect and is retained only as failed audit evidence. Profile v7 corrected that defect and reproduced the candidate set, but its Gradle fat JAR retained build timestamps and could not be reconstructed byte-for-byte from source. Profile v8 keeps the exact v7 cohort, mutation semantics, and runtime Java code while making archive metadata deterministic. Its two experiment runs were semantically identical to each other and to v7 after excluding failed-baseline runtime diagnostics.

## Results

### Site-weighted outcomes

| Measure | Result |
| --- | ---: |
| Preregistered specifications | 15 |
| Completed baselines | 13 |
| Census GCD sites and terminal records | 51/51 |
| Conforming census sites | 45 |
| GCD terminal results from completed baselines | 42 |
| Completed GCD evaluations | 38 |
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

The control cohort produced four expanding sites from 14, while the enriched cohort produced five from 37. This experiment was not designed or powered to establish an uplift in expansion rate. It supports the feasibility of obtaining concrete candidates from a small stratified vertical slice; it does not establish that stratification alone improved the candidate rate.

## Candidate Review

The candidate interpretation is unchanged from the historical v2, v5, and v7 proxy reviews because v8 reproduced the same outcome set.

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

Raw v8 evidence is available in [the frozen census](gcd-stratified-census.json), [the JSON report](gcd-stratified.json), and [the readable report](gcd-stratified.md). Historical v7 evidence is documented in [its assessment](gcd-stratified-assessment-v7.md), and the failed v6 instrumentation remains documented separately.
