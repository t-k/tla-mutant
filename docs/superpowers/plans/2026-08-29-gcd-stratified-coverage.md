# GCD-Stratified Corpus Coverage Ledger

## Issue Draft

The first 10-specification experiment produced 16 GCD mutants but no state-expanding or state-incomparable survivor. Twelve resolved survivors were state-equal, so the cohort did not exercise enough action shapes where deleting a positive current-state guard can enable a new successor. Build a second, prospectively enriched 15-specification experiment without selecting entries or sites from mutation outcomes.

Expected behavior:

- preserve the first manifest and reports as immutable evidence;
- preregister five controls and ten GCD-enriched specifications at the same pinned public revision;
- census GCD source shape inside the existing sandbox before mutation execution;
- reject nonconforming preregistration without substituting entries;
- execute every eligible GCD site and report every terminal outcome;
- separate implementation completion from the two-specification, two-domain human-usefulness gate.

Risk is high because outcome-based selection would invalidate the experiment, host-side SANY parsing would violate the trust boundary, and denominator loss could overstate usefulness.

## Source Investigation Summary

The current corpus runner validates only corpus size, ID uniqueness, and domain count before running every entry. The manifest has no cohort or selection metadata, and the sandbox command always invokes the full mutation experiment. GCD accepts top-level state-level conjuncts under Model-only Next definitions but does not record whether an action-level successor sibling remains. Existing integration coverage proves a cross-family candidate exists but does not require a GCD-specific expanding witness.

The public-source investigation at commit `ed3cc4c35f7001d7cec08a22dcd698dae7570dc3` identified ten unexecuted configurations with direct positive guards separated from successor updates. Candidate selection used repository source and existing configurations only; no mutant or TLC outcome was observed.

## Code Evidence Log

| ID | Kind | Evidence | Consequence |
| --- | --- | --- | --- |
| E01 | evidence | `CorpusExperimentRunner.java:33-55` checks only 10–20 entries, unique IDs, and three domains. | Add profile-specific cohort validation. |
| E02 | evidence | `CorpusManifest.java:6-17` contains no profile, cohort, stratum, cluster, or rationale. | Add backward-compatible version-2 metadata. |
| E03 | evidence | `PodmanCommand.java:84-101` hard-codes the `experiment` command. | Add a closed task selector for census versus experiment. |
| E04 | evidence | `GcdMutator.java:13-34` emits top-level state conjunct mutations but no structural profile. | Derive census records from the same eligibility path. |
| E05 | evidence | `ExperimentIntegrationTest.java:16-37` checks only a cross-family witness. | Add a GCD-specific relation and witness assertion. |
| E06 | evidence | `MutatorIntegrationTest.java:19-89` covers eligibility and SANY validity but not successor-sibling shape. | Add positive and negative census fixtures. |
| E07 | evidence | `docs/experiments/vertical-slice-assessment.md` reports 12 state-equal survivors and zero gap candidates. | Preserve as control evidence; do not overwrite. |
| E08 | inference | Selecting specifications after a mutant relation is observed conditions on the dependent variable. | Freeze census and manifest before mutation output exists. |
| E09 | assumption | At least some preregistered enriched specifications will pass the current conservative GCD eligibility. | Census conformance verifies this without observing outcomes. |

## Specification Delta

- S01: Schema-version-1 manifests and original reports remain reproducible.
- S02: Schema-version-2 profiles `gcd-stratified-v1` through `gcd-stratified-v7` contain exactly five controls and ten enriched entries; v2 is the outcome-free cohort reselection, v3 is the first review-driven instrumentation amendment, v4 corrects positive compound classification, v5 separates nested shared-value syntax from direct shared-definition mutation, v6 binds every mounted input and reports every site terminal, and v7 preserves mutable mutant workspaces when the mounted corpus snapshot is read-only. Every instrumentation profile retains the cohort, and failed instrumentation runs remain versioned audit evidence.
- S03: Selection metadata is finite, bounded, allowlisted, and immutable for the run.
- S04: Public census parsing occurs only inside rootless Podman.
- S05: Census output contains structural attributes only and cannot contain mutation outcomes.
- S06: Every enriched entry has at least one positive current-state GCD conjunct with an action-level successor sibling.
- S07: A family cluster contributes at most 25% of all census GCD sites.
- S08: The mutation phase starts only after census conformance and census artifact persistence.
- S09: Every eligible GCD site appears exactly once as evaluated, invalid, or unresolved.
- S10: Reports expose cohort-, stratum-, cluster-, specification-, and site-weighted denominators.
- S11: Candidate witnesses and unresolved reasons appear in both JSON and Markdown.
- S12: The usefulness gate requires two specifications, two domains, two clusters, and human acceptance of at least one candidate.

## Invariants

- I01: Mutation outcome, relation, and witness never influence entry or site selection.
- I02: No public TLA+ byte is parsed or executed on the host.
- I03: A failed preregistered entry is never silently replaced or removed from a denominator.
- I04: The census and experiment use the same manifest revision, source digests, tools, image, and policy.
- I05: The census invokes the same GCD eligibility implementation as mutation generation.
- I06: A census result cannot be mistaken for proof of reachable-state expansion.
- I07: Existing configured State Invariants are not removed merely to manufacture survivors.
- I08: Kills remain unattributed until Type Invariant reruns exist.
- I09: One large or related specification family cannot silently dominate the reported rate.
- I10: Sandbox success, failure, timeout, output truncation, interruption, and cancellation leave no run-labeled resource.
- I11: Nonliteral configured transition actions use the same root-module GCD eligibility as literal `Next` without enabling imported-source patching.

## Partition Axes and Constraints

| Axis | Values |
| --- | --- |
| Manifest version | 1, 2 |
| Cohort | control, gcd-enriched |
| GCD stratum | control baseline, guarded admission, lifecycle/PC ordering, type-only gap prospect, collision/capacity |
| Static site shape | state guard plus successor, state guard without successor, nested, negative, primed, shared/property |
| Census terminal state | conforming, no eligible site, invalid output, timeout, sandbox failure |
| Baseline terminal state | completed, timeout, tool error, output/resource limit |
| GCD terminal state | invalid semantic, unresolved, killed unattributed, equal, expanding, incomparable, restricting |
| Aggregation | site weighted, specification weighted, cohort, stratum, cluster, domain |
| Repetition | first census, repeated census, first experiment, repeated experiment |

Constraints:

- Version 1 has no selection metadata and cannot use the stratified command.
- Every version-2 entry has exactly one cohort, stratum, family cluster, and rationale.
- Controls use `control-baseline`; enriched entries use one non-control stratum.
- A conforming enriched entry has at least one state-guard-plus-successor site.
- A mutation relation exists only for completed passing mutant runs with complete state output.
- A candidate has a non-null mutant-only witness.

## Coverage Obligations

| ID | Type | Required behavior or risk | Risk | Minimum checks | Status |
| --- | --- | --- | --- | ---: | --- |
| O01 | compatibility | Version-1 manifest and first experiment remain unchanged. | Critical | 2 | covered |
| O02 | validation | Version-2 profile enforces 5/10 cohorts, strata, clusters, domains, and bounded rationale. | High | 2 | covered |
| O03 | scientific invariant | Outcome fields cannot enter census or selection logic. | Critical | 2 | covered |
| O04 | semantic | Census and mutator share exact GCD eligibility and stable candidate IDs. | Critical | 2 | covered |
| O05 | structural | Enriched sites require current-state guard plus action-level successor sibling. | High | 2 | covered |
| O06 | security | Public census parses only inside the approved Podman boundary. | Critical | 2 | covered |
| O07 | failure mode | Census failure aborts mutation without entry substitution. | Critical | 2 | covered |
| O08 | reproducibility | Manifest, census, sources, toolchain, policy, and report identities are linked. | Critical | 2 | covered |
| O09 | denominator | Every eligible GCD site has exactly one terminal record. | Critical | 2 | covered |
| O10 | aggregation | Site- and specification-weighted results agree with raw records. | High | 2 | covered |
| O11 | reporting | JSON and Markdown expose strata, candidates, witnesses, and unresolved reasons safely. | High | 2 | covered |
| O12 | lifecycle | Census and experiment leave no container, volume, process, or writable host output. | Critical | 2 | covered |
| O13 | regression | A repository fixture proves a GCD-specific expanding/incomparable witness. | High | 2 | covered |
| O14 | corpus | All 15 preregistered entries and at least three domains are reported without replacement. | High | 2 | covered_with_two_incomplete_baselines |
| O15 | decision | The human-review gate is calculated from distinct specification, domain, and cluster identities. | High | 2 | automated_gate_covered_human_review_pending |

## Coverage Ledger

| Obligations | Primary artifact | Independent artifact | Overlap | Planned status | Debt or note |
| --- | --- | --- | --- | --- | --- |
| O01–O02 | manifest validator unit tests | old manifest validation and experiment regression | regression_overlap | covered | Both stratified profiles and the original version-1 manifest remain accepted. |
| O03–O05 | census classifier integration tests | census/mutant candidate-ID consistency test | layer_overlap | covered | Static capability remains a proxy, not proof. |
| O06–O08 | Podman command and runner tests | repeated public census with digest comparison | intentional_overlap | covered | Hostile census failure injection remains partial. |
| O09–O10 | pure aggregation tests | raw JSON consistency assertions | layer_overlap | covered | Nine sites from two incomplete baseline entries remain explicitly represented as unresolved terminal records. |
| O11 | report writer hostile-input tests | generated artifact inspection | layer_overlap | covered | JSON and Markdown contain the same nine witnesses and all unresolved categories. |
| O12 | sandbox containment test | post-run exact resource inspection | intentional_overlap | covered | No run-labeled container or volume remained after census or experiment. |
| O13 | local TLC fixture | final public result consistency | regression_overlap | covered | Public candidates are not required for test success. |
| O14–O15 | manifest/corpus run | assessment gate calculation | layer_overlap | automated_gate_covered | Six specifications, domains, and clusters meet the proxy gate; human usefulness remains pending. |

## Test Scenarios

- T01: The original version-1 manifest validates and produces its existing 10-entry identity.
- T02: A valid schema-version-2 stratified manifest has 5 controls, 10 enriched entries, 3+ domains, 3+ enriched strata, and bounded rationales.
- T03: Wrong cohort counts, missing selection fields, unknown values, duplicate IDs, unsafe clusters, and oversized rationales fail before fetch.
- T04: A direct state guard beside primed updates produces one conforming census site with the same ID as GCD generation.
- T05: A negative, primed, directly property-shared, conditional, or successor-only conjunct never becomes a conforming census guard. A positive compound predicate may conform only as a whole action-level top-level conjunct; its inner conjuncts remain nonconforming when they have no action-successor sibling. A shared value helper nested inside a larger positive guard is syntax-expanded but is not itself mutated.
- T06: An enriched entry with no conforming site aborts the mutation phase and is not replaced.
- T07: Census JSON contains no verdict, oracle, state relation, state count, or witness field.
- T08: Hostile census text cannot inject raw HTML, links, tables, ANSI, controls, bidi text, or unbounded output.
- T09: Census success and every bounded failure terminal state leave no labeled container or volume.
- T10: Repeated census runs produce the same candidate IDs and attributes.
- T10 evidence: v7 produced byte-identical schema-version-3 census files with 51 sites, 45 conforming sites, application identity `c3249a97341f5635ff34af529f4c6d06c489a173a065c5d307b86b3de73613eb`, and census SHA-256 `02e6609b70e59be83b21a71027d372ab3c5d99c3fa41afb6ee67af75cfb7cc2a`. The distinct v6 census remains versioned with its failed instrumentation run.
- T11: Every raw GCD result maps to one cohort, stratum, cluster, domain, and terminal bucket.
- T12: Specification weighting counts a specification once regardless of its GCD site count.
- T13: A cluster over 25% aborts before mutation and requires a new preregistration version.
- T14: A GCD fixture produces `STATE_EXPANDING` or `STATE_INCOMPARABLE` with a non-null witness.
- T15: The 15-entry public experiment records baseline and all mutation failures without replacement.
- T16: Repeated experiment runs preserve candidate IDs, relations, witnesses, and denominator totals.
- T17: The gate fails for two candidates in one domain or cluster and passes only for 2+ specifications, domains, and clusters.
- T18: Original and stratified reports coexist and README reproduction commands identify both.
- T19: A read-only snapshotted corpus input still produces writable mutant workspaces and evaluates all fixture mutants.

## Optional Verification Suggestions

- TLA+ Tools/SANY/TLC: `executed-by-request`. The user explicitly requested the bounded public experiment. It provides semantic census integration, baseline execution, complete-state comparison, and witness evidence. Artifacts are the census and experiment reports.
- Rootless Podman containment: `executed-by-request`. It is necessary because public TLA+ is untrusted. The command is the existing `sandboxTest` plus the new census containment scenario.
- Property-based testing: `suggested_pending_implementation`. Candidate-ID equality and aggregation conservation fit deterministic generated cases, but direct examples are sufficient for the first implementation unless simple Java generators can be added without another dependency.
- PICT/ACTS: `not_applicable`. The bounded axes have strong semantic constraints and are clearer as named direct scenarios.
- Alloy/Dafny: `not_applicable`. This change is experimental orchestration and AST classification rather than a compact relational or contract proof.
- Fuzzing: `deferred_heavy`. Parser fuzzing would improve hostile-input assurance but does not directly answer the GCD usefulness hypothesis.

## Questions for the User

None. The user approved the exact 5-control/10-enriched design and delegated execution.

## Coverage Debt

- D01: Static census cannot prove state expansion.
- D02: Type-only attribution remains deferred.
- D03: Mixed positive/negative use of one GCD helper remains a conservative-eligibility debt.
- D04: Deterministic malformed-output and interrupted-cleanup injection for census remains desirable before a general-purpose security claim.
- D05: Human usefulness cannot be automated; a proxy dossier is not acceptance.
