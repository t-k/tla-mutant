# Vertical-Slice Assessment

## Decision

The experiment does not yet support the hypothesis that state-expanding survivors lead to useful State Invariant improvements. The run produced no State-Invariant Gap Candidates, so there was no candidate for a human to accept or reject. The project should not implement the full design yet.

## Scope

- Public corpus: 10 specifications from `tlaplus/Examples`
- Pinned revision: `ed3cc4c35f7001d7cec08a22dcd698dae7570dc3`
- Mutation families: GCD, BRL, and UOI only
- Oracle: configured State Invariants; temporal properties were removed from the execution configuration
- State relation: complete canonical state values, not fingerprints alone
- Isolation: rootless Podman, no network, read-only root filesystem, dropped capabilities, fixed seccomp profile, digest-pinned image, bounded CPU, memory, PIDs, file size, inode count, output, state count, and wall clock

## Results

All 10 specifications completed. The run generated 21 single-site mutants: 16 GCD, 4 BRL, and 1 UOI. Seventeen mutants reached a resolved oracle result and four remained unresolved.

| Result | Count | Interpretation |
| --- | ---: | --- |
| Killed by a State Invariant | 5 | Existing invariants detected the changed reachable behavior. |
| State-equal survivor | 12 | The bounded reachable-state set did not change. These are not gap candidates. |
| State-expanding or state-incomparable survivor | 0 | No invariant-improvement candidate was produced. |
| Unresolved | 4 | These must not be counted as killed or survived. |

## What this establishes

The vertical path is technically viable: semantic role analysis, three mutation families, isolated TLC execution, complete-state capture, set-relation classification, and reproducible corpus reporting all ran end to end. It also caught a real containment bug: `RLIMIT_FSIZE` had been scaled as KiB even though Podman expects bytes. A regression test now fixes the byte-level contract.

The product-value hypothesis remains unproven. Most resolved survivors were state-equal, while the mutations that changed safety-relevant behavior were already killed. The current public corpus also exposed weak mutation-family coverage: only one UOI site was eligible.

## Next bounded experiment

Do not expand to the remaining design sections. Run another 10–20-specification experiment only after making one small, reviewable change:

1. Stratify the corpus for action shapes where removing a positive guard conjunct can add reachable states.
2. Expand UOI only for additional SANY-proven primed-assignment RHS shapes, retaining single-site and semantic-validity checks.
3. Split the four unresolved outcomes into deadlock, state-output bound, and TLC evaluation-error categories.
4. Require at least one witness-bearing state-expanding or state-incomparable survivor before scheduling human usefulness review.

If the next bounded run again produces no candidates, the evidence favors stopping or substantially revising the mutation strategy rather than implementing the full tool.
