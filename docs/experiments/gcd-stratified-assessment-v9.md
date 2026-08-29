# GCD Stratified v9 Assessment

## Decision

The v9 experiment confirms the preregistered primary hypothesis and the Request closed loop for one user-delegated reviewed invariant. The complete set of preregistered v9 success criteria is only partially met because the expected SimpleAllocator type-only kill was not observed. `NoRequestAllocationOverlap` nevertheless has adoption value for SimpleAllocator because it rejects the exact unsafe overlap exposed by the v8 Request survivor while the unmutated model still completes with the same 400 reachable states.

This is confirmatory, post-selection evidence. The v8 witness was used to choose the invariant, so this result does not measure how often the workflow will discover useful invariants on unseen specifications.

## Reproducibility

The census was generated twice and was byte-identical. The two mutation runs used the frozen census SHA-256 `cd714aecfec926c3250922ec52e9dc6caef944c1300ff2cafaf9911c2f7a66cb`. Their diagnostic text contains run-specific seeds and scratch paths, but the projected specification statuses, mutant outcomes, state counts, state relations, verdicts, attributions, witnesses, and aggregate summaries were identical.

## SimpleAllocator Results

| Candidate | Full oracle | Type-only oracle | Legacy oracle | Result |
|---|---|---|---|---|
| Request `gcd-94424c8f20df1b20ffb7` | killed after 53 states | passed, 1024 states | passed, 1024 states, expanding | `adopted-target-invariant-kill` |
| Allocate `gcd-802804f2cb0816da2fa1` | killed after 34 states | passed, 729 states | killed after 34 states | `legacy-target-invariant-kill` |
| Return `gcd-82ee15acd48a3a94db2e` | passed, 400 states | not run | not run | `state-equal` |

The Request legacy witness contains a state where client `c1` has resource `r1` in both `unsat[c1]` and `alloc[c1]`. The adopted predicate prohibits exactly this intersection. The full baseline completes at 400 states, matching v8, so the new check accepts every reachable state of the original model.

The preregistered secondary expectation that Allocate would be a SimpleAllocator type-only kill was not met. Its type-only run passes, while the legacy `ResourceMutex` oracle kills it. A type-invariant GCD kill was observed elsewhere in the corpus for asynchronous termination detection. That entry has only a type oracle, so it confirms direct type-only classification but does not exercise the type-only attribution rerun branch or satisfy the narrower SimpleAllocator expectation.

## Corpus Totals

All 51 frozen GCD sites have one terminal record: one adopted-target kill, four legacy-target kills, one type-invariant kill, two unresolved kill attributions, 22 state-equal results, eight state-expanding results, and 13 unresolved results. These buckets sum to 51.

The existing baseline/tool-limit failures remain visible: five specifications have at least one unresolved GCD result, including baseline failures for `cf1s-folklore` and `bcast-folklore`. They do not affect the completed SimpleAllocator intervention, and they were not reclassified as kills.

## Conclusion and Next Evidence

The useful claim now supported is narrow but real: a state-expanding survivor led to a user-delegated reviewed invariant, and the independently rerun oracle attribution proves that this invariant kills the survivor while type and prior target invariants do not. This establishes the Request closed loop, while the overall preregistered v9 success criteria remain partially achieved.

The next meaningful experiment is prospective validation. Freeze several previously unseen specifications and review rules before inspecting survivors, then measure how often reviewed survivor-derived predicates are accepted and whether they catch seeded or historical defects without rejecting valid behavior. Tool-limit failures should be investigated separately so they do not blur that estimate.
