# GCD-Stratified Public Corpus Design

## Objective

Run a second bounded public-specification experiment that tests GCD on action shapes that are prospectively capable of adding reachable states. Preserve the first 10-specification experiment as immutable evidence, preregister a new 15-specification cohort before observing any new mutation outcome, and keep general-corpus prevalence separate from enriched-cohort usefulness.

The experiment succeeds as an implementation when its preregistered cohort runs reproducibly and reports every outcome. It passes the product-value gate only if witness-bearing GCD survivors occur in at least two independent specifications from at least two domains and those witnesses are subsequently judged useful by a human.

## Scientific Constraint

The dependent variable must not determine cohort membership. A specification, action, or mutation site must not be selected because a preliminary mutation run showed `STATE_EXPANDING`, `STATE_INCOMPARABLE`, survival, or a desirable witness.

Selection may use only:

- source shape observed at the pinned repository revision;
- existing finite configuration data;
- static SANY-derived GCD census attributes;

The census is a state-expansion-capable proxy, not a proof that the reachable-state set will expand. Actual expansion depends on the transition semantics and the baseline reachable states. Reports and documentation must retain this distinction.

## Scope

### Included

- The existing pinned `tlaplus/Examples` revision `ed3cc4c35f7001d7cec08a22dcd698dae7570dc3`.
- Fifteen public configurations across at least three domains.
- Five previously executed controls and ten previously unexecuted GCD-enriched configurations.
- GCD structural census inside the existing rootless Podman trust boundary.
- All eligible GCD sites in every selected specification.
- Existing BRL and UOI execution for denominator transparency, without using their outcomes for GCD cohort selection.
- Separate census, experiment, and assessment artifacts.

### Excluded

- Expanding GCD syntax or semantic eligibility.
- Selecting or dropping specifications after observing mutation outcomes.
- Replacing a preregistered entry when census, baseline, or mutant execution fails.
- Claiming that enriched-cohort rates estimate general public-specification prevalence.
- Type-only kill attribution, liveness mutation, transition-graph comparison, and the remaining full-product design.

## Preregistered Cohort

The first experiment artifacts and `corpus/manifest.json` remain unchanged. The second experiment uses `corpus/gcd-stratified-manifest.json` with exactly 15 entries.

### Control cohort

| ID | Domain | Family cluster | Reason retained |
| --- | --- | --- | --- |
| `channel` | interface protocol | asynchronous-interface | Small state-equal GCD control. |
| `nbacc-ray97` | consensus | nonblocking-atomic-commit | State-equal distributed-protocol control. |
| `barrier` | synchronization | barrier | State-equal synchronization control. |
| `token-ring` | self-stabilization | token-ring | Larger state-equal control with three GCD sites. |
| `asynchronous-termination-detection` | termination detection | termination-detection | Contains both configured-invariant kills and a state-equal GCD result. |

### GCD-enriched cohort

| ID | Domain | Stratum | Repository files | Prospective rationale |
| --- | --- | --- | --- | --- |
| `lock` | mutual exclusion | guarded-admission-positive-control | `specifications/locks_auxiliary_vars/Lock.tla`, `Lock.cfg` | `l1` separates `lock = 1` from successor updates to `lock` and `pc`. |
| `peterson` | mutual exclusion | guarded-admission-positive-control | `specifications/locks_auxiliary_vars/Peterson.tla`, `Peterson.cfg` | `a3` separates an admission guard from the `pc = "cs"` successor. |
| `simple-allocator` | resource allocation | guarded-admission-positive-control | `specifications/allocator/SimpleAllocator.tla`, `SimpleAllocator.cfg` | `Allocate` separates availability from allocation updates. |
| `voucher-life-cycle` | lifecycle | lifecycle-or-pc-ordering | `specifications/byihive/VoucherLifeCycle.tla`, `VoucherLifeCycle.cfg` | Lifecycle guards are separated from voucher-state updates. |
| `tcommit` | transaction protocol | lifecycle-or-pc-ordering | `specifications/transaction_commit/TCommit.tla`, `TCommit.cfg` | `Prepare` separates the working-state guard from RM-state update. |
| `missionaries-and-cannibals` | planning puzzle | type-only-gap-prospect | `specifications/MissionariesAndCannibals/MissionariesAndCannibals.tla`, `MissionariesAndCannibals.cfg` | Boat-load enablement is separated from shore and boat updates; `Solution` is a goal predicate, not an invariant oracle. |
| `readers-writers` | concurrency | guarded-admission-positive-control | `specifications/ReadersWriters/MC.tla`, `ReadersWriters.tla`, `MC.cfg` | Reader/writer admission guards are separated from queue and ownership updates. |
| `teaching-concurrency-simple` | concurrent program | lifecycle-or-pc-ordering | `specifications/TeachingConcurrency/Simple.tla`, `Simple.cfg` | Program-counter guards are separated from variable and PC updates. |
| `single-lane-bridge` | traffic safety | collision-or-capacity | `specifications/SingleLaneBridge/MC.tla`, `SingleLaneBridge.tla`, `MC.cfg` | `MoveInsideBridge` separates membership and collision guards from location updates. |
| `bcast-folklore` | reliable broadcast | type-only-gap-prospect | `specifications/bcastFolklore/bcastFolklore.tla`, `bcastFolklore.cfg` | The crash-bound guard is separated from crash-state updates while the existing configuration declares only its existing state invariant. |

No family cluster may contribute more than 25% of all GCD sites. If the preregistered census violates this limit, the mutation phase must not start; a replacement cohort requires a new versioned preregistration rather than an in-place edit. The report must show both specification-weighted and site-weighted denominators so a large specification cannot silently dominate the result.

## Manifest Metadata

The new manifest uses schema version 2 and profile `gcd-stratified-v1`. Existing schema-version-1 manifests remain valid and reproducible.

Each version-2 entry adds one selection object:

```json
{
  "cohort": "control | gcd-enriched",
  "gcdStratum": "control-baseline | guarded-admission-positive-control | lifecycle-or-pc-ordering | type-only-gap-prospect | collision-or-capacity",
  "familyCluster": "stable non-empty identifier",
  "selectionRationale": "bounded preregistered source-shape explanation"
}
```

The validator requires exactly five controls and ten enriched entries, at least three domains overall, at least three enriched strata, unique IDs, bounded rationale text, and no duplicate family-cluster variant presented as an independent specification without explicit clustering.

## Sandboxed GCD Census

Public source remains untrusted and must never be parsed on the host. A new sandbox-internal `census` command performs SANY analysis with the existing closed resolver and emits bounded JSON. The outer `gcd-stratified-experiment` command fetches digest-pinned inputs, invokes one census container per entry, validates the census result, and only then starts the mutation experiment.

For each eligible GCD site the census records:

- stable mutation candidate ID;
- module and operator definition;
- bounded guard source text;
- semantic level of the removed conjunct;
- whether the same top-level action conjunction contains at least one action-level successor constraint;
- the count of remaining state-level and action-level conjuncts;
- the declared cohort, stratum, and family cluster joined by the outer runner from trusted manifest metadata.

An enriched entry is structurally conforming only when it has at least one GCD site whose removed conjunct is a positive current-state top-level conjunct and whose remaining siblings contain an action-level successor constraint. A control entry may have zero or more conforming sites.

The census must not execute mutants, compare state sets, or emit mutation verdicts. This keeps preregistration independent from the experiment outcome.

## Data Flow

```text
version-2 manifest
  -> manifest and cohort validation on trusted metadata
  -> digest-pinned fetch
  -> rootless Podman census of untrusted TLA+
  -> census/manifest conformance gate
  -> freeze census artifact
  -> rootless Podman baseline and all eligible mutants
  -> complete-state relation analysis
  -> cohort/stratum/family-cluster aggregation
  -> separate assessment and optional human-review dossier
```

The census artifact is written before the mutation report and content-addressed in the assessment. The experiment report references the manifest commit and census digest so the selection record cannot be silently replaced after outcomes are known.

## Outcome Reporting

The original artifacts remain at `docs/experiments/vertical-slice.*`. New artifacts are:

- `docs/experiments/gcd-stratified-census.json`;
- `docs/experiments/gcd-stratified.json`;
- `docs/experiments/gcd-stratified.md`;
- `docs/experiments/gcd-stratified-assessment.md`.

Reports include:

- entry, domain, cohort, stratum, and family cluster counts;
- conforming GCD site counts before execution;
- generated, evaluated, killed-unattributed, state-equal, state-expanding, state-incomparable, restricting, invalid, and unresolved GCD counts;
- specification-weighted and site-weighted denominators;
- every mutant-only witness for a GCD gap candidate;
- all incomplete entries and diagnostic reasons without replacement or denominator removal;
- separate BRL and UOI totals for transparency.

The Markdown report must expose unresolved reasons and candidate witnesses rather than relying on JSON alone.

## Failure Handling

- Invalid version-2 selection metadata rejects the manifest before any public source is parsed.
- A census parse, sandbox, output, or structural-conformance failure remains attached to the preregistered entry and aborts the mutation phase. The entry is not replaced.
- A baseline or mutant timeout, tool error, resource limit, invalid semantic mutation, or truncated output remains in the experiment denominator with its explicit status.
- A state-invariant kill remains `state-invariant-kill-unattributed` until Type Invariant attribution is implemented.
- Missing or malformed census digests reject report generation.
- Container, volume, process, network, file, output, and deadline boundaries remain identical to the approved first experiment.

## Testing Strategy

### Unit coverage

- Version-1 manifests remain valid without selection metadata.
- Version-2 manifests reject wrong cohort counts, missing rationale, unknown strata, duplicate IDs, and invalid family clusters.
- Census records reject missing candidate IDs, unbounded text, non-state-level guards, and missing successor constraints for enriched entries.
- Cohort aggregation produces both specification-weighted and site-weighted denominators.
- Markdown reports neutralize hostile census, diagnostic, rationale, and witness text.

### SANY integration coverage

Repository-owned fixtures cover:

- a positive state guard plus complete successor update;
- a guard whose remaining conjunction has no action-level successor;
- a nested, negative, primed, property-shared, or conditional conjunct;
- multiple GCD sites in one action;
- a GCD mutation that produces a non-null mutant-only witness and a GCD-specific expanding or incomparable relation.

### Sandbox coverage

- Public census runs only inside rootless Podman with no network and read-only inputs.
- Census success, invalid output, timeout, interruption, and cleanup leave no labeled container or volume.
- The exact approved image, tool JAR, seccomp profile, resource limits, and absolute process deadlines remain enforced.

### Experiment verification

- Run the census twice and require identical entry/site IDs and structural attributes.
- Run the 15-entry experiment twice and compare content-addressed candidate IDs, relations, and witnesses.
- Confirm that all enriched entries were fixed before the first mutation report.
- Confirm that every eligible GCD site appears exactly once in the result or has an explicit invalid/unresolved status.

## Decision Gate

The implementation is complete when the preregistered census and experiment are reproducible and all 15 entries are reported.

The product-value gate passes only when:

1. at least two independent specifications produce witness-bearing GCD `STATE_EXPANDING` or `STATE_INCOMPARABLE` survivors;
2. those specifications span at least two domains and two family clusters;
3. a human judges at least one witness to reveal a useful missing or improvable State Invariant rather than a known allowed state, configuration artifact, or type-only omission.

If no candidate is produced, stop expanding the implementation and record stronger negative evidence against the current GCD strategy. If candidates appear but fail human review, revise the mutation semantics or usefulness hypothesis before adding more mutator families.

## Residual Debt

- Static structure cannot prove reachable-state expansion.
- The enriched cohort cannot estimate general-corpus prevalence.
- Existing Type Invariant attribution remains deferred and prevents meaningful-kill labeling.
- GCD helper definitions with mixed positive and negative model uses require conservative exclusion before broadening eligibility.
- Full hostile-input census failure injection remains required before claiming general-purpose untrusted TLA+ execution.
