# Vendored TLA+ Tools Pin Security Review

- Date: 2026-09-02
- Reviewed commits: `5108dfd`, `c5ced61`
- Decision: GO
- Scope: vendored TLA+ Tools provenance, local provisioning, source and destination path handling, file permissions, atomic replacement, failure cleanup, regression coverage, historical receipt compatibility, and publication gates.

## Must Fix

None.

## Should Fix

None.

The initial review requested three changes. All are resolved:

- POSIX permission setup is fail-closed. Provisioning requires an owner-readable and owner-writable temporary file and verifies the same `0600` mode after atomic replacement.
- Destination handling rejects symbolic-link components and paths outside the checkout before writing. It repeats the symbolic-link check after creating the destination directory and verifies the destination parent's real path.
- Dynamic integration coverage now exercises offline provisioning, the compatibility alias, exact digest and permissions, truncation, same-size byte changes, missing input, absolute and escaping paths, source and destination symlinks, preservation of an existing valid cache, and cleanup after an atomic move failure.

A follow-up review requested bounded cleanup for timed-out nested Gradle processes, an explicit concurrent-writer boundary, and post-temporary-file failure coverage. The test harness now terminates only the process it started and its recorded descendants, escalating from graceful to forced termination after a bounded wait. The provenance document defines a same-user concurrent checkout writer as outside the supported threat model. The atomic-move failure test confirms that no `.tla2tools-*.part` file remains.

## Notes

- `config/tla-tools.lock.json`, the tracked JAR, and the provisioned runtime copy bind 4,487,757 bytes with SHA-256 `eabd140a70f49eb9305a3bd3f3df944eddf87e5a90d329789085f8953a80533a` and manifest revision `9787e65714c37d94eebab40774bff401bd9f616d`.
- Provisioning contains no network client or fallback. `upstreamUrl` is provenance only, and `fetchTlaTools` is a network-free compatibility alias for `provisionTlaTools`.
- The source is checked before copying, the owner-only temporary copy is checked again before replacement, and `verifyTlaTools` independently checks the runtime copy.
- The upstream JAR is unsigned. Repository history and the signed project archive tag attest which bytes this project preserved; they do not provide an upstream signature or independent proof of origin.
- Another process running as the same user and rewriting the checkout during provisioning is outside this threat model. Verification must run without a concurrent checkout writer. Including that attacker would require directory-handle-based file operations.
- Historical experiment inputs, receipts, reports, and assessments are unchanged.
- The final focused gate passed all 10 provisioning integration tests. The rootless Podman containment gate passed all 5 sandbox tests and left no task-owned container or volume behind.

## Final Decision

GO. There are no open Must Fix or Should Fix findings. Publication requires the signed annotated tag `toolchain/tla2tools-1.8.0-9787e65` to identify the final reviewed commit and record the artifact identity and unsigned upstream status.
