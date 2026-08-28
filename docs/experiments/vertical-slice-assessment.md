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

All 10 specifications completed. The run generated 17 single-site mutants: 16 GCD and 1 UOI. No comparison met the conservative BRL eligibility rule in this corpus. Fifteen mutants reached a resolved oracle result and two remained unresolved.

| Result | Count | Interpretation |
| --- | ---: | --- |
| Killed by a configured State Invariant | 3 | A configured invariant detected the mutant, but Type Invariant attribution was not run. |
| State-equal survivor | 12 | The bounded reachable-state set did not change. These are not gap candidates. |
| State-expanding or state-incomparable survivor | 0 | No invariant-improvement candidate was produced. |
| Unresolved | 2 | Both are TLC tool errors with recorded event codes; they are not counted as killed or survived. |

## What this establishes

The vertical path is technically viable: semantic role analysis, the three implemented mutation families, isolated TLC execution, complete-state capture, set-relation classification, and reproducible corpus reporting run end to end in the fixture suite. The public corpus exercised GCD and UOI, but no BRL site passed the conservative context filter. The work also caught a real containment bug: `RLIMIT_FSIZE` had been scaled as KiB even though Podman expects bytes. A regression test now fixes the byte-level contract.

The product-value hypothesis remains unproven. All 12 resolved survivors were state-equal. Three mutants were killed by a configured State Invariant, but this run did not distinguish Type Invariant-only kills from target-invariant kills. The current public corpus also exposed weak mutation-family coverage: only one UOI site and no BRL site were eligible.

## Next bounded experiment

Do not expand to the remaining design sections. Run another 10–20-specification experiment only after making one small, reviewable change:

1. Stratify the corpus for action shapes where removing a positive guard conjunct can add reachable states.
2. Expand UOI only for additional SANY-proven primed-assignment RHS shapes, retaining single-site and semantic-validity checks.
3. Investigate the two unresolved TLC tool errors using their recorded event codes before changing the denominator.
4. Require at least one witness-bearing state-expanding or state-incomparable survivor before scheduling human usefulness review.

If the next bounded run again produces no candidates, the evidence favors stopping or substantially revising the mutation strategy rather than implementing the full tool.
