# Delegated Usefulness Review of GCD-Derived State Invariant Proposals

## Decision

The user-delegated qualitative usefulness gate is **accepted** on the strength of `simple-allocator` alone. `NoRequestAllocationOverlap == \A c \in Clients : unsat[c] \cap alloc[c] = {}` is a precise, nonvacuous, inductive State Invariant that is not implied by the configured invariants and directly rejects the recorded mutant-only witness.

This decision means that the experiment produced at least one adoption-worthy model-quality improvement under an agent-conducted review delegated by the user. It does not mean that an upstream maintainer or external human domain owner has accepted the change, that every proposed invariant is useful, or that the full product scope is justified.

The canonical v8 JSON and Markdown reports retain their execution-time `pending` status. This document is the downstream delegated agent decision and does not rewrite the frozen experimental evidence. The original external-human gate remains unfulfilled.

## Review Boundary

- Pinned Examples revision: `ed3cc4c35f7001d7cec08a22dcd698dae7570dc3`
- Canonical report: [`gcd-stratified.json`](gcd-stratified.json)
- Canonical manifest: [`../../corpus/gcd-stratified-manifest.json`](../../corpus/gcd-stratified-manifest.json)
- Reviewed proposals: `asynchronous-termination-detection`, `simple-allocator`, `nbacc-ray97`, and `glowing-raccoon-clean`
- Source and configuration SHA-256 values were checked against the canonical manifest before review.

An `ACCEPT` decision requires all of the following:

1. An exact state predicate.
2. A mutant-only witness that falsifies the predicate.
3. Establishment by `Init` and preservation by every original action.
4. A nonvacuous reachable interpretation.
5. Information not already supplied by the configured State Invariants.
6. Alignment with the source specification's stated abstraction.
7. A genuinely missing assertion rather than an experiment-configuration omission.

## Candidate Decisions

| Proposal | Decision | Reason |
| --- | --- | --- |
| `simple-allocator`: `NoRequestAllocationOverlap` | `ACCEPT` | Exact, inductive, nonvacuous, nonredundant, and directly tied to the request/allocation lifecycle. |
| `asynchronous-termination-detection`: register `Safe` | `CONFIGURATION_FIX`, not adoption evidence | `Safe` already exists in the pinned module and is already registered by the pinned cfg. The experiment manifest omitted it. |
| `nbacc-ray97`: vote consistency and decision-implies-vote-sent | `NEEDS_DOMAIN_OWNER` | Both predicates are plausible and inductive, but the original proposal combines two requirements and the source does not name either as a safety goal. |
| `glowing-raccoon-clean`: `HotImpliesNoHybrid` | `NEEDS_STRENGTHENING_AND_DOMAIN_OWNER` | The predicate holds on the explored reachable states but is not 1-inductive by itself; its usefulness also depends on the simplified phase abstraction. |

## Accepted Proposal: `simple-allocator`

The pinned source describes `unsat` as each client's outstanding requests and `alloc` as the resources held by that client. [`Request`](https://github.com/tlaplus/Examples/blob/ed3cc4c35f7001d7cec08a22dcd698dae7570dc3/specifications/allocator/SimpleAllocator.tla#L39-L45) requires both sets to be empty before creating a new request, [`Allocate`](https://github.com/tlaplus/Examples/blob/ed3cc4c35f7001d7cec08a22dcd698dae7570dc3/specifications/allocator/SimpleAllocator.tla#L47-L52) moves a selected set from `unsat[c]` to `alloc[c]`, and [`Return`](https://github.com/tlaplus/Examples/blob/ed3cc4c35f7001d7cec08a22dcd698dae7570dc3/specifications/allocator/SimpleAllocator.tla#L54-L59) only removes allocated resources.

The proposed invariant is:

```tla
NoRequestAllocationOverlap ==
  \A c \in Clients : unsat[c] \cap alloc[c] = {}
```

It is inductive for the original specification:

- `Init` establishes empty `unsat` and `alloc` sets for every client.
- `Request` establishes a new outstanding set while allocation remains empty.
- `Allocate` changes a disjoint pair into `unsat[c] \ S` and `alloc[c] \cup S`; neither the removed set nor the previous allocation can intersect the new outstanding set.
- `Return` only shrinks `alloc[c]`.

It is nonvacuous: after requesting multiple resources and receiving a strict subset, a client has nonempty but disjoint `unsat[c]` and `alloc[c]`. It is also not implied by `TypeInvariant`, which only constrains the two maps to subsets of `Resources`, or by `ResourceMutex`, which only compares allocations belonging to distinct clients.

The canonical GCD witness for `Request` contains `unsat[c1] = {r1}` and `alloc[c1] = {r1}`. It satisfies the configured type and cross-client mutex invariants but falsifies `NoRequestAllocationOverlap`. The new predicate therefore captures a concrete lifecycle error that the current target set misses.

## Excluded Configuration Artifact: `asynchronous-termination-detection`

The pinned module already defines [`Safe == terminationDetected => terminated`](https://github.com/tlaplus/Examples/blob/ed3cc4c35f7001d7cec08a22dcd698dae7570dc3/specifications/ewd998/AsyncTerminationDetection.tla#L82-L96), includes it in `IndInv`, and registers it under `INVARIANTS` in the pinned cfg. The experiment manifest lists only `TypeOK` and reconstructs the sandbox cfg from that allowlist, so the experiment omitted an intended upstream check and rediscovered it.

Registering `Safe` in a future versioned manifest is worthwhile, but it is a corpus/oracle fidelity repair rather than evidence that GCD discovered a new specification improvement.

## Deferred Proposals

For `nbacc-ray97`, the witnesses support two exact predicates:

```tla
VoteConsistency ==
  \A p \in Proc :
    ~(<<p, "YES">> \in sent /\ <<p, "NO">> \in sent)

DecisionImpliesVoteSent ==
  \A p \in Proc :
    pc[p] \in {"ABORT", "COMMIT"} => \E m \in M : <<p, m>> \in sent
```

The original actions preserve both, and the recorded witnesses respectively contain conflicting votes from one process and an `ABORT` state with `sent = {}`. They remain `NEEDS_DOMAIN_OWNER` because the encoded paper model does not name these predicates as independent safety requirements and the initial proposal incorrectly treated them as one improvement.

For `glowing-raccoon-clean`, the witness `tee = "Hot"`, `hybrid = 1` violates `HotImpliesNoHybrid == tee = "Hot" => hybrid = 0` while satisfying the configured invariants. The predicate holds on the explored original reachable states, but it is not 1-inductive by itself: a state with `tee = "TooHot"` and `hybrid = 1` satisfies the predicate, and the original `cool` action reaches `tee' = "Hot"` without changing `hybrid`. A plausible inductive strengthening is `tee \in {"Hot", "TooHot"} => hybrid = 0`. The module also describes itself as a simplified PCR exercise with further refinements elsewhere, so both the strengthening and its value as a domain safety rule require an owner of that abstraction.

## Consequence

The delegated usefulness condition is satisfied: at least one state-expanding survivor yielded an adoption-worthy State Invariant improvement. External human or upstream-maintainer acceptance remains a separate gate. The justified next scope is still narrow: add Type-only attribution, investigate the four completed-entry tool errors, and correct the omitted `Safe` target only in a newly preregistered corpus version. The result does not establish an uplift caused by stratification and does not justify implementing the remaining full-product sections.
