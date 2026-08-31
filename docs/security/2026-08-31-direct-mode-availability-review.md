# Direct-Mode Availability Security Review

Date: 2026-08-31

Scope: bounded UOI semantic traversal, the direct full-property aggregate deadline, CLI limit enforcement, TLC child-process and stream lifecycle, result classification, frozen application identity, and regression coverage. The review excludes compromise by arbitrary code already executing as the same OS user. The accepted process-ownership boundary is the exact TLC child; the inherited-pipe shell descendant is a test-owned fixture used to verify bounded collector failure.

Review inputs: the original three availability findings, the accepted design and implementation plan, diff `804774a..2cb85d4`, focused RED/GREEN evidence, full unit and integration results, Podman containment results, and two clean reproducible application builds. The configured Security Specialist file was unavailable, so a clean-context security review agent applied the required review format and boundary assumptions.

## Must Fix

None.

## Should Fix

None.

## Notes

- `MutationSupport` uses iterative pre-order traversal with one active-path UID set. It checks the 1,000,000-node and 4,096-frame production limits before admitting another node. `UoiMutator` retains the existing candidate limit, exact candidate identities, and final ordering.
- `ExperimentRunner` starts a monotonic deadline at full-property entry. The public API rejects aggregate timeouts above one hour, and the CLI accepts only 1 through 3,600 seconds in full-property mode.
- Deadline tests distinguish expiry before baseline launch, during baseline TLC, after a passing baseline, and during mutant TLC. Baseline overrun fails the experiment. Post-baseline expiry starts no further mutant TLC process and conserves every admitted candidate as `TIMEOUT/NOT_EVALUATED/STATE_UNRESOLVED` with reversible mutation evidence. A mutant result that returns after aggregate expiry cannot be accepted as a kill or survivor.
- `TlcRunner` closes child stdin, bounds the exact child's graceful and forced termination, shares one bounded collector-drain window across stdout and stderr, closes streams, cancels unfinished collectors, bounds executor termination, restores interruption, and strictly deletes the exact run directory. Failed reap, collector, executor, or run-directory cleanup is `TOOL_ERROR/NOT_EVALUATED`; a normally reaped TLC timeout remains `TIMEOUT/NOT_EVALUATED`.
- Real TLC regression coverage includes pass, state-safety, named temporal, generic temporal, deadlock, and timeout outcomes. The unchanged rootless Podman containment suite verifies exact container and scratch-volume removal.
- The final reproducible application JAR was built twice from clean, uncached inputs. Both builds produced SHA-256 `a6dd8cab30f0b574c13bda03c1152eb1050fa5f4d9b16d1e95b4674c23914269` and size `3219730` bytes. `config/current-application.json` and the v11 closure assessment bind that identity.

## Verdict

GO
