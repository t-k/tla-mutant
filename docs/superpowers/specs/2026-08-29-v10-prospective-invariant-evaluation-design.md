# v10 Prospective Invariant Evaluation Design

## Goal

Evaluate whether GCD state-expanding survivors from previously unevaluated public specifications can produce adoption-worthy State Invariants. The cohort, review rubric, eligibility rule, resource policy, and success criteria are frozen before any candidate census or mutation outcome is observed.

The experiment uses a user-delegated agent review. An `ACCEPT` decision is evidence that a rigorously reviewed proposal is useful to the project under the frozen rubric. It is not external-human, domain-owner, or upstream-maintainer acceptance.

## Scientific Boundary

The evaluation is split into two independently frozen phases:

1. v10 discovery evaluates the holdout cohort and reviews every eligible survivor.
2. v11 intervention checks whether accepted predicates preserve their original baselines and kill the corresponding survivor specifically through the adopted oracle.

The v10 discovery rate is never recomputed after the v11 intervention. The v11 result is a closure check for accepted discoveries, not a new discovery observation.

The following outcomes never permit cohort replacement:

- zero GCD census sites;
- unsupported source structure;
- SANY or TLC failure;
- baseline failure;
- timeout, state limit, memory limit, or other resource limit;
- unresolved state relation;
- no review-eligible survivor.

Existing v8 and v9 manifests, censuses, reports, assessments, and preregistrations remain byte-for-byte frozen.

## Prospective Holdout Cohort

The source revision remains `tlaplus/Examples@ed3cc4c35f7001d7cec08a22dcd698dae7570dc3`. Selection used only repository source, finite configuration data, declared State Invariants, dependency compatibility, and static action shape. No TLC, census, or mutation command was run for these candidates during selection.

The historical source registry includes every root, configuration, and dependency file used by all earlier manifests. The sorted set of 19 historical root paths has SHA-256 `ed6de70903c046aa736d2f3a89638c0e623354ef17ff41e2148a36ecb1dd57a3`. Holdout validation compares immutable repository paths and source identities, not only entry IDs, so renaming an old specification cannot make it unseen.

### Standard-risk entries

| ID | Domain | Root and configuration | State oracle | Prospective rationale |
|---|---|---|---|---|
| `readers-writers` | concurrency control | `specifications/ReadersWriters/ReadersWriters.tla`, `APReadersWriters.cfg` | legacy target `Safety` | Admission guards are separated from reader/writer state updates and directly exercise mutual exclusion. |
| `coffee-can` | state-machine puzzle | `specifications/CoffeeCan/CoffeeCan.tla`, `APCoffeeCan.cfg` | type `TypeInvariant` | Bean-count and color-count guards precede arithmetic updates, providing a type-only calibration and possible domain-invariant gaps. |
| `ewd840` | termination detection | `specifications/ewd840/EWD840.tla`, `EWD840.cfg`, `SyncTerminationDetection.tla` | type `TypeOK`; legacy targets `TerminationDetection`, `Inv` | Token position, color, and activity guards can be attributed against both type and protocol invariants. |
| `nbacg-guer01` | atomic commit | `specifications/nbacg_guer01/nbacg_guer01.tla`, `nbacg_guer01.cfg` | type `TypeOK` | The configuration exposes only a type oracle, while crash, vote, receive-count, and commit guards can reveal missing state assertions. |
| `c1cs` | crash consensus | `specifications/c1cs/c1cs.tla`, `c1cs.cfg` | type `TypeOK`; legacy targets `Validity`, `Agreement`, `WeakAgreement`, `IndStrengthens` | Multiple admission, quorum, and decision-message guards allow type and existing-target attribution. |
| `bosco` | Byzantine consensus | `specifications/bosco/bosco.tla`, `bosco.cfg` | type `TypeOK`; legacy targets `Lemma3_0`, `Lemma3_1`, `Lemma4_0`, `Lemma4_1` | Program-counter, quorum, and preference guards exercise an independent agreement-family model. |

### High-risk, no-replacement entries

| ID | Domain | Root and configuration | State oracle | Declared risk |
|---|---|---|---|---|
| `slush-small` | probabilistic consensus abstraction | `specifications/SlushProtocol/Slush.tla`, `SlushSmall.cfg` | type `TypeInvariant` | Message sets and seven process-control variables may exceed resource bounds. Failure remains censored in place. |
| `buffered-random-access-file` | storage model | `specifications/braf/BufferedRandomAccessFile.tla`, `BufferedRandomAccessFile.cfg` | type `TypeOK`; legacy targets `Inv1`, `Inv3`, `Inv4`, `Inv5` | The file contains multiple modules and many branches. Outcome-free SANY incompatibility or resource failure remains censored in place. |

The manifest must contain exactly these eight entries, six standard-risk and two high-risk entries, at the pinned revision. A new versioned preregistration is required for any membership change.

## Frozen Artifacts and Identity Chain

The discovery phase introduces these machine-readable inputs:

- `corpus/historical-source-registry-v10.json`: identities from every historical corpus manifest;
- `config/prospective-protocol-v10.json`: membership, risk class, eligibility rule, review rubric, resource policy, stopping rules, and endpoints;
- `corpus/gcd-prospective-manifest-v10.json`: exact source bytes and State Invariant classification;
- `docs/experiments/gcd-prospective-receipt-v10.json`: SHA-256 and size of the registry, protocol, manifest, policy, TLA+ Tools JAR, and application JAR.

The receipt is content-addressed rather than self-referential. Git history provides the audit evidence that the receipt and outcome-free inputs were committed before the census and that the census was committed before mutation. The census and experiment commands verify all receipt-bound bytes before public source parsing or mutation. The report records the receipt digest.

The v10 phase writes these new artifacts only:

- `docs/experiments/gcd-prospective-census-v10.json`;
- `docs/experiments/gcd-prospective-v10.json`;
- `docs/experiments/gcd-prospective-v10.md`;
- `docs/experiments/gcd-prospective-review-dossier-v10.json`;
- `docs/experiments/gcd-prospective-review-v10.json`;
- `docs/experiments/gcd-prospective-assessment-v10.md`.

Every canonical census, experiment, dossier, and review output uses write-once publication. Repeated verification runs use distinct paths under `build/reports`.

## Discovery Eligibility

A v10 mutant is review-eligible only when all of the following are true:

1. The corpus entry baseline completed successfully.
2. The mutant execution completed successfully.
3. The full configured State Invariant oracle passed.
4. The complete-state comparison is `STATE_EXPANDING` or `STATE_INCOMPARABLE`.
5. A bounded mutant-only witness exists.

Tool errors, killed mutants, invalid mutants, incomplete comparisons, state-equal mutants, and state-restricting mutants remain in their terminal report buckets but are not review-eligible.

The review dossier is derived mechanically from the frozen v10 result and includes every eligible candidate exactly once. It records the entry and candidate identities, mutation site, baseline and mutant state counts, configured oracle sets, witness, and hashes of the source snapshot and review protocol.

## User-Delegated Review Rubric

Every dossier candidate receives exactly one terminal decision:

- `ACCEPT`;
- `REJECT_ALLOWED_STATE`;
- `REJECT_REDUNDANT`;
- `REJECT_NON_INDUCTIVE`;
- `CONFIGURATION_FIX`;
- `NEEDS_DOMAIN_OWNER`;
- `NEEDS_STRENGTHENING`.

An `ACCEPT` decision requires all seven criteria:

1. The proposal provides one exact state predicate.
2. The recorded mutant-only witness falsifies the predicate.
3. The predicate holds in every explored state of the original baseline.
4. The original actions preserve the predicate under the frozen finite configuration.
5. The predicate is nonvacuous on reachable baseline states.
6. The predicate adds information beyond the configured type and legacy target invariants.
7. The predicate matches the source specification's stated abstraction and is not merely an omitted existing configuration invariant.

The review ledger records a criterion-by-criterion result and bounded evidence for every candidate. Missing, duplicated, or extra decisions; an `ACCEPT` with any failed criterion; a changed rubric digest; or an unrecognized candidate rejects the complete ledger.

## v11 Intervention

If v10 produces no `ACCEPT`, the experiment stops with a negative discovery result and does not create a v11 intervention.

If v10 produces accepted proposals, v11 verifies at most three. The deterministic order is entry ID followed by candidate ID. Accepted proposals beyond the first three remain reported as accepted but unverified due to the preregistered intervention cap.

The v11 inputs bind:

- the frozen v10 receipt, census, result, dossier, and review ledger;
- each selected `ACCEPT` decision;
- the exact adopted operator name;
- the base and derived source digests and sizes;
- the insertion-only edit defining the predicate;
- the unchanged policy, TLA+ Tools JAR, and reproducible application JAR.

Each intervention entry is evaluated independently. One entry's edit or accepted decision cannot justify another entry's adopted oracle.

An intervention closes successfully only when:

1. The derived baseline completes under the full oracle.
2. The corresponding original GCD mutant is killed by the full oracle.
3. The same mutant passes the type-only oracle.
4. The same mutant passes the legacy oracle and remains expanding or incomparable. When no legacy target exists, the legacy oracle equals the type-only oracle and the redundant execution is omitted.
5. The result is classified as `adopted-target-invariant-kill`.

A predicate rejected by the derived baseline becomes `REJECTED_BY_BASELINE`. A tool or attribution error remains unresolved and does not count as closure success.

## Endpoints and Interpretation

The primary discovery endpoint is whether at least one previously unseen specification produces an `ACCEPT` decision. Reported denominators are:

- frozen specifications;
- completed baselines;
- generated and evaluated GCD mutants;
- state-expanding or state-incomparable survivors;
- review-eligible candidates;
- reviewed candidates;
- each review decision category;
- selected and unselected accepted proposals;
- intervention attempts and successful closures.

The following conservation rules are mandatory:

```text
reviewed = eligible
reviewed = sum(all terminal review decisions)
accepted = selected-for-intervention + accepted-unverified-by-cap
attempted interventions = closure success + rejected by baseline + unresolved intervention
```

Discovery success means at least one `ACCEPT`. Closure success means at least one selected accepted predicate meets all five intervention conditions. The report states both independently. A failed endpoint is retained as evidence rather than softened by changing membership, limits, or review rules.

Because the review is user-delegated agent review, even a successful result supports only this claim: under a preregistered rubric, the workflow found a technically adoption-worthy predicate in a previously unevaluated specification and verified its finite-model effect. It does not establish external human usefulness, upstream acceptance, or general population prevalence.

## Implementation Boundaries

Dedicated prospective commands are added instead of weakening historical profile rules:

- `prospective-census` validates the v10 manifest, historical registry, protocol, and receipt before reusing the sandbox census runner.
- `prospective-experiment` validates the frozen census and receipt before reusing the mutation runner and attribution machinery.
- `prospective-review-dossier` produces the complete write-once eligible-candidate dossier.
- `validate-prospective-review` validates a completed review ledger against the dossier and rubric.
- `prospective-intervention-census` and `prospective-intervention-experiment` validate v11 traceability before reusing derived-source and oracle-attribution infrastructure.

New prospective records and validators remain separate from the historical `CorpusManifest` compatibility path where practical. Shared digest, source snapshot, census, mutation, attribution, and sandbox components are reused without changing v8/v9 semantics.

## Failure and Security Handling

- Registry and protocol paths are bounded regular files read without following symlinks.
- All receipt digests and sizes are validated before public source parsing or Podman startup.
- Historical overlap checks compare repository URL, commit, repository path, and content digest. Entry ID differences do not bypass them.
- Review text, predicates, witnesses, rationales, and diagnostics are bounded and reject NUL, control, and bidirectional formatting characters.
- Derived edits retain the v9 path, UTF-8, insertion, digest, size, and sandbox-only constraints.
- Public TLA+ source is parsed only inside rootless Podman with no network, a read-only root filesystem, dropped capabilities, fixed resource and output limits, and exact labeled-resource cleanup.
- No shell evaluates source text, review text, predicates, or edit metadata.
- Security review records `Must Fix / Should Fix / Notes`; any Must Fix blocks the first public census or intervention.

## Test Strategy

Development follows red-green-refactor TDD without mocks.

### Unit tests

- Accept the exact eight-entry holdout and reject historical path, digest, renamed-entry, membership, risk-class, oracle, or protocol mismatches.
- Reject missing, stale, aliased, malformed, or oversized receipt inputs before execution.
- Derive exactly the eligible candidates and reject missing, duplicate, extra, or invalid review decisions.
- Enforce all seven `ACCEPT` criteria and deterministic three-item intervention selection.
- Bind accepted decisions to the correct entry, candidate, operator, source edit, and review-ledger digest.
- Conserve discovery, review, and intervention denominators.
- Preserve exact v8/v9 artifact digests and compatibility.

### Integration tests

- Real SANY/TLC fixtures produce one eligible expanding survivor, one kill, one state-equal survivor, and one tool/unresolved exclusion.
- A completed review ledger produces an intervention for the accepted fixture only.
- The derived baseline passes, type and legacy oracles pass the target mutant, and the adopted oracle kills it.
- Semantic comparison accepts run-specific seed and scratch-path differences but rejects verdict, relation, witness, count, or attribution differences.

### Sandbox and end-to-end verification

- Malformed receipt or review bindings fail before Podman starts.
- Successful, failed, timed-out, and interrupted runs leave no labeled container or volume.
- Build the application twice from clean uncached inputs and require identical bytes.
- Run the v10 census twice and require byte-identical output.
- Commit the receipt and census before running mutation.
- Run v10 mutation twice and require semantic outcome equality.
- Generate and validate the complete review ledger.
- If accepted candidates exist, freeze and run v11 twice with the same reproducibility rules.
- Stop the Podman machine and Gradle daemons and inspect exact process ownership at completion.

## Coverage Obligations

| ID | Obligation | Independent evidence |
|---|---|---|
| P01 | Every v10 source identity is absent from historical execution inputs. | registry validator test plus committed registry audit |
| P02 | Cohort, rubric, limits, censoring, and endpoints are fixed before outcomes. | receipt test plus git ancestry audit |
| P03 | Census remains outcome-free and write-once. | sandbox census test plus double-run comparison |
| P04 | Mutation cannot start with a missing or mismatched receipt or census. | CLI integration rejection tests |
| P05 | Every eligible candidate receives one terminal review decision. | review validator matrix plus committed ledger audit |
| P06 | Accepted decisions trace exactly to v11 edits and adopted oracles. | binding tests plus v11 artifact audit |
| P07 | Adopted predicates do not reject original reachable behavior. | derived baseline test plus v11 run |
| P08 | Adopted predicates kill their source survivors specifically. | type/legacy/adopted attribution fixture plus v11 run |
| P09 | Review and intervention denominators conserve all terminal outcomes. | pure reducer tests plus final artifact assertions |
| P10 | Tool and resource failures cannot become kills or accepts. | failure matrix plus final report audit |
| P11 | Application, census, and semantic experiment results are reproducible. | double build, census, and experiment runs |
| P12 | Historical v8/v9 evidence remains unchanged. | exact digest integration tests and git diff audit |
| P13 | All owned processes and sandbox resources are cleaned up. | sandbox cleanup tests and final lifecycle audit |

## Non-Goals

- Claiming external-human or domain-owner usefulness.
- Estimating prevalence across all TLA+ specifications.
- Replacing failed holdout entries.
- Changing GCD mutation semantics after seeing v10 outcomes.
- Investigating unrelated historical tool failures during the prospective run.
- Implementing BRL, UOI, liveness, transition-graph, or full product-scope improvements.
