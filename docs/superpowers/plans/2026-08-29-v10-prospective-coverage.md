# v10 Prospective Evaluation Coverage Ledger

## Issue Draft

The v9 intervention proved one post-selected Request closed loop but could not estimate whether unseen specifications yield useful survivor-derived State Invariants. Build a two-phase prospective experiment that freezes eight previously unevaluated specifications and a review rubric before outcomes, reviews every eligible state-expanding or state-incomparable GCD survivor, and verifies up to three accepted predicates through a separately frozen v11 intervention.

The highest risks are scientific leakage, historical source reuse under a different ID, review cherry-picking, review-to-edit substitution, untrusted-input expansion, denominator loss through censoring, and unreproducible publication.

## Source Investigation Summary

- Historical GCD validation is profile-specific and accepts only v1-v9. v9 alone permits one derived edit and adopted target.
- `CorpusExperimentRunner` requires 10 to 20 entries, while the prospective cohort has exactly eight.
- Historical census conformance requires complete entries and at least one site, while v10 must retain zero-site and failed entries without replacement.
- Census identity currently binds manifest, policy, tools, and application but not historical registry or review protocol.
- Mutation results already preserve execution, oracle, relation, counts, witness, and attribution data required for dossier generation.
- Existing experiment JSON and Markdown writers replace outputs; prospective canonical artifacts must be write-once.
- v8 artifact digests are integration-tested. v9 artifacts require equivalent exact freeze assertions.

## Code Evidence Log

| Claim type | Claim | Evidence |
|---|---|---|
| Evidence | Historical profile, entry-count, cohort, and v9 intervention rules are hard-coded. | `src/main/java/dev/tlamutant/corpus/StratifiedManifestValidator.java:10-109` |
| Evidence | Fetch acceptance independently hard-codes schema/profile combinations. | `src/main/java/dev/tlamutant/corpus/CorpusFetcher.java:160-171` |
| Evidence | The existing corpus runner rejects fewer than ten entries. | `src/main/java/dev/tlamutant/experiment/CorpusExperimentRunner.java:37-52` |
| Evidence | Existing census conformance rejects incomplete or zero-site enriched entries. | `src/main/java/dev/tlamutant/census/CensusConformanceValidator.java:42-68` |
| Evidence | Bounded snapshots already reject symlinks and parse strict JSON from one byte snapshot. | `src/main/java/dev/tlamutant/security/BoundedFileSnapshot.java:29-82` |
| Evidence | Mutation results contain the fields needed for eligibility and dossier derivation. | `src/main/java/dev/tlamutant/experiment/MutationResult.java:3-17` |
| Evidence | Type, legacy, and adopted attribution is represented structurally. | `src/main/java/dev/tlamutant/experiment/KillAttribution.java:5-77` |
| Evidence | Existing canonical experiment writers overwrite rather than publish once. | `src/main/java/dev/tlamutant/report/AtomicJsonWriter.java:13-20`, `AtomicTextWriter.java:9-16` |
| Inference | Extending the historical stratified validator would create more regression risk than a dedicated prospective preflight and schema. | The approved design freezes v8/v9 semantics, while v10 requires incompatible count and censoring rules. |
| Assumption | The pinned upstream bytes and listed State Invariant classifications match the source-selection audit. | Must be verified when the manifest and registry are generated from the pinned checkout. |

## Specification Delta

- Add a dedicated prospective execution request that binds historical registry, protocol, manifest, policy, TLA+ Tools, application, and later census identities.
- Add exact eight-entry holdout validation independent of historical stratified profile rules.
- Treat zero-site, compatibility, baseline, resource, and tool failures as censored terminal observations without replacement.
- Derive a write-once dossier from every and only review-eligible v10 result.
- Validate a complete user-delegated review ledger against the frozen dossier and rubric.
- Select at most three accepted proposals deterministically and bind each v11 edit and adopted oracle to its review decision.
- Report discovery and closure endpoints separately with conserving denominators.
- Preserve every historical v8/v9 artifact byte.

## Invariants

- I01: No historical source identity can enter the v10 holdout under any entry ID or repository path alias.
- I02: No public source is fetched, parsed, or executed until all prospective receipt roles match their frozen bytes.
- I03: Cohort membership, rubric, limits, censoring, and endpoints do not change after v10 outcomes exist.
- I04: Every eligible candidate has exactly one terminal review decision, and no ineligible candidate has one.
- I05: `ACCEPT` implies one exact predicate and seven passing criteria with bounded evidence.
- I06: Every v11 adopted oracle and edit traces to the same selected accepted entry and candidate.
- I07: Tool, resource, baseline, and attribution failures never count as survivors, accepts, or closures.
- I08: Review and intervention totals satisfy the preregistered conservation equations.
- I09: All canonical prospective artifacts are write-once and content-addressed by their downstream consumers.
- I10: All public-source parsing and execution remains inside the existing rootless Podman boundary.
- I11: v8 and v9 artifacts remain byte-identical.

## Partition Axes and Constraints

| Axis | Partitions |
|---|---|
| Historical identity relation | unseen; same path; same bytes at different path; renamed entry; wrong revision; omitted registry history |
| Risk class | six standard; two high-risk no-replacement |
| Census terminal state | completed with sites; completed with zero sites; compatibility failure; sandbox failure |
| Baseline state | completed; tool error; timeout; resource limit; invalid output |
| Mutant oracle | pass; type kill; legacy kill; adopted kill; unresolved |
| State relation | equal; expanding; incomparable; restricting; unresolved |
| Witness | present; absent; malformed; oversized |
| Review decision | accept; allowed state; redundant; non-inductive; configuration fix; domain owner; strengthening |
| Review coverage | exact key set; missing; duplicate; extra; changed rubric |
| Accepted criteria | seven pass; one fail; missing evidence; missing predicate; unsafe text |
| Intervention selection | zero accepts; one to three accepts; more than three accepts; cross-entry substitution |
| Publication | new path; existing path; input/output alias; interrupted write |
| Reproduction | identical bytes; diagnostic-only differences; semantic difference |

Constraints:

- Review decisions exist only for candidates that meet all five eligibility conditions.
- v11 does not exist when accepted count is zero.
- Selection cap applies only after all eligible candidates receive review decisions.
- High-risk failure remains represented and cannot trigger replacement.
- When no legacy target exists, the legacy oracle equals type-only and its redundant execution is omitted.

## Coverage Obligations

| ID | Type | Required behavior | Risk | Minimum independent checks | Status |
|---|---|---|---|---:|---|
| P01 | validity | Exact holdout sources are absent from all historical execution inputs. | Critical | 2 | planned |
| P02 | validity | Cohort, rubric, limits, censoring, and endpoints are receipt-bound before outcomes. | Critical | 2 | planned |
| P03 | integrity | Outcome-free census is write-once and retains all eight entries. | Critical | 2 | planned |
| P04 | security | Any receipt or census mismatch fails before fetch, SANY, or Podman. | Critical | 3 | planned |
| P05 | review | Dossier eligibility and review ledger form an exact bijection. | Critical | 2 | planned |
| P06 | review | `ACCEPT` requires exact predicate, seven criteria, and bounded safe evidence. | Critical | 2 | planned |
| P07 | traceability | Accepted decisions bind exactly to v11 entry, candidate, operator, predicate bytes, and edit. | Critical | 2 | planned |
| P08 | behavior | Derived baseline accepts original behavior under the frozen finite model. | Critical | 2 | planned |
| P09 | attribution | Source survivor passes type/legacy and is killed by adopted oracle. | Critical | 2 | planned |
| P10 | reporting | Discovery, review, and intervention denominators conserve all terminal outcomes. | Critical | 2 | planned |
| P11 | failure mode | Tool/resource/baseline errors remain censored and never become success evidence. | Critical | 2 | planned |
| P12 | reproducibility | JAR and censuses are byte reproducible; experiments are semantically reproducible. | Critical | 2 | planned |
| P13 | compatibility | All frozen v8/v9 artifacts and legacy behavior remain unchanged. | Critical | 2 | planned |
| P14 | security | Source parsing, edit application, and TLC execution stay in the sandbox trust boundary. | Critical | 2 | planned |
| P15 | lifecycle | Owned containers, volumes, Podman machine, and Gradle daemons are stopped. | Critical | 2 | planned |
| P16 | interpretation | Agent review and finite-model limitations are explicit in every assessment. | High | 2 | planned |

## Coverage Ledger

| Obligations | Primary artifact | Secondary artifact | Overlap | Current status | Debt or note |
|---|---|---|---|---|---|
| P01 | `ProspectiveHoldoutValidatorTest` | committed registry reconstruction audit | intentional_overlap | planned | Canonical historical serialization must be explicit. |
| P02-P04 | receipt/preflight unit tests | CLI preflight integration and sandbox non-start evidence | layer_overlap | planned | Git commit order remains an audited history property. |
| P05-P06 | dossier/review pure tests | committed dossier-ledger key audit | layer_overlap | planned | Review quality is rubric-bound but still agent judgment. |
| P07-P09 | intervention binding tests | real TLC fixture and v11 result | intentional_overlap | planned | Exact predicate bytes or digest must be decision-bound. |
| P10-P11 | assessment reducer matrix | final artifact assertions | layer_overlap | planned | Zero-site and failed entries remain in specification denominators. |
| P12 | reproducibility tests and comparator | double public runs | layer_overlap | planned | Only declared diagnostic seed/scratch fields may differ. |
| P13 | historical digest integration test | git diff against `b6c5483` | intentional_overlap | planned | v9 needs exact artifact coverage matching v8. |
| P14-P15 | sandbox containment tests | exact final resource/process audit | layer_overlap | planned | No broad process kills. |
| P16 | report writer tests | security/correctness review | layer_overlap | planned | External human acceptance remains unfulfilled. |

## Test Scenarios

- T001: The exact eight-entry v10 manifest and complete historical registry validate.
- T002: A historical repository path under a new entry ID is rejected.
- T003: Historical bytes under a different repository path are rejected.
- T004: A missing or duplicated historical registry record is rejected.
- T005: Wrong membership, risk class, oracle set, repository, revision, digest, or size is rejected.
- T006: Each receipt role independently rejects missing, stale, symlinked, aliased, malformed, duplicate-key, trailing, oversized, or digest-mismatched input.
- T007: A receipt failure occurs before malformed public source can reach SANY or create a labeled Podman resource.
- T008: Census retains completed-with-sites, completed-with-zero-sites, and failed entries without replacement.
- T009: Eligibility accepts only completed full-oracle passes with expanding/incomparable relation and witness.
- T010: Dossier output contains each eligible key exactly once and no ineligible key.
- T011: Review rejects missing, duplicate, extra, unknown, or changed-rubric decisions.
- T012: Each terminal decision validates; `ACCEPT` rejects any failed criterion, blank evidence, absent predicate, or unsafe text.
- T013: Zero accepted decisions authorize no v11 intervention.
- T014: One to three accepts are selected; four or more use deterministic entry/candidate ordering and report overflow.
- T015: Cross-entry, rejected-candidate, changed-operator, changed-predicate, non-root edit, or review-digest substitution is rejected.
- T016: A real fixture yields an eligible survivor, a kill, a state-equal survivor, and an excluded unresolved result.
- T017: The accepted fixture's baseline passes; its target mutant passes type/legacy with expansion and is adopted-target killed.
- T018: Every discovery, review, and intervention conservation equation holds for a table of all terminal outcomes.
- T019: Canonical prospective outputs reject replacement and all protected input/output aliases.
- T020: Semantic comparison accepts only declared diagnostic differences and rejects any outcome, count, witness, relation, verdict, or attribution difference.
- T021: Every frozen v8/v9 artifact matches its exact digest and has no git diff from `b6c5483`.
- T022: Sandbox success, failure, timeout, interruption, and edit paths remove exact labeled containers and volumes and preserve host source bytes.
- T023: Two clean JAR builds and two censuses are byte-identical; two experiments are semantically equal.
- T024: Git ancestry proves inputs/receipt precede census, census precedes mutation, review precedes v11, and earlier bound files remain unchanged.
- T025: Final lifecycle audit reports zero owned resources, a stopped owned Podman machine, and no running Gradle daemon.

## Optional Verification Suggestions

- TLA+/TLC with the existing pinned tool JAR: `blocked` until the prospective commands and fixtures exist. The state-transition shape directly fits model checking; execution is already authorized by the user's request. It will produce fixture evidence, v10/v11 censuses, mutation reports, and oracle attribution.
- Property-based testing: `not_applicable` for the first implementation. Receipt roles, review decisions, and terminal classifications are small closed matrices better served by explicit table tests that preserve readable failure attribution.
- PICT/ACTS: `not_applicable`. Constraints make several combinations impossible, and the critical cases are security boundaries requiring intentional overlap rather than pairwise sampling.
- Fuzzing: `deferred_heavy`. Strict JSON, bounded snapshots, and explicit hostile-input matrices cover the current experiment; a generic public ingestion fuzz campaign is outside this prospective slice.

## Questions for the User

All product decisions are resolved: the user approved eight fixed specifications, a v10 discovery/v11 intervention split, a three-intervention cap, and user-delegated agent review.

## Coverage Debt

- External human or domain-owner usefulness remains unmeasured.
- Git commit ordering is auditable but cannot be proven solely by content digests embedded in a self-contained JAR.
- The holdout is enriched and small; it cannot estimate prevalence across the full public corpus.
- Resource-censored entries reduce effective sample size and are not replaced.
- Generic fuzzing of prospective JSON and arbitrary multi-module derived edits is deferred.

## Executed Discovery Log

- Inspected current manifest, census, experiment, attribution, sandbox, and report code paths with `rg` and bounded source reads.
- Inspected the pinned upstream revision in a temporary read-only checkout without running SANY, TLC, census, or mutation on the eight candidates.
- Used read-only agent fan-out for impact mapping, unseen candidate selection, and test-gap analysis.
