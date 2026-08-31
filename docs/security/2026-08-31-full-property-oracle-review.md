# Full-Property Oracle Security Review

Date: 2026-08-31

Scope: the `full-property` direct and corpus execution paths, including input freezing, loaded-artifact identity, TLC process launch and classification, evidence validation, resource bounds, sandbox transport, and cleanup.

Final recommendation: GO. The clean-context Security Specialist review found no remaining Must Fix issue. The independent correctness review also approved the final implementation.

## Must Fix

None.

The initial review blocked release on four classes of issue. The final implementation resolves them as follows:

- The runner verifies the actual loaded SANY/TLC classes against the recorded TLA+ Tools JAR and records the application JAR separately.
- Full-property classification rejects malformed UTF-8, malformed or partial tool frames, truncated output, unknown positive-severity events, incomplete state output, and zero or multiple terminal outcomes.
- Direct and corpus inputs are copied into private bounded snapshots; model files and the real manifest bytes are identity-bound to the report.
- TLC run-directory cleanup is exact and observable. Cleanup failure converts an otherwise completed execution into `TOOL_ERROR/NOT_EVALUATED`.

The final correctness pass additionally made schema-v3 reports recompute their totals and validate each result's status/oracle/verdict/relation combination, digest-bound candidate identity, evidence patch, and terminal event-code coherence before corpus aggregation.

## Should Fix

- Refactor UOI semantic traversal to avoid materializing the entire application graph before candidate filtering. The candidate list itself is bounded, and public corpus execution remains within the Podman memory and wall-clock boundary, so this is direct-mode availability hardening.
- Add an aggregate direct-experiment deadline. Each TLC execution is capped at 600 seconds and candidate generation is capped at 1,024, but a direct run has no single outer deadline. Corpus execution already has one.
- Bound process-output collector waits after a failed forced reap. The current runner force-kills a timed-out child, but a host-level failure to close the child pipes could delay direct-mode cleanup. Corpus execution retains its outer sandbox deadline.

## Notes

- Model traversal is no-follow and bounded by 256 files, 256 directories, depth 16, 240 UTF-8 path bytes, 64 MiB per file, and 128 MiB total.
- Full-property module and config paths are allowlisted relative single-hard-link regular files below the frozen model root. Commands use argument vectors without a shell.
- State output, process output, event names, candidate counts, manifest/model closure, and report totals are bounded or validated before acceptance.
- Corpus execution continues to use rootless Podman with no network, a read-only root filesystem, dropped capabilities, resource limits, and exact labeled container/volume cleanup.
- Same-UID replacement or permission races are treated as an operating-system account compromise and remain outside the untrusted-corpus boundary.
